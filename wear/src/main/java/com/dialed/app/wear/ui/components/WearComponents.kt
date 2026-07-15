package com.dialed.app.wear.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.foundation.ScrollInfoProvider
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SegmentedCircularProgressIndicator
import androidx.wear.compose.material3.Text
import com.dialed.app.wear.ui.theme.DialedWearColors

/**
 * Standard Dialed screen shell: an optional single EdgeButton (bottom, bezel-following) and a
 * centered content column. Wrap the whole app once in AppScaffold (WearApp) — this provides the
 * per-screen ScreenScaffold + TimeText. Pass [showTimeText] = false for celebration screens.
 */
@Composable
fun DialedScreen(
    showTimeText: Boolean = true,
    edgeButton: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val timeText: (@Composable () -> Unit)? = if (showTimeText) null else ({})

    if (edgeButton != null) {
        ScreenScaffold(
            scrollInfoProvider = ScrollInfoProvider(scrollState),
            edgeButton = edgeButton,
            timeText = timeText,
        ) { contentPadding -> DialedScreenBody(contentPadding, content) }
    } else {
        ScreenScaffold(
            scrollInfoProvider = ScrollInfoProvider(scrollState),
            timeText = timeText,
        ) { contentPadding -> DialedScreenBody(contentPadding, content) }
    }
}

@Composable
private fun DialedScreenBody(
    contentPadding: PaddingValues,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

/**
 * Hero-screen shell for the absolute/fractional layouts in the design spec (Home 1a/1b/1c). Unlike
 * [DialedScreen] this gives a FULL-SCREEN [BoxWithConstraints] — no centered Column, and it does NOT
 * apply the scaffold's content-padding. The EdgeButton overlays the bottom bezel (design: bottom:-18px,
 * reserves no content space), and children anchor to fractions of `maxHeight` (status .14 · face .245 ·
 * caption .656 …) so the face reads as the star and nothing clips. TimeText sits at the very top (~5%),
 * above the .14 status. Same one-EdgeButton rule.
 */
@Composable
fun DialedHeroScreen(
    edgeButton: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    if (edgeButton != null) {
        ScreenScaffold(
            scrollInfoProvider = ScrollInfoProvider(scrollState),
            edgeButton = edgeButton,
        ) { _ -> BoxWithConstraints(Modifier.fillMaxSize(), content = content) }
    } else {
        ScreenScaffold(
            scrollInfoProvider = ScrollInfoProvider(scrollState),
        ) { _ -> BoxWithConstraints(Modifier.fillMaxSize(), content = content) }
    }
}

/** One filled (gold) or tonal EdgeButton — the single edge action per screen. */
@Composable
fun DialedEdgeButton(
    text: String,
    onClick: () -> Unit,
    filled: Boolean = true,
    enabled: Boolean = true,
) {
    EdgeButton(
        onClick = onClick,
        buttonSize = EdgeButtonSize.Large,
        enabled = enabled,
        colors = if (filled) {
            ButtonDefaults.buttonColors(
                containerColor = DialedWearColors.primary,
                contentColor = DialedWearColors.onPrimary,
            )
        } else {
            ButtonDefaults.filledTonalButtonColors(
                containerColor = DialedWearColors.primaryContainer,
                contentColor = DialedWearColors.onPrimaryContainer,
            )
        },
        border = if (filled) null else BorderStroke(1.dp, DialedWearColors.primary.copy(alpha = 0.35f)),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

enum class WatchLink { CONNECTED, CONNECTING, UNREACHABLE }

/** Dot + label; announces connection changes to TalkBack via a live region. */
@Composable
fun ConnectionStatus(link: WatchLink, modifier: Modifier = Modifier) {
    val label = when (link) {
        WatchLink.CONNECTED -> "Connected"
        WatchLink.CONNECTING -> "Connecting…"
        WatchLink.UNREACHABLE -> "Phone not reachable"
    }
    val textColor = if (link == WatchLink.CONNECTED) DialedWearColors.onSurfaceVariant else DialedWearColors.disabled
    Row(
        modifier = modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = label
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val dotModifier = Modifier.size(9.dp).clip(CircleShape)
        when (link) {
            WatchLink.CONNECTED -> Box(dotModifier.background(DialedWearColors.success))
            WatchLink.CONNECTING -> Box(dotModifier.border(1.5.dp, DialedWearColors.disabled, CircleShape))
            WatchLink.UNREACHABLE -> Box(dotModifier.background(DialedWearColors.disabled))
        }
        Spacer(Modifier.width(8.dp))
        Text(text = label, color = textColor, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Full-bezel indeterminate arc (the receive sweep). Tints [error] and no longer spins on error. */
@Composable
fun BezelIndeterminate(modifier: Modifier = Modifier, error: Boolean = false) {
    if (error) {
        CircularProgressIndicator(
            progress = { 0.27f },
            modifier = modifier.fillMaxSize().padding(3.5.dp),
            colors = ProgressIndicatorDefaults.colors(
                indicatorColor = DialedWearColors.error,
                trackColor = DialedWearColors.progressTrack,
            ),
            strokeWidth = 4.5.dp,
        )
    } else {
        CircularProgressIndicator(
            modifier = modifier.fillMaxSize().padding(3.5.dp),
            colors = ProgressIndicatorDefaults.colors(
                indicatorColor = DialedWearColors.primary,
                trackColor = DialedWearColors.progressTrack,
            ),
            strokeWidth = 4.5.dp,
        )
    }
}

/** Full-bezel 12-segment install indicator; [filled] segments (0..12) glow gold. */
@Composable
fun BezelSegments(filled: Int, modifier: Modifier = Modifier) {
    SegmentedCircularProgressIndicator(
        segmentCount = 12,
        progress = { (filled.coerceIn(0, 12)) / 12f },
        modifier = modifier.fillMaxSize().padding(3.5.dp),
        colors = ProgressIndicatorDefaults.colors(
            indicatorColor = DialedWearColors.primary,
            trackColor = DialedWearColors.progressTrack,
        ),
        strokeWidth = 4.5.dp,
    )
}
