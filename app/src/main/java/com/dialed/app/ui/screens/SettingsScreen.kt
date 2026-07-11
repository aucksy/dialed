package com.dialed.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.dialed.app.model.WatchStatus
import com.dialed.app.ui.theme.DialedRadius
import com.dialed.app.ui.theme.DialedSpacing
import com.dialed.app.ui.theme.ThemeMode
import com.dialed.app.ui.theme.dialedColors

@Composable
fun SettingsScreen(
    watchStatus: WatchStatus,
    entitled: Boolean,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    onRestore: () -> Unit,
) {
    val c = dialedColors
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier.padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_back), "Back", tint = c.onSurface,
                modifier = Modifier.size(42.dp).clip(CircleShape).border(1.dp, c.outline, CircleShape)
                    .clickable(onClick = onBack).padding(11.dp),
            )
            Text("Settings", style = MaterialTheme.typography.headlineSmall, color = c.onSurface)
        }

        SectionHeader("Watch")
        SettingsCard {
            Column(Modifier.padding(16.dp)) {
                Text(
                    watchStatus.deviceName ?: "No watch connected",
                    style = MaterialTheme.typography.titleMedium, color = c.onSurface,
                )
                Text(
                    if (watchStatus.isConnected) "Connected · ${watchStatus.wearOsVersion ?: "Wear OS"}"
                    else "Pair a Wear OS 6 watch to install faces",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (watchStatus.isConnected) c.success else c.onSurfaceVariant,
                )
                watchStatus.activeFaceName?.let {
                    Text("Active face: $it", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                }
            }
        }

        SectionHeader("Purchases")
        SettingsCard {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (entitled) "Collection unlocked" else "Collection locked",
                        style = MaterialTheme.typography.titleMedium, color = c.onSurface,
                    )
                    Text(
                        if (entitled) "OWNED" else "LOCKED",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (entitled) c.success else c.onSurfaceVariant,
                        modifier = Modifier.clip(CircleShape)
                            .background((if (entitled) c.success else c.locked).copy(alpha = 0.12f))
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
                Divider()
                Text(
                    "Restore purchase",
                    style = MaterialTheme.typography.titleMedium, color = c.primary,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onRestore).padding(16.dp),
                )
            }
        }

        SectionHeader("Appearance")
        SettingsCard {
            Column(Modifier.padding(16.dp)) {
                Text("Theme", style = MaterialTheme.typography.titleMedium, color = c.onSurface)
                Spacer(Modifier.height(12.dp))
                ThemeSelector(themeMode, onThemeChange)
            }
        }

        SectionHeader("About")
        SettingsCard {
            Column {
                AboutRow("Version", trailingText = "0.1.0")
                Divider()
                AboutRow("Privacy policy")
                Divider()
                AboutRow("Open-source licenses")
            }
        }
        Spacer(Modifier.height(DialedSpacing.xxl))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium, color = dialedColors.primary,
        modifier = Modifier.padding(start = 24.dp, top = 22.dp, bottom = 10.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    val c = dialedColors
    Column(
        Modifier.padding(horizontal = 24.dp).fillMaxWidth()
            .clip(RoundedCornerShape(DialedRadius.lg))
            .background(c.surface)
            .border(1.dp, c.outlineVariant, RoundedCornerShape(DialedRadius.lg)),
    ) { content() }
}

@Composable
private fun Divider() {
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(dialedColors.outlineVariant),
    )
}

@Composable
private fun AboutRow(label: String, trailingText: String? = null) {
    val c = dialedColors
    Row(
        Modifier.fillMaxWidth().clickable {}.padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = c.onSurface)
        if (trailingText != null) {
            Text(trailingText, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
        } else {
            Icon(painterResource(R.drawable.ic_chevron_right), null, tint = c.locked, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ThemeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val c = dialedColors
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).background(c.background)
            .border(1.dp, c.outlineVariant, CircleShape).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ThemeMode.entries.forEach { mode ->
            val on = mode == selected
            Text(
                mode.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelLarge,
                color = if (on) c.onPrimaryContainer else c.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).clip(CircleShape)
                    .background(if (on) c.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(mode) }.padding(vertical = 9.dp),
            )
        }
    }
}
