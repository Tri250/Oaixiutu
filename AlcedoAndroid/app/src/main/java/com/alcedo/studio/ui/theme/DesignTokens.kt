package com.alcedo.studio.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Alcedo design tokens. Centralised spacing, sizing, motion and typography-rhythm
 * constants inspired by RapidRAW's minimal, photography-first design philosophy.
 *
 * Key RapidRAW design principles applied:
 * - Generous corner radii (15dp large radius) for a soft, modern feel
 * - Smooth 400ms color transitions for theme switching
 * - 150ms transform transitions for interactive feedback
 * - Shiny shadow for display text (luminous glow effect)
 * - Optimized for Chinese photographers with larger touch targets and clearer typography
 */
object DesignTokens {
    // ---- Spacing (4pt grid) ----
    val spacingNone = 0.dp
    val spacingXxs = 2.dp
    val spacingXs = 4.dp
    val spacingSm = 8.dp
    val spacingMd = 12.dp
    val spacingLg = 16.dp
    val spacingXl = 24.dp
    val spacingXxl = 32.dp
    val spacingHuge = 48.dp

    // ---- Corner radius (RapidRAW-inspired: more generous) ----
    val radiusXs = 2.dp
    val radiusSm = 4.dp
    val radiusMd = 8.dp                          // RapidRAW --radius-md: 8px
    val radiusLg = 15.dp                         // RapidRAW --radius-lg: 15px (was 12dp)
    val radiusXl = 20.dp                         // Slightly more generous
    val radiusPill = 24.dp

    // ---- Elevation (shadows are subtle in a dark theme) ----
    val elevationNone = 0.dp
    val elevationLow = 1.dp
    val elevationMd = 3.dp
    val elevationHigh = 6.dp
    val elevationOverlay = 12.dp

    // ---- RapidRAW-inspired shadow tokens ----
    // --shadow-shiny: 0 0 24px rgba(255, 255, 255, 0.12)
    const val shadowShinyBlur = 24f
    const val shadowShinyAlpha = 0.12f

    // Text shadow for display headings: 0 0 18px rgba(255, 255, 255, 0.35)
    const val textShadowShinyBlur = 18f
    const val textShadowShinyAlpha = 0.35f

    // ---- Component sizing ----
    val touchTargetMin = 48.dp
    val sliderTrackHeight = 6.dp
    val sliderThumbRadius = 10.dp
    val thumbnailMinSize = 80.dp
    val thumbnailMaxSize = 320.dp
    val panelWidthCompact = 320.dp
    val panelWidthExpanded = 380.dp
    val inspectorWidth = 300.dp
    val topBarHeight = 56.dp
    val bottomBarHeight = 80.dp
    val dividerThickness = 1.dp

    // ---- Control rhythm ----
    val controlSpacing = 8.dp

    // ---- Motion durations (ms) ----
    // RapidRAW-inspired: 150ms for transforms, 400ms for color transitions
    const val motionFast = 150                   // RapidRAW slider thumb transform: 150ms
    const val motionNormal = 250                 // Page transitions
    const val motionSlow = 380                   // Expand/collapse
    const val motionPanel = 300                  // Panel switch

    // RapidRAW color transition: 400ms ease-in-out for color/bg/border
    const val colorTransitionMs = 400            // RapidRAW .enable-color-transitions: 0.4s
    const val colorTransitionEasing = "ease-in-out"  // RapidRAW uses ease-in-out

    // Slider-specific: RapidRAW uses 150ms transform + 400ms background-color
    const val sliderTransformMs = 150            // RapidRAW slider thumb scale
    const val sliderColorMs = 400                // RapidRAW slider thumb color
    const val sliderScaleActive = 1.1f           // RapidRAW scale-110 on active

    // ---- Histogram / scope sizing ----
    val histogramHeight = 80.dp
    val scopeHeight = 140.dp

    // ---- Font scale helpers ----
    // Enlarged for CJK readability: Chinese characters need slightly more space
    val fontCaption = 12.sp
    val fontBody = 14.sp
    val fontTitle = 16.sp
    val fontHeader = 18.sp
}

/** Common alpha values used for layered dark surfaces. */
object AlphaTokens {
    const val overlayScrim = 0.6f
    const val surfaceHover = 0.08f
    const val surfaceSelected = 0.16f
    const val disabled = 0.38f
    const val hint = 0.55f
    const val trackInactive = 0.22f
}
