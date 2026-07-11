package com.dialed.app.wear.wfp

import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import androidx.wear.watchfacepush.WatchFacePushManager
import androidx.wear.watchfacepush.WatchFacePushManagerFactory
import com.dialed.app.wear.common.WatchFaceActivationStrategy
import com.dialed.app.wear.common.WearConstants
import kotlinx.coroutines.flow.first

/**
 * Thin wrapper over androidx.wear.watchfacepush 1.0.0 (the Kotlin suspend API — NOT the
 * com.google.wear.services.* platform class). Slot = 1 on Wear OS 6, so installing face B over
 * A is an [updateWatchFace] full replace; slotId is a String and is queried FRESH each time
 * (never persisted). Every op is guarded so a WFP failure returns false, never crashes.
 */
class WatchFacePushRepository(private val context: Context) {

    /** True on Wear OS 6+ where WFP exists. Call BEFORE [manager] — the factory throws otherwise. */
    fun isSupported(): Boolean = WatchFacePushManagerFactory.isSupported()

    private fun manager(): WatchFacePushManager =
        WatchFacePushManagerFactory.createWatchFacePushManager(context)

    /** add-or-update the single WFP slot. Returns true on success. */
    suspend fun installOrUpdate(apkFd: ParcelFileDescriptor, token: String): Boolean {
        val wfp = manager()
        val response = wfp.listWatchFaces()
        return try {
            if (response.remainingSlotCount > 0) {
                wfp.addWatchFace(apkFd, token)
            } else {
                val slotId = response.installedWatchFaceDetails.first().slotId
                wfp.updateWatchFace(slotId, apkFd, token)
            }
            true
        } catch (e: WatchFacePushManager.AddWatchFaceException) {
            false
        } catch (e: WatchFacePushManager.UpdateWatchFaceException) {
            false
        }
    }

    /** Set the pushed face active with no user action (permission-gated, one-shot). */
    suspend fun setActive(): Boolean {
        val wfp = manager()
        val slotId = wfp.listWatchFaces().installedWatchFaceDetails.firstOrNull()?.slotId ?: return false
        return try {
            wfp.setWatchFaceAsActive(slotId)
            true
        } catch (e: WatchFacePushManager.SetWatchFaceAsActiveException) {
            false
        }
    }

    suspend fun hasActiveWatchFace(): Boolean = try {
        val wfp = manager()
        wfp.listWatchFaces().installedWatchFaceDetails.any { wfp.isWatchFaceActive(it.packageName) }
    } catch (e: Exception) {
        false
    }

    suspend fun hasInstalledFace(): Boolean = try {
        manager().listWatchFaces().installedWatchFaceDetails.isNotEmpty()
    } catch (e: Exception) {
        false
    }

    fun hasPushPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, WearConstants.PERMISSION_PUSH) ==
            PackageManager.PERMISSION_GRANTED

    fun hasSetActivePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, WearConstants.PERMISSION_SET_ACTIVE) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Computed BEFORE install — installing/switching briefly makes "do I own the active face"
     * inaccurate, so the strategy is decided up front and carried through the transfer.
     */
    suspend fun activationStrategy(store: WfpStateStore): WatchFaceActivationStrategy =
        WatchFaceActivationStrategy.fromWatchFaceState(
            hasActiveWatchFace = hasActiveWatchFace(),
            hasGrantedSetActivePermission = hasSetActivePermission(),
            canRequestSetActivePermission = !store.permissionDenied.first(),
            hasUsedSetActiveApi = store.setActiveApiUsed.first(),
        )
}
