package com.dialed.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dialed.app.R
import com.dialed.app.catalog.Face
import com.dialed.app.model.WatchStatus
import com.dialed.app.ui.components.DialStatus
import com.dialed.app.ui.components.FaceDial
import com.dialed.app.ui.components.FeatureChip
import com.dialed.app.ui.components.InstallButton
import com.dialed.app.ui.components.InstallState
import com.dialed.app.ui.components.UninstallButton
import com.dialed.app.ui.theme.DialedSpacing
import com.dialed.app.ui.theme.FaceSize
import com.dialed.app.ui.theme.dialedColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FaceDetailScreen(
    face: Face,
    entitled: Boolean,
    watchStatus: WatchStatus,
    isInstalled: Boolean,
    isActive: Boolean,
    slotOccupied: Boolean, // a *different* Dialed face already occupies the single WFP slot
    uninstalling: Boolean,
    onBack: () -> Unit,
    onUnlock: () -> Unit,
    onInstall: () -> Unit = {}, // opens the PushToWatchSheet
    onUninstall: () -> Unit = {},
) {
    val c = dialedColors
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Back only. There was a "More" button here that did nothing at all — a shown control must
        // do something (the v0.18 lesson); it returns when it has a menu to open.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Start,
        ) {
            CircleIconButton(R.drawable.ic_back, "Back", onBack)
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(DialedSpacing.lg))
            FaceDial(
                face = face,
                size = FaceSize.hero,
                status = when {
                    isActive -> DialStatus.ACTIVE
                    isInstalled -> DialStatus.INSTALLED
                    else -> DialStatus.NONE
                },
                // A generic gold seconds hand misrepresents each face (owner feedback): removed.
                // The REAL per-face second-hand animation lands in a follow-up (only 7/18 faces
                // are analog; the rest are digital/sprite/arc and stay static).
                ticking = false,
            )
            Spacer(Modifier.height(28.dp))
            Text(face.displayName, style = MaterialTheme.typography.headlineLarge, color = c.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                face.tag.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = c.primary,
            )
            if (face.description.isNotBlank()) {
                Text(
                    face.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 46.dp, vertical = 14.dp),
                )
            }
            if (face.features.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = DialedSpacing.screenMargin, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(DialedSpacing.sm, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(DialedSpacing.sm),
                ) {
                    face.features.forEach { FeatureChip(it) }
                }
            }
            Spacer(Modifier.height(DialedSpacing.xl))
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = DialedSpacing.screenMargin, vertical = 16.dp)) {
            when {
                !entitled -> InstallButton(state = InstallState.Locked, onClick = onUnlock)
                isInstalled -> {
                    // Slot = 1: this face is on the watch. Show its status + a single-tap uninstall,
                    // instead of an install action.
                    if (isActive) {
                        InstallButton(state = InstallState.InstalledActive, onClick = {})
                    } else {
                        // Installed but not the live face (user switched faces on the watch).
                        InstalledNotActiveChip()
                    }
                    Spacer(Modifier.height(DialedSpacing.md))
                    UninstallButton(onClick = onUninstall, loading = uninstalling)
                }
                else -> InstallButton(
                    state = if (slotOccupied) InstallState.Replace else InstallState.Ready,
                    onClick = onInstall,
                )
            }
            Spacer(Modifier.height(DialedSpacing.md))
            val caption = when {
                isActive -> "This is your active watch face."
                isInstalled -> "Installed on ${watchStatus.deviceName ?: "your watch"}. Set it active from your watch's face picker."
                // Before !isConnected: an unsupported watch IS present, it just can't ever install.
                watchStatus.isUnsupported ->
                    "${watchStatus.deviceName ?: "Your watch"} needs Wear OS 6 to install faces."
                !watchStatus.isConnected -> "Connect a Wear OS 6 watch to install"
                slotOccupied -> "Replaces the Dialed face on ${watchStatus.deviceName} — one face at a time."
                else -> "Installs to ${watchStatus.deviceName} · Connected"
            }
            Text(
                caption,
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun InstalledNotActiveChip() {
    val c = dialedColors
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(CircleShape)
            .border(1.dp, c.outline, CircleShape),
        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_watch), contentDescription = null,
            tint = c.onSurfaceVariant, modifier = Modifier.size(18.dp),
        )
        Text(
            "On your watch",
            color = c.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** 42dp of visible circle inside a 48dp touch target (HANDOFF.md §8). */
@Composable
private fun CircleIconButton(iconRes: Int, desc: String, onClick: () -> Unit) {
    val c = dialedColors
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = desc,
            tint = c.onSurface,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .border(1.dp, c.outline, CircleShape)
                .padding(11.dp),
        )
    }
}
