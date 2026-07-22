package com.dialed.app.transport

import android.content.Context
import android.net.Uri
import android.util.Log
import com.dialed.app.catalog.Face
import com.dialed.app.wear.common.QueryStateResult
import com.dialed.app.wear.common.WatchFaceInstallResult
import com.dialed.app.wear.common.WatchFaceUninstallResult
import com.dialed.app.wear.common.WearConstants
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.AvailabilityException
import com.google.android.gms.common.api.GoogleApi
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID

/** A reachable watch running Dialed with WFP support (advertises the install capability). */
data class ConnectedWatch(val nodeId: String, val displayName: String)

/** Progress of a phone -> watch push, surfaced in the PushToWatchSheet. */
sealed interface PushStatus {
    data object Idle : PushStatus
    data object Preparing : PushStatus
    data object Sending : PushStatus

    /** Watch reported the install finished. [needsActivation] = user must still activate it. */
    data class Done(val needsActivation: Boolean) : PushStatus
    data class Error(val message: String) : PushStatus
    data object NoWatch : PushStatus

    /** The watch answered that it has no Watch Face Push at all (Wear OS < 6) — retrying can't help. */
    data object Unsupported : PushStatus

    /**
     * The watch app hasn't been set up yet (install permission never granted). The fix is on the
     * watch — "open Dialed on your watch and tap Set up Dialed" — retrying without that can't help.
     */
    data object NeedsWatchSetup : PushStatus
}

/** Guards Data-Layer calls on devices without Google Play services / the Wearable API. */
private object WearableApiAvailability {
    suspend fun isAvailable(api: GoogleApi<*>): Boolean = try {
        GoogleApiAvailability.getInstance().checkApiAvailability(api).await()
        true
    } catch (e: AvailabilityException) {
        false
    }
}

/** Copies a bundled face APK out of assets to a real file and reads its validation token. */
class FaceAssetProvider(private val context: Context) {

    /**
     * Stages [face]'s APK to a per-transfer file the Data Layer can stream. The caller MUST delete
     * it when the transfer ends ([WatchBridge.pushFace] does, under NonCancellable) — a staged copy
     * per face would otherwise pile up the whole collection in cacheDir. Any orphans from an earlier
     * crash/kill are swept here; safe because only one push runs at a time and the sweep happens
     * before the new file is written.
     */
    suspend fun stageApk(face: Face, transferId: String): File = withContext(Dispatchers.IO) {
        sweepOrphans()
        val out = File(context.cacheDir, "$STAGE_PREFIX$transferId.apk")
        context.assets.open(face.apkAsset).use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        out
    }

    private fun sweepOrphans() {
        runCatching {
            context.cacheDir.listFiles { f -> f.name.startsWith(STAGE_PREFIX) && f.name.endsWith(".apk") }
                ?.forEach { it.delete() }
        }
    }

    suspend fun readToken(face: Face): String = withContext(Dispatchers.IO) {
        context.assets.open(face.tokenAsset).bufferedReader().use { it.readText().trim() }
    }

    private companion object {
        const val STAGE_PREFIX = "push_"
    }
}

/**
 * Phone half of the Dialed WFP bridge (adapted from androidify's WearDeviceRepository +
 * WearAssetTransmitter, minus Hilt/ProtoBuf). Discovers a Dialed-capable watch and streams a
 * bundled face APK + token to it over the Data Layer.
 */
class WatchBridge(context: Context) {

    private val appContext = context.applicationContext
    private val capabilityClient = Wearable.getCapabilityClient(appContext)
    private val messageClient = Wearable.getMessageClient(appContext)
    private val channelClient = Wearable.getChannelClient(appContext)
    private val nodeClient = Wearable.getNodeClient(appContext)
    private val assets = FaceAssetProvider(appContext)

    /**
     * Nudges [connectedWatch] to re-query the capability right now. FILTER_REACHABLE is a transient
     * snapshot that races the Data Layer's own initial sync, and OnCapabilityChangedListener does
     * NOT re-fire for a capability that was already present when we started listening — so a single
     * unlucky first query left the phone convinced the Dialed watch app was missing for the whole
     * session ("{watch} needs the Dialed watch app" over an installed watch app). The wear side
     * already self-heals its half of the link this way; this is the phone's.
     */
    private val capabilityRefresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun requestCapabilityRefresh() {
        capabilityRefresh.tryEmit(Unit)
    }

    /** Live set of reachable Dialed-capable watches (null = none / API unavailable). */
    val connectedWatch: Flow<ConnectedWatch?> = callbackFlow {
        var listener: CapabilityClient.OnCapabilityChangedListener? = null
        if (WearableApiAvailability.isAvailable(nodeClient)) {
            trySend(queryCapability())

            listener = CapabilityClient.OnCapabilityChangedListener { info ->
                trySend(select(info.nodes))
            }
            capabilityClient.addListener(listener, WearConstants.CAPABILITY_WEAR)

            // Re-query on demand (screen visible / app resumed / setup poll tick).
            launch {
                capabilityRefresh.collect { trySend(queryCapability()) }
            }
        } else {
            trySend(null)
        }
        awaitClose { listener?.let { capabilityClient.removeListener(it) } }
    }

