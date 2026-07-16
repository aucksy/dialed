package com.dialed.app.model

/** Connection state of the paired watch (HANDOFF.md §6 WatchStatusPill). */
enum class WatchConnection { CONNECTED, DISCONNECTED, CONNECTING, UNSUPPORTED }

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
