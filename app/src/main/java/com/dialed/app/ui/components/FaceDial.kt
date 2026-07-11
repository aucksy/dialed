package com.dialed.app.ui.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dialed.app.catalog.Face

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
    @Suppress("UNUSED_PARAMETER") ticking: Boolean = false,
) {
    val desc = buildString {
        append(face.displayName); append(" watch face, "); append(face.tag)
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
                .border(1.dp, Color.White.copy(alpha = 0.09f), CircleShape),
        )
        if (locked) {
            LockBadge(size = size, modifier = Modifier.align(Alignment.BottomEnd))
        }
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
