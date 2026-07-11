package com.dialed.app.wear.common

/**
 * Shared phone <-> watch Data Layer contract for the Dialed WFP push (adapted from Google's
 * androidify sample). Kept dependency-free: control messages use trivial UTF-8/byte encoding
 * (no kotlinx-serialization) — the transferId travels in the message/channel PATH, the token
 * in the setup message payload, and the APK bytes over a ChannelClient file transfer.
 */
object WearConstants {
    /** Phone -> watch RPC: "may I send a face?" payload = "<transferId>\n<token>". */
    const val PATH_INITIATE_TRANSFER = "/dialed/initiate_transfer"

    /** Phone -> watch channel that streams the face APK. Format arg = transferId. */
    const val PATH_TRANSFER_APK_TEMPLATE = "/dialed/transfer_apk/%s"

    /** Watch -> phone completion message. Format arg = transferId. */
    const val PATH_FINALIZE_TRANSFER_TEMPLATE = "/dialed/finalize_transfer/%s"

    /** CapabilityClient capability the WATCH advertises when WFP is supported. */
    const val CAPABILITY_WEAR = "dialed_wfp_install"

    /** Runtime permission required to programmatically set the pushed face active (one-shot). */
    const val PERMISSION_SET_ACTIVE = "com.google.wear.permission.SET_PUSHED_WATCH_FACE_AS_ACTIVE"

    const val SETUP_TIMEOUT_MS = 60_000L
    const val TRANSFER_TIMEOUT_MS = 60_000L

    /** InitialResponse over MessageClient.sendRequest: single byte, 1 = proceed. */
    const val RESPONSE_PROCEED: Byte = 1
    const val RESPONSE_BUSY: Byte = 0
}

/** Outcome of a watch-side install, reported back to the phone. */
enum class WatchFaceInstallResult {
    INSTALLED_ACTIVE, // installed AND now the active face (no user action needed)
    INSTALLED_NEEDS_ACTIVATION, // installed; user/permission action needed to activate
    FAILED,
}
