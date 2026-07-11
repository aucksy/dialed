package com.dialed.app.ui.theme

import androidx.compose.ui.unit.dp

// HANDOFF.md §4 spacing scale (4dp base) + face sizes.
object DialedSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp

    val screenMargin = 24.dp
    val gridGutter = 20.dp
    val cardPadding = 16.dp
    val sectionGap = 32.dp
}

/** FaceDial sizes actually used across screens (HANDOFF.md §6). */
object FaceSize {
    val row = 44.dp
    val sheet = 72.dp
    val gridSmall = 96.dp
    val grid = 150.dp
    val vitrine = 224.dp
    val hero = 268.dp
}
