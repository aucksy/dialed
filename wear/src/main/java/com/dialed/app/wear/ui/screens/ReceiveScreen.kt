package com.dialed.app.wear.ui.screens

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.ScrollInfoProvider
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.dialed.app.wear.ui.components.BezelIndeterminate
import com.dialed.app.wear.ui.components.BezelSegments
import com.dialed.app.wear.ui.components.DialedEdgeButton
import com.dialed.app.wear.ui.components.DialedScreen
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

        is ReceiveState.Failed -> DialedScreen(
            showTimeText = false,
            edgeButton = { DialedEdgeButton("Dismiss", onDismiss, filled = true) },
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

        else -> Unit // Idle / Success handled by the navigator.
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
