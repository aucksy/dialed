package com.dialed.app.wear.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OpenOnPhoneDialog
import androidx.wear.compose.material3.Text
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.dialed.app.wear.HomeFaceState
import com.dialed.app.wear.WearUiState
import com.dialed.app.wear.common.WearConstants
import com.dialed.app.wear.ui.components.ConnectionStatus
import com.dialed.app.wear.ui.components.DialMark
import com.dialed.app.wear.ui.components.DialedEdgeButton
import com.dialed.app.wear.ui.components.DialedHeroScreen
import com.dialed.app.wear.ui.components.FaceDial
import com.dialed.app.wear.ui.components.WatchLink
import com.dialed.app.wear.ui.theme.DialedWearColors

/**
 * The idle glance (design spec §A · states 1a/1b/1c, plus the installed-but-inactive case the spec
 * predates). Home reflects the GENUINE Watch-Face-Push slot state — never a stale last-pushed cache:
 *
 * - **No face installed** → "No face yet · Push one from the Dialed app" + Open-on-phone.
 * - **Installed & active** → the face + "ACTIVE" (1a) + Browse-on-phone.
 * - **Installed but NOT active** → the face + a "Set as your face" action (setting active is a local
 *   watch op, so it's offered even when the phone is unreachable); if the platform refuses, a
 *   long-press hint replaces the label.
 *
 * Layout is ABSOLUTE/fractional per the mockup (status .14 · face .245 @ 73dp · caption .656 over the
 * 192 dp canvas), NOT a centred Column — a centred Column overflows the round screen and clips. The
 * EdgeButton overlays the bottom bezel. Fractions hold at 192 dp and 225 dp+.
 */
@Composable
fun HomeScreen(state: WearUiState, onSetActive: () -> Unit, onSetUp: () -> Unit) {
    val context = LocalContext.current
    val remoteHelper = remember { RemoteActivityHelper(context) }
    var showOpenOnPhone by remember { mutableStateOf(false) }
    val home = state.home
    val reachable = state.link != WatchLink.UNREACHABLE
    // Setup was skipped ("Later") or never finished: Dialed cannot install anything yet. Home is
    // the ONLY screen left in that state, so it must carry the way back — the phone's setup screen
    // says "open Dialed on your watch and tap Set up Dialed", and before this there was no such
    // button anywhere once "Later" had resolved the one-time gate.
    val needsPermission = !state.pushGranted
    // Actually launch the phone app; drop the "check your phone" confirmation only on a real failure.
    val openOnPhone: () -> Unit = {
        showOpenOnPhone = true
        openDialedOnPhone(context, remoteHelper) { ok -> if (!ok) showOpenOnPhone = false }
    }
    // One EdgeButton per screen. An installed-but-inactive face makes SETTING it the primary action
    // (local, works offline); otherwise the edge points back to the phone, and only when reachable.
    val inactiveFace = home is HomeFaceState.Installed && !home.active

    DialedHeroScreen(
        edgeButton = {
            when {
                // A face they can try to wear right now still outranks everything (unchanged).
                inactiveFace -> DialedEdgeButton(text = "Set as your face", onClick = onSetActive, filled = true)
                // Otherwise, with no install permission nothing else here can work — and after
                // "Later" this is the ONLY remaining route back to setup.
                needsPermission -> DialedEdgeButton(text = "Set up Dialed", onClick = onSetUp, filled = true)
                reachable -> DialedEdgeButton(
                    text = if (home is HomeFaceState.Installed) "Browse on phone" else "Open on phone",
                    onClick = openOnPhone,
                    filled = false,
                )
                // Unreachable + no local action → no edge button (reconnection is automatic).
            }
        },
    ) {
        val h = maxHeight
        ConnectionStatus(
            link = state.link,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = h * 0.14f),
        )

        when (home) {
            is HomeFaceState.None -> {
                DialMark(
                    size = 66.dp,
                    alpha = 0.5f,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = h * 0.219f),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = h * 0.599f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (needsPermission) "Not set up yet" else "No face yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = DialedWearColors.onSurface,
                    )
                    Text(
                        if (needsPermission) {
                            "Dialed needs your OK to put faces on this watch."
                        } else {
                            "Push one from the Dialed app."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = DialedWearColors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            is HomeFaceState.Installed -> {
                FaceDial(
                    preview = home.preview,
                    size = 73.dp,
                    faceName = home.name,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = h * 0.245f),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = h * 0.656f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        home.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = DialedWearColors.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    when {
                        home.active ->
                            Text("ACTIVE", style = MaterialTheme.typography.labelSmall, color = DialedWearColors.primary)
                        state.setByHandHint ->
                            // The unattended set-active was refused (platform's once-ever budget spent).
                            // The only path left is the system carousel long-press — say so plainly, in
                            // the same words as the post-push guide (ConciergeScreen GuidedHandoff).
                            Text(
                                "Press & hold your watch face, then swipe to pick it.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DialedWearColors.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        else ->
                            Text("NOT ACTIVE", style = MaterialTheme.typography.labelSmall, color = DialedWearColors.disabled)
                    }
                }
            }
        }
    }

    OpenOnPhoneDialog(
        visible = showOpenOnPhone,
        onDismissRequest = { showOpenOnPhone = false },
        curvedText = null,
    )
}

private const val OPEN_ON_PHONE_TAG = "DialedWearHome"

/**
 * Actually open the Dialed app on the phone. RemoteActivityHelper hands a VIEW intent to the Wear
 * companion, which IS privileged to launch it on the phone — a plain Data-Layer message + startActivity
 * is blocked by background-activity-launch rules, so this is the only reliable path. The phone's
 * MainActivity resolves [WearConstants.PHONE_DEEP_LINK] (its VIEW/BROWSABLE intent-filter). [onResult]
 * reports false only on a genuine dispatch failure, so the caller can drop a false "check your phone".
 */
private fun openDialedOnPhone(
    context: Context,
    remoteHelper: RemoteActivityHelper,
    onResult: (Boolean) -> Unit,
) {
    val intent = Intent(Intent.ACTION_VIEW)
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .setData(Uri.parse(WearConstants.PHONE_DEEP_LINK))
    val future = try {
        remoteHelper.startRemoteActivity(intent, null)
    } catch (e: Exception) {
        Log.w(OPEN_ON_PHONE_TAG, "startRemoteActivity threw", e)
        onResult(false)
        return
    }
    future.addListener({
        val ok = try {
            future.get()
            true
        } catch (e: Exception) {
            Log.w(OPEN_ON_PHONE_TAG, "open-on-phone failed", e)
            false
        }
        onResult(ok)
    }, ContextCompat.getMainExecutor(context))
}

