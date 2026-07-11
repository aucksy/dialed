package com.dialed.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Dialed's full token set (HANDOFF.md §2). Held in a CompositionLocal so custom
 * components read exact tokens directly, independent of Material's ColorScheme.
 *
 * IMPORTANT: the filled CTA is identical in both themes — [ctaContainer] #D8BC7A /
 * [onCta] #221A06. Only gold *text/links* ([primary]) darken to #8F7326 in light.
 */
@Immutable
class DialedColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val primary: Color,            // gold TEXT/links (theme-dependent)
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val ctaContainer: Color,       // filled CTA fill — SAME in both themes
    val onCta: Color,              // filled CTA content — SAME in both themes
    val success: Color,            // also watchConnected
    val error: Color,
    val locked: Color,
    val scrim: Color,
)

val DialedDarkColors = DialedColors(
    isDark = true,
    background = Color(0xFF0B0B0D),
    surface = Color(0xFF131316),
    surfaceContainer = Color(0xFF17171B),
    surfaceContainerHigh = Color(0xFF1D1D22),
    onSurface = Color(0xFFF4F2ED),
    onSurfaceVariant = Color(0xFFA6A29A),
    outline = Color(0xFF34343B),
    outlineVariant = Color(0xFF232328),
    primary = Color(0xFFD8BC7A),
    onPrimary = Color(0xFF221A06),
    primaryContainer = Color(0xFF2A2517),
    onPrimaryContainer = Color(0xFFEFD9A2),
    ctaContainer = Color(0xFFD8BC7A),
    onCta = Color(0xFF221A06),
    success = Color(0xFF7ECFA0),
    error = Color(0xFFE5776C),
    locked = Color(0xFF8B857A),
    scrim = Color(0x99000000),
)

val DialedLightColors = DialedColors(
    isDark = false,
    background = Color(0xFFF7F5F0),
    surface = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF1EEE7),
    surfaceContainerHigh = Color(0xFFEBE7DD),
    onSurface = Color(0xFF1B1A17),
    onSurfaceVariant = Color(0xFF6E6A61),
    outline = Color(0xFFDCD8CC),
    outlineVariant = Color(0xFFE9E5DA),
    primary = Color(0xFF8F7326),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF0E3BE),
    onPrimaryContainer = Color(0xFF4A3A0C),
    ctaContainer = Color(0xFFD8BC7A),
    onCta = Color(0xFF221A06),
    success = Color(0xFF2F8F5B),
    error = Color(0xFFC0453B),
    locked = Color(0xFF9A937F),
    scrim = Color(0x99000000),
)

val LocalDialedColors = staticCompositionLocalOf { DialedDarkColors }
