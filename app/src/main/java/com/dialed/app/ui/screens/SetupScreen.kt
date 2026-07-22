package com.dialed.app.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.dialed.app.catalog.Face
import com.dialed.app.transport.WatchSetup
import com.dialed.app.transport.WatchSetupState
import com.dialed.app.ui.components.DialStatus
import com.dialed.app.ui.components.DialedButton
import com.dialed.app.ui.components.DialedButtonVariant
import com.dialed.app.ui.components.FaceDial
import com.dialed.app.ui.theme.DialedRadius
import com.dialed.app.ui.theme.DialedSpacing
import com.dialed.app.ui.theme.dialedColors
import kotlinx.coroutines.delay

/**
 * The ONE pre-Home screen (replaces the old 3-page onboarding pager — docs/ONBOARDING-REDESIGN.md
 * §5.1). It performs the watch setup instead of describing it: a live status card watches the real
 * state of the watch (paired? Dialed watch app on it? set up? supported?) and adapts its copy + CTA,
 * auto-advancing as the state improves. The store is never hostage to hardware — "Browse faces
 * first" is always available, and the no-watch/unsupported paths end in "Browse faces anyway".
 */
@Composable
fun SetupScreen(
    setup: WatchSetup,
    starterFaces: List<Face>,
    onRefresh: () -> Unit,
    onFinish: () -> Unit,
) {
    val c = dialedColors

    // Live detection loop while this screen is visible (probes are cheap: one node list + one
    // query-state RPC; the RPC self-degrades to a no-op when nothing is reachable).
    LaunchedEffect(Unit) {
        while (true) {
            onRefresh()
            delay(POLL_MS)
        }
    }

    // "Set up my watch" was tapped: relabel WATCH_APP_MISSING as the in-progress wait state.
    var installStarted by rememberSaveable { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }

    // The wait state must be able to end. It used to latch forever: a user who tapped "Set up my
    // watch" and then didn't install anything was left staring at "Installing on your watch…" with
    // no way back to the real CTA. Reset it whenever the state moves on, and time it out otherwise.
    LaunchedEffect(setup.state, installStarted) {
        if (setup.state != WatchSetupState.WATCH_APP_MISSING) {
            installStarted = false
        } else if (installStarted) {
            delay(INSTALL_WAIT_MS)
            installStarted = false
        }
    }

    // READY beat: when the watch turns ready, hold the CTA disabled for a moment so the state
    // change never yanks the screen mid-read (spec: auto-advance never yanks).
    var readyArmed by remember { mutableStateOf(false) }
    LaunchedEffect(setup.state) {
        if (setup.state == WatchSetupState.READY) {
            delay(READY_BEAT_MS)
            readyArmed = true
        } else {
            readyArmed = false
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 28.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
            Text(
                "Browse faces first",
                style = MaterialTheme.typography.titleMedium,
                color = c.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onFinish)
                    .padding(horizontal = 12.dp, vertical = 12.dp), // ≥48dp target
            )
        }

        Spacer(Modifier.height(DialedSpacing.xl))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Dialed",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = c.onSurface,
            )
            Text(
                ".",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = c.primary,
            )
        }
        Text(
            "Real faces for your watch.",
            style = MaterialTheme.typography.bodyLarge,
            color = c.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )

        // The product is the hero: real faces, the same vitrine trio language as Home.
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (starterFaces.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((-18).dp),
                ) {
                    starterFaces.getOrNull(1)?.let { FaceDial(it, 96.dp, Modifier.zIndex(1f), status = DialStatus.NONE) }
                    starterFaces.getOrNull(0)?.let { FaceDial(it, 132.dp, Modifier.zIndex(2f), status = DialStatus.NONE) }
                    starterFaces.getOrNull(2)?.let { FaceDial(it, 96.dp, Modifier.zIndex(1f), status = DialStatus.NONE) }
                }
            }
        }

        val ui = setupUi(setup, installStarted, readyArmed)

        // The live element: state-driven card. TalkBack hears state changes (polite live region).
        val cardShape = RoundedCornerShape(DialedRadius.lg)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(c.surfaceContainerHigh)
                .border(1.dp, c.outlineVariant, cardShape)
                .padding(DialedSpacing.lg)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "${ui.line1} ${ui.line2}"
                },
        ) {
            Crossfade(targetState = ui, label = "setupCard") { s ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(s.dot(c)))
                        Spacer(Modifier.size(10.dp))
                        Text(
                            s.line1,
                            style = MaterialTheme.typography.titleMedium,
                            color = c.onSurface,
                        )
                    }
                    Text(
                        s.line2,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(DialedSpacing.md))
        DialedButton(
            text = ui.cta,
            onClick = {
                when (ui.action) {
                    SetupAction.CONTINUE -> onFinish()
                    SetupAction.INSTALL -> {
                        installStarted = true
                        showGuide = true
                    }
                    SetupAction.GUIDE -> showGuide = true
                    SetupAction.RECHECK -> onRefresh()
                    SetupAction.NONE -> Unit
                }
            },
            variant = if (ui.ghost) DialedButtonVariant.TONAL else DialedButtonVariant.FILLED,
            enabled = ui.action != SetupAction.NONE,
            height = 52.dp,
        )
        Text(
            "Nothing leaves your phone.",
            style = MaterialTheme.typography.labelMedium,
            color = c.locked,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }

    if (showGuide) {
        WatchInstallGuideSheet(watchName = setup.watchName, onDismiss = { showGuide = false })
    }
}

