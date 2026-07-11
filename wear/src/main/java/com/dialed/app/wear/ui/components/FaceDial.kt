package com.dialed.app.wear.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dialed.app.wear.ui.theme.DialedWearColors

/**
 * The Dialed dial mark (ring + gold hand resting at 1 o'clock) drawn on Canvas. Used as the
 * empty/placeholder face and as [FaceDial]'s fallback when no preview bitmap is available.
 */
@Composable
fun DialMark(size: Dp, modifier: Modifier = Modifier, alpha: Float = 1f) {
    Canvas(modifier.size(size)) {
        val dim = minOf(this.size.width, this.size.height)
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val ringRadius = dim * 0.36f
        val ringStroke = dim * 0.05f

        drawCircle(
            color = DialedWearColors.onBackground.copy(alpha = alpha),
            radius = ringRadius,
            center = center,
            style = Stroke(width = ringStroke),
        )
        // Hand from center toward 12, rotated 30° clockwise (1 o'clock).
        rotate(degrees = 30f, pivot = center) {
            drawLine(
                color = DialedWearColors.primary.copy(alpha = alpha),
                start = center,
                end = Offset(center.x, center.y - ringRadius * 0.86f),
                strokeWidth = ringStroke,
                cap = StrokeCap.Round,
            )
        }
        drawCircle(
            color = DialedWearColors.onBackground.copy(alpha = alpha),
            radius = ringStroke * 0.95f,
            center = center,
        )
    }
}

/**
 * A face preview rendered as a perfect circle (HANDOFF-WATCH.md §6). Shows the real preview
 * extracted from the pushed APK when available; otherwise the dial mark. Hands never tick — the
 * watch shows a still image (battery).
 */
@Composable
fun FaceDial(
    preview: Bitmap?,
    size: Dp,
    modifier: Modifier = Modifier,
    faceName: String? = null,
) {
    val desc = faceName?.let { "$it watch face" } ?: "Watch face"
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.09f), CircleShape)
            .clearAndSetSemantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        if (preview != null) {
            Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            DialMark(size = size)
        }
    }
}
