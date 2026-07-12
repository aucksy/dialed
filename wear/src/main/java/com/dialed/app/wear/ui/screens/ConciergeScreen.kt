package com.dialed.app.wear.ui.screens

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** Activation concierge: one tap when we can, else teach the gesture (spec D, motion W2/W3). */
@Composable
fun ConciergeScreen(
    state: ReceiveState.Success,
    onSetActive: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state.strategy) {
        WatchFaceActivationStrategy.NO_ACTION_NEEDED,
        WatchFaceActivationStrategy.CALL_SET_ACTIVE_NO_USER_ACTION,
        -> Celebration(state, onDismiss)

        WatchFaceActivationStrategy.FOLLOW_PROMPT_ON_WATCH -> OneTapApply(state, onSetActive)

        WatchFaceActivationStrategy.LONG_PRESS_TO_SET,
        WatchFaceActivationStrategy.GO_TO_WATCH_SETTINGS,
        -> GestureCoaching(onDismiss)
    }
}

/** 1l — the face lands, "Dialed in.", Confirm haptic, auto-exit to the live face. */
@Composable
private fun Celebration(state: ReceiveState.Success, onDismiss: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        // Linger long enough to actually register on the wrist (this returns to Dialed's Home, not
        // the live face, so vanishing too fast reads as "no confirmation").
        delay(2600)
        onDismiss()
    }
    DialedScreen(showTimeText = false) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(112.dp)
                    .border(2.dp, DialedWearColors.primary.copy(alpha = 0.5f), CircleShape),
            )
            FaceDial(preview = state.preview, size = 97.dp, faceName = state.faceName)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Dialed in.",
            style = MaterialTheme.typography.displaySmall,
            color = DialedWearColors.onPrimaryContainer,
        )
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

/** 1m — a wink, not a manual (reduced-motion 3-step form; spec W3 fallback). */
@Composable
private fun GestureCoaching(onDismiss: () -> Unit) {
    DialedScreen(
        edgeButton = { DialedEdgeButton("Got it", onDismiss, filled = false) },
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
