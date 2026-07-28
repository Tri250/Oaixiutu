package com.alcedo.studio.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Alcedo design tokens. Centralised spacing, sizing, motion and typography-rhythm
 * constants used across the dark, professional photography editor theme.
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

    // ---- Corner radius ----
    val radiusXs = 2.dp
    val radiusSm = 4.dp
    val radiusMd = 8.dp
    val radiusLg = 12.dp
    val radiusXl = 16.dp
    val radiusPill = 24.dp

    // ---- Elevation (shadows are subtle in a dark theme) ----
    val elevationNone = 0.dp
    val elevationLow = 1.dp
    val elevationMd = 3.dp
    val elevationHigh = 6.dp
    val elevationOverlay = 12.dp

    // ---- Component sizing ----
    val touchTargetMin = 48.dp
    val sliderTrackHeight = 4.dp
    val sliderThumbRadius = 9.dp
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
    const val motionFast = 120
    const val motionNormal = 220
    const val motionSlow = 360
    const val motionPanel = 280

    // ---- Histogram / scope sizing ----
    val histogramHeight = 80.dp
    val scopeHeight = 140.dp

    // ---- Font scale helpers ----
    val fontCaption = 11.sp
    val fontBody = 13.sp
    val fontTitle = 15.sp
    val fontHeader = 17.sp
}

/** Common alpha values used for layered dark surfaces. */
object AlphaTokens {
    const val overlayScrim = 0.6f
    const val surfaceHover = 0.08f
    const val surfaceSelected = 0.16f
    const val disabled = 0.38f
    const val hint = 0.55f
    const val trackInactive = 0.18f
}
