package com.dialed.app.wear

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dialed.app.wear.common.WatchFaceActivationStrategy
import com.dialed.app.wear.ui.components.WatchLink
import com.dialed.app.wear.wfp.FacePreviewExtractor
import com.dialed.app.wear.wfp.ReceiveState
import com.dialed.app.wear.wfp.TransferSession
import com.dialed.app.wear.wfp.WatchFacePushRepository
import com.dialed.app.wear.common.WearConstants
import com.dialed.app.wear.wfp.WfpStateStore
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Everything the watch UI needs to pick a screen and render it. */
data class WearUiState(
    val supported: Boolean = true,
    val pushGranted: Boolean = false,
    val pushPermanentlyDenied: Boolean = false,
    val link: WatchLink = WatchLink.CONNECTING,
    val homeFaceName: String? = null,
    val homePreview: Bitmap? = null,
    val receive: ReceiveState = ReceiveState.Idle,
)

class WearViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = WatchFacePushRepository(app)
    private val store = WfpStateStore(app)
    private val supported = repo.isSupported()

    private val pushGranted = MutableStateFlow(repo.hasPushPermission())
    private val pushDenied = MutableStateFlow(false)
    private val link = MutableStateFlow(WatchLink.CONNECTING)
    private val homeFace = MutableStateFlow(HomeFace(null, null))

    private val capabilityClient by lazy { Wearable.getCapabilityClient(getApplication<Application>()) }
    // Live "is the phone app there?" — re-query reachability whenever the Data Layer reports the
    // Dialed phone-app capability appearing/disappearing (install/uninstall/connect/disconnect).
    private val phoneCapListener = CapabilityClient.OnCapabilityChangedListener {
        viewModelScope.launch { updateLink() }
    }

    val uiState: StateFlow<WearUiState> =
        combine(pushGranted, pushDenied, link, TransferSession.state, homeFace) { granted, denied, l, receive, home ->
            WearUiState(
                supported = supported,
                pushGranted = granted,
                pushPermanentlyDenied = denied,
                link = l,
                homeFaceName = home.name,
                homePreview = home.preview,
                receive = receive,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            WearUiState(supported = supported, pushGranted = pushGranted.value),
        )

    init {
        capabilityClient.addListener(phoneCapListener, WearConstants.CAPABILITY_PHONE)
        refresh()
        // Self-heal a stale link: FILTER_REACHABLE is a transient snapshot and the capability
        // listener does not always fire on a silent transport blip, so re-check on a gentle cadence
        // while the ViewModel is alive (the reported "Connected" flapping / stuck not-reachable).
        viewModelScope.launch {
            while (true) {
                delay(LINK_RECHECK_MS)
                updateLink()
            }
        }
    }

    override fun onCleared() {
        capabilityClient.removeListener(phoneCapListener)
        super.onCleared()
    }

    /** Re-read permission + connection + last-face state (call from Activity onResume). */
    fun refresh() {
        pushGranted.value = repo.hasPushPermission()
        viewModelScope.launch { updateLink() }
        viewModelScope.launch { loadHome() }
    }

    fun onPushPermissionResult(granted: Boolean) {
        pushGranted.value = granted
        pushDenied.value = !granted
    }

    /** Concierge one-tap: the set-active permission result came back. Spend it here (one-shot). */
    fun onSetActivePermissionResult(granted: Boolean) {
        viewModelScope.launch {
            store.setPermissionDenied(!granted)
            val current = TransferSession.state.value as? ReceiveState.Success ?: return@launch
            if (granted && repo.setActive()) {
                store.setActiveApiUsed(true)
                TransferSession.update(
                    current.copy(strategy = WatchFaceActivationStrategy.NO_ACTION_NEEDED),
                )
                loadHome()
            } else {
                // Permission denied, or the one-shot API was already spent -> teach the gesture.
                TransferSession.update(
                    current.copy(strategy = WatchFaceActivationStrategy.LONG_PRESS_TO_SET),
                )
            }
        }
    }

    /** Dismiss a finished receive/concierge flow back to Home. */
    fun dismissReceive() {
        TransferSession.clear()
        refresh()
    }

    private suspend fun loadHome() {
        homeFace.value = HomeFace(
            name = store.lastFaceName.first(),
            preview = FacePreviewExtractor.loadCached(getApplication()),
        )
    }

    /**
     * "Connected" means the Dialed PHONE APP is reachable — a node that advertises
     * [WearConstants.CAPABILITY_PHONE] — NOT merely that some phone is paired. Otherwise the watch
     * says "Connected" with no phone app to push from (issue #4). A reachable node with the
     * capability => CONNECTED; a paired-but-appless (or absent) phone => UNREACHABLE.
     */
    private suspend fun updateLink() {
        // FILTER_REACHABLE is a transient snapshot that also races the Data Layer's initial
        // capability sync right after launch, so a single empty result must NOT immediately flap the
        // badge to "not reachable". Retry a few times before concluding UNREACHABLE; a reachable node
        // settles to CONNECTED instantly. While retrying the badge holds its value (starts CONNECTING).
        repeat(REACH_ATTEMPTS) { attempt ->
            val reachable = try {
                capabilityClient
                    .getCapability(WearConstants.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
                    .await().nodes
            } catch (e: Exception) {
                emptySet<Node>()
            }
            if (reachable.isNotEmpty()) {
                link.value = WatchLink.CONNECTED
                return
            }
            if (attempt < REACH_ATTEMPTS - 1) delay(REACH_RETRY_MS)
        }
        link.value = WatchLink.UNREACHABLE
    }

    private data class HomeFace(val name: String?, val preview: Bitmap?)

    private companion object {
        const val REACH_ATTEMPTS = 3       // reachability probes before declaring UNREACHABLE
        const val REACH_RETRY_MS = 1200L   // wait between probes (rides out a transport blip)
        const val LINK_RECHECK_MS = 12_000L // periodic self-heal cadence while alive
    }
}
