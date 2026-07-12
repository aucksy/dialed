package com.dialed.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dialed.app.R
import com.dialed.app.catalog.Face
import com.dialed.app.ui.theme.dialedColors

/** Whether this face is present on the watch (slot = 1), and if it is the live/active face. */
enum class DialStatus { NONE, INSTALLED, ACTIVE }

/**
 * A face preview rendered as a perfect circle (HANDOFF.md §6). Unlike the design-doc's
 * procedural placeholder dials, Dialed shows each face's REAL preview.png from the
 * fablecollection submodule — the faithful representation the spec said to swap in.
 * `ticking` is kept as an API hook for the F1 living-gallery overlay (deferred).
 */
@Composable
fun FaceDial(
    face: Face,
    size: Dp,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    status: DialStatus = DialStatus.NONE,
    ticking: Boolean = false,
) {
    val c = dialedColors
    val ringColor = when (status) {
        DialStatus.ACTIVE -> c.success
        DialStatus.INSTALLED -> c.onSurfaceVariant.copy(alpha = 0.55f)
        DialStatus.NONE -> Color.White.copy(alpha = 0.09f)
    }
    val desc = buildString {
        append(face.displayName); append(" watch face, "); append(face.tag)
        when (status) {
            DialStatus.ACTIVE -> append(", active on your watch")
            DialStatus.INSTALLED -> append(", installed on your watch")
            DialStatus.NONE -> Unit
        }
        if (locked) append(", locked")
    }
    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { contentDescription = desc }
    ) {
        Image(
            painter = painterResource(id = face.previewRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .shadow(elevation = (size.value * 0.06f).dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .border(if (status == DialStatus.NONE) 1.dp else 2.dp, ringColor, CircleShape),
        )
        if (ticking) {
            // F1 living gallery: a thin gold seconds hand sweeping once/min over the preview,
            // driven by one lifecycle-aware frame loop (transform only). Reduced motion freezes it.
            val angle = rememberSecondsAngle(enabled = true)
            val gold = c.primary
            Canvas(Modifier.fillMaxSize().clip(CircleShape)) {
                val r = this.size.minDimension / 2f
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                rotate(degrees = angle.value, pivot = center) {
                    drawLine(
                        color = gold.copy(alpha = 0.9f),
                        start = Offset(center.x, center.y + r * 0.13f),
                        end = Offset(center.x, center.y - r * 0.84f),
                        strokeWidth = r * 0.022f,
                        cap = StrokeCap.Round,
                    )
                }
                drawCircle(color = gold, radius = r * 0.035f, center = center)
                drawCircle(color = Color(0xFF0A0A0C), radius = r * 0.014f, center = center)
            }
        }
        if (locked) {
            LockBadge(size = size, modifier = Modifier.align(Alignment.BottomEnd))
        } else if (status != DialStatus.NONE) {
            StatusBadge(size = size, active = status == DialStatus.ACTIVE, modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun BoxScope.StatusBadge(size: Dp, active: Boolean, modifier: Modifier = Modifier) {
    val c = dialedColors
    val tint = if (active) c.success else c.onSurfaceVariant
    val badge = (size.value * 0.24f).dp.coerceAtLeast(20.dp)
    Box(
        modifier = modifier
            .padding(size.value.times(0.02f).dp)
            .size(badge)
            .clip(CircleShape)
            .background(Color(0xCC0A0A0C))
            .border(1.dp, tint.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = if (active) R.drawable.ic_check else R.drawable.ic_watch),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(badge * 0.52f),
        )
    }
}

@Composable
private fun BoxScope.LockBadge(size: Dp, modifier: Modifier = Modifier) {
    val badge = (size.value * 0.24f).dp.coerceAtLeast(22.dp)
    Box(
        modifier = modifier
            .padding(size.value.times(0.02f).dp)
            .size(badge)
            .clip(CircleShape)
            .background(Color(0xCC0A0A0C))
            .border(1.dp, Color(0xFFD8BC7A).copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = com.dialed.app.R.drawable.ic_lock),
            contentDescription = null,
            tint = Color(0xFFD8BC7A),
            modifier = Modifier.size(badge * 0.5f),
        )
    }
}
