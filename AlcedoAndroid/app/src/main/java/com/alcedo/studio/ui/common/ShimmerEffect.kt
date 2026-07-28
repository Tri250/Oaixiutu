package com.alcedo.studio.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * A shimmering placeholder used while thumbnails and previews load. Draws a
 * travelling highlight gradient over a base skeleton color.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = DesignTokens.radiusSm,
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerTranslate",
    )
    val base = AlcedoColors.SurfaceElevated
    val highlight = AlcedoColors.SurfaceHover
    Box(
        modifier = modifier
            .background(base, RoundedCornerShape(cornerRadius))
            .drawWithContent {
                drawContent()
                val width = size.width
                val startX = -width + translate * (2 * width)
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            highlight.copy(alpha = 0.6f),
                            Color.Transparent,
                        ),
                        start = Offset(startX, 0f),
                        end = Offset(startX + width, size.height),
                    ),
                )
            },
    )
}

/** A circular shimmer placeholder for avatar/thumbnail slots. */
@Composable
fun ShimmerCircle(modifier: Modifier = Modifier) {
    ShimmerBox(modifier = modifier, cornerRadius = 9999.dp)
}
