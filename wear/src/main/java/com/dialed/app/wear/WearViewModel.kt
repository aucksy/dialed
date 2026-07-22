package com.dialed.app.wear

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
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

/** Permission fold (same reason as [HomeBundle]). */
private data class PermBundle(
    val granted: Boolean,
    val softDenied: Boolean,
    val permanentlyDenied: Boolean,
)

/** Setup-moment fold: the transient beats of the one-tap setup chain. */
private data class SetupBundle(
    val busy: Boolean,
    val celebrate: Boolean,
    val awaitingFace: String?,
    val settled: Boolean,
    val booted: Boolean,
)

/** Everything the watch UI needs to pick a screen and render it. */
data class WearUiState(
    val supported: Boolean = true,
    val pushGranted: Boolean = false,
    // Denied ONCE — the OS will still prompt again, so the honest move is a re-ask, not Settings.
    val pushSoftDenied: Boolean = false,
    // Denied for good (the OS refuses to prompt again) — Settings is the only remaining route.
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
    // True once the durable store has answered at least once. Until then NO screen is picked: the
    // defaults above are deliberately "don't show setup", so rendering before this flashes Home for
    // a beat on a fresh install and then jumps to the setup screen.
    val booted: Boolean = false,
    // The one-tap setup chain is working (installing/activating the default face). Without this the
    // setup screen sits there unchanged for seconds and its button can be tapped a second time,
    // re-entering the whole chain.
    val setupBusy: Boolean = false,
    // One-shot: setup just installed + activated the default face → show "Dialed in." then exit.
    val setupCelebrate: Boolean = false,
    // One-shot: setup finished but nothing became active (set-active refused, or no bundled
    // default) → an honest "You're set." beat. Without it the screen just became Home and the user
    // could not tell whether their taps did anything.
    val setupSettled: Boolean = false,
    // One-shot: setup finished while a face was waiting on the phone — the phone has been told to
    // re-send it, so say so instead of celebrating the DEFAULT face the user never chose.
    val awaitingFaceName: String? = null,
    val receive: ReceiveState = ReceiveState.Idle,
)

class WearViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = WatchFacePushRepository(app)
    private val store = WfpStateStore(app)
    private val supported = repo.isSupported()

    private val pushGranted = MutableStateFlow(repo.hasPushPermission())

    /**
     * Denied ONCE. Android's first denial is NOT final — the OS will prompt again — so this must
     * offer "Allow", never the Settings dead-end. (It used to be one `pushDenied` flag driving the
     * Settings screen, so a single accidental Deny sent the user to Settings with no way back.)
     */
    private val pushSoftDenied = MutableStateFlow(false)

    /** Denied for good: the OS will not prompt again, so Settings genuinely is the only route. */
    private val pushPermanentlyDenied = MutableStateFlow(false)

    private val link = MutableStateFlow(WatchLink.CONNECTING)
    private val homeFace = MutableStateFlow<HomeFaceState>(HomeFaceState.None)
    private val setByHandHint = MutableStateFlow(false)
    private val homeLoaded = MutableStateFlow(false)
    private val setupCelebrate = MutableStateFlow(false)
    private val setupSettled = MutableStateFlow(false)
    private val awaitingFaceName = MutableStateFlow<String?>(null)
    private val setupBusy = MutableStateFlow(false)
    private val booted = MutableStateFlow(false)

    /** Re-entrancy guard: the setup chain must never run twice concurrently (two installDefault()s). */
    private var setupRunning = false

    private val capabilityClient by lazy { Wearable.getCapabilityClient(getApplication<Application>()) }
    private val messageClient by lazy { Wearable.getMessageClient(getApplication<Application>()) }
    // Live "is the phone app there?" — re-query reachability whenever the Data Layer reports the
    // Dialed phone-app capability appearing/disappearing (install/uninstall/connect/disconnect).
    private val phoneCapListener = CapabilityClient.OnCapabilityChangedListener {
        viewModelScope.launch { updateLink() }
    }

    val uiState: StateFlow<WearUiState> =
        combine(
            // Fold each group so the outer combine stays at 5 typed args.
            combine(pushGranted, pushSoftDenied, pushPermanentlyDenied) { g, s, p -> PermBundle(g, s, p) },
            link,
            TransferSession.state,
            // Fold the Home-related flows + onboarding flag + loaded flag + pending face into one.
            combine(homeFace, setByHandHint, store.onboardingComplete, homeLoaded, store.pendingFaceName) {
                face, hint, onb, loaded, pending ->
                HomeBundle(face, hint, onb, loaded, pending)
            },
            combine(setupBusy, setupCelebrate, awaitingFaceName, setupSettled, booted) {
                busy, celebrate, awaiting, settled, boot ->
                SetupBundle(busy, celebrate, awaiting, settled, boot)
            },
        ) { perm, l, receive, home, setup ->
            WearUiState(
                supported = supported,
                pushGranted = perm.granted,
                pushSoftDenied = perm.softDenied,
                pushPermanentlyDenied = perm.permanentlyDenied,
                link = l,
                home = home.face,
                setByHandHint = home.hint,
                onboardingComplete = home.onboarded,
                homeLoaded = home.loaded,
                pendingFaceName = home.pendingFace,
                booted = setup.booted,
                setupBusy = setup.busy,
                setupCelebrate = setup.celebrate,
                setupSettled = setup.settled,
                awaitingFaceName = setup.awaitingFace,
                receive = receive,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            WearUiState(supported = supported, pushGranted = pushGranted.value),
        )

    init {
        capabilityClient.addListener(phoneCapListener, WearConstants.CAPABILITY_PHONE)
        // The durable store has answered — screens may now be picked (see WearUiState.booted). The
        // read is a local DataStore hit (single-digit ms); the UI shows a quiet mark until then and
        // stops waiting on its own short deadline regardless, so this can never wedge the app.
        viewModelScope.launch {
            runCatching { store.onboardingComplete.first() }
            booted.value = true
        }
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
        val granted = repo.hasPushPermission()
        pushGranted.value = granted
        // Granted now (typically: the user went to Settings and turned it on) => every denial state
        // is stale. Without this the app stayed stuck on "Permission needed · Open Settings" for the
        // rest of the session even though the permission was already on.
        if (granted) {
            pushSoftDenied.value = false
            pushPermanentlyDenied.value = false
        }
        setByHandHint.value = false // a fresh glance shouldn't carry a stale "set it by hand" hint
        // NOTE: the celebration flag is deliberately NOT cleared here. onResume fires whenever the
        // screen wakes, and clearing it mid-celebration swapped the "Dialed in." moment for Home and
        // skipped the exit-to-face. It is in-memory only (it cannot survive process death) and its
        // own screen clears it on dismiss, so there is nothing stale to guard against.
        viewModelScope.launch { updateLink() }
        viewModelScope.launch { loadHome() }
    }

    /** The "Set up Dialed" tap: hold a working state so the screen can't be re-tapped mid-chain. */
    fun beginSetup() {
        setupBusy.value = true
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

    /**
     * The install-permission dialog answered. [canAskAgain] is the Activity's
     * `shouldShowRequestPermissionRationale` AFTER the denial: true = the OS will prompt again (so
     * offer "Allow"), false = it never will (so Settings is the only honest route). Treating every
     * denial as permanent — the shipped behaviour — sent a user who fat-fingered Deny straight to a
     * Settings dead-end with no way to simply try again.
     */
    fun onPushPermissionResult(granted: Boolean, canAskAgain: Boolean) {
        pushGranted.value = granted
        pushSoftDenied.value = !granted && canAskAgain
        pushPermanentlyDenied.value = !granted && !canAskAgain
        if (!granted) setupBusy.value = false // the chain stopped here; release the working state
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
        if (setupRunning) return // a second tap must never race a second installDefault()
        setupRunning = true
        setupBusy.value = true
        viewModelScope.launch {
            // The face the phone tried to push before setup, if any. Captured BEFORE the store is
            // cleared: it decides the closing beat and is echoed to the phone so it can re-send.
            val pending = runCatching { store.pendingFaceName.first() }.getOrNull()
            var activated = false
            try {
                store.setPermissionDenied(!setActiveGranted)
                if (repo.hasPushPermission() && repo.installedState().installedPackages.isEmpty()) {
                    if (repo.installDefault() && setActiveGranted && repo.setActive()) {
                        store.setActiveApiUsed(true)
                        activated = true
                    }
                }
                store.setPendingFaceName(null) // the ask is resolved either way
                store.setOnboardingComplete(true)
                loadHome()
            } finally {
                setupRunning = false
                setupBusy.value = false
            }

            if (pending != null && repo.hasPushPermission()) {
                // A face was waiting. Tell the phone to send it now that setup is done — it is the
                // face the user actually chose, and the screen promised it "goes on now". The
                // DEFAULT face is not that face, so do NOT celebrate it here; show the hand-off
                // beat and let the arriving push own the celebration.
                awaitingFaceName.value = pending
                notifyPhoneSetupComplete(pending)
            } else {
                // Celebrate only a genuinely-active default ("Dialed in." must never lie); when
                // nothing became active, still confirm that the setup itself worked — the screen
                // used to just become Home, so two granted permissions looked like a no-op.
                setupCelebrate.value = activated
                setupSettled.value = !activated
                if (!activated) notifyPhoneSetupComplete(null)
            }
        }
    }

    /**
     * One-way nudge to the phone: watch-side setup is done. The phone uses it to re-send a face it
     * was holding (it answered NEEDS_SETUP), so the user does not have to go back and push again.
     *
     * Sent only HERE, after every permission dialog has closed and the default face is installed —
     * a push arriving mid-setup would launch the receive UI over a system dialog and cancel it, and
     * the set-active dialog can be shown only ONCE EVER. Best-effort: if the phone app is not
     * running there is nothing to receive it, and its own Retry button remains.
     */
    private suspend fun notifyPhoneSetupComplete(faceName: String?) {
        runCatching {
            val nodes = capabilityClient
                .getCapability(WearConstants.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
                .await().nodes
            val payload = faceName.orEmpty().toByteArray(Charsets.UTF_8)
            nodes.forEach { node ->
                messageClient.sendMessage(node.id, WearConstants.PATH_SETUP_COMPLETE, payload).await()
            }
        }.onFailure { Log.w(TAG, "setup-complete nudge failed (phone will still offer Retry)", it) }
    }

    /** The setup celebration was shown and dismissed (exit-to-face). */
    fun clearSetupCelebrate() {
        setupCelebrate.value = false
    }

    /** The "You're set." beat was acknowledged. */
    fun clearSetupSettled() {
        setupSettled.value = false
    }

    /** The "bringing {Face} over" beat timed out or was dismissed — fall through to Home. */
    fun clearAwaitingFace() {
        awaitingFaceName.value = null
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
        const val TAG = "DialedWearVM"
        const val REACH_ATTEMPTS = 3       // reachability probes before declaring UNREACHABLE
        const val REACH_RETRY_MS = 1200L   // wait between probes (rides out a transport blip)
        const val LINK_RECHECK_MS = 12_000L // periodic self-heal cadence while alive
    }
}
