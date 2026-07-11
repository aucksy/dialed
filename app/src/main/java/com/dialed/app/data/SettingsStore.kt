package com.dialed.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dialed.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("dialed_settings")

class SettingsStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val onboardedKey = booleanPreferencesKey("onboarded")

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[themeKey] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    val onboarded: Flow<Boolean> = context.settingsDataStore.data.map { it[onboardedKey] ?: false }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setOnboarded(value: Boolean) {
        context.settingsDataStore.edit { it[onboardedKey] = value }
    }
}
