package com.dialed.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.dialed.app.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.entitlementDataStore by preferencesDataStore("dialed_entitlement")

/**
 * Cached copy of the one-time-unlock entitlement. Play is the source of truth
 * (Phase 2 BillingManager writes here after acknowledge / restore); this cache
 * just avoids a cold-start flash before the billing query returns.
 *
 * DEBUG builds default to UNLOCKED so every face is free to install/test (the paywall
 * still exists for release; the debug toggle can flip back to preview the locked flow).
 */
class EntitlementStore(private val context: Context) {
    private val unlockedKey = booleanPreferencesKey("collection_unlocked")

    val unlocked: Flow<Boolean> =
        context.entitlementDataStore.data.map { it[unlockedKey] ?: BuildConfig.DEBUG }

    suspend fun setUnlocked(value: Boolean) {
        context.entitlementDataStore.edit { it[unlockedKey] = value }
    }
}
