package com.alcedo.studio.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ═══════════════════════════════════════════════════════════════════
// 2026 Flagship Typography System
// ═══════════════════════════════════════════════════════════════════
// Principles:
//   - Line-height ratio 1.25–1.5x for optimal readability
//   - Tight tracking on large display, relaxed on body
//   - Weight hierarchy: Light→SemiBold creates visual contrast
//   - Optical sizing: each scale level tuned independently
//
// Custom font family placeholder.
// To use a custom font, place .ttf/.otf files in res/font/ and use:
//   val AlcedoFontFamily = FontFamily(
//       Font(R.font.alcedo_thin, FontWeight.Thin),
//       Font(R.font.alcedo_light, FontWeight.Light),
//       Font(R.font.alcedo_regular, FontWeight.Normal),
//       Font(R.font.alcedo_medium, FontWeight.Medium),
//       Font(R.font.alcedo_semibold, FontWeight.SemiBold),
//       Font(R.font.alcedo_bold, FontWeight.Bold)
//   )
// Then replace FontFamily.SansSerif below with AlcedoFontFamily.
// ═══════════════════════════════════════════════════════════════════

val AlcedoFontFamily = FontFamily.SansSerif

val AlcedoTypography = Typography(
    // ── Display: Hero / splash-screen headlines ────────────────────
    displayLarge = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 57.sp,
        lineHeight = 72.sp,       // 1.26x – tight, cinematic
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 45.sp,
        lineHeight = 58.sp,       // 1.29x
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 48.sp,       // 1.33x
        letterSpacing = 0.sp
    ),

    // ── Headline: Section headers, top-level titles ─────────────────
    headlineLarge = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 42.sp,       // 1.31x
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 38.sp,       // 1.36x
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 34.sp,       // 1.42x
        letterSpacing = 0.sp
    ),

    // ── Title: Card titles, panel headers ───────────────────────────
    titleLarge = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 30.sp,       // 1.36x
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 26.sp,       // 1.44x
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,       // 1.47x
        letterSpacing = 0.1.sp
    ),

    // ── Body: Primary reading content ───────────────────────────────
    bodyLarge = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,       // 1.625x – generous for readability
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,       // 1.57x
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,       // 1.5x
        letterSpacing = 0.4.sp
    ),

    // ── Label: Buttons, tags, chips, navigation items ───────────────
    labelLarge = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,       // 1.43x
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,       // 1.5x
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = AlcedoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,       // 1.45x
        letterSpacing = 0.5.sp
    )
)

// Keep legacy reference for compatibility
val Typography = AlcedoTypography
