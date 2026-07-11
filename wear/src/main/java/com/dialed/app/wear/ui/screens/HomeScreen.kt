package com.dialed.app.wear.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.dialed.app.wear.ui.components.DialedScreen
import com.dialed.app.wear.ui.components.FaceDial
import com.dialed.app.wear.ui.components.WatchLink
import com.dialed.app.wear.ui.theme.DialedWearColors

/** The idle glance: status, the active face as the star, one edge action back to the phone. */
@Composable
fun HomeScreen(state: WearUiState) {
    var showOpenOnPhone by remember { mutableStateOf(false) }
    val hasFace = state.homeFaceName != null
    val unreachable = state.link == WatchLink.UNREACHABLE

    DialedScreen(
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
        ConnectionStatus(link = state.link)
        Spacer(Modifier.height(12.dp))

        when {
            unreachable -> {
                FaceDial(
                    preview = state.homePreview,
                    size = 66.dp,
                    faceName = state.homeFaceName,
                    modifier = Modifier.alpha(0.5f),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Your watch face stays put. Pushed faces arrive when you reconnect.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DialedWearColors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }

            hasFace -> {
                FaceDial(preview = state.homePreview, size = 73.dp, faceName = state.homeFaceName)
                Spacer(Modifier.height(12.dp))
                Text(state.homeFaceName!!, style = MaterialTheme.typography.titleMedium, color = DialedWearColors.onSurface)
                Text("ACTIVE", style = MaterialTheme.typography.labelSmall, color = DialedWearColors.primary)
            }

            else -> {
                DialMark(size = 60.dp, alpha = 0.5f)
                Spacer(Modifier.height(14.dp))
                Text("No face yet", style = MaterialTheme.typography.titleMedium, color = DialedWearColors.onSurface)
                Text(
                    "Push one from the Dialed app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DialedWearColors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
    }

    OpenOnPhoneDialog(
        visible = showOpenOnPhone,
        onDismissRequest = { showOpenOnPhone = false },
        curvedText = null,
    )
}
