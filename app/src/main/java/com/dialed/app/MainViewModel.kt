package com.dialed.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dialed.app.catalog.Face
import com.dialed.app.catalog.FaceCatalog
import com.dialed.app.data.EntitlementStore
import com.dialed.app.data.SettingsStore
import com.dialed.app.model.WatchStatus
import com.dialed.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsStore(app)
    private val entitlement = EntitlementStore(app)

    val faces: List<Face> = FaceCatalog.faces

    val themeMode: StateFlow<ThemeMode> =
        settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val onboarded: StateFlow<Boolean> =
        settings.onboarded.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val entitled: StateFlow<Boolean> =
        entitlement.unlocked.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // Phase 4 wires real CapabilityClient detection; disconnected until then.
    private val _watchStatus = MutableStateFlow(WatchStatus())
    val watchStatus: StateFlow<WatchStatus> = _watchStatus.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }

    fun completeOnboarding() = viewModelScope.launch { settings.setOnboarded(true) }

    /** Debug-only: flip the local entitlement to preview locked/unlocked states. */
    fun debugToggleEntitlement() =
        viewModelScope.launch { entitlement.setUnlocked(!entitled.value) }
}
