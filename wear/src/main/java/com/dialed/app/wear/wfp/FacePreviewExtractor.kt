package com.dialed.app.wear.wfp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import java.io.File

/**
 * Pulls the `preview` drawable out of a received (not-yet-installed) face APK so the watch UI
 * can show the real face rather than a placeholder. Resources are resolved by NAME, which is
 * why this survives the facepacks' minify path-shortening (arsc names are kept — the WFP
 * validator relies on the same fact). Best-effort: any failure falls back to the dial mark.
 */
object FacePreviewExtractor {
    private const val CACHE_FILE = "last_face_preview.png"

    /** The package name declared by a received (not-yet-installed) face APK. Used so Home can match
     *  the last-installed package against what WFP currently reports installed. Best-effort. */
    fun packageOf(context: Context, apkPath: String): String? = try {
        context.packageManager.getPackageArchiveInfo(apkPath, 0)?.packageName
    } catch (e: Exception) {
        null
    }

    fun extract(context: Context, apkPath: String): Bitmap? = try {
        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(apkPath, 0)
        val appInfo = info?.applicationInfo
        if (appInfo == null) {
            null
        } else {
            appInfo.sourceDir = apkPath
            appInfo.publicSourceDir = apkPath
            val res = pm.getResourcesForApplication(appInfo)
            val id = res.getIdentifier("preview", "drawable", info.packageName)
            if (id == 0) null else ResourcesCompat.getDrawable(res, id, null)?.toBitmap()?.centerSquare()
        }
    } catch (e: Exception) {
        null
    }

    /**
     * Center-crop to a square so the circular [FaceDial] never letterboxes/stretches a non-square
     * source (defensive — the bundled previews are already square). Dialed's own surfaces are thus
     * always a clean circle; any oval the user sees during apply is the platform's system
     * "applying watch face" thumbnail, which Dialed cannot reshape.
     */
    private fun Bitmap.centerSquare(): Bitmap {
        if (width == height) return this
        val side = minOf(width, height)
        return Bitmap.createBitmap(this, (width - side) / 2, (height - side) / 2, side, side)
    }

    fun cache(context: Context, bitmap: Bitmap) {
        try {
            File(context.filesDir, CACHE_FILE).outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } catch (e: Exception) {
            // Non-fatal: Home just shows the placeholder dial if the cache write fails.
        }
    }

    fun loadCached(context: Context): Bitmap? = try {
        val f = File(context.filesDir, CACHE_FILE)
        if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    } catch (e: Exception) {
        null
    }
}
