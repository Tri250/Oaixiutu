package com.alcedo.studio.ui.common

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alcedo.studio.ui.theme.LocalAlcedoColorScheme

// ═══════════════════════════════════════════════════════════════════
// Liquid Glass Effect System – 2026 flagship frosted-glass UI
// Enhanced with RenderEffect backdrop blur (API 31+)
// ═══════════════════════════════════════════════════════════════════
// Three levels of glass depth:
//   Surface  – light tint, minimal blur (cards, list items, chips)
//   Panel    – medium tint, moderate blur (bottom sheets, side panels)
//   Floating – heavy tint, strong blur (overlays, floating toolbars)
// ═══════════════════════════════════════════════════════════════════

private object LiquidGlassDefaults {
    const val SURFACE_BLUR = 12f
    const val SURFACE_TINT_ALPHA = 0.08f
    const val SURFACE_BORDER_ALPHA = 0.12f

    const val PANEL_BLUR = 22f
    const val PANEL_TINT_ALPHA = 0.14f
    const val PANEL_BORDER_ALPHA = 0.18f

    const val FLOATING_BLUR = 32f
    const val FLOATING_TINT_ALPHA = 0.22f
    const val FLOATING_BORDER_ALPHA = 0.25f
}

@Composable
private fun glassColors(): Triple<Color, Color, Color> {
    val alcedoScheme = LocalAlcedoColorScheme()
    return Triple(
        alcedoScheme.glassTint,
        alcedoScheme.glassBorder,
        alcedoScheme.glassHighlight
    )
}

private fun Modifier.glassBlur(radius: Float): Modifier {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.graphicsLayer {
            renderEffect = RenderEffect.createBlurEffect(
                radius, radius, Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
        }
    } else {
        this
    }
}

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
            .glassBlur(LiquidGlassDefaults.SURFACE_BLUR)
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

@Composable
fun LiquidGlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable ColumnScope.() -> Unit
) {
    val (tint, border, highlight) = glassColors()

    Box(
        modifier = modifier
            .clip(shape)
            .glassBlur(LiquidGlassDefaults.PANEL_BLUR)
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

@Composable
fun LiquidGlassFloating(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    content: @Composable ColumnScope.() -> Unit
) {
    val (tint, border, highlight) = glassColors()

    Box(
        modifier = modifier
            .clip(shape)
            .glassBlur(LiquidGlassDefaults.FLOATING_BLUR)
            .drawBehind {
                drawRect(tint)
                drawLine(
                    highlight, Offset(0f, 0f), Offset(size.width, 0f),
                    strokeWidth = 2.dp.toPx()
                )
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

@Composable
fun LiquidGlassToolbar(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable () -> Unit
) {
    val (tint, border, highlight) = glassColors()

    Box(
        modifier = modifier
            .clip(shape)
            .glassBlur(LiquidGlassDefaults.PANEL_BLUR)
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
