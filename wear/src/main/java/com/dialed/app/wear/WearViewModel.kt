package com.dialed.app.wear

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dialed.app.wear.common.WatchFaceActivationStrategy
import com.dialed.app.wear.ui.components.WatchLink
import com.dialed.app.wear.wfp.DefaultFace
import com.dialed.app.wear.wfp.FacePreviewExtractor
import com.dialed.app.wear.wfp.ReceiveState
import com.dialed.app.wear.wfp.TransferSession
import com.dialed.app.wear.wfp.WatchFacePushRepository
import com.dialed.app.wear.common.WearConstants
import com.dialed.app.wear.wfp.WfpStateStore
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** The GENUINE Watch-Face-Push slot state, as Home must convey it (never the stale last-pushed cache). */
sealed interface HomeFaceState {
    /** No Dialed face is installed on the watch. */
    data object None : HomeFaceState
    /** A Dialed face is installed; [active] = it is the live watch face right now. */
    data class Installed(val name: String, val preview: Bitmap?, val active: Boolean) : HomeFaceState
}

/** Internal fold of the Home-related flows so the ui-state combine stays within its typed-arg limit. */
private data class HomeBundle(
    val face: HomeFaceState,
    val hint: Boolean,
    val onboarded: Boolean,
    val loaded: Boolean,
    val pendingFace: String?,
)

/** Everything the watch UI needs to pick a screen and render it. */
data class WearUiState(
    val supported: Boolean = true,
    val pushGranted: Boolean = false,
    val pushPermanentlyDenied: Boolean = false,
    val link: WatchLink = WatchLink.CONNECTING,
    val home: HomeFaceState = HomeFaceState.None,
    val setByHandHint: Boolean = false, // an in-app set-active attempt was refused → teach long-press
    // True once the one-time "Make Dialed your watch face" step is resolved. Default true = don't
    // show it (safe until the real value loads / for existing setups).
    val onboardingComplete: Boolean = true,
    // True once the GENUINE installed-face state has been queried at least once. The default-face
    // setup screen waits for this so it never flashes over an existing user's face during the
    // (up to ~1s) first WFP query.
    val homeLoaded: Boolean = false,
    // A face the phone tried to push before setup (listener answered NEEDS_SETUP) — the setup
    // screen asks with it as the context ("{Face} is waiting").
    val pendingFaceName: String? = null,
    // One-shot: setup just installed + activated the default face → show "Dialed in." then exit.
    val setupCelebrate: Boolean = false,
    val receive: ReceiveState = ReceiveState.Idle,
)

class WearViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = WatchFacePushRepository(app)
    private val store = WfpStateStore(app)
    private val supported = repo.isSupported()

    private val pushGranted = MutableStateFlow(repo.hasPushPermission())
    private val pushDenied = MutableStateFlow(false)
    private val link = MutableStateFlow(WatchLink.CONNECTING)
    private val homeFace = MutableStateFlow<HomeFaceState>(HomeFaceState.None)
    private val setByHandHint = MutableStateFlow(false)
    private val homeLoaded = MutableStateFlow(false)
    private val setupCelebrate = MutableStateFlow(false)

    private val capabilityClient by lazy { Wearable.getCapabilityClient(getApplication<Application>()) }
    // Live "is the phone app there?" — re-query reachability whenever the Data Layer reports the
    // Dialed phone-app capability appearing/disappearing (install/uninstall/connect/disconnect).
    private val phoneCapListener = CapabilityClient.OnCapabilityChangedListener {
        viewModelScope.launch { updateLink() }
    }

    val uiState: StateFlow<WearUiState> =
        combine(
            // Fold the permission pair + celebrate flag so the outer combine stays at 5 typed args.
            combine(pushGranted, pushDenied, setupCelebrate) { g, d, c -> Triple(g, d, c) },
            link,
            TransferSession.state,
            // Fold the Home-related flows + onboarding flag + loaded flag + pending face into one.
            combine(homeFace, setByHandHint, store.onboardingComplete, homeLoaded, store.pendingFaceName) {
                face, hint, onb, loaded, pending ->
                HomeBundle(face, hint, onb, loaded, pending)
            },
        ) { perm, l, receive, home ->
            WearUiState(
                supported = supported,
                pushGranted = perm.first,
                pushPermanentlyDenied = perm.second,
                setupCelebrate = perm.third,
                link = l,
                home = home.face,
                setByHandHint = home.hint,
                onboardingComplete = home.onboarded,
                homeLoaded = home.loaded,
                pendingFaceName = home.pendingFace,
                receive = receive,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            WearUiState(supported = supported, pushGranted = pushGranted.value),
        )

    init {
        capabilityClient.addListener(phoneCapListener, WearConstants.CAPABILITY_PHONE)
        refresh()
        // Self-heal a stale link: FILTER_REACHABLE is a transient snapshot and the capability
        // listener does not always fire on a silent transport blip, so re-check on a gentle cadence
        // while the ViewModel is alive (the reported "Connected" flapping / stuck not-reachable).
        viewModelScope.launch {
            while (true) {
                delay(LINK_RECHECK_MS)
                updateLink()
            }
        }
    }

    override fun onCleared() {
        capabilityClient.removeListener(phoneCapListener)
        super.onCleared()
    }

    /** Re-read permission + connection + the GENUINE installed/active face state (call from onResume). */
    fun refresh() {
        pushGranted.value = repo.hasPushPermission()
        setByHandHint.value = false // a fresh glance shouldn't carry a stale "set it by hand" hint
        // A leftover celebration flag must not replay on a later open. Safe for the live setup
        // flow: refresh() runs at the permission-dialog resume, BEFORE finishSetup's coroutine
        // (install + set-active) can possibly set the flag.
        setupCelebrate.value = false
        viewModelScope.launch { updateLink() }
        viewModelScope.launch { loadHome() }
    }

    /**
     * Home "Set as your face" for an installed-but-inactive face: attempt the unattended set-active.
     * On success Home flips to ACTIVE; if the platform refuses (budget spent / permission missing) we
     * surface the manual long-press hint instead of leaving a dead button. Setting active is a LOCAL
     * watch op, so this works even when the phone is unreachable.
     */
    fun setInstalledFaceActive() {
        viewModelScope.launch {
            val ok = repo.setActive()
            if (ok) store.setActiveApiUsed(true)
            loadHome()                 // refresh installed/active state first...
            setByHandHint.value = !ok  // ...then set the hint so loadHome can't clear it
        }
    }

    fun onPushPermissionResult(granted: Boolean) {
        pushGranted.value = granted
        pushDenied.value = !granted
    }

    /**
     * The single "Set up Dialed" tap's finish line — called with the SET_ACTIVE permission result
     * after the PUSH permission was granted (MainActivity chains the two dialogs off the one tap).
     * Installs the bundled Dialed default face and spends the one-shot set-active HERE, in context —
     * so Dialed OWNS the active slot: from then on every pushed face updates the slot in place and
     * stays active (no set-active, no permission, no long-press). Deliberately NEVER replaces an
     * already-installed face (upgrade path). If set-active is refused, the default is still
     * installed; Home then offers the manual "Set as your face". Either way the step is resolved.
     */
    fun finishSetup(setActiveGranted: Boolean) {
        viewModelScope.launch {
            store.setPermissionDenied(!setActiveGranted)
            var activated = false
            if (repo.hasPushPermission() && repo.installedState().installedPackages.isEmpty()) {
                if (repo.installDefault() && setActiveGranted && repo.setActive()) {
                    store.setActiveApiUsed(true)
                    activated = true
                }
            }
            store.setPendingFaceName(null) // the ask is resolved; the phone re-pushes the real face
            store.setOnboardingComplete(true)
            loadHome()
            // Celebrate only a genuinely-active default ("Dialed in." must never lie).
            setupCelebrate.value = activated
        }
    }

    /** The setup celebration was shown and dismissed (exit-to-face). */
    fun clearSetupCelebrate() {
        setupCelebrate.value = false
    }

    /** Setup "Later": resolve the step without installing the default (shown once, then never). */
    fun skipDefaultFaceSetup() {
        viewModelScope.launch {
            store.setPendingFaceName(null) // declining the waiting face resolves its ask too
            store.setOnboardingComplete(true)
        }
    }

    /** Concierge one-tap: the set-active permission result came back. Spend it here (one-shot). */
    fun onSetActivePermissionResult(granted: Boolean) {
        viewModelScope.launch {
            store.setPermissionDenied(!granted)
            val current = TransferSession.state.value as? ReceiveState.Success ?: return@launch
            if (granted && repo.setActive()) {
                store.setActiveApiUsed(true)
                TransferSession.update(
                    current.copy(strategy = WatchFaceActivationStrategy.NO_ACTION_NEEDED),
                )
                loadHome()
            } else {
                // Permission denied, or the one-shot API was already spent -> teach the gesture.
                TransferSession.update(
                    current.copy(strategy = WatchFaceActivationStrategy.LONG_PRESS_TO_SET),
                )
            }
        }
    }

    /** Dismiss a finished receive/concierge flow back to Home. */
    fun dismissReceive() {
        TransferSession.clear()
        refresh()
    }

    /**
     * Reflect the GENUINE WFP slot state, not the last-pushed cache: none installed / installed-active
     * / installed-inactive. A friendly name + cached preview are used ONLY when the stored package
     * matches the package WFP currently reports installed; otherwise the name is derived from the
     * package and the preview falls back to the dial mark — so Home never claims a face that isn't there.
     */
    private suspend fun loadHome() {
        val snapshot = repo.installedState()
        val installedPkg = snapshot.installedPackages.firstOrNull()
        // An already-installed Dialed face means the user is past first-run setup (an upgrade, or
        // they pushed before onboarding finished) — resolve the one-time step silently so the
        // "Make Dialed your watch face" screen is never shown over an existing face.
        if (installedPkg != null && !store.onboardingComplete.first()) {
            store.setOnboardingComplete(true)
        }
        // Any face actually landing resolves a pre-setup pending ask (the push clearly succeeded).
        if (installedPkg != null && store.pendingFaceName.first() != null) {
            store.setPendingFaceName(null)
        }
        homeFace.value = if (installedPkg == null) {
            HomeFaceState.None
        } else {
            val matchesCache = store.lastFacePackage.first() == installedPkg
            val name = (if (matchesCache) store.lastFaceName.first() else null) ?: deriveFaceName(installedPkg)
            val preview = if (matchesCache) FacePreviewExtractor.loadCached(getApplication()) else null
            HomeFaceState.Installed(name = name, preview = preview, active = snapshot.activePackage == installedPkg)
        }
        homeLoaded.value = true // the genuine slot state is now known — safe to gate onboarding on it
    }

    /** Fallback display name from a WFP package (com.dialed.app.watchfacepush.<series>.<face>).
     *  The bundled default reads as its brand name "Dialed", not the derived "Classic". */
    private fun deriveFaceName(pkg: String): String =
        if (pkg == DefaultFace.PACKAGE) DefaultFace.DISPLAY_NAME
        else pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }

    /**
     * "Connected" means the Dialed PHONE APP is reachable — a node that advertises
     * [WearConstants.CAPABILITY_PHONE] — NOT merely that some phone is paired. Otherwise the watch
     * says "Connected" with no phone app to push from (issue #4). A reachable node with the
     * capability => CONNECTED; a paired-but-appless (or absent) phone => UNREACHABLE.
     */
    private suspend fun updateLink() {
        // FILTER_REACHABLE is a transient snapshot that also races the Data Layer's initial
        // capability sync right after launch, so a single empty result must NOT immediately flap the
        // badge to "not reachable". Retry a few times before concluding UNREACHABLE; a reachable node
        // settles to CONNECTED instantly. While retrying the badge holds its value (starts CONNECTING).
        repeat(REACH_ATTEMPTS) { attempt ->
            val reachable = try {
                capabilityClient
                    .getCapability(WearConstants.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
                    .await().nodes
            } catch (e: Exception) {
                emptySet<Node>()
            }
            if (reachable.isNotEmpty()) {
                link.value = WatchLink.CONNECTED
                return
            }
            if (attempt < REACH_ATTEMPTS - 1) delay(REACH_RETRY_MS)
        }
        link.value = WatchLink.UNREACHABLE
    }

    private companion object {
        const val REACH_ATTEMPTS = 3       // reachability probes before declaring UNREACHABLE
        const val REACH_RETRY_MS = 1200L   // wait between probes (rides out a transport blip)
        const val LINK_RECHECK_MS = 12_000L // periodic self-heal cadence while alive
    }
}
