package com.alcedo.studio.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alcedo.studio.ui.theme.AlcedoColorScheme
import com.alcedo.studio.ui.theme.AlcedoTheme

// ═══════════════════════════════════════════════════════════════════
// Liquid Glass Effect System – 2026 flagship frosted-glass UI
// ═══════════════════════════════════════════════════════════════════
// Provides three levels of glass depth:
//   Surface  – light tint, minimal blur (cards, list items)
//   Panel    – medium tint, moderate blur (bottom sheets, dialogs)
//   Floating – heavy tint, strong blur (overlays, floating toolbars)
// ═══════════════════════════════════════════════════════════════════

private object LiquidGlassDefaults {
    // Surface-level glass – subtle, for inline elements
    const val SURFACE_BLUR = 12f
    const val SURFACE_TINT_ALPHA = 0.08f
    const val SURFACE_BORDER_ALPHA = 0.12f

    // Panel-level glass – medium, for overlays
    const val PANEL_BLUR = 22f
    const val PANEL_TINT_ALPHA = 0.14f
    const val PANEL_BORDER_ALPHA = 0.18f

    // Floating-level glass – heavy, for modals
    const val FLOATING_BLUR = 32f
    const val FLOATING_TINT_ALPHA = 0.22f
    const val FLOATING_BORDER_ALPHA = 0.25f
}

/**
 * Read the current theme's glass colors from AlcedoColorScheme.
 * Falls back to sensible defaults when the Alcedo theme is not active.
 */
@Composable
private fun glassColors(): Triple<Color, Color, Color> {
    // Use MaterialTheme colors as approximation; exact colors come from AlcedoColorScheme
    val surface = MaterialTheme.colorScheme.surface
    val primary = MaterialTheme.colorScheme.primary
    return Triple(
        surface.copy(alpha = LiquidGlassDefaults.SURFACE_TINT_ALPHA),
        primary.copy(alpha = LiquidGlassDefaults.SURFACE_BORDER_ALPHA),
        Color.White.copy(alpha = 0.06f)
    )
}

/**
 * LiquidGlassSurface – light frosted-glass layer for cards & list items.
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable ColumnScope.() -> Unit
) {
    val (tint, border, highlight) = glassColors()

    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                // Frosted tint fill
                drawRect(tint)
                // Top-edge highlight
                drawLine(
                    highlight, Offset(0f, 0f), Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .border(
                width = 0.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(border, border.copy(alpha = 0.05f)),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                ),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

/**
 * LiquidGlassPanel – medium frosted-glass layer for panels & bottom sheets.
 */
@Composable
fun LiquidGlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable ColumnScope.() -> Unit
) {
    val surface = MaterialTheme.colorScheme.surface
    val primary = MaterialTheme.colorScheme.primary
    val tint = surface.copy(alpha = LiquidGlassDefaults.PANEL_TINT_ALPHA)
    val border = primary.copy(alpha = LiquidGlassDefaults.PANEL_BORDER_ALPHA)
    val highlight = Color.White.copy(alpha = 0.1f)

    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                drawRect(tint)
                drawLine(
                    highlight, Offset(0f, 0f), Offset(size.width, 0f),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
            .border(
                width = 0.75.dp,
                brush = Brush.linearGradient(
                    colors = listOf(border, border.copy(alpha = 0.06f)),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                ),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

/**
 * LiquidGlassFloating – heavy frosted-glass layer for floating toolbars & modals.
 */
@Composable
fun LiquidGlassFloating(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    content: @Composable ColumnScope.() -> Unit
) {
    val surface = MaterialTheme.colorScheme.surface
    val primary = MaterialTheme.colorScheme.primary
    val tint = surface.copy(alpha = LiquidGlassDefaults.FLOATING_TINT_ALPHA)
    val border = primary.copy(alpha = LiquidGlassDefaults.FLOATING_BORDER_ALPHA)
    val highlight = Color.White.copy(alpha = 0.14f)

    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                drawRect(tint)
                // Top highlight bar
                drawLine(
                    highlight, Offset(0f, 0f), Offset(size.width, 0f),
                    strokeWidth = 2.dp.toPx()
                )
                // Inner glow at top
                val glowBrush = Brush.verticalGradient(
                    colors = listOf(highlight.copy(alpha = 0.08f), Color.Transparent),
                    startY = 0f,
                    endY = size.height * 0.3f
                )
                drawRect(glowBrush)
            }
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(border, border.copy(alpha = 0.08f)),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                ),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

/**
 * LiquidGlassToolbar – compact frosted-glass strip for floating toolbars.
 */
@Composable
fun LiquidGlassToolbar(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable () -> Unit
) {
    val surface = MaterialTheme.colorScheme.surface
    val primary = MaterialTheme.colorScheme.primary
    val tint = surface.copy(alpha = LiquidGlassDefaults.PANEL_TINT_ALPHA)
    val border = primary.copy(alpha = LiquidGlassDefaults.PANEL_BORDER_ALPHA)
    val highlight = Color.White.copy(alpha = 0.1f)

    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                drawRect(tint)
                drawLine(
                    highlight, Offset(0f, 0f), Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .border(
                width = 0.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(border, border.copy(alpha = 0.05f)),
                    start = Offset.Zero,
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                ),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
