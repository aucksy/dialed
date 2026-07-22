package com.dialed.app.wear.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.ScrollInfoProvider
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.dialed.app.wear.ui.components.DialMark
import com.dialed.app.wear.ui.components.DialedEdgeButton
import com.dialed.app.wear.ui.components.DialedScreen
import com.dialed.app.wear.ui.theme.DialedWearColors
import kotlinx.coroutines.delay

/**
 * The ONE watch-side setup moment (replaces the old FirstRun permission screen + MakeDefault screen
 * pair — two decisions collapsed into one tap). "Set up Dialed" chains, in direct consequence of the
 * tap: the install permission dialog → the one-shot set-active permission dialog → installing +
 * activating the bundled Dialed default face → the "Dialed in." celebration → exit to the face.
 *
 * The set-active permission is the highest-stakes dialog in the product — the platform allows it to
 * be REQUESTED exactly once, ever — so it must arrive with its benefit on screen ("your first face
 * goes on now"), never as a context-free stacked prompt (which is what the old flow did).
 *
 * Variant: when a face was pushed BEFORE setup ([pendingFaceName]), that face IS the context —
 * "{Face} is waiting" — the strongest possible framing for the ask.
 *
 * "Later" is safe, not a dead end: the ask simply moves to the moment a face actually arrives
 * (the phone answers NEEDS_SETUP and guides the user back here with the face named).
 */
@Composable
fun SetupScreen(
    pendingFaceName: String?,
    permanentlyDenied: Boolean,
    onSetUp: () -> Unit,
    onSkip: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    if (permanentlyDenied) {
        // The install permission was denied — the OS won't re-prompt; Settings is the honest path.
        DialedScreen(
            edgeButton = { DialedEdgeButton("Open Settings", onOpenSettings, filled = false) },
        ) {
            InfoBadge()
            Spacer(Modifier.height(14.dp))
            Text(
                "Permission needed",
                style = MaterialTheme.typography.titleMedium,
                color = DialedWearColors.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Turn on Watch faces for Dialed in Settings › Apps.",
                style = MaterialTheme.typography.bodyLarge,
                color = DialedWearColors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
        }
        return
    }

    val scrollState = rememberScrollState()
    ScreenScaffold(
        scrollInfoProvider = ScrollInfoProvider(scrollState),
        edgeButton = {
            DialedEdgeButton(
                text = if (pendingFaceName != null) "Allow and install" else "Set up Dialed",
                onClick = onSetUp,
                filled = true,
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(6.dp))
            DialMark(size = if (pendingFaceName != null) 72.dp else 96.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                pendingFaceName?.let { "$it is waiting" } ?: "Make Dialed your watch face",
                style = MaterialTheme.typography.titleMedium,
                color = DialedWearColors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (pendingFaceName != null) {
                    "Allow Dialed to install it, and it goes on now."
                } else {
                    "One tap: Dialed can install the faces you pick, and your first face goes on now."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = DialedWearColors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
            Spacer(Modifier.height(14.dp))
            // Secondary action: a quiet text button (the EdgeButton owns the primary action).
            Text(
                "Later",
                style = MaterialTheme.typography.labelLarge,
                color = DialedWearColors.disabled,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clickable(onClick = onSkip)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Setup's payoff: the bundled default face is genuinely ACTIVE (never shown otherwise — "Dialed in."
 * must not lie). Same visual language as the post-push celebration: gold ring, "Dialed in.", Confirm
 * haptic, then auto-exit so the wrist lands on the new face.
 */
@Composable
fun SetupDoneScreen(onDone: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        delay(2000)
        onDone()
    }
    DialedScreen(showTimeText = false) {
        Box(
            Modifier
                .size(112.dp)
                .border(2.dp, DialedWearColors.primary.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            DialMark(size = 84.dp)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Dialed in.",
            style = MaterialTheme.typography.displaySmall,
            color = DialedWearColors.onPrimaryContainer,
        )
    }
}

/** Spec 1f — an info mark (dot over a stroke) in a hairline circle, above the denied copy. */
@Composable
private fun InfoBadge() {
    val tint = DialedWearColors.onSurfaceVariant
    Canvas(Modifier.size(56.dp).border(1.5.dp, DialedWearColors.outline, CircleShape)) {
        val cx = size.width / 2f
        drawCircle(color = tint, radius = size.minDimension * 0.035f, center = Offset(cx, size.height * 0.36f))
        drawLine(
            color = tint,
            start = Offset(cx, size.height * 0.46f),
            end = Offset(cx, size.height * 0.66f),
            strokeWidth = size.minDimension * 0.06f,
            cap = StrokeCap.Round,
        )
    }
}
