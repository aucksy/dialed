package com.dialed.app.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import com.dialed.app.wear.WearViewModel
import com.dialed.app.wear.ui.screens.ConciergeScreen
import com.dialed.app.wear.ui.screens.FirstRunScreen
import com.dialed.app.wear.ui.screens.HomeScreen
import com.dialed.app.wear.ui.screens.ReceiveScreen
import com.dialed.app.wear.ui.screens.UnsupportedScreen
import com.dialed.app.wear.wfp.ReceiveState

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
                ConciergeScreen(receive, onSetActive = onSetActive, onExitToWatchFace = onExitToWatchFace)

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
