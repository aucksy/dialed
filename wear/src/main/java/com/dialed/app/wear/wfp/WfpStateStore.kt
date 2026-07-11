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
 * Durable Watch-Face-Push state. Two flags MUST survive process death:
 * - [setActiveApiUsed]: the set-active call is one-shot; re-calling risks ERROR_MAXIMUM_ATTEMPTS.
 * - [permissionDenied]: SET_PUSHED_WATCH_FACE_AS_ACTIVE can only be requested once.
 * Plus [lastFaceName] so Home can show the face that was last pushed.
 */
class WfpStateStore(private val context: Context) {

    val setActiveApiUsed: Flow<Boolean> =
        context.wfpDataStore.data.map { it[KEY_ACTIVE_USED] == true }

    val permissionDenied: Flow<Boolean> =
        context.wfpDataStore.data.map { it[KEY_PERM_DENIED] == true }

    val lastFaceName: Flow<String?> =
        context.wfpDataStore.data.map { it[KEY_LAST_FACE] }

    suspend fun setActiveApiUsed(value: Boolean) =
        context.wfpDataStore.edit { it[KEY_ACTIVE_USED] = value }.let {}

    suspend fun setPermissionDenied(value: Boolean) =
        context.wfpDataStore.edit { it[KEY_PERM_DENIED] = value }.let {}

    suspend fun setLastFaceName(value: String) =
        context.wfpDataStore.edit { it[KEY_LAST_FACE] = value }.let {}

    private companion object {
        val KEY_ACTIVE_USED = booleanPreferencesKey("setActiveUsed")
        val KEY_PERM_DENIED = booleanPreferencesKey("permissionDenied")
        val KEY_LAST_FACE = stringPreferencesKey("lastFaceName")
    }
}
