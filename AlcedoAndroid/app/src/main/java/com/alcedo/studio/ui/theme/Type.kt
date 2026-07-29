package com.alcedo.studio.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Alcedo typography. Optimized for Chinese photographers with larger font sizes
 * for CJK readability, while maintaining the compact, technical feel of a
 * professional editor. A mono family is used for numeric/slider readouts.
 *
 * Key changes for CJK: CJK characters are inherently more complex than Latin,
 * requiring slightly larger minimum sizes and more generous line heights.
 */
val AlcedoTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 32.sp,
        lineHeight = 42.sp,          // 40→42: more breathing room for CJK
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 26.sp,
        lineHeight = 36.sp,          // 34→36: CJK line height
        letterSpacing = (-0.25).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp,          // 28→30: CJK line height
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,            // 19→20: round number, better CJK
        lineHeight = 26.sp,          // 24→26: CJK line height
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = DesignTokens.fontHeader,
        lineHeight = 24.sp,          // 22→24: CJK needs more vertical space
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = DesignTokens.fontTitle,
        lineHeight = 22.sp,          // 20→22: CJK line height
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,            // 13→14: CJK minimum readable
        lineHeight = 20.sp,          // 18→20: CJK line height
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = DesignTokens.fontBody,
        lineHeight = 20.sp,          // 18→20: CJK line height
        letterSpacing = 0.15.sp,     // 0.2→0.15: slightly tighter for CJK
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,            // 12→13: CJK minimum body size
        lineHeight = 18.sp,          // 16→18: CJK line height
        letterSpacing = 0.2.sp,      // 0.25→0.2: slightly tighter for CJK
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = DesignTokens.fontCaption,
        lineHeight = 16.sp,          // 14→16: CJK needs more line height
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,            // 13→14: CJK minimum label size
        lineHeight = 20.sp,          // 18→20: CJK line height
        letterSpacing = 0.3.sp,      // 0.4→0.3: slightly tighter for CJK
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 17.sp,          // 16→17: CJK line height
        letterSpacing = 0.4.sp,      // 0.5→0.4: slightly tighter for CJK
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,            // 10→11: CJK minimum readable label
        lineHeight = 15.sp,          // 13→15: CJK line height
        letterSpacing = 0.4.sp,      // 0.5→0.4: slightly tighter for CJK
    ),
)

/** Monospace style used for numeric readouts, EXIF values and slider values. */
val AlcedoMonoStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,                // 12→13: larger for CJK readability
    lineHeight = 18.sp,              // 16→18: CJK line height
    letterSpacing = 0.sp,
)

/** Compact caption used inside dense panels. */
val AlcedoPanelCaption = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,                // 10→11: CJK minimum readable
    lineHeight = 15.sp,              // 13→15: CJK line height
    letterSpacing = 0.25.sp,         // 0.3→0.25: slightly tighter for CJK
    color = AlcedoColors.TextTertiary,
)
