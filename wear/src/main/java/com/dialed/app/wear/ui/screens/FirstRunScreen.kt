package com.dialed.app.wear.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.dialed.app.wear.ui.components.DialMark
import com.dialed.app.wear.ui.components.DialedEdgeButton
import com.dialed.app.wear.ui.components.DialedScreen
import com.dialed.app.wear.ui.theme.DialedWearColors

/**
 * The rationale before the system permission dialog. The one-shot set-active permission is NOT
 * asked here — it's spent at the first install (concierge). Once push permission is granted the
 * app routes straight to Home, so there's no separate "granted" step.
 */
@Composable
fun FirstRunScreen(
    permanentlyDenied: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    if (permanentlyDenied) {
        DialedScreen(
            edgeButton = { DialedEdgeButton("Open Settings", onOpenSettings, filled = false) },
        ) {
            Text(
                "Permission needed",
                style = MaterialTheme.typography.titleMedium,
                color = DialedWearColors.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Turn on Watch faces for Dialed in Settings › Apps.",
                style = MaterialTheme.typography.bodyMedium,
                color = DialedWearColors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
        }
    } else {
        DialedScreen(
            edgeButton = { DialedEdgeButton("Allow", onAllow, filled = true) },
        ) {
            DialMark(size = 46.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                "Faces, from your phone",
                style = MaterialTheme.typography.titleMedium,
                color = DialedWearColors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Allow Dialed to receive and install the faces you push from your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = DialedWearColors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
        }
    }
}
