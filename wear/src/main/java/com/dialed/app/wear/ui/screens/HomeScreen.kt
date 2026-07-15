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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OpenOnPhoneDialog
import androidx.wear.compose.material3.Text
import com.dialed.app.wear.WearUiState
import com.dialed.app.wear.ui.components.ConnectionStatus
import com.dialed.app.wear.ui.components.DialMark
import com.dialed.app.wear.ui.components.DialedEdgeButton
import com.dialed.app.wear.ui.components.DialedHeroScreen
import com.dialed.app.wear.ui.components.FaceDial
import com.dialed.app.wear.ui.components.WatchLink
import com.dialed.app.wear.ui.theme.DialedWearColors

/**
 * The idle glance (design spec §A · states 1a/1b/1c): calm status, the active face as the star, one
 * edge action back to the phone.
 *
 * Layout is ABSOLUTE/fractional per the mockup — NOT a centered Column. The old centered Column
 * overflowed on the round screen (status + 73dp face + name + "ACTIVE" ≈ 157dp, minus the space the
 * scaffold reserves for the EdgeButton), so it clipped the status off the top and the name off the
 * bottom and shoved the face into the upper third. Here each element anchors to a fraction of the
 * screen height (status .14 · face .245 · caption .656 — the mockup's 54/94/252 px over its 384 px /
 * 192 dp canvas), and the EdgeButton overlays the bottom bezel, so the face is the centred star and
 * nothing clips. Fractions hold at 192 dp and 225 dp+.
 */
@Composable
fun HomeScreen(state: WearUiState) {
    var showOpenOnPhone by remember { mutableStateOf(false) }
    val hasFace = state.homeFaceName != null
    val unreachable = state.link == WatchLink.UNREACHABLE

    DialedHeroScreen(
        edgeButton = {
            // Phone-not-reachable offers no action (reconnection is automatic) — hide the button.
            if (!unreachable) {
                DialedEdgeButton(
                    text = if (hasFace) "Browse on phone" else "Open on phone",
                    onClick = { showOpenOnPhone = true },
                    filled = false,
                )
            }
        },
    ) {
        val h = maxHeight
        // Status: dot + label just under TimeText. Always shown; the label reflects the link.
        ConnectionStatus(
            link = state.link,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = h * 0.14f),
        )

        when {
            unreachable -> {
                FaceDial(
                    preview = state.homePreview,
                    size = 65.dp,
                    faceName = state.homeFaceName,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = h * 0.286f)
                        .alpha(0.5f),
                )
                Text(
                    "Your watch face stays put. Pushed faces arrive when you reconnect.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DialedWearColors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = h * 0.672f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                )
            }

            hasFace -> {
                FaceDial(
                    preview = state.homePreview,
                    size = 73.dp,
                    faceName = state.homeFaceName,
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
                        state.homeFaceName!!,
                        style = MaterialTheme.typography.titleMedium,
                        color = DialedWearColors.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text("ACTIVE", style = MaterialTheme.typography.labelSmall, color = DialedWearColors.primary)
                }
            }

            else -> {
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
        }
    }

    OpenOnPhoneDialog(
        visible = showOpenOnPhone,
        onDismissRequest = { showOpenOnPhone = false },
        curvedText = null,
    )
}
