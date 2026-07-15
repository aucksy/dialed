package com.dialed.app.wear.wfp

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.util.Log
import com.dialed.app.wear.MainActivity
import com.dialed.app.wear.common.WatchFaceActivationStrategy
import com.dialed.app.wear.common.WatchFaceInstallResult
import com.dialed.app.wear.common.WatchFaceUninstallResult
import com.dialed.app.wear.common.WearConstants
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileInputStream

/**
 * Receives a pushed face from the phone over the Data Layer and installs it via WFP.
 * Adapted from Google's androidify AndroidifyDataListenerService, minus ProtoBuf: our control
 * messages use [WearConstants]'s trivial byte encoding.
 *
 * Flow: phone MessageClient.sendRequest(/dialed/initiate_transfer) -> [onRequest] replies 1 byte
 * (proceed/busy) and captures the setup -> phone opens /dialed/transfer_apk/<id> channel ->
 * [onChannelOpened] streams the APK to a temp file, installs, optionally sets active, and
 * MessageClient.sendMessage(/dialed/finalize_transfer/<id>) back with the [WatchFaceInstallResult].
 */
class DialedListenerService : WearableListenerService() {

    private val messageClient by lazy { Wearable.getMessageClient(this) }
    private val channelClient by lazy { Wearable.getChannelClient(this) }
    private val repo by lazy { WatchFacePushRepository(this) }
    private val store by lazy { WfpStateStore(this) }

    private var timeoutJob: Job? = null

    override fun onDestroy() {
        // A WearableListenerService can be unbound + destroyed the moment its callback returns, even
        // with a receive/install still in flight. The work runs on the process-scoped [transferScope]
        // (NOT a service Job), so it survives this teardown — GMS clients are process-scoped too. If a
        // transfer is still mid-flight here, that is the teardown-race signal we log to confirm on-device.
        Log.w(TAG, "onDestroy state=${TransferSession.state.value::class.simpleName}")
        super.onDestroy()
    }

    /**
     * Phone RPCs. Initiate = "may I send a face?" (reply proceed/busy byte). Query/uninstall are
     * standalone request/reply RPCs answered here on the binder thread (mirroring the initiate path's
     * [runBlocking] on the suspend repo); a [withTimeoutOrNull] guards against a wedged WFP service so
     * the binder thread always replies before the phone's own [WearConstants.QUERY_STATE_TIMEOUT_MS].
     */
    override fun onRequest(nodeId: String, path: String, data: ByteArray): Task<ByteArray?>? {
        when (path) {
            WearConstants.PATH_QUERY_STATE ->
                return Tasks.forResult(runBlocking {
                    val s = withTimeoutOrNull(QUERY_OP_TIMEOUT_MS) { repo.installedState() }
                        ?: InstalledState(emptyList(), null)
                    WearConstants.encodeQueryState(s.activePackage, s.installedPackages)
                })
            WearConstants.PATH_UNINSTALL ->
                return Tasks.forResult(runBlocking {
                    val target = WearConstants.decodeUninstallRequest(data)
                    val result = withTimeoutOrNull(QUERY_OP_TIMEOUT_MS) { repo.removeByPackage(target) }
                        ?: WatchFaceUninstallResult.FAILED
                    WearConstants.encodeUninstallResult(result)
                })
        }
        if (!path.startsWith(WearConstants.PATH_INITIATE_TRANSFER)) {
            return Tasks.forResult(null)
        }
        val request = WearConstants.decodeInitiate(data)
        val canProceed = TransferSession.tryBegin()
        if (canProceed) {
            try {
                // Strategy is computed BEFORE install (installing makes "is my face active" flaky).
                val strategy = runBlocking { repo.activationStrategy(store) }
                TransferSession.pending = TransferSession.Pending(request, strategy)
                TransferSession.update(ReceiveState.Receiving(request.faceName))
                // Arm the timeout guard BEFORE launching the UI, so even a startActivity failure
                // still leaves a path that releases the transfer lock (the guard is the ONLY
                // terminal path when no channel opens).
                timeoutJob = transferScope.launch {
                    delay(WearConstants.TRANSFER_TIMEOUT_MS)
                    if (TransferSession.pending?.request?.transferId == request.transferId &&
                        TransferSession.state.value is ReceiveState.Receiving &&
                        TransferSession.claimTerminal()
                    ) {
                        Log.w(TAG, "setup timeout, no channel opened t=${request.transferId}")
                        fail(request.faceName)
                        TransferSession.end()
                    }
                }
                runCatching { wakeAndLaunchUi() }
            } catch (e: Exception) {
                // Setup threw before the guard could fire — release the lock so future pushes work.
                Log.w(TAG, "onRequest setup failed t=${request.transferId}", e)
                timeoutJob?.cancel()
                timeoutJob = null
                TransferSession.end()
                return Tasks.forResult(byteArrayOf(WearConstants.RESPONSE_BUSY))
            }
        }
        val response = if (canProceed) WearConstants.RESPONSE_PROCEED else WearConstants.RESPONSE_BUSY
        return Tasks.forResult(byteArrayOf(response))
    }

