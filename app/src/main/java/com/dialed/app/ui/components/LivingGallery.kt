package com.dialed.app.ui.components

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/** The design's reduced-motion freeze pose (HANDOFF.md §5): 10:09:36 → seconds hand at 216°. */
const val FROZEN_SECONDS_ANGLE = 216f

/** True when the user has turned animations off (Settings.Global.ANIMATOR_DURATION_SCALE == 0). */
@Composable
fun isReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * F1 "living gallery" clock. One `withFrameNanos` loop drives a continuous once-per-minute sweep
 * (`0..360°`), gated on the lifecycle so it stops when the screen is not at least STARTED (no
 * battery drain in the background). Reduced motion → the frozen 10:09:36 pose. Returns a [State]
 * so callers read the angle inside the draw phase (a `rotate()` transform) — no per-frame
 * recomposition of the tree, per the spec.
 */
@Composable
fun rememberSecondsAngle(enabled: Boolean): State<Float> {
    val active = enabled && !isReducedMotion()
    val angle = remember { mutableFloatStateOf(if (active) secondsAngleNow() else FROZEN_SECONDS_ANGLE) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(active, owner) {
        if (!active) {
            angle.floatValue = FROZEN_SECONDS_ANGLE
            return@LaunchedEffect
        }
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                withFrameNanos { }
                angle.floatValue = secondsAngleNow()
            }
        }
    }
    return angle
}

private fun secondsAngleNow(): Float =
    (System.currentTimeMillis() % 60_000L).toFloat() / 60_000f * 360f
