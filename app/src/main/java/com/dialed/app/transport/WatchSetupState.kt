package com.dialed.app.transport

/**
 * Result of probing for ANY paired + reachable watch node (Dialed watch app or not) via NodeClient.
 * The difference between this and the Dialed-capability node IS the "watch there, Dialed watch app
 * missing" state the capability alone can never see (docs/ONBOARDING-REDESIGN.md §1.1/P4).
 */
sealed interface PairedProbe {
    /** Not probed yet (first launch instant). */
    data object Unknown : PairedProbe

    /** No paired watch is currently reachable. */
    data object None : PairedProbe

    /** At least one watch is paired + reachable; [name] is its display name. */
    data class Found(val name: String) : PairedProbe
}

/**
 * The Setup screen's 6 live states (docs/ONBOARDING-REDESIGN.md §5.1). Derived in MainViewModel
 * from three signals the app already produces: the paired-node probe, the Dialed-capability node,
 * and the watch's own query-state answer (supported + watch-side setup done).
 */
enum class WatchSetupState {
    /** Probes still running — first moments only. */
    CHECKING,

    /** Dialed watch app reachable, watch supported, watch-side setup done (or unknown = assumed). */
    READY,

    /** A watch is paired + reachable but the Dialed watch app is not on it. */
    WATCH_APP_MISSING,

    /** Watch app installed but its one-time setup hasn't been done (watch reported no permission). */
    OPEN_ON_WATCH,

    /** No watch paired with this phone (or none reachable). */
    NO_WATCH,

    /** Watch found but on Wear OS < 6 — can never install faces. */
    UNSUPPORTED,
}

/** State + the watch's display name (when one is known) for the Setup screen's copy. */
data class WatchSetup(
    val state: WatchSetupState = WatchSetupState.CHECKING,
    val watchName: String? = null,
)
