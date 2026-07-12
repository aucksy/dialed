package com.dialed.app.transport

import android.content.Context
import android.net.Uri
import android.util.Log
import com.dialed.app.catalog.Face
import com.dialed.app.wear.common.WatchFaceInstallResult
import com.dialed.app.wear.common.WearConstants
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.AvailabilityException
import com.google.android.gms.common.api.GoogleApi
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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

    suspend fun stageApk(face: Face): Uri = withContext(Dispatchers.IO) {
        val out = File(context.cacheDir, "push_${face.id}.apk")
        context.assets.open(face.apkAsset).use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        Uri.fromFile(out)
    }

    suspend fun readToken(face: Face): String = withContext(Dispatchers.IO) {
        context.assets.open(face.tokenAsset).bufferedReader().use { it.readText().trim() }
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

    /** Live set of reachable Dialed-capable watches (null = none / API unavailable). */
    val connectedWatch: Flow<ConnectedWatch?> = callbackFlow {
        var listener: CapabilityClient.OnCapabilityChangedListener? = null
        if (WearableApiAvailability.isAvailable(nodeClient)) {
            runCatching {
                val reachable = capabilityClient
                    .getCapability(WearConstants.CAPABILITY_WEAR, CapabilityClient.FILTER_REACHABLE)
                    .await()
                trySend(select(reachable.nodes))
            }.onFailure { trySend(null) }

            listener = CapabilityClient.OnCapabilityChangedListener { info ->
                trySend(select(info.nodes))
            }
            capabilityClient.addListener(listener, WearConstants.CAPABILITY_WEAR)
        } else {
            trySend(null)
        }
        awaitClose { listener?.let { capabilityClient.removeListener(it) } }
    }

    private fun select(nodes: Set<Node>): ConnectedWatch? =
        nodes.firstOrNull()?.let { ConnectedWatch(it.id, it.displayName) }

    /**
     * Pushes [face] to the reachable watch. Emits [PushStatus] as it progresses. The APK travels
     * over a ChannelClient file transfer (no byte-level progress from the platform), so Sending is
     * indeterminate until the watch's finalize message arrives.
     */
    suspend fun pushFace(face: Face, emit: (PushStatus) -> Unit) {
        val nodeId = runCatching {
            capabilityClient
                .getCapability(WearConstants.CAPABILITY_WEAR, CapabilityClient.FILTER_REACHABLE)
                .await().nodes.firstOrNull()?.id
        }.getOrNull()

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

        try {
            messageClient.addListener(listener).await()
            emit(PushStatus.Preparing)
            val apkUri = assets.stageApk(face)
            val token = assets.readToken(face)

            emit(PushStatus.Sending)
            val response = withTimeout(WearConstants.SETUP_TIMEOUT_MS) {
                messageClient.sendRequest(
                    nodeId,
                    WearConstants.PATH_INITIATE_TRANSFER,
                    WearConstants.encodeInitiate(transferId, token, face.faceName),
                ).await()
            }
            if (response.firstOrNull() != WearConstants.RESPONSE_PROCEED) {
                emit(PushStatus.Error("Your watch is busy — try again in a moment."))
                return
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
        } catch (e: Exception) {
            Log.w(TAG, "push failed", e)
            emit(PushStatus.Error("Couldn't reach your watch. Keep it nearby and retry."))
        } finally {
            runCatching { messageClient.removeListener(listener).await() }
        }
    }

    private companion object {
        const val TAG = "WatchBridge"
    }
}
