package com.dialed.app.wear.ui.screens

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.wear.compose.foundation.ScrollInfoProvider
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
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
 * Activation concierge: one tap when we can, else guide the manual hand-off (spec D, motion W2/W3).
 * Every terminal state exits Dialed to the watch face ([onExitToWatchFace]) — after a successful
 * apply the wrist lands on the freshly-applied face; after coaching it lands on the watch face,
 * which is exactly where the long-press is performed. Only a genuinely-active face
 * ([NO_ACTION_NEEDED]) shows the celebration.
 *
 * ⚠ The manual hand-off is NOT a fallback we chose — it is a PLATFORM wall. `setWatchFaceAsActive`
 * grants exactly ONE unattended activation ever (Google anti-hijack rule; androidify has it too);
 * once it's spent and the live face is another app's, the platform refuses, and the ONLY user path
 * back is the system carousel long-press. There is no picker Intent an app can launch (verified
 * against the WFP API surface + Google docs, 2026-07-18). So [GuidedHandoff] makes that unavoidable
 * step as clear and quick as the platform allows — it never pretends a button can bypass it.
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
        -> GuidedHandoff(state, onExitToWatchFace)
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

/**
 * 1k — backed by the one-shot set-active permission; this is the moment it's spent. Shown only while
 * the unattended set-active MIGHT still land (a fresh budget, or the permission not yet requested):
 * a single tap either applies the face (→ Celebration) or, if the platform refuses, hands off to
 * [GuidedHandoff] via the strategy the caller re-maps. We never re-map a spent-budget install here —
 * the listener already sends those straight to LONG_PRESS_TO_SET so this button is never a dead tap.
 */
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

/**
 * 1m — the guided manual hand-off (spec W3). Reached when the platform has refused the unattended
 * set-active (its once-ever budget is spent) — the documented, ONLY user path left is the system
 * carousel long-press, so this screen makes that step obvious and quick rather than leaving the user
 * to guess. It shows the face they're setting (so they know what to look for), the three real gestures
 * personalised with the face's name, and an action button that drops them straight onto the watch
 * face — which IS the starting point of the long-press. A gentle highlight walks the steps in order
 * (reduced motion → all three shown static, per W5). No button here re-attempts the spent set: that
 * would be a dead tap, and an honest guide beats a false "applied".
 *
 * Built on ScreenScaffold with a scrollable column (not the centred DialedScreen body): the face +
 * three steps can exceed a small round screen, and a scrollable column can never clip — it just
 * scrolls, and the EdgeButton keeps its reserved space.
 */
@Composable
private fun GuidedHandoff(state: ReceiveState.Success, onExit: () -> Unit) {
    val scrollState = rememberScrollState()
    val reduced = isReducedMotionWear()
    // Call the cycle unconditionally (never a conditional composable) — just ignore its value under
    // reduced motion so all three steps show static (W5). `reduced` is remembered-stable anyway.
    val cycledStep = rememberStepCycle()
    val activeStep = if (reduced) 0 else cycledStep
    ScreenScaffold(
        scrollInfoProvider = ScrollInfoProvider(scrollState),
        edgeButton = { DialedEdgeButton("Take me there", onExit, filled = true) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            FaceDial(preview = state.preview, size = 50.dp, faceName = state.faceName)
            Spacer(Modifier.height(10.dp))
            Text(
                "Set it on your watch",
                style = MaterialTheme.typography.titleMedium,
                color = DialedWearColors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))
            CoachStep(1, "Press & hold your face", highlighted = activeStep == 1)
            CoachStep(2, "Swipe to ${state.faceName}", highlighted = activeStep == 2)
            CoachStep(3, "Tap to wear it", highlighted = activeStep == 3)
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * W3 loop, reduced to what a small screen needs: return which step (1..3) is currently lit, cycling
 * 1→2→3→rest so the eye follows the gesture order. Self-cancels with the composition (LaunchedEffect).
 */
@Composable
private fun rememberStepCycle(): Int {
    var step by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            for (s in 1..3) {
                step = s
                delay(1100)
            }
            step = 0        // brief rest so the loop reads as a repeat, not a stutter
            delay(900)
        }
    }
    return step
}

/** One numbered gesture step; the lit step brightens its ring + text (static when not highlighted). */
@Composable
private fun CoachStep(number: Int, text: String, highlighted: Boolean = false) {
    val ringColor =
        if (highlighted) DialedWearColors.primary else DialedWearColors.primary.copy(alpha = 0.5f)
    val textColor =
        if (highlighted) DialedWearColors.onSurface else DialedWearColors.onSurfaceVariant
    Row(
        modifier = Modifier.padding(vertical = 5.dp, horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(22.dp).border(1.dp, ringColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = MaterialTheme.typography.labelSmall, color = ringColor)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = textColor)
    }
}
