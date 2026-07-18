package com.dialed.app.wear.ui.theme

import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography

/**
 * Dialed watch tokens — true-black AMOLED, one champagne-gold accent (HANDOFF-WATCH.md §2).
 * The watch UI is dark-only, so these are held as a plain object and read directly by
 * components (like the phone's DialedColors). Roles not on the Wear M3 [ColorScheme] —
 * [success], [disabled], [progressTrack] — live here and are used explicitly.
 */
object DialedWearColors {
    val background = Color(0xFF000000)
    val onBackground = Color(0xFFF4F2ED)
    val surfaceContainer = Color(0xFF16161A)
    val surfaceContainerHigh = Color(0xFF1D1D22)
    val onSurface = Color(0xFFF4F2ED)
    val onSurfaceVariant = Color(0xFFA6A29A)
    val outline = Color(0xFF34343B)
    val outlineVariant = Color(0xFF232328)
    val primary = Color(0xFFD8BC7A) // champagne gold
    val onPrimary = Color(0xFF221A06)
    val primaryContainer = Color(0xFF2A2517)
    val onPrimaryContainer = Color(0xFFEFD9A2)
    val success = Color(0xFF7ECFA0) // connected / dialed-in
    val onSuccess = Color(0xFF06210F)
    val error = Color(0xFFE5776C)
    val onError = Color(0xFF3A0906)
    val disabled = Color(0xFF8B857A)
    val progressTrack = Color(0x17FFFFFF) // rgba(255,255,255,.09)
}

/** Wear M3 ColorScheme built from the Dialed tokens (note Dim + surfaceContainer* roles). */
private val DialedWearColorScheme: ColorScheme = ColorScheme(
    primary = DialedWearColors.primary,
    primaryDim = DialedWearColors.primaryContainer,
    primaryContainer = DialedWearColors.primaryContainer,
    onPrimary = DialedWearColors.onPrimary,
    onPrimaryContainer = DialedWearColors.onPrimaryContainer,
    secondary = DialedWearColors.primary,
    secondaryDim = DialedWearColors.primaryContainer,
    secondaryContainer = DialedWearColors.primaryContainer,
    onSecondary = DialedWearColors.onPrimary,
    onSecondaryContainer = DialedWearColors.onPrimaryContainer,
    tertiary = DialedWearColors.success,
    tertiaryDim = DialedWearColors.success,
    tertiaryContainer = DialedWearColors.primaryContainer,
    onTertiary = DialedWearColors.onSuccess,
    onTertiaryContainer = DialedWearColors.onPrimaryContainer,
    surfaceContainerLow = DialedWearColors.surfaceContainer,
    surfaceContainer = DialedWearColors.surfaceContainer,
    surfaceContainerHigh = DialedWearColors.surfaceContainerHigh,
    onSurface = DialedWearColors.onSurface,
    onSurfaceVariant = DialedWearColors.onSurfaceVariant,
    outline = DialedWearColors.outline,
    outlineVariant = DialedWearColors.outlineVariant,
    background = DialedWearColors.background,
    onBackground = DialedWearColors.onBackground,
    error = DialedWearColors.error,
    errorDim = DialedWearColors.error,
    errorContainer = DialedWearColors.primaryContainer,
    onError = DialedWearColors.onError,
    onErrorContainer = DialedWearColors.onPrimaryContainer,
)

/**
 * Dialed Wear type scale (HANDOFF-WATCH.md §3). Without this the screens rendered at the platform
 * default sizes — headings undersized, overlines (ACTIVE/RECEIVING) with no tracking. Only the roles
 * the app actually uses are overridden; every other Wear M3 role keeps its platform default.
 * (Instrument Sans stays a later swap — sizes/weights/tracking now match the spec.)
 */
private val brand = FontFamily.Default
private val DialedWearTypography = Typography(
    displaySmall = TextStyle(fontFamily = brand, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = brand, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = brand, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 26.sp),
    bodyLarge = TextStyle(fontFamily = brand, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = brand, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = brand, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelSmall = TextStyle(fontFamily = brand, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 2.sp),
)

@Composable
fun DialedWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DialedWearColorScheme, typography = DialedWearTypography, content = content)
}

/** Motion tokens (HANDOFF-WATCH.md §5), shared spring shapes with the phone. */
object DialedWearMotion {
    fun <T> springFast() = spring<T>(dampingRatio = 0.9f, stiffness = 1400f)     // presses
    fun <T> springStandard() = spring<T>(dampingRatio = 0.9f, stiffness = 700f)  // enter/exit
    fun <T> springSettle() = spring<T>(dampingRatio = 0.65f, stiffness = 400f)   // landings

    const val BEZEL_SWEEP_MS = 900
    const val DUR_FAST = 150
    const val DUR_STD = 250
    const val DUR_EMPH = 350
    const val COACH_LOOP_MS = 6000
}
