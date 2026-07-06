package com.alcedo.studio.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.alcedo.studio.i18n.stringRes
import kotlin.math.*

/**
 * CDL Color Wheel for Lift/Gamma/Gain controls.
 *
 * Uses sinusoidal basis function mapping:
 * - The indicator position (dx, dy) on the wheel maps to RGB offsets
 * - Horizontal drag rotates the hue direction of the color shift
 * - Vertical drag changes the magnitude (saturation) of the color shift
 * - The wheel draws a gradient-filled circle representing the color space
 */
@Composable
fun CdlColorWheel(
    wheelType: ColorWheelType,
    r: Float,
    g: Float,
    b: Float,
    onColorChanged: (Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    wheelSizeDp: androidx.compose.ui.unit.Dp = 200.dp
) {
    val maxShift = when (wheelType) {
        ColorWheelType.LIFT -> 0.5f
        ColorWheelType.GAMMA -> 1f
        ColorWheelType.GAIN -> 1f
    }

    // Neutral center values: Lift→(0,0,0), Gamma/Gain→(1,1,1)
    val neutral = when (wheelType) {
        ColorWheelType.LIFT -> 0f
        ColorWheelType.GAMMA -> 1f
        ColorWheelType.GAIN -> 1f
    }

    // Convert current RGB offsets to wheel position (angle + magnitude)
    val wheelState = remember(r, g, b, neutral, maxShift) {
        rgbToWheelPosition(r - neutral, g - neutral, b - neutral, maxShift)
    }

    var hueAngle by remember { mutableFloatStateOf(wheelState.first) }
    var magnitude by remember { mutableFloatStateOf(wheelState.second) }
    val accColorWheelDesc = stringRes { accColorWheel }

    // Update when external values change
    LaunchedEffect(r, g, b) {
        val state = rgbToWheelPosition(r - neutral, g - neutral, b - neutral, maxShift)
        hueAngle = state.first
        magnitude = state.second
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(wheelSizeDp)
                .semantics {
                    contentDescription = accColorWheelDesc
                    stateDescription = "R: ${"%.2f".format(r)}, G: ${"%.2f".format(g)}, B: ${"%.2f".format(b)}"
                    customActions = listOf(
                        CustomAccessibilityAction("Reset color wheel") {
                            onColorChanged(neutral, neutral, neutral)
                            true
                        }
                    )
                }
                .pointerInput(wheelType) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val radius = size.width / 2f * 0.85f
                            val dx = (offset.x - center.x) / radius
                            val dy = (offset.y - center.y) / radius

                            // If tap is near the indicator dot, start drag from current position
                            val indicatorX = cos(hueAngle) * magnitude
                            val indicatorY = sin(hueAngle) * magnitude
                            val distToIndicator = sqrt(
                                (dx - indicatorX).pow(2) + (dy - indicatorY).pow(2)
                            )

                            if (distToIndicator < 0.25f || sqrt(dx * dx + dy * dy) < 1.1f) {
                                // Start dragging: snap to touch position
                                hueAngle = atan2(dy, dx)
                                magnitude = sqrt(dx * dx + dy * dy).coerceIn(0f, 1f)
                                emitColorChange(hueAngle, magnitude, maxShift, neutral, onColorChanged)
                            }
                        },
                        onDrag = { change, _ ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val radius = size.width / 2f * 0.85f

                            // Trackball interaction:
                            // Horizontal drag → rotate hue
                            // Vertical drag → change magnitude
                            val deltaX = change.position.x - change.previousPosition.x
                            val deltaY = change.position.y - change.previousPosition.y

                            // Hue rotation: horizontal delta changes angle
                            val hueSensitivity = 0.008f
                            hueAngle += deltaX * hueSensitivity

                            // Magnitude: vertical delta changes magnitude
                            // Dragging down (positive deltaY) increases magnitude
                            val magSensitivity = 0.005f
                            magnitude = (magnitude + deltaY * magSensitivity).coerceIn(0f, 1f)

                            // Also support direct position mode for more intuitive control
                            // If drag is far from indicator, use direct position instead
                            val dx = (change.position.x - center.x) / radius
                            val dy = (change.position.y - center.y) / radius
                            val dist = sqrt(dx * dx + dy * dy)

                            if (dist > 0.15f) {
                                // Blend between trackball and direct position based on distance
                                val blendFactor = (dist - 0.15f).coerceIn(0f, 0.85f) / 0.85f
                                val directAngle = atan2(dy, dx)
                                val directMag = dist.coerceIn(0f, 1f)

                                // Normalize angle difference for smooth blending
                                var angleDiff = directAngle - hueAngle
                                while (angleDiff > PI) angleDiff -= 2 * PI.toFloat()
                                while (angleDiff < -PI) angleDiff += 2 * PI.toFloat()
                                hueAngle += angleDiff * blendFactor * 0.3f
                                magnitude = magnitude + (directMag - magnitude) * blendFactor * 0.3f
                            }

                            emitColorChange(hueAngle, magnitude, maxShift, neutral, onColorChanged)
                        },
                        onDragEnd = { }
                    )
                }
                .pointerInput(wheelType) {
                    detectTapGestures(
                        onDoubleTap = {
                            // Reset to neutral
                            hueAngle = 0f
                            magnitude = 0f
                            onColorChanged(neutral, neutral, neutral)
                        },
                        onTap = { offset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val radius = size.width / 2f * 0.85f
                            val dx = (offset.x - center.x) / radius
                            val dy = (offset.y - center.y) / radius
                            val dist = sqrt(dx * dx + dy * dy)

                            if (dist <= 1f) {
                                hueAngle = atan2(dy, dx)
                                magnitude = dist
                                emitColorChange(hueAngle, magnitude, maxShift, neutral, onColorChanged)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.width / 2f * 0.85f

                // Draw gradient-filled color wheel
                drawCdlWheelGradient(center, radius)

                // Draw center crosshair
                drawLine(
                    Color.White.copy(alpha = 0.15f),
                    Offset(center.x - radius, center.y),
                    Offset(center.x + radius, center.y),
                    strokeWidth = 0.5f
                )
                drawLine(
                    Color.White.copy(alpha = 0.15f),
                    Offset(center.x, center.y - radius),
                    Offset(center.x, center.y + radius),
                    strokeWidth = 0.5f
                )

                // Draw diagonal crosshair
                drawLine(
                    Color.White.copy(alpha = 0.08f),
                    Offset(center.x - radius * 0.707f, center.y - radius * 0.707f),
                    Offset(center.x + radius * 0.707f, center.y + radius * 0.707f),
                    strokeWidth = 0.5f
                )
                drawLine(
                    Color.White.copy(alpha = 0.08f),
                    Offset(center.x + radius * 0.707f, center.y - radius * 0.707f),
                    Offset(center.x - radius * 0.707f, center.y + radius * 0.707f),
                    strokeWidth = 0.5f
                )

                // Draw center dot
                drawCircle(
                    Color.White.copy(alpha = 0.3f),
                    radius = 2f,
                    center = center
                )

                // Draw indicator dot
                val indicatorX = center.x + cos(hueAngle) * magnitude * radius
                val indicatorY = center.y + sin(hueAngle) * magnitude * radius
                val indicatorPos = Offset(indicatorX, indicatorY)

                // Indicator shadow
                drawCircle(
                    Color.Black.copy(alpha = 0.5f),
                    radius = 11f,
                    center = Offset(indicatorPos.x + 1f, indicatorPos.y + 1f)
                )
                // Indicator outer ring
                drawCircle(
                    Color.White,
                    radius = 10f,
                    center = indicatorPos,
                    style = Stroke(width = 2.5f)
                )
                // Indicator fill with current color
                val currentColor = wheelPositionToRgb(hueAngle, magnitude, maxShift, neutral)
                val displayColor = Color(
                    red = currentColor[0].coerceIn(0f, 1f),
                    green = currentColor[1].coerceIn(0f, 1f),
                    blue = currentColor[2].coerceIn(0f, 1f)
                )
                drawCircle(displayColor, radius = 7f, center = indicatorPos)

                // Draw line from center to indicator
                if (magnitude > 0.05f) {
                    drawLine(
                        Color.White.copy(alpha = 0.2f),
                        center,
                        indicatorPos,
                        strokeWidth = 1f
                    )
                }

                // Wheel border
                drawCircle(
                    Color.White.copy(alpha = 0.2f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 1f)
                )
            }
        }
    }
}

