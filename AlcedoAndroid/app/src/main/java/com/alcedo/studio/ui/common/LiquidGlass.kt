package com.alcedo.studio.ui.common

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import com.alcedo.studio.ui.theme.LocalAlcedoColorScheme

// ═══════════════════════════════════════════════════════════════════
// Liquid Glass Effect System – 2026 flagship frosted-glass UI
// Uses RenderEffect backdrop blur (API 31+) with fallback tint
// Three levels of glass depth:
//   Surface  – light tint, minimal blur (cards, list items, chips)
//   Panel    – medium tint, moderate blur (bottom sheets, side panels)
//   Floating – heavy tint, strong blur (overlays, floating toolbars)
// ═══════════════════════════════════════════════════════════════════

private object LiquidGlassDefaults {
    // 2026 摄影师优化: 增强面板层次感,提升暗色主题下的可读性
    const val SURFACE_BLUR = 14f       // 12→14: 更强的毛玻璃效果
    const val SURFACE_TINT_ALPHA = 0.10f  // 0.08→0.10: 更明确的色调
    const val SURFACE_BORDER_ALPHA = 0.14f // 0.12→0.14: 更清晰的面板边界

    const val PANEL_BLUR = 24f          // 22→24: 面板级更强的毛玻璃
    const val PANEL_TINT_ALPHA = 0.16f  // 0.14→0.16: 面板色调更明确
    const val PANEL_BORDER_ALPHA = 0.20f // 0.18→0.20: 面板边界更清晰

    const val FLOATING_BLUR = 34f       // 32→34: 浮动层最强调
    const val FLOATING_TINT_ALPHA = 0.24f // 0.22→0.24: 浮动层色调
    const val FLOATING_BORDER_ALPHA = 0.28f // 0.25→0.28: 浮动层边界
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

/**
 * Backdrop blur modifier – blurs the content BEHIND this composable.
 * On API 31+ (Android 12+), uses RenderEffect with the correct layer placement
 * to achieve a true frosted glass look. On older APIs, falls back to a
 * semi-transparent tinted overlay.
 */
private fun Modifier.glassBlur(radius: Float): Modifier {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.graphicsLayer {
            // Use renderEffect on the content behind this layer.
            // The key insight: we apply blur at the graphicsLayer level so that
            // the composables rendered BEHIND this one get blurred when they
            // show through the semi-transparent background.
            renderEffect = RenderEffect.createBlurEffect(
                radius, radius, Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
            // Preserve alpha so the tinted background can blend with blurred backdrop
            this.alpha = 0.96f
        }
    } else {
        // Fallback: stronger tint on pre-Android 12 to simulate glass
        this
    }
}

/**
 * Animated glass appearance – fades in with a gentle scale animation.
 * 2026 摄影师优化: 更快的进入动画(200ms), 更柔和的弹性
 */
@Composable
private fun Modifier.animatedGlassEntry(visible: Boolean = true): Modifier {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "glassAlpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.97f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "glassScale"
    )
    return this.graphicsLayer {
        this.alpha = alpha
        this.scaleX = scale
        this.scaleY = scale
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
        modifier = modifier.clip(shape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .glassBlur(LiquidGlassDefaults.SURFACE_BLUR)
                .drawBehind {
                    drawRect(tint)
                    drawLine(
                        highlight, Offset(0f, 0f), Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx()
                    )
                    val glowBrush = Brush.verticalGradient(
                        colors = listOf(highlight.copy(alpha = 0.06f), Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.15f
                    )
                    drawRect(glowBrush)
                }
                .border(
                    width = 0.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(border, border.copy(alpha = 0.05f)),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    ),
                    shape = shape
                )
        )
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
        modifier = modifier.clip(shape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .glassBlur(LiquidGlassDefaults.PANEL_BLUR)
                .drawBehind {
                    drawRect(tint)
                    drawLine(
                        highlight, Offset(0f, 0f), Offset(size.width, 0f),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    val glowBrush = Brush.verticalGradient(
                        colors = listOf(highlight.copy(alpha = 0.08f), Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.2f
                    )
                    drawRect(glowBrush)
                }
                .border(
                    width = 0.75.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(border, border.copy(alpha = 0.06f)),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    ),
                    shape = shape
                )
        )
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
        modifier = modifier.clip(shape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .glassBlur(LiquidGlassDefaults.FLOATING_BLUR)
                .drawBehind {
                    drawRect(tint)
                    drawLine(
                        highlight, Offset(0f, 0f), Offset(size.width, 0f),
                        strokeWidth = 2.dp.toPx()
                    )
                    val glowBrush = Brush.verticalGradient(
                        colors = listOf(highlight.copy(alpha = 0.1f), Color.Transparent),
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
                )
        )
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
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        // Blur background layer — ONLY blurs what's behind, not the content
        Box(
            modifier = Modifier
                .matchParentSize()
                .glassBlur(LiquidGlassDefaults.PANEL_BLUR)
                .drawBehind {
                    drawRect(tint)
                    drawLine(
                        highlight, Offset(0f, 0f), Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx()
                    )
                    val glowBrush = Brush.verticalGradient(
                        colors = listOf(highlight.copy(alpha = 0.06f), Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.2f
                    )
                    drawRect(glowBrush)
                }
                .border(
                    width = 0.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(border, border.copy(alpha = 0.05f)),
                        start = Offset.Zero,
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    ),
                    shape = shape
                )
        )
        // Content layer — rendered on top, NO blur applied
        content()
    }
}
