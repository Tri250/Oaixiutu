package com.alcedo.studio.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Alcedo design tokens. Centralised spacing, sizing, motion and typography-rhythm
 * constants used across the dark, professional photography editor theme.
 *
 * Optimized for Chinese photographers: larger touch targets, clearer typography,
 * smoother animations with proper easing curves for a premium feel.
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
    val sliderTrackHeight = 6.dp       // 4dp→6dp: thicker track for easier thumb targeting
    val sliderThumbRadius = 10.dp      // 9dp→10dp: larger thumb for precise dragging
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
    // Optimized for a premium, fluid feel with proper easing
    const val motionFast = 150         // 120→150: slightly more generous for tactile feedback
    const val motionNormal = 250       // 220→250: smoother page transitions
    const val motionSlow = 380         // 360→380: more deliberate expand/collapse
    const val motionPanel = 300        // 280→300: panel switch with breathing room

    // ---- Histogram / scope sizing ----
    val histogramHeight = 80.dp
    val scopeHeight = 140.dp

    // ---- Font scale helpers ----
    // Enlarged for CJK readability: Chinese characters need slightly more space
    val fontCaption = 12.sp            // 11sp→12sp: minimum readable size for CJK
    val fontBody = 14.sp               // 13sp→14sp: comfortable reading size
    val fontTitle = 16.sp              // 15sp→16sp: clear section titles
    val fontHeader = 18.sp             // 17sp→18sp: prominent panel headers
}

/** Common alpha values used for layered dark surfaces. */
object AlphaTokens {
    const val overlayScrim = 0.6f
    const val surfaceHover = 0.08f
    const val surfaceSelected = 0.16f
    const val disabled = 0.38f
    const val hint = 0.55f
    const val trackInactive = 0.22f    // 0.18→0.22: slightly more visible inactive track
}
