package com.dialed.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// HANDOFF.md §4: sm 12 · md 16 · lg 20 (cards) · sheet 28 (top) · buttons/pills = full.
val DialedShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object DialedRadius {
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val sheet = 28.dp
}
