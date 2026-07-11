package com.dialed.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dialed.app.ui.theme.dialedColors

/** Selected = primaryContainer bg + gold border; unselected = outline only (HANDOFF.md §6). */
@Composable
fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = dialedColors
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) c.onPrimaryContainer else c.onSurfaceVariant,
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) c.primaryContainer else Color.Transparent)
            .border(1.dp, if (selected) c.ctaContainer.copy(alpha = 0.5f) else c.outline, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Outline-only chip used for complication/feature tags on the detail screen. */
@Composable
fun FeatureChip(text: String, modifier: Modifier = Modifier) {
    val c = dialedColors
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = c.onSurface.copy(alpha = 0.82f),
        modifier = modifier
            .clip(CircleShape)
            .border(1.dp, c.outline, CircleShape)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    )
}