    /**
     * One capability snapshot. A failure yields null (= not reachable) rather than throwing, but a
     * genuine cancellation still propagates so a torn-down collector never gets a fake "no watch".
     */
    private suspend fun queryCapability(): ConnectedWatch? = try {
        select(
            capabilityClient
                .getCapability(WearConstants.CAPABILITY_WEAR, CapabilityClient.FILTER_REACHABLE)
                .await().nodes,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "capability query failed", e)
        null
    }

    private fun select(nodes: Set<Node>): ConnectedWatch? =
        nodes.firstOrNull()?.let { ConnectedWatch(it.id, it.displayName) }

    /**
     * The watch's one-way "setup just finished" nudge ([WearConstants.PATH_SETUP_COMPLETE]),
     * carrying the name of the face it was holding (empty = none). The phone uses it to re-send a
     * face it had to refuse with RESPONSE_NEEDS_SETUP, so the user never has to push twice.
     *
     * A live MessageClient listener, exactly like the finalize listener in [pushFace] — no manifest
     * service, so it exists only while the phone app is running, which is precisely when there is a
     * push sheet to act on. An older watch simply never sends this.
     */
    val setupCompleteSignals: Flow<String> = callbackFlow {
        val listener = MessageClient.OnMessageReceivedListener { event ->
            if (event.path == WearConstants.PATH_SETUP_COMPLETE) {
                val faceName = String(event.data, Charsets.UTF_8)
                Log.i(TAG, "watch reported setup complete (waiting face='$faceName')")
                trySend(faceName)
            }
        }
        try {
            messageClient.addListener(listener).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "setup-complete listener registration failed", e)
        }
        awaitClose {
            runCatching { messageClient.removeListener(listener) }
        }
    }

    /**
     * Display name of ANY paired + reachable watch node — Dialed watch app or not — or null when
     * none. The difference between this and the capability node is the "watch there, Dialed watch
     * app missing" state (the capability alone reads that, falsely, as "no watch connected").
     */
    suspend fun pairedWatchName(): String? = try {
        if (!WearableApiAvailability.isAvailable(nodeClient)) null
        else nodeClient.connectedNodes.await().firstOrNull()?.displayName
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "pairedWatchName failed", e)
        null
    }

    /**
     * The reachable Dialed-capable watch's nodeId, or null if none is reachable / API unavailable.
     * Cancellation is rethrown, never reported as "no watch": a cancelled push must not emit a
     * NoWatch state into a sheet the user already dismissed.
     */
    private suspend fun reachableNodeId(): String? = try {
        capabilityClient
            .getCapability(WearConstants.CAPABILITY_WEAR, CapabilityClient.FILTER_REACHABLE)
            .await().nodes.firstOrNull()?.id
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "reachableNodeId failed", e)
        null
    }

    /**
     * Ask the watch which Dialed face is installed + which is active (slot = 1, so 0..1 installed),
     * and whether it supports Watch Face Push at all. Returns null when the watch is unreachable or
     * gives no reply — callers keep their prior state.
     *
     * Timeouts use [withTimeoutOrNull] (not `withTimeout`) so a slow watch degrades to null while a
     * genuine parent cancellation still propagates: a plain `runCatching` would swallow BOTH.
     */
    suspend fun queryInstalledState(): QueryStateResult? {
        val nodeId = reachableNodeId() ?: return null
        return try {
            val reply = withTimeoutOrNull(WearConstants.QUERY_STATE_TIMEOUT_MS) {
                messageClient.sendRequest(nodeId, WearConstants.PATH_QUERY_STATE, ByteArray(0)).await()
            } ?: return null
            WearConstants.decodeQueryState(reply)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "queryInstalledState failed", e)
            null
        }
    }

    /** Ask the watch to uninstall [face]. Returns the watch's result (FAILED if unreachable). */
    suspend fun uninstallFace(face: Face): WatchFaceUninstallResult {
        val nodeId = reachableNodeId() ?: return WatchFaceUninstallResult.FAILED
        return try {
            val reply = withTimeoutOrNull(WearConstants.QUERY_STATE_TIMEOUT_MS) {
                messageClient.sendRequest(
                    nodeId,
                    WearConstants.PATH_UNINSTALL,
                    WearConstants.encodeUninstallRequest(face.packageName),
                ).await()
            } ?: return WatchFaceUninstallResult.FAILED
            WearConstants.decodeUninstallResult(reply)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "uninstallFace failed", e)
            WatchFaceUninstallResult.FAILED
        }
    }

    /**
     * Pushes [face] to the reachable watch. Emits [PushStatus] as it progresses. The APK travels
     * over a ChannelClient file transfer (no byte-level progress from the platform), so Sending is
     * indeterminate until the watch's finalize message arrives.
     */
    suspend fun pushFace(face: Face, emit: (PushStatus) -> Unit) {
        val nodeId = reachableNodeId()
        if (nodeId == null) {
            emit(PushStatus.NoWatch)
            return
        }

        val transferId = UUID.randomUUID().toString().take(8)
        val finalizePath = WearConstants.PATH_FINALIZE_TRANSFER_TEMPLATE.format(transferId)
        val finalize = CompletableDeferred<WatchFaceInstallResult>()
        val listener = MessageClient.OnMessageReceivedListener { event ->
            if (event.path == finalizePath) {
                val decoded = WearConstants.decodeResult(event.data)
                Log.i(TAG, "finalize recv t=$transferId result=$decoded")
                finalize.complete(decoded)
            }
        }

        var staged: File? = null
        try {
            messageClient.addListener(listener).await()
            emit(PushStatus.Preparing)
            staged = assets.stageApk(face, transferId)
            val apkUri = Uri.fromFile(staged)
            val token = assets.readToken(face)

            emit(PushStatus.Sending)
            // withTimeoutOrNull, NOT withTimeout: withTimeout signals expiry by throwing
            // TimeoutCancellationException — a CancellationException — which the cancellation guard
            // below would rethrow WITHOUT emitting, leaving the sheet stuck on "Sending…" forever.
            // Timeouts must degrade to a visible error; only a genuine parent cancel propagates.
            val response = withTimeoutOrNull(WearConstants.SETUP_TIMEOUT_MS) {
                messageClient.sendRequest(
                    nodeId,
                    WearConstants.PATH_INITIATE_TRANSFER,
                    WearConstants.encodeInitiate(transferId, token, face.faceName),
                ).await()
            }
            if (response == null) {
                Log.w(TAG, "initiate timed out t=$transferId")
                emit(PushStatus.Error("Your watch didn't answer. Keep it nearby and retry."))
                return
            }
            when (response.firstOrNull()) {
                WearConstants.RESPONSE_PROCEED -> Unit // carry on below
                // The watch has no Watch Face Push at all — retrying can never help, so say so.
                WearConstants.RESPONSE_UNSUPPORTED -> {
                    Log.w(TAG, "watch reported UNSUPPORTED t=$transferId")
                    emit(PushStatus.Unsupported)
                    return
                }
                // The watch app was never set up (no install permission) — the fix is on the watch,
                // and "busy — try again" would be a lie that retrying can never repair.
                WearConstants.RESPONSE_NEEDS_SETUP -> {
                    Log.w(TAG, "watch reported NEEDS_SETUP t=$transferId")
                    emit(PushStatus.NeedsWatchSetup)
                    return
                }
                else -> {
                    emit(PushStatus.Error("Your watch is busy — try again in a moment."))
                    return
                }
            }

            val channelPath = WearConstants.PATH_TRANSFER_APK_TEMPLATE.format(transferId)
            Log.i(TAG, "push start node=$nodeId t=$transferId face=${face.id}")
            val channel = channelClient.openChannel(nodeId, channelPath).await()
            channelClient.sendFile(channel, apkUri).await()
            Log.i(TAG, "sendFile done t=$transferId, awaiting finalize")

            // Wait longer than the watch's receive+install budget so a slow success isn't
            // misreported as a timeout while the face actually changed.
            val result = withTimeoutOrNull(WearConstants.PHONE_FINALIZE_TIMEOUT_MS) { finalize.await() }
            when (result) {
                null -> {
                    Log.w(TAG, "finalize timed out t=$transferId")
                    emit(PushStatus.Error("The transfer timed out. Keep your watch close and retry."))
                }
                WatchFaceInstallResult.FAILED -> {
                    Log.w(TAG, "watch reported FAILED t=$transferId")
                    emit(PushStatus.Error("The watch couldn't install this face."))
                }
                WatchFaceInstallResult.INSTALLED_ACTIVE -> emit(PushStatus.Done(needsActivation = false))
                WatchFaceInstallResult.INSTALLED_NEEDS_ACTIVATION -> emit(PushStatus.Done(needsActivation = true))
            }
        } catch (e: CancellationException) {
            // A genuine parent cancel (the ViewModel was cleared / the app is going away) — NOT a
            // timeout, which every call above degrades to a visible error instead. Emit nothing:
            // there is no longer anyone to tell. The cleanup below still runs (NonCancellable).
            Log.i(TAG, "push cancelled t=$transferId")
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "push failed", e)
            emit(PushStatus.Error("Couldn't reach your watch. Keep it nearby and retry."))
        } finally {
            // Cleanup must survive cancellation: on a cancelled coroutine every suspend call throws
            // immediately, so without NonCancellable the listener would leak and the staged APK
            // would be left behind in cacheDir.
            withContext(NonCancellable) {
                runCatching { messageClient.removeListener(listener).await() }
                runCatching { staged?.delete() }
            }
        }
    }

    private companion object {
        const val TAG = "WatchBridge"
    }
}
