package com.dialed.app.wear.ui.screens

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.ScrollInfoProvider
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.dialed.app.wear.ui.components.BezelIndeterminate
import com.dialed.app.wear.ui.components.BezelSegments
import com.dialed.app.wear.ui.components.DialedEdgeButton
import com.dialed.app.wear.ui.components.FaceDial
import com.dialed.app.wear.ui.theme.DialedWearColors
import com.dialed.app.wear.wfp.ReceiveState

/** The catch: bezel progress + the face materializing in the center (spec C, motion W1). */
@Composable
fun ReceiveScreen(state: ReceiveState, onDismiss: () -> Unit) {
    when (state) {
        is ReceiveState.Receiving -> BezelScreen {
            BezelIndeterminate()
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FaceDial(preview = null, size = 69.dp, faceName = state.faceName, modifier = Modifier.alpha(0.55f))
                Spacer(Modifier.height(16.dp))
                Text("RECEIVING", style = MaterialTheme.typography.labelSmall, color = DialedWearColors.onSurfaceVariant)
                Text(state.faceName, style = MaterialTheme.typography.titleMedium, color = DialedWearColors.onSurface)
            }
        }

        is ReceiveState.Installing -> {
            var target by remember { mutableIntStateOf(0) }
            LaunchedEffect(Unit) { target = 12 }
            val segments by animateIntAsState(targetValue = target, animationSpec = tween(1800), label = "install-segments")
            BezelScreen {
                BezelSegments(filled = segments)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FaceDial(preview = null, size = 69.dp, faceName = state.faceName)
                    Spacer(Modifier.height(16.dp))
                    Text("Installing…", style = MaterialTheme.typography.titleMedium, color = DialedWearColors.onSurface)
                    Text("Just a moment", style = MaterialTheme.typography.bodyMedium, color = DialedWearColors.disabled)
                }
            }
        }

        // 1j — the bezel FREEZES where it stopped and tints error; the face dims. Keeping the bezel
        // (not a plain text screen) holds the receive flow's visual language on the one screen the
        // user is most anxious on. Action stays "Dismiss": the watch can't re-initiate a phone push,
        // so the honest next step is to push again from the phone (spec's "Retry" would be a false
        // affordance here).
        is ReceiveState.Failed -> {
            val scrollState = rememberScrollState()
            ScreenScaffold(
                scrollInfoProvider = ScrollInfoProvider(scrollState),
                edgeButton = { DialedEdgeButton("Dismiss", onDismiss, filled = true) },
                timeText = {},
            ) { contentPadding ->
                Box(Modifier.fillMaxSize().background(DialedWearColors.background)) {
                    BezelIndeterminate(error = true)
                    Column(
                        modifier = Modifier.fillMaxSize().padding(contentPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        FaceDial(preview = null, size = 58.dp, faceName = state.faceName, modifier = Modifier.alpha(0.35f))
                        Spacer(Modifier.height(14.dp))
                        Text("Interrupted", style = MaterialTheme.typography.titleMedium, color = DialedWearColors.onSurface)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Keep your phone nearby and push it again.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DialedWearColors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 22.dp),
                        )
                    }
                }
            }
        }

        else -> Unit // Idle / Success handled by the navigator (ReceiveSuccess + ConciergeScreen).
    }
}

/** Full-bleed hero surface with TimeText suppressed (the catch owns the whole screen). */
@Composable
private fun BezelScreen(content: @Composable BoxScope.() -> Unit) {
    val scrollState = rememberScrollState()
    ScreenScaffold(
        scrollInfoProvider = ScrollInfoProvider(scrollState),
        timeText = {},
    ) { _ ->
        Box(
            modifier = Modifier.fillMaxSize().background(DialedWearColors.background),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

/**
 * 1i / W1 beat 5 — the catch's payoff, restored: a gold arc sweeps the bezel, a check pops, "On your
 * wrist." + "{name} installed" confirm the face is genuinely on the watch. Shown for ~1.1s (the
 * navigator advances to the activation concierge after). Confirm haptic; reduced motion → static.
 * This is the "installed" confirmation the flow was missing — without it a first install gave no
 * visible success, which read as "it didn't work".
 */
@Composable
fun ReceiveSuccess(state: ReceiveState.Success) {
    val haptic = LocalHapticFeedback.current
    val reduced = isReducedMotionWear()
    val draw = remember { Animatable(0f) } // 0..1 arc sweep + check reveal
    LaunchedEffect(state.faceName) {
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        if (reduced) draw.snapTo(1f) else draw.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
    }
    val gold = DialedWearColors.primary
    val scrollState = rememberScrollState()
    ScreenScaffold(scrollInfoProvider = ScrollInfoProvider(scrollState), timeText = {}) { _ ->
        Box(
            modifier = Modifier.fillMaxSize().background(DialedWearColors.background),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                val stroke = 4.5.dp.toPx()
                drawArc(
                    color = gold.copy(alpha = 0.75f),
                    startAngle = 210f,
                    sweepAngle = 120f * draw.value,
                    useCenter = false,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(
                    Modifier.size(56.dp).graphicsLayer {
                        alpha = draw.value
                        val s = 0.6f + 0.4f * draw.value
                        scaleX = s
                        scaleY = s
                    },
                ) {
                    val w = size.width
                    val h = size.height
                    val stroke = w * 0.09f
                    drawLine(gold, Offset(w * 0.18f, h * 0.52f), Offset(w * 0.42f, h * 0.74f), stroke, cap = StrokeCap.Round)
                    drawLine(gold, Offset(w * 0.42f, h * 0.74f), Offset(w * 0.82f, h * 0.28f), stroke, cap = StrokeCap.Round)
                }
                Spacer(Modifier.height(16.dp))
                Text("On your wrist.", style = MaterialTheme.typography.displaySmall, color = DialedWearColors.onSurface)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${state.faceName} installed",
                    style = MaterialTheme.typography.bodyLarge,
                    color = DialedWearColors.onSurfaceVariant,
                )
            }
        }
    }
}

/** Reduced motion = animator duration scale 0. Haptics still fire. */
@Composable
private fun isReducedMotionWear(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
