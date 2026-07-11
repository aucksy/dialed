package com.dialed.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// TODO(Instrument Sans): the brand face is Instrument Sans via Google downloadable fonts
//  (add androidx.compose.ui:ui-text-google-fonts + res/font/ + font_certs, per HANDOFF.md §3).
//  The metrics below already match §3 exactly; only the typeface substitution is pending.
private val Brand = FontFamily.Default

val DialedTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Brand, fontWeight = FontWeight.Bold,
        fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-0.01).em,
    ),
    headlineLarge = TextStyle(
        fontFamily = Brand, fontWeight = FontWeight.Bold,
        fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.005).em,
    ),
    headlineSmall = TextStyle(
        fontFamily = Brand, fontWeight = FontWeight.Bold,
        fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = 0.em,
    ),
    titleLarge = TextStyle(
        fontFamily = Brand, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = 0.em,
    ),
    titleMedium = TextStyle(
        fontFamily = Brand, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.em,
    ),
    bodyLarge = TextStyle(
        fontFamily = Brand, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.em,
    ),
    bodyMedium = TextStyle(
        fontFamily = Brand, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.em,
    ),
    labelLarge = TextStyle(
        fontFamily = Brand, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Brand, fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Brand, fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp,
    ),
)
