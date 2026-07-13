package com.dialed.app.wear.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.dialed.app.wear.ui.theme.DialedWearColors

/**
 * The Dialed dial mark (ring + gold hand resting at 1 o'clock) drawn on Canvas. Used as the
 * empty/placeholder face and as [FaceDial]'s fallback when no preview bitmap is available.
 */
@Composable
fun DialMark(size: Dp, modifier: Modifier = Modifier, alpha: Float = 1f) {
    Canvas(modifier.requiredSize(size)) {
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
 * A face preview rendered as a **guaranteed perfect circle** (HANDOFF-WATCH.md §6).
 *
 * Two things make the shape bulletproof — both were needed because the round face was rendering as
 * a wide oval on-wrist:
 *  1. [requiredSize] forces an EXACT square box. Plain `.size()` only *requests* a square and is
 *     coerced by the parent's constraints; when a Wear layout squeezes the height, the box becomes
 *     a rectangle and `CircleShape` clips it into an ellipse. `requiredSize` cannot be coerced.
 *  2. The bitmap is drawn with an explicit center-cropped square src → square dst inside a circular
 *     clip, so no `ContentScale`/aspect path can ever letterbox or stretch it. A non-square source
 *     is center-cropped (never distorted). Hands never tick — a still image (battery).
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
            .requiredSize(size)
            .clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.09f), CircleShape)
            .clearAndSetSemantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        if (preview != null) {
            Canvas(Modifier.fillMaxSize()) {
                val img = preview.asImageBitmap()
                val srcSide = minOf(img.width, img.height)
                val side = this.size.minDimension.toInt()
                drawImage(
                    image = img,
                    srcOffset = IntOffset((img.width - srcSide) / 2, (img.height - srcSide) / 2),
                    srcSize = IntSize(srcSide, srcSide),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(side, side),
                    filterQuality = FilterQuality.High,
                )
            }
        } else {
            DialMark(size = size)
        }
    }
}
