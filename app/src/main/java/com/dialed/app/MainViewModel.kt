package com.dialed.app

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dialed.app.catalog.Face
import com.dialed.app.catalog.FaceCatalog
import com.dialed.app.catalog.FaceCollection
import com.dialed.app.catalog.collectionsOf
import com.dialed.app.data.EntitlementStore
import com.dialed.app.data.SettingsStore
import com.dialed.app.model.WatchConnection
import com.dialed.app.model.WatchStatus
import com.dialed.app.transport.ConnectedWatch
import com.dialed.app.transport.PairedProbe
import com.dialed.app.transport.PushStatus
import com.dialed.app.transport.WatchBridge
import com.dialed.app.transport.WatchSetup
import com.dialed.app.transport.WatchSetupState
import com.dialed.app.ui.theme.ThemeMode
import com.dialed.app.wear.common.QueryStateResult
import com.dialed.app.wear.common.WatchFaceUninstallResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsStore(app)
    private val entitlement = EntitlementStore(app)

    val faces: List<Face> = FaceCatalog.faces

    /** Faces grouped into browsable collections (docs/DESIGN-ADDENDUM-COLLECTIONS.md §2). */
    val collections: List<FaceCollection> = collectionsOf(faces)

    val themeMode: StateFlow<ThemeMode> =
        settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    /**
     * null = not read yet. DataStore answers a frame or two after launch, and defaulting to `false`
     * meant EVERY cold start rendered the Setup screen for that beat before snapping to Home — the
     * onboarding appeared to come back on every launch. The UI renders nothing until this is known.
     */
    val onboarded: StateFlow<Boolean?> =
        settings.onboarded.map { it as Boolean? }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Home's "Put your first face on" starters (docs/ONBOARDING-REDESIGN.md §5.2). */
    val starterFaces: List<Face> = STARTER_IDS.mapNotNull { id -> faces.firstOrNull { it.id == id } }

    /**
     * True once any face has been observed installed — Home's starter card retires forever.
     * Initial TRUE so the card never flashes for an already-set-up user while the flag loads;
     * a genuinely new user just sees it appear a beat later.
     */
    val firstInstallDone: StateFlow<Boolean> =
        settings.firstInstallDone.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val entitled: StateFlow<Boolean> =
        entitlement.unlocked.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val watchBridge = WatchBridge(app)

    /**
     * The reachable Dialed-capable watch, shared once for the ViewModel's life: both [watchStatus]
     * and the init collector below read it, and an Eagerly-shared StateFlow keeps that to ONE
     * CapabilityClient listener (collecting the cold flow twice would register two).
     */
    private val connectedWatch: StateFlow<ConnectedWatch?> =
        watchBridge.connectedWatch.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * False once a reachable watch tells us it has no Watch Face Push (Wear OS < 6). Optimistic by
     * default and reset on every (re)connect, so swapping to a supported watch never inherits a
     * previous watch's verdict.
     */
    private val watchSupported = MutableStateFlow(true)

    /**
     * ANY paired + reachable watch node (Dialed watch app or not) — NodeClient, not capability.
     * With the capability node this distinguishes "watch there, Dialed watch app missing" (a
     * guided setup step) from "no watch at all" (previously both read "No watch connected").
     */
    private val pairedProbe = MutableStateFlow<PairedProbe>(PairedProbe.Unknown)

    /**
     * The watch app's own one-time setup state (install permission granted), from the query-state
     * reply. Null = this watch hasn't answered a query yet (Setup shows CHECKING, not a false
     * READY); a reply WITHOUT the flag (older wear app) is recorded as true — never nag on unknown.
     */
    private val watchSetupDone = MutableStateFlow<Boolean?>(null)

    /** Real reachable-watch detection via CapabilityClient (dialed_wfp_install) + the node probe. */
    val watchStatus: StateFlow<WatchStatus> =
        combine(connectedWatch, watchSupported, pairedProbe, watchSetupDone) { watch, supported, probe, setupDone ->
            when {
                watch != null && !supported -> WatchStatus(WatchConnection.UNSUPPORTED, watch.displayName)
                // The watch app is there but its one-time setup was never done, so every push will
                // be refused. Saying "Connected" was true about the link and useless about the
                // outcome — the pill now names the actual blocker, everywhere Home shows it.
                watch != null && setupDone == false -> WatchStatus(WatchConnection.NEEDS_SETUP, watch.displayName)
                watch != null -> WatchStatus(WatchConnection.CONNECTED, watch.displayName)
                probe is PairedProbe.Found -> WatchStatus(WatchConnection.APP_MISSING, probe.name)
                probe is PairedProbe.Unknown -> WatchStatus(WatchConnection.CONNECTING)
                else -> WatchStatus(WatchConnection.DISCONNECTED)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchStatus())

    /** The Setup screen's live state (docs/ONBOARDING-REDESIGN.md §5.1). */
    val watchSetup: StateFlow<WatchSetup> =
        combine(connectedWatch, watchSupported, pairedProbe, watchSetupDone) { watch, supported, probe, setupDone ->
            when {
                watch != null && !supported -> WatchSetup(WatchSetupState.UNSUPPORTED, watch.displayName)
                // Capability there but the watch hasn't answered a query yet: keep CHECKING rather
                // than flash a READY that may immediately correct to OPEN_ON_WATCH (the Setup
                // screen's poll re-queries every few seconds, so this can't stick on a live link).
                watch != null && setupDone == null -> WatchSetup(WatchSetupState.CHECKING, watch.displayName)
                watch != null && setupDone == false -> WatchSetup(WatchSetupState.OPEN_ON_WATCH, watch.displayName)
                watch != null -> WatchSetup(WatchSetupState.READY, watch.displayName)
                probe is PairedProbe.Found -> WatchSetup(WatchSetupState.WATCH_APP_MISSING, probe.name)
                probe is PairedProbe.None -> WatchSetup(WatchSetupState.NO_WATCH)
                else -> WatchSetup(WatchSetupState.CHECKING)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchSetup())

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

    /** Set when an uninstall genuinely failed — shown once as a snackbar, then cleared. */
    private val _uninstallError = MutableStateFlow<String?>(null)
    val uninstallError: StateFlow<String?> = _uninstallError.asStateFlow()

    /**
     * Identifies the live push. Incremented by [startPush] and [dismissPush], and captured by each
     * push's status callback, so a superseded transfer's late emission can never write into the
     * sheet that now belongs to another face (or to a sheet the user already dismissed). Plain Long:
     * every read/write happens on the main dispatcher (viewModelScope's default), never concurrently.
     *
     * This token is the WHOLE guard, deliberately. We do NOT cancel an in-flight push: cancelling the
     * phone coroutine cannot stop the Data Layer transfer the watch is already installing, it only
     * blinds us to the result — the face lands on the wrist while the phone still shows "Install to
     * watch". Letting the transfer finish and report is what keeps the badges honest, and it is the
     * contract the push sheet documents ("a mid-flight transfer simply completes on the watch").
     */
    private var pushToken = 0L

    init {
        // Keep the installed/active snapshot fresh: query when a watch becomes reachable, clear when
        // it drops. Keyed off the connection itself (NOT the derived watchStatus) — an UNSUPPORTED
        // verdict makes isConnected false, and gating the re-query on that would strand a later,
        // genuinely-supported watch with a stale verdict.
        viewModelScope.launch {
            connectedWatch.collect { watch ->
                if (watch != null) {
                    watchSupported.value = true // optimistic until this watch says otherwise
                    watchSetupDone.value = null // this watch will say via the next query
                    queryMisses = 0             // a new watch gets a fresh chance to answer
                    refreshInstalledState()
                } else {
                    _installedPackages.value = emptyList()
                    _activePackage.value = null
                    watchSupported.value = true
                    watchSetupDone.value = null
                    // Capability gone: re-probe raw nodes so APP_MISSING vs NO_WATCH stays honest.
                    refreshPairedProbe()
                }
            }
        }

        // The watch finished its setup and told us so. If we are holding a face it refused
        // (RESPONSE_NEEDS_SETUP), send it now — the watch screen promised the user their face would
        // come over, and making them walk back to the phone and press Retry breaks that promise.
        //
        // Only the sheet's OWN face is re-sent, and only while that sheet is still open on the
        // NeedsWatchSetup state, so this can never fire a push the user didn't ask for.
        viewModelScope.launch {
            watchBridge.setupCompleteSignals.collect {
                val face = _pushingFace.value ?: return@collect
                if (_pushStatus.value !is PushStatus.NeedsWatchSetup) return@collect
                watchSetupDone.value = true
                startPush(face)
            }
        }
    }

    /**
     * Re-probe for any paired watch node (drives APP_MISSING vs NO_WATCH).
     *
     * A single empty `getConnectedNodes()` is NOT proof that no watch is paired: right after launch
     * it races the Data Layer's own sync and comes back empty for a paired, present watch — which
     * showed "No watch is paired with this phone yet" within a second of opening the app. So an
     * empty answer only counts after [EMPTY_PROBES_BEFORE_NONE] consecutive misses; a hit resets
     * the count instantly. (Same reasoning as the wear side's REACH_ATTEMPTS.)
     */
    private var emptyProbes = 0

    /** Wall-clock anchor for the settle window below (monotonic; unaffected by clock changes). */
    private val createdAt = SystemClock.elapsedRealtime()

    private suspend fun refreshPairedProbe() {
        val name = watchBridge.pairedWatchName()
        if (name != null) {
            emptyProbes = 0
            pairedProbe.value = PairedProbe.Found(name)
            return
        }
        emptyProbes++
        // Both guards are needed: the probe fires from two places at once at launch (the capability
        // collector AND the Setup screen's first tick), so a count alone can be satisfied inside a
        // few milliseconds — before the Data Layer has synced anything.
        val settled = SystemClock.elapsedRealtime() - createdAt >= PROBE_SETTLE_MS
        if (emptyProbes >= EMPTY_PROBES_BEFORE_NONE && settled) pairedProbe.value = PairedProbe.None
        // else: hold the previous value (Unknown = still CHECKING, Found = don't flap it away)
    }

    /** The Setup screen's poll tick: refresh the node probe, the capability, and the watch's answers. */
    fun refreshWatchSetup() {
        viewModelScope.launch { refreshPairedProbe() }
        // The capability listener does not re-fire for a capability that was already present when
        // we subscribed, so re-ask explicitly or a missed first query never heals.
        watchBridge.requestCapabilityRefresh()
        refreshInstalledState()
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }

    fun completeOnboarding() = viewModelScope.launch { settings.setOnboarded(true) }

    /**
     * Open the push sheet for [face] and start streaming it to the watch. The newest tap owns the
     * sheet (see [pushToken]); an earlier transfer is left to finish rather than cancelled, because
     * only the WATCH can actually stop it, and its outcome is still the truth about what is on the
     * wrist. If two pushes do overlap, the watch's own single-transfer lock answers the second one
     * BUSY and the sheet says so honestly.
     */
    fun startPush(face: Face) {
        val token = ++pushToken
        _pushingFace.value = face
        _pushStatus.value = PushStatus.Preparing
        viewModelScope.launch {
            watchBridge.pushFace(face) { status ->
                // Facts about the watch are recorded no matter who is listening: a face that really
                // installed must update the badges even if this sheet was dismissed or superseded,
                // and a watch that can't install anything should say so everywhere.
                if (status is PushStatus.Done) refreshInstalledState()
                if (status is PushStatus.Unsupported) watchSupported.value = false
                if (status is PushStatus.NeedsWatchSetup) watchSetupDone.value = false
                // Only the sheet write is gated — a stale transfer must never repaint a sheet that
                // now belongs to another face.
                if (token != pushToken) return@pushFace
                _pushStatus.value = status
            }
        }
    }

    /**
     * Guards [refreshInstalledState] to ONE in-flight query. The Setup screen polls every 3 s while
     * a query may take up to 12 s, so without this up to four overlapped RPCs raced each other and
     * a stale reply landing last could revert a fresh answer — the setup card flapped between
     * "open Dialed on your watch" and "ready".
     */
    private var queryInFlight = false

    /** A refresh asked for while one was already running — run exactly one more when it lands. */
    private var queryQueued = false

    /** Consecutive query-state RPCs the watch didn't answer (resets on any reply / reconnect). */
    private var queryMisses = 0

    /** Re-read the watch's installed/active snapshot. No-op (keeps prior state) if unreachable. */
    fun refreshInstalledState() {
        if (queryInFlight) {
            queryQueued = true
            return
        }
        queryInFlight = true
        viewModelScope.launch {
            var state: QueryStateResult? = null
            try {
                state = watchBridge.queryInstalledState()
            } finally {
                queryInFlight = false
                if (queryQueued) {
                    queryQueued = false
                    refreshInstalledState()
                }
            }
            if (state == null) {
                // The watch didn't answer. With a live capability that leaves watchSetupDone at
                // null, which the Setup screen renders as CHECKING — so a watch whose listener is
                // slow to wake stranded the screen on "Looking for your watch…" with a disabled CTA
                // and no way forward. After a couple of misses, assume it's fine and let the user
                // move on: pushing is the real test, and the push sheet is honest about NEEDS_SETUP.
                queryMisses++
                if (queryMisses >= QUERY_MISSES_BEFORE_ASSUME_OK && watchSetupDone.value == null) {
                    watchSetupDone.value = true
                }
                return@launch
            }
            queryMisses = 0
            watchSupported.value = state.supported
            // A reply WITHOUT the setup flag (older wear app) counts as done — never nag on unknown.
            watchSetupDone.value = state.pushGranted ?: true
            if (!state.supported) {
                _installedPackages.value = emptyList()
                _activePackage.value = null
                return@launch
            }
            _installedPackages.value = state.installedPackages
            _activePackage.value = state.activePackage
            // Any observed install of a CATALOG face retires Home's starter card, forever
            // (write-once). Deliberately not "any package": watch-side setup installs the bundled
            // Dialed DEFAULT face, which is not in the catalog — counting it retired the
            // "Put your first face on" card before the user had chosen a single face.
            if (state.installedPackages.any { pkgToFaceId.containsKey(it) } &&
                !settings.firstInstallDone.first()
            ) {
                settings.setFirstInstallDone(true)
            }
        }
    }

    /**
     * Single-tap uninstall of [face] from the watch, then reconcile the badge state with truth.
     * A genuine failure (unreachable watch, WFP error) surfaces in [uninstallError] — it used to
     * end silently, so the spinner just stopped and the badge stayed with no explanation.
     */
    fun uninstallFace(face: Face) {
        if (_uninstallingFaceId.value != null) return // one uninstall at a time
        _uninstallingFaceId.value = face.id
        _uninstallError.value = null
        viewModelScope.launch {
            try {
                when (watchBridge.uninstallFace(face)) {
                    WatchFaceUninstallResult.REMOVED -> {
                        // Optimistic clear for instant feedback; refreshInstalledState() reconciles below.
                        _installedPackages.value = _installedPackages.value - face.packageName
                        if (_activePackage.value == face.packageName) _activePackage.value = null
                    }
                    // Already gone (removed on the watch): not an error — the refresh below is truth.
                    WatchFaceUninstallResult.NOT_FOUND -> Unit
                    WatchFaceUninstallResult.FAILED ->
                        _uninstallError.value =
                            "Couldn't remove ${face.displayName} — keep your watch nearby and try again."
                }
            } finally {
                _uninstallingFaceId.value = null
                refreshInstalledState()
            }
        }
    }

    /** The snackbar has been shown — don't repeat it on the next recomposition. */
    fun clearUninstallError() {
        _uninstallError.value = null
    }

    fun retryPush() {
        _pushingFace.value?.let { startPush(it) }
    }

    /**
     * Close the sheet. The transfer itself is deliberately NOT cancelled — it completes on the watch
     * (the phone cannot stop it) and its result still reconciles the badges; bumping the token is
     * enough to stop it repainting a sheet the user has dismissed.
     */
    fun dismissPush() {
        pushToken++
        _pushingFace.value = null
        _pushStatus.value = PushStatus.Idle
    }

    /**
     * DEBUG ONLY: flip the local entitlement to preview the locked/unlocked states without Play.
     * Hard no-op in release — this is wired to the paywall's purchase/restore buttons until real
     * Billing lands (Phase 3), and without this guard a release build would hand out a free,
     * permanent unlock on a single tap.
     */
    fun debugToggleEntitlement() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch { entitlement.setUnlocked(!entitled.value) }
    }

    /** DEBUG ONLY: stand-in for "restore purchases" — only ever grants, never revokes. */
    fun debugRestoreEntitlement() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch { entitlement.setUnlocked(true) }
    }

    private companion object {
        /** Home's first-face starters (owner-picked 2026-07-22; revisit when the free map lands). */
        val STARTER_IDS = listOf("arclight_solstice", "vakt_gt", "aurum_guilloche")

        /** Consecutive empty node probes before we believe "no watch is paired" (see the probe). */
        const val EMPTY_PROBES_BEFORE_NONE = 2

        /** …and no sooner than this after launch, so the Data Layer has had time to sync. */
        const val PROBE_SETTLE_MS = 2_500L

        /** Unanswered query-state RPCs before the Setup screen stops waiting and lets the user on. */
        const val QUERY_MISSES_BEFORE_ASSUME_OK = 2
    }
}
