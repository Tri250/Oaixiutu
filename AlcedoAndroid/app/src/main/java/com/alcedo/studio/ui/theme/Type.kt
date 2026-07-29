package com.alcedo.studio.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Alcedo typography. Inspired by RapidRAW's typography system:
 * - Display and headline levels use **bold** weight with text-shadow-shiny glow
 * - Body and label levels use normal/medium weight for readability
 * - Optimized for CJK with larger font sizes and more generous line heights
 *
 * RapidRAW typography mapping:
 *   displayLarge → text-5xl bold + text-shadow-shiny
 *   display      → text-3xl bold + text-shadow-shiny
 *   headline     → text-2xl bold + text-shadow-shiny
 *   title        → text-xl bold + text-shadow-shiny
 *   heading      → text-base semibold
 *   body         → text-sm normal (secondary color)
 *   label        → text-sm medium (secondary color)
 *   small        → text-xs normal (secondary color)
 */
val AlcedoTypography = Typography(
    // ---- Display level: RapidRAW displayLarge (text-5xl bold + shiny) ----
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,              // RapidRAW: bold (was Light)
        fontSize = 48.sp,                         // text-5xl ≈ 48sp (was 32sp)
        lineHeight = 56.sp,
        letterSpacing = (-0.5).sp,
    ),
    // ---- Display level: RapidRAW display (text-3xl bold + shiny) ----
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,              // RapidRAW: bold (was Light)
        fontSize = 30.sp,                         // text-3xl ≈ 30sp (was 26sp)
        lineHeight = 40.sp,
        letterSpacing = (-0.25).sp,
    ),
    // ---- Headline level: RapidRAW headline (text-2xl bold + shiny) ----
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,              // RapidRAW: bold (was SemiBold)
        fontSize = 24.sp,                         // text-2xl ≈ 24sp (was 22sp)
        lineHeight = 32.sp,
    ),
    // ---- Headline level: RapidRAW title (text-xl bold + shiny) ----
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,              // RapidRAW: bold (was SemiBold)
        fontSize = 20.sp,                         // text-xl ≈ 20sp
        lineHeight = 28.sp,
    ),
    // ---- Title level: RapidRAW heading (text-base semibold) ----
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,          // RapidRAW: semibold
        fontSize = DesignTokens.fontHeader,        // text-base ≈ 16sp
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,            // RapidRAW: medium
        fontSize = DesignTokens.fontTitle,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    // ---- Body level: RapidRAW body (text-sm normal, secondary) ----
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,            // RapidRAW: normal
        fontSize = DesignTokens.fontBody,          // text-sm ≈ 14sp
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = DesignTokens.fontCaption,        // text-xs ≈ 12sp
        lineHeight = 16.sp,
    ),
    // ---- Label level: RapidRAW label (text-sm medium, secondary) ----
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,            // RapidRAW: medium
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.3.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.4.sp,
    ),
    // ---- Small level: RapidRAW small (text-xs normal, secondary) ----
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,            // RapidRAW: normal (was Medium)
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.4.sp,
    ),
)

/** Monospace style used for numeric readouts, EXIF values and slider values. */
val AlcedoMonoStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.sp,
)

/** Compact caption used inside dense panels. */
val AlcedoPanelCaption = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 15.sp,
    letterSpacing = 0.25.sp,
    color = AlcedoColors.TextTertiary,
)
