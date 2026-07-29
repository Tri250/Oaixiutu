package com.alcedo.studio.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.AlcedoTheme
import com.alcedo.studio.ui.theme.DesignTokens

/** A determinate linear progress bar matching the Alcedo editor aesthetic. */
@Composable
fun LinearProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = DesignTokens.sliderTrackHeight,
    trackColor: Color = AlcedoTheme.extendedColors.sliderTrackInactive,
    progressColor: Color = AlcedoTheme.extendedColors.accent,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val barHeight = size.height
        drawRoundRect(
            color = trackColor,
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barHeight / 2),
        )
        val clamped = progress.coerceIn(0f, 1f)
        if (clamped > 0f) {
            drawRoundRect(
                color = progressColor,
                size = Size(size.width * clamped, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barHeight / 2),
            )
        }
    }
}

/** An indeterminate linear progress bar with an animated travelling segment. */
@Composable
fun IndeterminateProgressBar(
    modifier: Modifier = Modifier,
    height: Dp = DesignTokens.sliderTrackHeight,
    color: Color = AlcedoTheme.extendedColors.accent,
) {
    val transition = rememberInfiniteTransition(label = "indeterminate")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "travel",
    )
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val barHeight = size.height
        drawRoundRect(
            color = AlcedoTheme.extendedColors.sliderTrackInactive,
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barHeight / 2),
        )
        val segmentWidth = size.width * 0.3f
        val startX = (size.width - segmentWidth) * progress
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(color.copy(alpha = 0.2f), color),
                startX = startX,
                endX = startX + segmentWidth,
            ),
            topLeft = Offset(startX, 0f),
            size = Size(segmentWidth, barHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barHeight / 2),
        )
    }
}

/** A circular progress ring showing a determinate percentage. */
@Composable
fun CircularProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 4.dp,
    progressColor: Color = AlcedoTheme.extendedColors.accent,
    trackColor: Color = AlcedoTheme.extendedColors.sliderTrackInactive,
) {
    Canvas(modifier = modifier) {
        val stroke = strokeWidth.toPx()
        val diameter = size.minDimension - stroke
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = Size(diameter, diameter),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawArc(
            color = progressColor,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = topLeft,
            size = Size(diameter, diameter),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}
