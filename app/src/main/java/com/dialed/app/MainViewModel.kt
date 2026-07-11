package com.dialed.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dialed.app.catalog.Face
import com.dialed.app.catalog.FaceCatalog
import com.dialed.app.data.EntitlementStore
import com.dialed.app.data.SettingsStore
import com.dialed.app.model.WatchConnection
import com.dialed.app.model.WatchStatus
import com.dialed.app.transport.PushStatus
import com.dialed.app.transport.WatchBridge
import com.dialed.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
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

    private val watchBridge = WatchBridge(app)

    /** Real reachable-watch detection via CapabilityClient (dialed_wfp_install). */
    val watchStatus: StateFlow<WatchStatus> =
        watchBridge.connectedWatch.map { watch ->
            if (watch != null) {
                WatchStatus(connection = WatchConnection.CONNECTED, deviceName = watch.displayName)
            } else {
                WatchStatus(connection = WatchConnection.DISCONNECTED)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchStatus())

    /** The face whose PushToWatchSheet is open (null = closed), and the transfer's progress. */
    private val _pushingFace = MutableStateFlow<Face?>(null)
    val pushingFace: StateFlow<Face?> = _pushingFace.asStateFlow()

    private val _pushStatus = MutableStateFlow<PushStatus>(PushStatus.Idle)
    val pushStatus: StateFlow<PushStatus> = _pushStatus.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }

    fun completeOnboarding() = viewModelScope.launch { settings.setOnboarded(true) }

    /** Open the push sheet for [face] and start streaming it to the watch. */
    fun startPush(face: Face) {
        _pushingFace.value = face
        _pushStatus.value = PushStatus.Preparing
        viewModelScope.launch {
            watchBridge.pushFace(face) { status -> _pushStatus.value = status }
        }
    }

    fun retryPush() {
        _pushingFace.value?.let { startPush(it) }
    }

    fun dismissPush() {
        _pushingFace.value = null
        _pushStatus.value = PushStatus.Idle
    }

    /** Debug-only: flip the local entitlement to preview locked/unlocked states. */
    fun debugToggleEntitlement() =
        viewModelScope.launch { entitlement.setUnlocked(!entitled.value) }
}