/**
 * Draw a CDL-style color wheel gradient using sinusoidal basis functions.
 * R direction: 0° (right), G direction: 120° (lower-right), B direction: 240° (lower-left)
 */
private fun DrawScope.drawCdlWheelGradient(center: Offset, radius: Float) {
    val steps = 64
    val stepSize = radius * 2f / steps

    for (iy in 0 until steps) {
        for (ix in 0 until steps) {
            val px = center.x - radius + (ix + 0.5f) * stepSize
            val py = center.y - radius + (iy + 0.5f) * stepSize

            val dx = (px - center.x) / radius
            val dy = (py - center.y) / radius
            val dist = sqrt(dx * dx + dy * dy)

            if (dist <= 1f) {
                val angle = atan2(dy, dx)
                val rVal = (cos(angle) * dist * 0.5f + 0.5f).coerceIn(0f, 1f)
                val gVal = (cos(angle - 2f * PI.toFloat() / 3f) * dist * 0.5f + 0.5f).coerceIn(0f, 1f)
                val bVal = (cos(angle + 2f * PI.toFloat() / 3f) * dist * 0.5f + 0.5f).coerceIn(0f, 1f)

                // Desaturate slightly and darken toward edges for better visibility
                val desat = 0.6f
                val lum = 0.5f
                val finalR = rVal * desat + lum * (1f - desat)
                val finalG = gVal * desat + lum * (1f - desat)
                val finalB = bVal * desat + lum * (1f - desat)

                // Darken toward edges based on distance
                val edgeDarken = 1f - dist * 0.3f

                drawCircle(
                    color = Color(
                        red = (finalR * edgeDarken).coerceIn(0f, 1f),
                        green = (finalG * edgeDarken).coerceIn(0f, 1f),
                        blue = (finalB * edgeDarken).coerceIn(0f, 1f),
                        alpha = 0.85f
                    ),
                    radius = stepSize * 0.72f,
                    center = Offset(px, py)
                )
            }
        }
    }
}

