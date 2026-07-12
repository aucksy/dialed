package com.dialed.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.dialed.app.model.WatchConnection
import com.dialed.app.model.WatchStatus
import com.dialed.app.ui.theme.dialedColors

/** HANDOFF.md §6 WatchStatusPill — 4 states. */
@Composable
fun WatchStatusPill(status: WatchStatus, modifier: Modifier = Modifier) {
    val c = dialedColors
    val (dotColor, textColor, bg, border, label) = when (status.connection) {
        WatchConnection.CONNECTED -> PillStyle(
            c.success, c.success, c.success.copy(alpha = 0.10f), c.success.copy(alpha = 0.28f),
            "${status.deviceName ?: "Watch"} · Connected",
        )
        WatchConnection.CONNECTING -> PillStyle(
            c.locked, c.onSurfaceVariant, Color.Transparent, c.outline, "Connecting…",
        )
        WatchConnection.UNSUPPORTED -> PillStyle(
            c.error, c.error, c.error.copy(alpha = 0.08f), c.error.copy(alpha = 0.3f),
            "Watch unsupported · Wear OS 6 required",
        )
        WatchConnection.DISCONNECTED -> PillStyle(
            c.locked, c.onSurfaceVariant, Color.Transparent, c.outline, "No watch connected",
        )
    }

    // F5: pulse once when the watch connects (scale 1→1.06→1). Skipped under reduced motion.
    val reduced = isReducedMotion()
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(status.connection) {
        if (status.connection == WatchConnection.CONNECTED && !reduced) {
            pulse.snapTo(1f)
            pulse.animateTo(1.06f, tween(300))
            pulse.animateTo(1f, tween(300))
        }
    }

    Row(
        modifier = modifier
            .graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, border, CircleShape)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(7.dp).clip(CircleShape).background(dotColor),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        Text(text = label, color = textColor, style = MaterialTheme.typography.labelMedium)
    }
}

private data class PillStyle(
    val dot: Color,
    val text: Color,
    val bg: Color,
    val border: Color,
    val label: String,
)
