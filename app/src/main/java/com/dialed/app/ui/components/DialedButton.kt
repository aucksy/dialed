package com.dialed.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dialed.app.ui.theme.DialedMotion
import com.dialed.app.ui.theme.dialedColors

enum class DialedButtonVariant { FILLED, TONAL, TEXT }

/**
 * HANDOFF.md §6 DialedButton. Filled CTA is the same gold in both themes; press = scale .96
 * (springFast); disabled = ~32% content alpha. Height 56 primary / 48 in sheets.
 */
@Composable
fun DialedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: DialedButtonVariant = DialedButtonVariant.FILLED,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val c = dialedColors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = DialedMotion.springFast(),
        label = "buttonScale",
    )

    val container: Color
    val content: Color
    var border: BorderStroke? = null
    when (variant) {
        DialedButtonVariant.FILLED -> { container = c.ctaContainer; content = c.onCta }
        DialedButtonVariant.TONAL -> {
            container = c.primaryContainer; content = c.onPrimaryContainer
            border = BorderStroke(1.dp, c.ctaContainer.copy(alpha = 0.5f))
        }
        DialedButtonVariant.TEXT -> { container = Color.Transparent; content = c.primary }
    }

    Row(
        modifier = modifier
            .scale(scale)
            .then(if (variant == DialedButtonVariant.TEXT) Modifier else Modifier.fillMaxWidth())
            .heightIn(min = 48.dp)
            .height(height)
            .clip(CircleShape)
            .background(container)
            .then(border?.let { Modifier.border(it, CircleShape) } ?: Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .alpha(if (enabled) 1f else 0.34f)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon?.invoke()
        Text(
            text = text,
            color = content,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}
