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
import com.dialed.app.wear.common.WatchFaceUninstallResult
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

    /** WFP package -> catalog id, so the watch's reported packages resolve to faces we shipped. */
    private val pkgToFaceId: Map<String, String> = faces.associate { it.packageName to it.id }

    private val _installedPackages = MutableStateFlow<List<String>>(emptyList())
    private val _activePackage = MutableStateFlow<String?>(null)

    /** Catalog ids of the Dialed face(s) currently installed on the watch (0..1; slot = 1). */
    val installedFaceIds: StateFlow<Set<String>> = _installedPackages
        .map { pkgs -> pkgs.mapNotNull { pkgToFaceId[it] }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Catalog id of the active Dialed face on the watch, or null. */
    val activeFaceId: StateFlow<String?> = _activePackage
        .map { pkg -> pkg?.let { pkgToFaceId[it] } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Non-null while a single-tap uninstall is in flight (drives the button spinner). */
    private val _uninstallingFaceId = MutableStateFlow<String?>(null)
    val uninstallingFaceId: StateFlow<String?> = _uninstallingFaceId.asStateFlow()

    init {
        // Keep the installed/active snapshot fresh: query when a watch becomes reachable, clear when
        // it drops. Collecting watchStatus also keeps its WhileSubscribed source hot for the app's life.
        viewModelScope.launch {
            watchStatus.collect { status ->
                if (status.isConnected) {
                    refreshInstalledState()
                } else {
                    _installedPackages.value = emptyList()
                    _activePackage.value = null
                }
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }

    fun completeOnboarding() = viewModelScope.launch { settings.setOnboarded(true) }

    /** Open the push sheet for [face] and start streaming it to the watch. */
    fun startPush(face: Face) {
        _pushingFace.value = face
        _pushStatus.value = PushStatus.Preparing
        viewModelScope.launch {
            watchBridge.pushFace(face) { status ->
                _pushStatus.value = status
                // A finished install changes the watch's slot — re-read the truth for the badges.
                if (status is PushStatus.Done) refreshInstalledState()
            }
        }
    }

    /** Re-read the watch's installed/active snapshot. No-op (keeps prior state) if unreachable. */
    fun refreshInstalledState() {
        viewModelScope.launch {
            val state = watchBridge.queryInstalledState() ?: return@launch
            _installedPackages.value = state.installedPackages
            _activePackage.value = state.activePackage
        }
    }

    /** Single-tap uninstall of [face] from the watch, then reconcile the badge state with truth. */
    fun uninstallFace(face: Face) {
        if (_uninstallingFaceId.value != null) return // one uninstall at a time
        _uninstallingFaceId.value = face.id
        viewModelScope.launch {
            try {
                val result = watchBridge.uninstallFace(face)
                if (result == WatchFaceUninstallResult.REMOVED) {
                    // Optimistic clear for instant feedback; refreshInstalledState() reconciles below.
                    _installedPackages.value = _installedPackages.value - face.packageName
                    if (_activePackage.value == face.packageName) _activePackage.value = null
                }
            } finally {
                _uninstallingFaceId.value = null
                refreshInstalledState()
            }
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
