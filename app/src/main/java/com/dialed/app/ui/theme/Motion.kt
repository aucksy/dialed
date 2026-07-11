package com.dialed.app.ui.theme

import androidx.compose.animation.core.spring

// HANDOFF.md §5 motion tokens.
object DialedMotion {
    fun <T> springFast() = spring<T>(dampingRatio = 0.9f, stiffness = 1400f)      // presses
    fun <T> springStandard() = spring<T>(dampingRatio = 0.9f, stiffness = 700f)   // enter/exit
    fun <T> springExpressive() = spring<T>(dampingRatio = 0.8f, stiffness = 380f) // shared element
    fun <T> springSettle() = spring<T>(dampingRatio = 0.65f, stiffness = 400f)    // transfer landing

    const val DUR_FAST = 150
    const val DUR_STD = 250
    const val DUR_EMPH = 350
    const val SHIMMER_LOOP = 1400

    // Reduced-motion freeze time for FaceDial hands.
    const val FREEZE_HOUR = 10
    const val FREEZE_MINUTE = 9
    const val FREEZE_SECOND = 36
}
