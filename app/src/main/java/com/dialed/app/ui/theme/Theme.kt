package com.dialed.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

enum class ThemeMode { SYSTEM, DARK, LIGHT }

private fun materialSchemeFrom(c: DialedColors): ColorScheme {
    val base = if (c.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.primary,
        onPrimary = c.onPrimary,
        primaryContainer = c.primaryContainer,
        onPrimaryContainer = c.onPrimaryContainer,
        secondary = c.primary,
        onSecondary = c.onPrimary,
        tertiary = c.success,
        background = c.background,
        onBackground = c.onSurface,
        surface = c.surface,
        onSurface = c.onSurface,
        surfaceVariant = c.surfaceContainerHigh,
        onSurfaceVariant = c.onSurfaceVariant,
        surfaceContainer = c.surfaceContainer,
        surfaceContainerHigh = c.surfaceContainerHigh,
        surfaceContainerLow = c.surfaceContainer,
        outline = c.outline,
        outlineVariant = c.outlineVariant,
        error = c.error,
        scrim = c.scrim,
        // The inverse roles are what Material's default Snackbar paints itself with. Left unmapped
        // they fall back to the baseline (non-Dialed) palette, which would make the snackbar the one
        // off-system surface in the app.
        inverseSurface = c.surfaceContainerHigh,
        inverseOnSurface = c.onSurface,
        inversePrimary = c.primary,
    )
}

@Composable
fun DialedTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val colors = if (dark) DialedDarkColors else DialedLightColors
    CompositionLocalProvider(LocalDialedColors provides colors) {
        MaterialTheme(
            colorScheme = materialSchemeFrom(colors),
            typography = DialedTypography,
            shapes = DialedShapes,
            content = content,
        )
    }
}

/** Convenience accessor for Dialed's full token set inside composables. */
val dialedColors: DialedColors
    @Composable @ReadOnlyComposable
    get() = LocalDialedColors.current
