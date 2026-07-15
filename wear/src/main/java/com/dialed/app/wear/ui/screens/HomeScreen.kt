package com.dialed.app.wear.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OpenOnPhoneDialog
import androidx.wear.compose.material3.Text
import com.dialed.app.wear.HomeFaceState
import com.dialed.app.wear.WearUiState
import com.dialed.app.wear.ui.components.ConnectionStatus
import com.dialed.app.wear.ui.components.DialMark
import com.dialed.app.wear.ui.components.DialedEdgeButton
import com.dialed.app.wear.ui.components.DialedHeroScreen
import com.dialed.app.wear.ui.components.FaceDial
import com.dialed.app.wear.ui.components.WatchLink
import com.dialed.app.wear.ui.theme.DialedWearColors

/**
 * The idle glance (design spec §A · states 1a/1b/1c, plus the installed-but-inactive case the spec
 * predates). Home reflects the GENUINE Watch-Face-Push slot state — never a stale last-pushed cache:
 *
 * - **No face installed** → "No face yet · Push one from the Dialed app" + Open-on-phone.
 * - **Installed & active** → the face + "ACTIVE" (1a) + Browse-on-phone.
 * - **Installed but NOT active** → the face + a "Set as your face" action (setting active is a local
 *   watch op, so it's offered even when the phone is unreachable); if the platform refuses, a
 *   long-press hint replaces the label.
 *
 * Layout is ABSOLUTE/fractional per the mockup (status .14 · face .245 @ 73dp · caption .656 over the
 * 192 dp canvas), NOT a centred Column — a centred Column overflows the round screen and clips. The
 * EdgeButton overlays the bottom bezel. Fractions hold at 192 dp and 225 dp+.
 */
@Composable
fun HomeScreen(state: WearUiState, onSetActive: () -> Unit) {
    var showOpenOnPhone by remember { mutableStateOf(false) }
    val home = state.home
    val reachable = state.link != WatchLink.UNREACHABLE
    // One EdgeButton per screen. An installed-but-inactive face makes SETTING it the primary action
    // (local, works offline); otherwise the edge points back to the phone, and only when reachable.
    val inactiveFace = home is HomeFaceState.Installed && !home.active

    DialedHeroScreen(
        edgeButton = {
            when {
                inactiveFace -> DialedEdgeButton(text = "Set as your face", onClick = onSetActive, filled = true)
                reachable -> DialedEdgeButton(
                    text = if (home is HomeFaceState.Installed) "Browse on phone" else "Open on phone",
                    onClick = { showOpenOnPhone = true },
                    filled = false,
                )
                // Unreachable + no local action → no edge button (reconnection is automatic).
            }
        },
    ) {
        val h = maxHeight
        ConnectionStatus(
            link = state.link,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = h * 0.14f),
        )

        when (home) {
            is HomeFaceState.None -> {
                DialMark(
                    size = 66.dp,
                    alpha = 0.5f,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = h * 0.219f),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = h * 0.599f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("No face yet", style = MaterialTheme.typography.titleMedium, color = DialedWearColors.onSurface)
                    Text(
                        "Push one from the Dialed app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DialedWearColors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            is HomeFaceState.Installed -> {
                FaceDial(
                    preview = home.preview,
                    size = 73.dp,
                    faceName = home.name,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = h * 0.245f),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = h * 0.656f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        home.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = DialedWearColors.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    when {
                        home.active ->
                            Text("ACTIVE", style = MaterialTheme.typography.labelSmall, color = DialedWearColors.primary)
                        state.setByHandHint ->
                            Text(
                                "Touch & hold your watch face to pick it.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DialedWearColors.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        else ->
                            Text("NOT ACTIVE", style = MaterialTheme.typography.labelSmall, color = DialedWearColors.disabled)
                    }
                }
            }
        }
    }

    OpenOnPhoneDialog(
        visible = showOpenOnPhone,
        onDismissRequest = { showOpenOnPhone = false },
        curvedText = null,
    )
}
