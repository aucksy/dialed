package com.dialed.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.dialed.app.ui.theme.DialedRadius
import com.dialed.app.ui.theme.dialedColors

/** Home pre-purchase banner (spec 1d): gold-tinted gradient card, price + Unlock. */
@Composable
fun UnlockBanner(
    faceCount: Int,
    price: String,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = dialedColors
    val gradient = Brush.linearGradient(
        listOf(c.ctaContainer.copy(alpha = 0.14f), c.ctaContainer.copy(alpha = 0.05f)),
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DialedRadius.md))
            .background(gradient)
            .border(1.dp, c.ctaContainer.copy(alpha = 0.30f), RoundedCornerShape(DialedRadius.md))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Unlock all $faceCount faces",
                style = MaterialTheme.typography.titleMedium,
                color = c.onPrimaryContainer,
            )
            Text(
                "One-time · $price",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant,
            )
        }
        Text(
            "Unlock",
            style = MaterialTheme.typography.labelLarge,
            color = c.onCta,
            modifier = Modifier
                .clip(CircleShape)
                .background(c.ctaContainer)
                .clickable(onClick = onUnlock)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