/** What each state renders (copy table: docs/ONBOARDING-REDESIGN.md §5.1). */
private enum class SetupAction { CONTINUE, INSTALL, GUIDE, RECHECK, NONE }

private data class SetupUi(
    val line1: String,
    val line2: String,
    val cta: String,
    val action: SetupAction,
    val ghost: Boolean,
    val dotKind: WatchSetupState,
) {
    fun dot(c: com.dialed.app.ui.theme.DialedColors): Color = when (dotKind) {
        WatchSetupState.READY -> c.success
        WatchSetupState.UNSUPPORTED -> c.error
        WatchSetupState.CHECKING, WatchSetupState.NO_WATCH -> c.locked
        else -> c.primary
    }
}

private fun setupUi(setup: WatchSetup, installStarted: Boolean, readyArmed: Boolean): SetupUi {
    val watch = setup.watchName ?: "Your watch"
    return when (setup.state) {
        WatchSetupState.CHECKING -> SetupUi(
            "Looking for your watch…", "This usually takes a moment.",
            "One moment…", SetupAction.NONE, ghost = true, dotKind = setup.state,
        )
        WatchSetupState.READY -> SetupUi(
            "$watch is ready.", "Faces you pick appear right on it.",
            "Choose your first face",
            if (readyArmed) SetupAction.CONTINUE else SetupAction.NONE,
            ghost = false, dotKind = setup.state,
        )
        WatchSetupState.WATCH_APP_MISSING ->
            if (installStarted) {
                SetupUi(
                    "Installing on your watch…", "This screen will move on by itself.",
                    "Having trouble?", SetupAction.GUIDE, ghost = true, dotKind = setup.state,
                )
            } else {
                SetupUi(
                    "$watch needs the Dialed watch app.", "It takes one tap and about a minute.",
                    "Set up my watch", SetupAction.INSTALL, ghost = false, dotKind = setup.state,
                )
            }
        WatchSetupState.OPEN_ON_WATCH -> SetupUi(
            "Almost there — open Dialed on your watch.", "Tap “Set up Dialed” on the watch screen.",
            "I've done it", SetupAction.RECHECK, ghost = true, dotKind = setup.state,
        )
        WatchSetupState.NO_WATCH -> SetupUi(
            "No watch is paired with this phone yet.",
            "Pair one in your watch app, then come back — Dialed will spot it.",
            "Browse faces anyway", SetupAction.CONTINUE, ghost = true, dotKind = setup.state,
        )
        WatchSetupState.UNSUPPORTED -> SetupUi(
            "$watch runs an older Wear OS.",
            "Dialed faces need Wear OS 6 — everything on your watch stays as it is.",
            "Browse faces anyway", SetupAction.CONTINUE, ghost = true, dotKind = setup.state,
        )
    }
}

/**
 * The watch-app install guide (WatchAppInstaller impl A — docs/ONBOARDING-REDESIGN.md §10.3).
 * When Dialed is on the Play Store this sheet's CTA becomes a remote install fired at the watch
 * node (RemoteActivityHelper + market:// URI); until then, the honest manual steps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchInstallGuideSheet(watchName: String?, onDismiss: () -> Unit) {
    val c = dialedColors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 40.dp, top = 8.dp)) {
            Text(
                "Get Dialed on ${watchName ?: "your watch"}",
                style = MaterialTheme.typography.titleMedium,
                color = c.onSurface,
            )
            Spacer(Modifier.height(DialedSpacing.md))
            GuideStep(1, "On your watch, open the Play Store.")
            GuideStep(2, "Search for “Dialed” and install it.")
            GuideStep(3, "Open it once — this screen moves on by itself.")
            Spacer(Modifier.height(DialedSpacing.lg))
            DialedButton("Got it", onDismiss, variant = DialedButtonVariant.TONAL, height = 48.dp)
        }
    }
}

@Composable
private fun GuideStep(number: Int, text: String) {
    val c = dialedColors
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).border(1.dp, c.primary.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = MaterialTheme.typography.labelMedium, color = c.primary)
        }
        Spacer(Modifier.size(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
    }
}

private const val POLL_MS = 3_000L
private const val READY_BEAT_MS = 600L

/** How long "Installing on your watch…" waits before offering the real CTA again. */
private const val INSTALL_WAIT_MS = 120_000L