    /** The APK arrives on the channel. Stream it in, install, set active if possible, finalize. */
    override fun onChannelOpened(channel: ChannelClient.Channel) {
        val pending = TransferSession.pending
        if (pending == null || !channel.path.contains(pending.request.transferId)) {
            return
        }
        // Claim the finish; if the setup timeout already ended this transfer, ignore the channel.
        if (!TransferSession.claimTerminal()) {
            return
        }
        timeoutJob?.cancel()
        timeoutJob = null

        transferScope.launch {
            val request = pending.request
            val tempFile = File.createTempFile("dialed_face", ".apk", cacheDir).apply { deleteOnExit() }
            // Default so EVERY terminal path finalizes exactly once (in the finally): a failed or
            // abnormal exit reports FAILED, so the phone fails fast instead of waiting out its
            // PHONE_FINALIZE_TIMEOUT. Only a genuine install success reassigns this.
            var finalResult = WatchFaceInstallResult.FAILED
            try {
                val received = receiveToFile(channel, tempFile)
                if (!received) {
                    Log.w(TAG, "receive failed t=${request.transferId}")
                    fail(request.faceName)
                    return@launch
                }
                TransferSession.update(ReceiveState.Installing(request.faceName))
                val preview = FacePreviewExtractor.extract(this@DialedListenerService, tempFile.absolutePath)

                val installed = FileInputStream(tempFile).use { stream ->
                    repo.installOrUpdate(ParcelFileDescriptor.dup(stream.fd), request.token)
                }
                if (!installed) {
                    Log.w(TAG, "install returned false t=${request.transferId}")
                    fail(request.faceName)
                    return@launch
                }

                // ---- Installed: the face is genuinely on the watch. Nothing below may downgrade
                //      this to FAILED — post-install work is best-effort only. ----
                var nowActive = pending.strategy == WatchFaceActivationStrategy.NO_ACTION_NEEDED
                if (pending.strategy == WatchFaceActivationStrategy.CALL_SET_ACTIVE_NO_USER_ACTION) {
                    runCatching {
                        if (repo.setActive()) {
                            store.setActiveApiUsed(true) // diagnostic record; no longer gates the decision
                            nowActive = true
                        }
                    }.onFailure { Log.w(TAG, "setActive threw (install already ok) t=${request.transferId}", it) }
                }

                // Map to the UI the user actually earned. Only a genuinely-active face shows the
                // "Dialed in." celebration; an attempted-but-REFUSED unattended set (platform budget
                // spent) must degrade to the manual-gesture coach, never a false "applied".
                val uiStrategy = when {
                    nowActive -> WatchFaceActivationStrategy.NO_ACTION_NEEDED
                    pending.strategy == WatchFaceActivationStrategy.CALL_SET_ACTIVE_NO_USER_ACTION ->
                        WatchFaceActivationStrategy.LONG_PRESS_TO_SET
                    else -> pending.strategy
                }
                finalResult = if (nowActive) {
                    WatchFaceInstallResult.INSTALLED_ACTIVE
                } else {
                    WatchFaceInstallResult.INSTALLED_NEEDS_ACTIVATION
                }
                // Commit the success UI BEFORE any fallible side-effect/report so a DataStore or
                // preview-cache hiccup can never flip a real install to Failed.
                TransferSession.update(ReceiveState.Success(request.faceName, uiStrategy, preview))
                runCatching {
                    store.setLastFaceName(request.faceName)
                    preview?.let { FacePreviewExtractor.cache(this@DialedListenerService, it) }
                }.onFailure { Log.w(TAG, "post-install side-effect failed (install already ok) t=${request.transferId}", it) }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    // Don't mask a teardown/cancel as a plain install failure — surface it.
                    Log.w(TAG, "transfer CANCELLED t=${request.transferId}")
                    throw e
                }
                Log.w(TAG, "onChannelOpened failed t=${request.transferId} installed=${finalResult != WatchFaceInstallResult.FAILED}", e)
                fail(request.faceName)
            } finally {
                // Exactly one finalize per transfer, on every terminal path (success OR failure).
                runCatching { sendFinalize(channel.nodeId, request.transferId, finalResult) }
                    .onFailure { Log.w(TAG, "sendFinalize failed t=${request.transferId}", it) }
                tempFile.delete()
                TransferSession.end()
            }
        }
    }

    /** Streams the channel's file into [tempFile]; true if it closed normally (complete). */
    private suspend fun receiveToFile(channel: ChannelClient.Channel, tempFile: File): Boolean {
        val done = CompletableDeferred<Boolean>()
        val callback = object : ChannelClient.ChannelCallback() {
            override fun onInputClosed(ch: ChannelClient.Channel, closeReason: Int, appErrorCode: Int) {
                // reason 1=NORMAL (sendFile closed the stream cleanly = success), 2=DISCONNECTED,
                // 3=LOCAL_CLOSE, 4=REMOTE_CLOSE. Only NORMAL is a complete transfer (matches androidify).
                Log.w(TAG, "onInputClosed reason=$closeReason appErr=$appErrorCode")
                done.complete(closeReason == CLOSE_REASON_NORMAL)
            }
        }
        return try {
            channelClient.registerChannelCallback(channel, callback).await()
            channelClient.receiveFile(channel, Uri.fromFile(tempFile), false)
            withTimeoutOrNull(WearConstants.TRANSFER_TIMEOUT_MS) { done.await() } ?: false
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            false
        } finally {
            runCatching { channelClient.unregisterChannelCallback(callback) }
        }
    }

    /**
     * Sets the UI to the failed state. Does NOT release the transfer lock — the caller's terminal
     * path does that exactly once (onChannelOpened's finally, or the setup-timeout handler), so a
     * subsequent transfer's lock can never be clobbered by a stray release.
     */
    private fun fail(faceName: String) {
        TransferSession.update(ReceiveState.Failed(faceName))
    }

    private suspend fun sendFinalize(nodeId: String, transferId: String, result: WatchFaceInstallResult) {
        val path = WearConstants.PATH_FINALIZE_TRANSFER_TEMPLATE.format(transferId)
        messageClient.sendMessage(nodeId, path, WearConstants.encodeResult(result)).await()
    }

    /** Wake the screen and bring the receive UI forward — otherwise the catch happens unseen. */
    @SuppressLint("WearRecents")
    private fun wakeAndLaunchUi() {
        wakeDevice()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(MainActivity.EXTRA_FROM_TRANSFER, true)
        }
        startActivity(intent)
    }

    private fun wakeDevice() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            WAKELOCK_TAG,
        )
        wakeLock.acquire(WAKELOCK_TIMEOUT_MS)
        wakeLock.release()
    }

    private companion object {
        const val TAG = "DialedListener"
        const val WAKELOCK_TAG = "dialed:wear"
        const val WAKELOCK_TIMEOUT_MS = 1000L

        /** Watch-side budget for a query/uninstall op; kept below the phone's QUERY_STATE_TIMEOUT_MS
         *  so the binder thread never blocks past the phone's own wait. */
        const val QUERY_OP_TIMEOUT_MS = 10_000L

        /**
         * Process-scoped (NOT bound to this service's lifecycle): GMS may unbind + destroy an idle
         * [WearableListenerService] the moment a callback returns, but an in-flight receive/install
         * must outlive that. GMS clients (channel/message) are process-scoped, so an already-registered
         * channel callback keeps functioning after the service dies. A per-transfer timeout in
         * [receiveToFile] plus TransferSession.end() in every finally keep this from leaking coroutines.
         */
        private val transferScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
