package com.alcedo.studio.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.*

@Composable
fun ColorWheelView(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onHueChanged: (Float) -> Unit,
    onSaturationChanged: (Float) -> Unit,
    onBrightnessChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    wheelSize: Float = 200f,
    mode: ColorWheelMode = ColorWheelMode.HUE_RING_WITH_TRIANGLE
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(wheelSize.dp)
                .pointerInput(mode) {
                    detectTransformGestures { _, pan, _, _ ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = size.width / 2f * 0.85f
                        val touchPos = Offset(
                            (size.width / 2f + pan.x).coerceIn(0f, size.width),
                            (size.height / 2f + pan.y).coerceIn(0f, size.height)
                        )
                        val dx = touchPos.x - center.x
                        val dy = touchPos.y - center.y
                        val dist = sqrt(dx * dx + dy * dy)

                        when (mode) {
                            ColorWheelMode.HUE_RING_WITH_TRIANGLE -> {
                                if (dist > radius * 0.6f) {
                                    val angle = (atan2(dy, dx) * 180f / PI.toFloat() + 360f) % 360f
                                    onHueChanged(angle)
                                } else {
                                    val relX = (dx / radius + 1f) / 2f
                                    val relY = (dy / radius + 1f) / 2f
                                    onSaturationChanged(relX.coerceIn(0f, 1f))
                                    onBrightnessChanged(relY.coerceIn(0f, 1f))
                                }
                            }
                            ColorWheelMode.FULL_WHEEL -> {
                                val angle = (atan2(dy, dx) * 180f / PI.toFloat() + 360f) % 360f
                                val normalizedDist = (dist / radius).coerceIn(0f, 1f)
                                onHueChanged(angle)
                                onSaturationChanged(normalizedDist)
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            onHueChanged(0f)
                            onSaturationChanged(0.5f)
                            onBrightnessChanged(0.5f)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.width / 2f * 0.85f
                val innerRadius = radius * 0.6f

                when (mode) {
                    ColorWheelMode.HUE_RING_WITH_TRIANGLE -> {
                        // Draw hue ring
                        drawHueRing(center, radius, innerRadius)

                        // Draw saturation/brightness triangle
                        val sbCenter = center
                        val sbRadius = innerRadius * 0.9f
                        drawSBTriangle(sbCenter, sbRadius, hue)

                        // Draw hue indicator
                        val hueAngle = Math.toRadians(hue.toDouble())
                        val huePos = Offset(
                            center.x + (radius + innerRadius) / 2f * cos(hueAngle).toFloat(),
                            center.y + (radius + innerRadius) / 2f * sin(hueAngle).toFloat()
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 6f,
                            center = huePos,
                            style = Stroke(width = 2f)
                        )
                        drawCircle(
                            color = Color.Black,
                            radius = 4f,
                            center = huePos,
                            style = Stroke(width = 1f)
                        )

                        // Draw SB indicator
                        val sbX = sbCenter.x + (saturation - 0.5f) * 2f * sbRadius
                        val sbY = sbCenter.y + (brightness - 0.5f) * 2f * sbRadius
                        val sbPos = Offset(sbX, sbY)
                        drawCircle(
                            color = Color.White,
                            radius = 5f,
                            center = sbPos,
                            style = Stroke(width = 2f)
                        )
                        drawCircle(
                            color = Color.Black,
                            radius = 3f,
                            center = sbPos,
                            style = Stroke(width = 1f)
                        )
                    }
                    ColorWheelMode.FULL_WHEEL -> {
                        // Full HSV wheel
                        drawHueCircle(center, radius)

                        val hueAngle = Math.toRadians(hue.toDouble())
                        val indicatorDist = radius * saturation
                        val indicatorPos = Offset(
                            center.x + indicatorDist * cos(hueAngle).toFloat(),
                            center.y + indicatorDist * sin(hueAngle).toFloat()
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 6f,
                            center = indicatorPos,
                            style = Stroke(width = 2.5f)
                        )
                        drawCircle(
                            color = Color.Black,
                            radius = 4f,
                            center = indicatorPos,
                            style = Stroke(width = 1.5f)
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawHueRing(center: Offset, outerRadius: Float, innerRadius: Float) {
    val steps = 360
    val sweepAngle = 360f / steps
    for (i in 0 until steps) {
        val hue = i.toFloat()
        val color = Color.hsv(hue, 1f, 1f)
        drawArc(
            color = color,
            startAngle = (hue - sweepAngle / 2f - 90f),
            sweepAngle = sweepAngle,
            useCenter = true,
            topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
            size = Size(outerRadius * 2, outerRadius * 2),
            style = Fill
        )
    }
    // Cut out center
    drawCircle(
        color = Color.Transparent,
        radius = innerRadius,
        center = center,
        blendMode = BlendMode.Clear
    )
    // Draw ring border
    drawCircle(
        color = Color.White.copy(alpha = 0.3f),
        radius = outerRadius,
        center = center,
        style = Stroke(width = 1f)
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.3f),
        radius = innerRadius,
        center = center,
        style = Stroke(width = 1f)
    )
}

private fun DrawScope.drawSBTriangle(center: Offset, radius: Float, hue: Float) {
    val hueColor = Color.hsv(hue, 1f, 1f)
    val white = Color.White
    val black = Color.Black

    // Draw triangle as a series of horizontal lines
    val steps = 50
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val y = center.y - radius + t * radius * 2f
        val lineWidth = (1f - abs(t - 0.5f) * 2f) * radius * 2f
        val left = center.x - lineWidth / 2f
        val right = center.x + lineWidth / 2f

        for (j in 0 until steps) {
            val s = j.toFloat() / steps
            val x = left + (right - left) * s / steps * steps
            val color = lerp(
                lerp(white, hueColor, s),
                black,
                t
            )
            drawLine(
                color = color,
                start = Offset(x, y),
                end = Offset(x + (right - left) / steps, y),
                strokeWidth = (radius * 2f / steps) + 1f
            )
        }
    }
}

private fun DrawScope.drawHueCircle(center: Offset, radius: Float) {
    val steps = 360
    for (i in 0 until steps) {
        for (j in 0..20) {
            val hue = i.toFloat()
            val sat = j.toFloat() / 20f
            val color = Color.hsv(hue, sat, 1f)
            val dist = radius * sat
            val angle = Math.toRadians((hue - 90).toDouble())
            drawCircle(
                color = color,
                radius = 2f,
                center = Offset(
                    center.x + dist * cos(angle).toFloat(),
                    center.y + dist * sin(angle).toFloat()
                )
            )
        }
    }
}

private fun lerp(a: Color, b: Color, t: Float): Color {
    return Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = a.alpha + (b.alpha - a.alpha) * t
    )
}

enum class ColorWheelMode {
    HUE_RING_WITH_TRIANGLE,
    FULL_WHEEL
}