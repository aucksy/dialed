package com.dialed.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.entitlementDataStore by preferencesDataStore("dialed_entitlement")

/**
 * Cached copy of the one-time-unlock entitlement. Play is the source of truth
 * (Phase 2 BillingManager writes here after acknowledge / restore); this cache
 * just avoids a cold-start flash before the billing query returns.
 */
class EntitlementStore(private val context: Context) {
    private val unlockedKey = booleanPreferencesKey("collection_unlocked")

    val unlocked: Flow<Boolean> = context.entitlementDataStore.data.map { it[unlockedKey] ?: false }

    suspend fun setUnlocked(value: Boolean) {
        context.entitlementDataStore.edit { it[unlockedKey] = value }
    }
}
