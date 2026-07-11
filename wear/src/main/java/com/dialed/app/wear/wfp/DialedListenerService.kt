package com.dialed.app.wear.wfp

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import com.dialed.app.wear.MainActivity
import com.dialed.app.wear.common.WatchFaceActivationStrategy
import com.dialed.app.wear.common.WatchFaceInstallResult
import com.dialed.app.wear.common.WearConstants
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
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

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val messageClient by lazy { Wearable.getMessageClient(this) }
    private val channelClient by lazy { Wearable.getChannelClient(this) }
    private val repo by lazy { WatchFacePushRepository(this) }
    private val store by lazy { WfpStateStore(this) }

    private var timeoutJob: Job? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    /** Phone asks "may I send a face?". Reply a single byte: proceed (1) or busy (0). */
    override fun onRequest(nodeId: String, path: String, data: ByteArray): Task<ByteArray?>? {
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
                timeoutJob = serviceScope.launch {
                    delay(WearConstants.TRANSFER_TIMEOUT_MS)
                    if (TransferSession.pending?.request?.transferId == request.transferId &&
                        TransferSession.state.value is ReceiveState.Receiving &&
                        TransferSession.claimTerminal()
                    ) {
                        fail(request.faceName)
                        TransferSession.end()
                    }
                }
                runCatching { wakeAndLaunchUi() }
            } catch (e: Exception) {
                // Setup threw before the guard could fire — release the lock so future pushes work.
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

        serviceScope.launch {
            val request = pending.request
            val tempFile = File.createTempFile("dialed_face", ".apk", cacheDir).apply { deleteOnExit() }
            try {
                val received = receiveToFile(channel, tempFile)
                if (!received) {
                    fail(request.faceName)
                    return@launch
                }
                TransferSession.update(ReceiveState.Installing(request.faceName))
                val preview = FacePreviewExtractor.extract(this@DialedListenerService, tempFile.absolutePath)

                val installed = FileInputStream(tempFile).use { stream ->
                    repo.installOrUpdate(ParcelFileDescriptor.dup(stream.fd), request.token)
                }
                if (!installed) {
                    fail(request.faceName)
                    return@launch
                }

                // If we can silently set it active, do it now (one-shot).
                var nowActive = pending.strategy == WatchFaceActivationStrategy.NO_ACTION_NEEDED
                if (pending.strategy == WatchFaceActivationStrategy.CALL_SET_ACTIVE_NO_USER_ACTION) {
                    if (repo.setActive()) {
                        store.setActiveApiUsed(true)
                        nowActive = true
                    }
                }

                store.setLastFaceName(request.faceName)
                preview?.let { FacePreviewExtractor.cache(this@DialedListenerService, it) }

                val uiStrategy = if (nowActive) WatchFaceActivationStrategy.NO_ACTION_NEEDED else pending.strategy
                val result = if (nowActive) {
                    WatchFaceInstallResult.INSTALLED_ACTIVE
                } else {
                    WatchFaceInstallResult.INSTALLED_NEEDS_ACTIVATION
                }
                TransferSession.update(ReceiveState.Success(request.faceName, uiStrategy, preview))
                sendFinalize(channel.nodeId, request.transferId, result)
            } catch (e: Exception) {
                fail(request.faceName)
                runCatching { sendFinalize(channel.nodeId, request.transferId, WatchFaceInstallResult.FAILED) }
            } finally {
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
                done.complete(closeReason == CLOSE_REASON_NORMAL)
            }
        }
        return try {
            channelClient.registerChannelCallback(channel, callback).await()
            channelClient.receiveFile(channel, Uri.fromFile(tempFile), false)
            withTimeoutOrNull(WearConstants.TRANSFER_TIMEOUT_MS) { done.await() } ?: false
        } catch (e: Exception) {
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
        const val WAKELOCK_TAG = "dialed:wear"
        const val WAKELOCK_TIMEOUT_MS = 1000L
    }
}
