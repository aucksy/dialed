package com.dialed.app.wear.ui.screens

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.dialed.app.wear.common.WatchFaceActivationStrategy
import com.dialed.app.wear.ui.components.DialedEdgeButton
import com.dialed.app.wear.ui.components.DialedScreen
import com.dialed.app.wear.ui.components.FaceDial
import com.dialed.app.wear.ui.theme.DialedWearColors
import com.dialed.app.wear.wfp.ReceiveState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Activation concierge: one tap when we can, else teach the gesture (spec D, motion W2/W3).
 * Every terminal state exits Dialed to the watch face ([onExitToWatchFace]) — after a successful
 * apply the wrist lands on the freshly-applied face; after coaching it lands where the user
 * long-presses to pick it. Only a genuinely-active face ([NO_ACTION_NEEDED]) shows the celebration.
 */
@Composable
fun ConciergeScreen(
    state: ReceiveState.Success,
    onSetActive: () -> Unit,
    onExitToWatchFace: () -> Unit,
) {
    when (state.strategy) {
        WatchFaceActivationStrategy.NO_ACTION_NEEDED -> Celebration(state, onExitToWatchFace)

        WatchFaceActivationStrategy.CALL_SET_ACTIVE_NO_USER_ACTION,
        WatchFaceActivationStrategy.FOLLOW_PROMPT_ON_WATCH,
        -> OneTapApply(state, onSetActive)

        WatchFaceActivationStrategy.LONG_PRESS_TO_SET,
        WatchFaceActivationStrategy.GO_TO_WATCH_SETTINGS,
        -> GestureCoaching(onExitToWatchFace)
    }
}

/**
 * 1l / motion W2 — the branded apply landing that covers the platform's (distorted) apply moment:
 * the face lands in a perfect circle, a gold conic sheen sweeps the ring once, "Dialed in." rises,
 * Confirm haptic, then auto-EXIT Dialed so the wrist lands on the freshly-applied watch face.
 * Reduced motion → static (sheen/rise snap to rest).
 */
@Composable
private fun Celebration(state: ReceiveState.Success, onExit: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val reduced = isReducedMotionWear()
    val sweep = remember { Animatable(0f) }  // 0..1 sheen travel around the ring
    val rise = remember { Animatable(0f) }   // 0..1 "Dialed in." rise + fade
    LaunchedEffect(Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        if (reduced) {
            sweep.snapTo(1f); rise.snapTo(1f)
        } else {
            launch { sweep.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
            rise.animateTo(1f, tween(250))
        }
        // The face is genuinely active now. Hold long enough for "Dialed in." to clearly read as the
        // completed-install confirmation, then leave Dialed entirely so the wrist lands on the newly-
        // applied face (spec W2 "auto-exit"). We no longer return to Dialed's own Home.
        delay(2000)
        onExit()
    }
    DialedScreen(showTimeText = false) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(112.dp)
                    .border(2.dp, DialedWearColors.primary.copy(alpha = 0.5f), CircleShape),
            )
            // W2 gold conic sheen: a bright arc travels once around the ring, brightest mid-sweep.
            val sheenGold = DialedWearColors.primary
            Canvas(Modifier.size(112.dp)) {
                val stroke = 2.5.dp.toPx()
                val alpha = 0.6f * sin(PI * sweep.value).toFloat().coerceAtLeast(0f)
                if (alpha > 0.01f) {
                    drawArc(
                        color = sheenGold.copy(alpha = alpha),
                        startAngle = -90f + sweep.value * 360f - 50f,
                        sweepAngle = 50f,
                        useCenter = false,
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
            FaceDial(preview = state.preview, size = 97.dp, faceName = state.faceName)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Dialed in.",
            style = MaterialTheme.typography.displaySmall,
            color = DialedWearColors.onPrimaryContainer,
            modifier = Modifier.graphicsLayer {
                translationY = (1f - rise.value) * 12.dp.toPx()
                alpha = rise.value
            },
        )
    }
}

/** Reduced motion = animator duration scale 0 (settings/accessibility). Haptics still fire. */
@Composable
private fun isReducedMotionWear(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** 1k — backed by the one-shot set-active permission; this is the moment it's spent. */
@Composable
private fun OneTapApply(state: ReceiveState.Success, onSetActive: () -> Unit) {
    DialedScreen(
        edgeButton = { DialedEdgeButton("Set as my face", onSetActive, filled = true) },
    ) {
        FaceDial(preview = state.preview, size = 80.dp, faceName = state.faceName)
        Spacer(Modifier.height(16.dp))
        Text(
            "Make it your watch face?",
            style = MaterialTheme.typography.titleMedium,
            color = DialedWearColors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

/** 1m — a wink, not a manual (reduced-motion 3-step form; spec W3 fallback). "Got it" leaves Dialed
 *  for the watch face, which is exactly where the long-press gesture is performed. */
@Composable
private fun GestureCoaching(onExit: () -> Unit) {
    DialedScreen(
        edgeButton = { DialedEdgeButton("Got it", onExit, filled = false) },
    ) {
        Text(
            "Or set it by hand",
            style = MaterialTheme.typography.titleMedium,
            color = DialedWearColors.onSurface,
        )
        Spacer(Modifier.height(14.dp))
        CoachStep(1, "Touch & hold your face")
        CoachStep(2, "Swipe to Dialed")
        CoachStep(3, "Tap to select")
    }
}

@Composable
private fun CoachStep(number: Int, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 5.dp, horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(22.dp).border(1.dp, DialedWearColors.primary.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = MaterialTheme.typography.labelSmall, color = DialedWearColors.primary)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = DialedWearColors.onSurfaceVariant)
    }
}
