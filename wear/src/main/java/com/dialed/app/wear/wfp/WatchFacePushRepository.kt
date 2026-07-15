package com.dialed.app.wear.wfp

import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.wear.watchfacepush.WatchFacePushManager
import androidx.wear.watchfacepush.WatchFacePushManagerFactory
import com.dialed.app.wear.common.WatchFaceActivationStrategy
import com.dialed.app.wear.common.WatchFaceUninstallResult
import com.dialed.app.wear.common.WearConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

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

    /**
     * add-or-update the single WFP slot. Returns true on success, false on ANY failure (never throws)
     * — [manager]/[listWatchFaces] are INSIDE the try so a cold Watch-Face-Receiver-Service bind
     * (ReceiverConnectionException) or an empty-list edge is a clean, logged false rather than an
     * exception that escapes to the listener service and surfaces as a false "Interrupted".
     */
    suspend fun installOrUpdate(apkFd: ParcelFileDescriptor, token: String): Boolean = try {
        withTimeoutOrNull(INSTALL_TIMEOUT_MS) {
            val wfp = manager()
            val response = wfp.listWatchFaces()
            if (response.remainingSlotCount > 0) {
                wfp.addWatchFace(apkFd, token)
            } else {
                val slotId = response.installedWatchFaceDetails.first().slotId
                wfp.updateWatchFace(slotId, apkFd, token)
            }
            true
        } ?: run {
            // A wedged Watch-Face-Push service must not hang the receive coroutine forever (that
            // leaks the single-transfer lock -> every later push replies BUSY). Time out to a clean
            // false so the listener finalizes FAILED and releases the lock.
            Log.w(TAG, "installOrUpdate timed out after ${INSTALL_TIMEOUT_MS}ms")
            false
        }
    } catch (e: WatchFacePushManager.AddWatchFaceException) {
        Log.w(TAG, "addWatchFace failed: ${e.message}", e)
        false
    } catch (e: WatchFacePushManager.UpdateWatchFaceException) {
        Log.w(TAG, "updateWatchFace failed: ${e.message}", e)
        false
    } catch (e: CancellationException) {
        throw e // never swallow structured-concurrency / timeout cancellation
    } catch (e: Exception) {
        // ReceiverConnectionException (cold bind), NoSuchElementException, unsupported-factory, etc.
        Log.w(TAG, "installOrUpdate unexpected ${e.javaClass.simpleName}: ${e.message}", e)
        false
    }

    /**
     * Set the pushed face active with no user action. The unattended set-active allowance is a
     * PLATFORM budget — this throws [WatchFacePushManager.SetWatchFaceAsActiveException]
     * (ERROR_MAXIMUM_ATTEMPTS) once it's spent and the live face is another app's, which is the
     * expected "first of the day" refusal. Returns true only on a real switch; never throws.
     */
    suspend fun setActive(): Boolean = try {
        withTimeoutOrNull(SET_ACTIVE_TIMEOUT_MS) {
            val wfp = manager()
            val slotId = wfp.listWatchFaces().installedWatchFaceDetails.firstOrNull()?.slotId
            if (slotId == null) {
                false
            } else {
                wfp.setWatchFaceAsActive(slotId)
                true
            }
        } ?: run {
            Log.w(TAG, "setActive timed out after ${SET_ACTIVE_TIMEOUT_MS}ms")
            false
        }
    } catch (e: WatchFacePushManager.SetWatchFaceAsActiveException) {
        // Expected when the platform's unattended budget is exhausted (e.g. re-seizing from a foreign
        // active face). Logged verbatim so an on-wrist test can read the exact code and confirm
        // whether the budget ever resets. The caller falls back to teaching the manual gesture.
        Log.w(TAG, "setWatchFaceAsActive refused (unattended budget?): ${e.message}", e)
        false
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "setActive unexpected ${e.javaClass.simpleName}: ${e.message}", e)
        false
    }

    suspend fun hasActiveWatchFace(): Boolean = try {
        withTimeoutOrNull(QUERY_TIMEOUT_MS) {
            val wfp = manager()
            wfp.listWatchFaces().installedWatchFaceDetails.any { wfp.isWatchFaceActive(it.packageName) }
        } ?: false // cold/wedged service -> assume not-owned (never block the pre-install strategy)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    suspend fun hasInstalledFace(): Boolean = try {
        manager().listWatchFaces().installedWatchFaceDetails.isNotEmpty()
    } catch (e: Exception) {
        false
    }

    /**
     * Slot-1 marketplace snapshot for the phone: the installed Dialed package(s) (0..1 on Wear OS 6)
     * plus the active package (the installed one iff it is currently the live face, else null).
     * Guarded — a WFP/cold-bind failure returns an empty snapshot, never throws.
     */
    suspend fun installedState(): InstalledState = try {
        withTimeoutOrNull(QUERY_TIMEOUT_MS) {
            val wfp = manager()
            val details = wfp.listWatchFaces().installedWatchFaceDetails
            val installed = details.map { it.packageName }
            val active = details.firstOrNull { wfp.isWatchFaceActive(it.packageName) }?.packageName
            InstalledState(installed, active)
        } ?: InstalledState(emptyList(), null)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "installedState unexpected ${e.javaClass.simpleName}: ${e.message}", e)
        InstalledState(emptyList(), null)
    }

    /** Remove the watch face in [slotId] (uninstalls it). Returns true on success; never throws. */
    suspend fun removeWatchFace(slotId: String): Boolean = try {
        manager().removeWatchFace(slotId)
        true
    } catch (e: WatchFacePushManager.RemoveWatchFaceException) {
        Log.w(TAG, "removeWatchFace failed: ${e.message}", e)
        false
    } catch (e: Exception) {
        Log.w(TAG, "removeWatchFace unexpected ${e.javaClass.simpleName}: ${e.message}", e)
        false
    }

    /**
     * Uninstall by package name. The phone knows the target package (from its catalog) but NOT the
     * volatile slotId, so we re-query slotId at call time (slot ids are never persisted) and remove.
     */
    suspend fun removeByPackage(packageName: String): WatchFaceUninstallResult {
        return try {
            val details = manager().listWatchFaces().installedWatchFaceDetails
            val slot = details.firstOrNull { it.packageName == packageName }
                ?: return WatchFaceUninstallResult.NOT_FOUND
            if (removeWatchFace(slot.slotId)) WatchFaceUninstallResult.REMOVED
            else WatchFaceUninstallResult.FAILED
        } catch (e: Exception) {
            Log.w(TAG, "removeByPackage unexpected ${e.javaClass.simpleName}: ${e.message}", e)
            WatchFaceUninstallResult.FAILED
        }
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
        )

    private companion object {
        const val TAG = "DialedWfpRepo"

        // Hard backstops on every WFP binder call. A cold/wedged Watch-Face-Push service must never
        // hang the receive coroutine, because that leaks the single-transfer lock and every later
        // push then replies BUSY ("your watch is busy") until the app is force-stopped. Normal ops
        // finish in well under a second; these only fire on a stuck service. Kept below the phone's
        // PHONE_FINALIZE_TIMEOUT (120s) so the phone still gets a finalize even in the worst case.
        const val INSTALL_TIMEOUT_MS = 30_000L
        const val SET_ACTIVE_TIMEOUT_MS = 12_000L
        const val QUERY_TIMEOUT_MS = 8_000L
    }
}

/** Slot-1 marketplace snapshot: which Dialed face(s) are installed + which (if any) is the live face. */
data class InstalledState(
    val installedPackages: List<String>,   // 0..1 on Wear OS 6 (slot limit = 1)
    val activePackage: String?,            // the installed pkg iff it is the active face, else null
)
