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
import com.dialed.app.wear.ui.screens.ConciergeScreen
import com.dialed.app.wear.ui.screens.FirstRunScreen
import com.dialed.app.wear.ui.screens.HomeScreen
import com.dialed.app.wear.ui.screens.ReceiveScreen
import com.dialed.app.wear.ui.screens.ReceiveSuccess
import com.dialed.app.wear.ui.screens.UnsupportedScreen
import com.dialed.app.wear.wfp.ReceiveState
import kotlinx.coroutines.delay

/**
 * Single-state navigator (no navigation dep). Order matters: an unsupported watch and an active
 * transfer both take precedence over the permission gate and Home.
 */
@Composable
fun WearApp(
    viewModel: WearViewModel,
    onAllow: () -> Unit,
    onSetActive: () -> Unit,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit,
    onExitToWatchFace: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val receive = state.receive

    AppScaffold {
        when {
            !state.supported -> UnsupportedScreen(onOk = onExit)

            receive is ReceiveState.Receiving ||
                receive is ReceiveState.Installing ||
                receive is ReceiveState.Failed ->
                ReceiveScreen(receive, onDismiss = viewModel::dismissReceive)

            receive is ReceiveState.Success ->
                SuccessThenConcierge(receive, onSetActive, onExitToWatchFace)

            !state.pushGranted ->
                FirstRunScreen(
                    permanentlyDenied = state.pushPermanentlyDenied,
                    onAllow = onAllow,
                    onOpenSettings = onOpenSettings,
                )

            else -> HomeScreen(state, onSetActive = viewModel::setInstalledFaceActive)
        }
    }
}

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
