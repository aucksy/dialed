package com.dialed.app.wear.wfp

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.wfpDataStore: DataStore<Preferences> by preferencesDataStore(name = "dialed_wfp")

/**
 * Durable Watch-Face-Push state that survives process death:
 * - [permissionDenied]: SET_PUSHED_WATCH_FACE_AS_ACTIVE can only be requested once — after a denial
 *   we must route to Settings instead of re-prompting.
 * - [setActiveApiUsed]: a DIAGNOSTIC record that the unattended set-active has been exercised at
 *   least once. It no longer GATES the activation decision — the platform's own
 *   ERROR_MAXIMUM_ATTEMPTS is the authority (a local latch could forfeit an activation the platform
 *   would still grant), so we always attempt and record the outcome here.
 * Plus [lastFaceName] / [lastFacePackage] so Home can put a friendly name + cached preview on the
 * face WFP reports as installed — but ONLY when the stored package matches the actually-installed
 * one (else Home derives the name from the package and shows a placeholder; never a stale face).
 */
class WfpStateStore(private val context: Context) {

    val setActiveApiUsed: Flow<Boolean> =
        context.wfpDataStore.data.map { it[KEY_ACTIVE_USED] == true }

    val permissionDenied: Flow<Boolean> =
        context.wfpDataStore.data.map { it[KEY_PERM_DENIED] == true }

    /**
     * True once the "Make Dialed your watch face" onboarding step has been resolved — either the user
     * set up the default (claiming the active slot / ownership chain) or skipped it, or a Dialed face
     * was already installed when we first looked (an upgrade past this step). Gates the one-time
     * default-face setup screen so it is never shown twice.
     */
    val onboardingComplete: Flow<Boolean> =
        context.wfpDataStore.data.map { it[KEY_ONBOARDED] == true }

    /**
     * Name of a face the phone tried to push BEFORE watch-side setup (the listener answered
     * NEEDS_SETUP). The setup screen uses it to ask with that face as the context ("{Face} is
     * waiting"), the strongest possible permission moment. Cleared once setup resolves or any
     * face actually lands.
     */
    val pendingFaceName: Flow<String?> =
        context.wfpDataStore.data.map { it[KEY_PENDING_FACE] }

    val lastFaceName: Flow<String?> =
        context.wfpDataStore.data.map { it[KEY_LAST_FACE] }

    /** Package of the last face we installed — Home only trusts [lastFaceName]/cache when this
     *  matches the package WFP currently reports installed. */
    val lastFacePackage: Flow<String?> =
        context.wfpDataStore.data.map { it[KEY_LAST_PACKAGE] }

    suspend fun setActiveApiUsed(value: Boolean) =
        context.wfpDataStore.edit { it[KEY_ACTIVE_USED] = value }.let {}

    suspend fun setPermissionDenied(value: Boolean) =
        context.wfpDataStore.edit { it[KEY_PERM_DENIED] = value }.let {}

    suspend fun setOnboardingComplete(value: Boolean) =
        context.wfpDataStore.edit { it[KEY_ONBOARDED] = value }.let {}

    suspend fun setPendingFaceName(value: String?) =
        context.wfpDataStore.edit {
            if (value == null) it.remove(KEY_PENDING_FACE) else it[KEY_PENDING_FACE] = value
        }.let {}

    suspend fun setLastFace(name: String, packageName: String?) =
        context.wfpDataStore.edit {
            it[KEY_LAST_FACE] = name
            if (packageName != null) it[KEY_LAST_PACKAGE] = packageName
        }.let {}

    private companion object {
        val KEY_ACTIVE_USED = booleanPreferencesKey("setActiveUsed")
        val KEY_PERM_DENIED = booleanPreferencesKey("permissionDenied")
        val KEY_ONBOARDED = booleanPreferencesKey("onboardingComplete")
        val KEY_PENDING_FACE = stringPreferencesKey("pendingFaceName")
        val KEY_LAST_FACE = stringPreferencesKey("lastFaceName")
        val KEY_LAST_PACKAGE = stringPreferencesKey("lastFacePackage")
    }
}
