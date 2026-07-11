package com.dialed.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import com.dialed.app.ui.components.FaceDial
import com.dialed.app.ui.components.FeatureChip
import com.dialed.app.ui.components.InstallButton
import com.dialed.app.ui.components.InstallState
import com.dialed.app.ui.theme.DialedSpacing
import com.dialed.app.ui.theme.FaceSize
import com.dialed.app.ui.theme.dialedColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FaceDetailScreen(
    face: Face,
    entitled: Boolean,
    watchStatus: WatchStatus,
    onBack: () -> Unit,
    onUnlock: () -> Unit,
    onInstall: () -> Unit = {}, // Phase 4 opens the PushToWatchSheet
) {
    val c = dialedColors
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CircleIconButton(R.drawable.ic_back, "Back", onBack)
            CircleIconButton(R.drawable.ic_more_vert, "More", {})
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(DialedSpacing.lg))
            FaceDial(face = face, size = FaceSize.hero)
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
            InstallButton(
                state = if (!entitled) InstallState.Locked else InstallState.Ready,
                onClick = { if (!entitled) onUnlock() else onInstall() },
            )
            Spacer(Modifier.height(DialedSpacing.md))
            val caption = when {
                watchStatus.isConnected -> "Installs to ${watchStatus.deviceName} · Connected"
                else -> "Connect a Wear OS 6 watch to install"
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
private fun CircleIconButton(iconRes: Int, desc: String, onClick: () -> Unit) {
    val c = dialedColors
    Icon(
        painter = painterResource(iconRes),
        contentDescription = desc,
        tint = c.onSurface,
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .border(1.dp, c.outline, CircleShape)
            .clickable(onClick = onClick)
            .padding(11.dp),
    )
}
