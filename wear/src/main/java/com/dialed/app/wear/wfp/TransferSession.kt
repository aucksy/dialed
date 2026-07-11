package com.dialed.app.wear.wfp

import android.graphics.Bitmap
import com.dialed.app.wear.common.InitiateRequest
import com.dialed.app.wear.common.WatchFaceActivationStrategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/** What the receive UI is showing, driven by [DialedListenerService] over one process. */
sealed interface ReceiveState {
    data object Idle : ReceiveState

    /** APK is streaming in (Data Layer gives no byte progress -> indeterminate). */
    data class Receiving(val faceName: String) : ReceiveState

    /** Bytes are in; WFP is installing. */
    data class Installing(val faceName: String) : ReceiveState

    /** Installed. [strategy] says whether it's already active or needs a user action. */
    data class Success(
        val faceName: String,
        val strategy: WatchFaceActivationStrategy,
        val preview: Bitmap?,
    ) : ReceiveState

    data class Failed(val faceName: String) : ReceiveState
}

/**
 * Process-wide bridge between [DialedListenerService] (which runs in the background and does the
 * install) and the Activity/ViewModel (which animates the receive flow). The service and the
 * Activity share one process, so a singleton StateFlow is enough — no serialization/DataStore.
 * [tryBegin] enforces a single concurrent transfer (mirrors androidify's static AtomicBoolean).
 */
object TransferSession {
    private val inProgress = AtomicBoolean(false)
    private val terminalClaimed = AtomicBoolean(false)

    /** Setup details captured in onRequest and read back in onChannelOpened. Held statically so a
     *  recreated service instance can still finish the channel. */
    data class Pending(val request: InitiateRequest, val strategy: WatchFaceActivationStrategy)

    @Volatile
    var pending: Pending? = null

    private val _state = MutableStateFlow<ReceiveState>(ReceiveState.Idle)
    val state: StateFlow<ReceiveState> = _state.asStateFlow()

    /** True if this call acquired the single transfer slot; false if one is already running. */
    fun tryBegin(): Boolean {
        val acquired = inProgress.compareAndSet(false, true)
        if (acquired) terminalClaimed.set(false)
        return acquired
    }

    /**
     * Exactly one of the two terminal paths — the setup timeout (no channel arrived) or
     * onChannelOpened (channel arrived) — may drive the finish. Whichever calls this first wins;
     * the loser must no-op. Prevents a ~60s-boundary race where both fire.
     */
    fun claimTerminal(): Boolean = terminalClaimed.compareAndSet(false, true)

    fun end() {
        inProgress.set(false)
    }

    fun update(newState: ReceiveState) {
        _state.value = newState
    }

    /** Clears back to Idle (used when the Activity dismisses a finished receive flow). */
    fun clear() {
        _state.value = ReceiveState.Idle
        pending = null
    }
}
