package com.dialed.app.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import com.dialed.app.wear.WearViewModel
import com.dialed.app.wear.common.WatchFaceActivationStrategy
import com.dialed.app.wear.ui.screens.AwaitingFaceScreen
import com.dialed.app.wear.ui.screens.BootScreen
import com.dialed.app.wear.ui.screens.ConciergeScreen
import com.dialed.app.wear.ui.screens.HomeScreen
import com.dialed.app.wear.ui.screens.ReceiveScreen
import com.dialed.app.wear.ui.screens.ReceiveSuccess
import com.dialed.app.wear.ui.screens.SetupDoneScreen
import com.dialed.app.wear.ui.screens.SetupScreen
import com.dialed.app.wear.ui.screens.SetupSettledScreen
import com.dialed.app.wear.ui.screens.SetupWorkingScreen
import com.dialed.app.wear.ui.screens.UnsupportedScreen
import com.dialed.app.wear.wfp.ReceiveState
import kotlinx.coroutines.delay

/**
 * Single-state navigator (no navigation dep). Order matters: an unsupported watch and an active
 * transfer both take precedence over the setup gate and Home.
 */
@Composable
fun WearApp(
    viewModel: WearViewModel,
    onSetUp: () -> Unit,
    onSetActive: () -> Unit,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit,
    onExitToWatchFace: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val receive = state.receive
    val awaitingFace = state.awaitingFaceName

    // Don't pick a screen from default state. WearUiState's defaults deliberately mean "don't show
    // setup" (so an existing user's face is never covered), which made a FRESH install render Home
    // — "No face yet" — for a beat before jumping to the setup screen. Wait for the durable store,
    // but never longer than BOOT_GATE_MS, so a slow disk can't wedge the app on a blank mark.
    var bootWaitOver by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(BOOT_GATE_MS)
        bootWaitOver = true
    }
    val booted = state.booted || bootWaitOver

    // The ONE setup moment (permissions + default face in a single tap). Shown when:
    // - fresh install (no permission, step unresolved) — immediately, no homeLoaded wait;
    // - a face was pushed before setup (pendingFaceName) — the ask returns WITH that context,
    //   even after "Later";
    // - the install permission was denied but is still re-askable — the honest "Allow" re-ask
    //   (a denial used to jump straight to a Settings dead-end);
    // - upgrade path: permission already granted but the default-face step unresolved — only once
    //   the GENUINE slot state is known (homeLoaded) and empty, so it never flashes over an
    //   existing user's face; the ViewModel resolves it silently when a face is observed installed.
    val needsSetup =
        (!state.pushGranted && !state.onboardingComplete) ||
            (!state.pushGranted && state.pendingFaceName != null) ||
            (!state.pushGranted && (state.pushSoftDenied || state.pushPermanentlyDenied)) ||
            (
                state.pushGranted && state.homeLoaded && !state.onboardingComplete &&
                    state.home is com.dialed.app.wear.HomeFaceState.None
                )

    AppScaffold {
        when {
            !state.supported -> UnsupportedScreen(onOk = onExit)

            receive is ReceiveState.Receiving ||
                receive is ReceiveState.Installing ||
                receive is ReceiveState.Failed ->
                ReceiveScreen(receive, onDismiss = viewModel::dismissReceive)

            receive is ReceiveState.Success ->
                SuccessThenConcierge(receive, onSetActive, onExitToWatchFace)

            !booted -> BootScreen()

            // The one-tap chain is mid-flight (installing/activating the default face).
            state.setupBusy -> SetupWorkingScreen()

            // Setup just made the default face active — celebrate once, then land on the face.
            state.setupCelebrate ->
                SetupDoneScreen(onDone = {
                    viewModel.clearSetupCelebrate()
                    onExitToWatchFace()
                })

            // Setup finished with a face waiting on the phone: the phone has been nudged to send it
            // and the incoming transfer's own screens take over the instant it lands.
            awaitingFace != null ->
                AwaitingFaceScreen(faceName = awaitingFace, onTimeout = viewModel::clearAwaitingFace)

            // Setup worked but nothing became active — confirm what DID happen.
            state.setupSettled -> SetupSettledScreen(onDone = viewModel::clearSetupSettled)

            needsSetup ->
                SetupScreen(
                    pendingFaceName = state.pendingFaceName,
                    softDenied = state.pushSoftDenied,
                    permanentlyDenied = state.pushPermanentlyDenied,
                    onSetUp = onSetUp,
                    onSkip = viewModel::skipDefaultFaceSetup,
                    onOpenSettings = onOpenSettings,
                )

            else -> HomeScreen(
                state,
                onSetActive = viewModel::setInstalledFaceActive,
                onSetUp = onSetUp,
            )
        }
    }
}

/** Longest the boot gate will hold the UI on the quiet mark before showing its best guess. */
private const val BOOT_GATE_MS = 700L

/**
 * The catch's payoff → the activation concierge. When the face still needs an activation step, show
 * the "On your wrist." success confirmation (spec 1i) for ~1.1s FIRST, then the concierge — this is
 * the "installed" beat the flow was missing. When the face is already active
 * (NO_ACTION_NEEDED) skip straight to the celebration so we don't stack two full-screen moments.
 */
@Composable
private fun SuccessThenConcierge(
    state: ReceiveState.Success,
    onSetActive: () -> Unit,
    onExitToWatchFace: () -> Unit,
) {
    if (state.strategy == WatchFaceActivationStrategy.NO_ACTION_NEEDED) {
        ConciergeScreen(state, onSetActive = onSetActive, onExitToWatchFace = onExitToWatchFace)
        return
    }
    // Keyed on the face so a subsequent push re-plays the confirmation.
    var confirmed by remember(state.faceName) { mutableStateOf(false) }
    if (confirmed) {
        ConciergeScreen(state, onSetActive = onSetActive, onExitToWatchFace = onExitToWatchFace)
    } else {
        ReceiveSuccess(state)
        LaunchedEffect(state.faceName) {
            delay(1100)
            confirmed = true
        }
    }
}
