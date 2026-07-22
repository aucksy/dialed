package com.dialed.app.model

/** Connection state of the paired watch (HANDOFF.md §6 WatchStatusPill).
 *  [APP_MISSING] = a watch is paired + reachable but the Dialed watch app isn't on it — previously
 *  indistinguishable from DISCONNECTED, which read (falsely) as "No watch connected".
 *  [NEEDS_SETUP] = the watch app is there and reachable, but its one-time setup was never finished,
 *  so every push is refused (RESPONSE_NEEDS_SETUP). Reporting that as "Connected" was true about
 *  the link and misleading about what would happen on Install. */
enum class WatchConnection { CONNECTED, DISCONNECTED, CONNECTING, UNSUPPORTED, APP_MISSING, NEEDS_SETUP }

/**
 * [deviceName] is the Data Layer node's display name. There is deliberately no `wearOsVersion` /
 * `activeFaceName` here: the Data Layer never reports the former, and the latter is already
 * modelled properly as `MainViewModel.activeFaceId` (resolved against the catalog).
 */
data class WatchStatus(
    val connection: WatchConnection = WatchConnection.DISCONNECTED,
    val deviceName: String? = null,
) {
    val isConnected: Boolean get() = connection == WatchConnection.CONNECTED

    /** A watch is present but can never install faces (Wear OS < 6). */
    val isUnsupported: Boolean get() = connection == WatchConnection.UNSUPPORTED
}
