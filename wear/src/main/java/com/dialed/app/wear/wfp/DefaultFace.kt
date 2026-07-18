package com.dialed.app.wear.wfp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The bundled Dialed DEFAULT face (the `:watchface` module, staged into `assets/` by CI). It is the
 * cornerstone of the ownership chain: onboarding installs + activates it so Dialed owns the active
 * slot, after which every pushed face updates the slot in place and stays active with no set-active,
 * no permission, no long-press. Uninstall reverts the slot to it rather than emptying it, so
 * ownership is never surrendered.
 *
 * Its package is a normal `com.dialed.app.watchfacepush.*` id, so the WFP repository treats it like
 * any other Dialed face — [hasActiveWatchFace] counts it as "we own the active face" automatically.
 * The phone's catalog does NOT list it, so the phone maps it to no face (correctly showing "nothing
 * installed" after an uninstall while the chain quietly persists on the watch).
 */
object DefaultFace {
    /** MUST match `:watchface`'s applicationId. */
    const val PACKAGE = "com.dialed.app.watchfacepush.dialed.classic"

    /** Shown on the watch Home when the default is the installed face (instead of the derived "Classic"). */
    const val DISPLAY_NAME = "Dialed"

    const val APK_ASSET = "default_watchface.apk"
    const val TOKEN_ASSET = "default_watchface.token"
}

/**
 * Stages the bundled default face out of `assets/` (mirrors the phone's FaceAssetProvider). Every
 * method is guarded so a build WITHOUT the CI-bundled default (e.g. a local dev build) degrades to a
 * clean null rather than crashing — the onboarding/uninstall callers all treat null as "no default
 * available" and fall back.
 */
class DefaultFaceProvider(private val context: Context) {

    /** True when both the APK and its token are present in assets. */
    fun isBundled(): Boolean = try {
        context.assets.open(DefaultFace.APK_ASSET).close()
        context.assets.open(DefaultFace.TOKEN_ASSET).close()
        true
    } catch (e: Exception) {
        false
    }

    /** Copy the bundled APK to a private cache file + read its token; null if either asset is absent. */
    suspend fun stage(): Staged? = withContext(Dispatchers.IO) {
        try {
            val token = context.assets.open(DefaultFace.TOKEN_ASSET)
                .bufferedReader().use { it.readText().trim() }
            if (token.isEmpty()) {
                Log.w(TAG, "default face token asset is empty")
                return@withContext null
            }
            val out = File.createTempFile("dialed_default", ".apk", context.cacheDir).apply { deleteOnExit() }
            context.assets.open(DefaultFace.APK_ASSET).use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            Staged(out, token)
        } catch (e: Exception) {
            Log.w(TAG, "default face not bundled / stage failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /** A staged copy of the default APK + its validation token. Delete [apk] when done. */
    data class Staged(val apk: File, val token: String)

    private companion object {
        const val TAG = "DialedDefaultFace"
    }
}
