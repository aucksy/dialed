package com.dialed.app.model

/** Connection state of the paired watch (HANDOFF.md §6 WatchStatusPill). */
enum class WatchConnection { CONNECTED, DISCONNECTED, CONNECTING, UNSUPPORTED }

data class WatchStatus(
    val connection: WatchConnection = WatchConnection.DISCONNECTED,
    val deviceName: String? = null,
    val wearOsVersion: String? = null,
    val activeFaceName: String? = null,
) {
    val isConnected: Boolean get() = connection == WatchConnection.CONNECTED
}
