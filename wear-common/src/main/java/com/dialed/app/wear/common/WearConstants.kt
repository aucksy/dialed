package com.dialed.app.wear.common

/**
 * Shared phone <-> watch Data Layer contract for the Dialed WFP push (adapted from Google's
 * androidify sample). Kept dependency-free: control messages use trivial UTF-8/byte encoding
 * (no kotlinx-serialization) — the transferId travels in the message/channel PATH, the token
 * and face name in the setup message payload, and the APK bytes over a ChannelClient file
 * transfer. Both modules ([:app] phone sender, [:wear] watch receiver) call these helpers so
 * the wire format has exactly one definition.
 */
object WearConstants {
    /** Phone -> watch RPC (MessageClient.sendRequest): "may I send a face?". */
    const val PATH_INITIATE_TRANSFER = "/dialed/initiate_transfer"

    /** Phone -> watch channel that streams the face APK. Full path = template.format(transferId). */
    const val PATH_TRANSFER_APK_TEMPLATE = "/dialed/transfer_apk/%s"

    /** Watch -> phone completion message. Full path = template.format(transferId). */
    const val PATH_FINALIZE_TRANSFER_TEMPLATE = "/dialed/finalize_transfer/%s"

    /** Phone -> watch RPC (MessageClient.sendRequest, no payload): "report your marketplace state". */
    const val PATH_QUERY_STATE = "/dialed/query_state"

    /** Phone -> watch RPC (sendRequest): "uninstall this face". Payload = target package name (UTF-8). */
    const val PATH_UNINSTALL = "/dialed/uninstall"

    /** CapabilityClient capability the WATCH advertises when WFP is supported (res/values/wear.xml). */
    const val CAPABILITY_WEAR = "dialed_wfp_install"

    /** Runtime permission required to programmatically set the pushed face active (one-shot). */
    const val PERMISSION_SET_ACTIVE = "com.google.wear.permission.SET_PUSHED_WATCH_FACE_AS_ACTIVE"

    /** Install permission required to push faces at all. */
    const val PERMISSION_PUSH = "com.google.wear.permission.PUSH_WATCH_FACES"

    const val SETUP_TIMEOUT_MS = 60_000L
    const val TRANSFER_TIMEOUT_MS = 60_000L

    /** How long the PHONE waits for a query/uninstall reply before treating the watch as unreachable. */
    const val QUERY_STATE_TIMEOUT_MS = 12_000L

    /**
     * How long the PHONE waits for the watch's finalize message. Must be strictly larger than the
     * watch's receive ([TRANSFER_TIMEOUT_MS]) + install budget, or a slow-but-successful push is
     * reported to the user as a failure while the face actually changed on the watch.
     */
    const val PHONE_FINALIZE_TIMEOUT_MS = 120_000L

    /** InitialResponse over MessageClient.sendRequest: single byte, 1 = proceed, 0 = busy. */
    const val RESPONSE_PROCEED: Byte = 1
    const val RESPONSE_BUSY: Byte = 0

    private const val SEP = "\n"

    /** Setup payload: transferId + validation token + human face name, newline-separated UTF-8. */
    fun encodeInitiate(transferId: String, token: String, faceName: String): ByteArray =
        listOf(transferId, token, faceName).joinToString(SEP).toByteArray(Charsets.UTF_8)

    fun decodeInitiate(bytes: ByteArray): InitiateRequest {
        val parts = String(bytes, Charsets.UTF_8).split(SEP, limit = 3)
        return InitiateRequest(
            transferId = parts.getOrElse(0) { "" },
            token = parts.getOrElse(1) { "" },
            faceName = parts.getOrElse(2) { "" },
        )
    }

    /** Completion payload: a single byte = [WatchFaceInstallResult.ordinal]. */
    fun encodeResult(result: WatchFaceInstallResult): ByteArray = byteArrayOf(result.ordinal.toByte())

    fun decodeResult(bytes: ByteArray): WatchFaceInstallResult {
        val idx = bytes.firstOrNull()?.toInt() ?: return WatchFaceInstallResult.FAILED
        return WatchFaceInstallResult.values().getOrNull(idx) ?: WatchFaceInstallResult.FAILED
    }

    /**
     * Query-state reply, newline-separated UTF-8 (same shape as [encodeInitiate]):
     * line 0 = the active Dialed package (empty string = none of our faces is active),
     * lines 1..N = every installed Dialed package (N = 0 or 1 on Wear OS 6, slot limit = 1).
     */
    fun encodeQueryState(activePackage: String?, installedPackages: List<String>): ByteArray =
        (listOf(activePackage.orEmpty()) + installedPackages)
            .joinToString(SEP).toByteArray(Charsets.UTF_8)

    fun decodeQueryState(bytes: ByteArray): QueryStateResult {
        val lines = String(bytes, Charsets.UTF_8).split(SEP)
        val active = lines.getOrElse(0) { "" }.ifEmpty { null }
        val installed = lines.drop(1).filter { it.isNotEmpty() }
        return QueryStateResult(activePackage = active, installedPackages = installed)
    }

    /** Uninstall request payload = the target package name (the phone knows its catalog packages). */
    fun encodeUninstallRequest(packageName: String): ByteArray =
        packageName.toByteArray(Charsets.UTF_8)

    fun decodeUninstallRequest(bytes: ByteArray): String =
        String(bytes, Charsets.UTF_8).trim()

    /** Uninstall reply: single byte = [WatchFaceUninstallResult.ordinal] (same shape as [encodeResult]). */
    fun encodeUninstallResult(result: WatchFaceUninstallResult): ByteArray =
        byteArrayOf(result.ordinal.toByte())

    fun decodeUninstallResult(bytes: ByteArray): WatchFaceUninstallResult {
        val idx = bytes.firstOrNull()?.toInt() ?: return WatchFaceUninstallResult.FAILED
        return WatchFaceUninstallResult.values().getOrNull(idx) ?: WatchFaceUninstallResult.FAILED
    }
}

/** Decoded setup request the watch reads from the initiate message. */
data class InitiateRequest(
    val transferId: String,
    val token: String,
    val faceName: String,
)

/**
 * Outcome of a watch-side install, reported back to the phone in the finalize message.
 * ORDINAL-STABLE: the wire value is [Enum.ordinal] — only APPEND new entries, never reorder.
 */
enum class WatchFaceInstallResult {
    INSTALLED_ACTIVE, // installed AND now the active face (no user action needed)
    INSTALLED_NEEDS_ACTIVATION, // installed; user/permission action needed to activate
    FAILED,
}

/** Decoded query-state reply: which Dialed face(s) are installed on the watch + which is active. */
data class QueryStateResult(
    val activePackage: String?,
    val installedPackages: List<String>,
)

/**
 * Outcome of a watch-side uninstall, reported back to the phone.
 * ORDINAL-STABLE: the wire value is [Enum.ordinal] — only APPEND new entries, never reorder.
 */
enum class WatchFaceUninstallResult { REMOVED, NOT_FOUND, FAILED }