/**
 * Convert wheel position (hueAngle, magnitude) to RGB values.
 * Uses sinusoidal basis functions: R at 0°, G at 120°, B at 240°.
 */
private fun wheelPositionToRgb(
    hueAngle: Float,
    magnitude: Float,
    maxShift: Float,
    neutral: Float
): FloatArray {
    val rOffset = cos(hueAngle) * magnitude * maxShift
    val gOffset = cos(hueAngle - 2f * PI.toFloat() / 3f) * magnitude * maxShift
    val bOffset = cos(hueAngle + 2f * PI.toFloat() / 3f) * magnitude * maxShift
    return floatArrayOf(neutral + rOffset, neutral + gOffset, neutral + bOffset)
}

/**
 * Convert RGB offsets back to wheel position (hueAngle, magnitude).
 * Inverse of the sinusoidal basis mapping.
 */
private fun rgbToWheelPosition(
    rOffset: Float,
    gOffset: Float,
    bOffset: Float,
    maxShift: Float
): Pair<Float, Float> {
    if (maxShift == 0f) return Pair(0f, 0f)

    // Normalize offsets by maxShift
    val rn = rOffset / maxShift
    val gn = gOffset / maxShift

    // From forward mapping:
    // rn = cos(angle) * magnitude
    // gn = cos(angle - 2π/3) * magnitude
    // Expand: cos(angle - 2π/3) = cos(angle)*cos(2π/3) + sin(angle)*sin(2π/3)
    //       = -0.5*cos(angle) + (√3/2)*sin(angle)
    // So: gn = (-0.5*cos(angle) + (√3/2)*sin(angle)) * magnitude
    // gn = -0.5*rn + (√3/2)*sin(angle)*magnitude
    // sin(angle)*magnitude = (gn + 0.5*rn) / (√3/2) = 2*(gn + 0.5*rn)/√3

    val sinAngleMag = 2f * (gn + 0.5f * rn) / sqrt(3f)
    val cosAngleMag = rn

    val angle = atan2(sinAngleMag, cosAngleMag)
    val magnitude = sqrt(cosAngleMag * cosAngleMag + sinAngleMag * sinAngleMag)
        .coerceIn(0f, 1f)

    return Pair(angle, magnitude)
}

/**
 * Emit color change from wheel position to RGB callback.
 */
private fun emitColorChange(
    hueAngle: Float,
    magnitude: Float,
    maxShift: Float,
    neutral: Float,
    onColorChanged: (Float, Float, Float) -> Unit
) {
    val rgb = wheelPositionToRgb(hueAngle, magnitude, maxShift, neutral)
    onColorChanged(rgb[0], rgb[1], rgb[2])
}


