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
import com.dialed.app.wear.wfp.WfpStateStore
import com.google.android.gms.wearable.Wearable
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
        refresh()
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

    private suspend fun updateLink() {
        link.value = try {
            val nodes = Wearable.getNodeClient(getApplication()).connectedNodes.await()
            if (nodes.isNotEmpty()) WatchLink.CONNECTED else WatchLink.UNREACHABLE
        } catch (e: Exception) {
            WatchLink.UNREACHABLE
        }
    }

    private data class HomeFace(val name: String?, val preview: Bitmap?)
}
