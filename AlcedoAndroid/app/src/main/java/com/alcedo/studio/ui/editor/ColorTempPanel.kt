package com.alcedo.studio.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.PipelineParams
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.SectionHeader

enum class WhiteBalanceMode(val label: String) {
    AS_SHOT("As Shot"),
    CUSTOM("Custom")
}

enum class WhiteBalancePreset(val label: String, val temp: Float, val tint: Float) {
    DAYLIGHT("Daylight", 5500f, 0f),
    CLOUDY("Cloudy", 6500f, 10f),
    TUNGSTEN("Tungsten", 3200f, 0f),
    FLUORESCENT("Fluorescent", 4200f, 20f),
    FLASH("Flash", 5500f, 0f)
}

@Composable
fun ColorTempPanel(
    params: PipelineParams,
    onParamsChanged: (PipelineParams) -> Unit,
    modifier: Modifier = Modifier
) {
    var wbMode by remember { mutableStateOf(WhiteBalanceMode.AS_SHOT) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Mode selector
        SectionHeader(title = "Mode") {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                WhiteBalanceMode.entries.forEach { mode ->
                    FilterChip(
                        selected = wbMode == mode,
                        onClick = {
                            wbMode = mode
                            if (mode == WhiteBalanceMode.AS_SHOT) {
                                onParamsChanged(
                                    params.copy(
                                        whiteBalanceTemp = 6500f,
                                        whiteBalanceTint = 0f
                                    )
                                )
                            }
                        },
                        label = { Text(mode.label) }
                    )
                }
            }
        }

        // CCT Slider with visual gradient track
        SectionHeader(title = "Color Temperature") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Visual gradient track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val steps = 256
                        val barWidth = size.width / steps
                        for (i in 0 until steps) {
                            val t = i.toFloat() / steps
                            val temp = 2000f + t * 13000f
                            val color = tempToColor(temp)
                            drawRect(
                                color = color,
                                topLeft = androidx.compose.ui.geometry.Offset(i * barWidth, 0f),
                                size = androidx.compose.ui.geometry.Size(barWidth + 1f, size.height)
                            )
                        }
                        // Border
                        drawRect(
                            color = Color.White.copy(alpha = 0.2f),
                            style = Stroke(width = 1f)
                        )
                    }
                    // Current position indicator
                    val tempNorm = ((params.whiteBalanceTemp - 2000f) / 13000f)
                        .coerceIn(0f, 1f)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val indicatorX = tempNorm * size.width
                        drawLine(
                            color = Color.White,
                            start = androidx.compose.ui.geometry.Offset(indicatorX, 0f),
                            end = androidx.compose.ui.geometry.Offset(indicatorX, size.height),
                            strokeWidth = 2f
                        )
                    }
                }

                AdjustmentSlider(
                    label = "CCT",
                    value = params.whiteBalanceTemp,
                    range = 2000f..15000f,
                    onValueChange = {
                        wbMode = WhiteBalanceMode.CUSTOM
                        onParamsChanged(params.copy(whiteBalanceTemp = it))
                    },
                    defaultValue = 6500f,
                    valueDisplayTransform = { "%.0fK".format(it) }
                )
            }
        }

        // Tint Slider with green-magenta gradient
        SectionHeader(title = "Tint") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Green-magenta gradient track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val steps = 256
                        val barWidth = size.width / steps
                        for (i in 0 until steps) {
                            val t = i.toFloat() / steps
                            val tintVal = -150f + t * 300f
                            val color = tintToColor(tintVal)
                            drawRect(
                                color = color,
                                topLeft = androidx.compose.ui.geometry.Offset(i * barWidth, 0f),
                                size = androidx.compose.ui.geometry.Size(barWidth + 1f, size.height)
                            )
                        }
                        drawRect(
                            color = Color.White.copy(alpha = 0.2f),
                            style = Stroke(width = 1f)
                        )
                    }
                    val tintNorm = ((params.whiteBalanceTint + 150f) / 300f)
                        .coerceIn(0f, 1f)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val indicatorX = tintNorm * size.width
                        drawLine(
                            color = Color.White,
                            start = androidx.compose.ui.geometry.Offset(indicatorX, 0f),
                            end = androidx.compose.ui.geometry.Offset(indicatorX, size.height),
                            strokeWidth = 2f
                        )
                    }
                }

                AdjustmentSlider(
                    label = "Tint",
                    value = params.whiteBalanceTint,
                    range = -150f..150f,
                    onValueChange = {
                        wbMode = WhiteBalanceMode.CUSTOM
                        onParamsChanged(params.copy(whiteBalanceTint = it))
                    },
                    defaultValue = 0f,
                    valueDisplayTransform = {
                        val sign = if (it >= 0) "+" else ""
                        "$sign${it.toInt()}"
                    }
                )
            }
        }

        // Preset white balance buttons
        SectionHeader(title = "Presets") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                WhiteBalancePreset.entries.forEach { preset ->
                    val isSelected = wbMode == WhiteBalanceMode.CUSTOM &&
                        params.whiteBalanceTemp == preset.temp &&
                        params.whiteBalanceTint == preset.tint

                    OutlinedButton(
                        onClick = {
                            wbMode = WhiteBalanceMode.CUSTOM
                            onParamsChanged(
                                params.copy(
                                    whiteBalanceTemp = preset.temp,
                                    whiteBalanceTint = preset.tint
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(preset.label)
                            Text(
                                "%.0fK  Tint: ${preset.tint.toInt()}".format(preset.temp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun tempToColor(temp: Float): Color {
    val t = temp / 100f
    val r: Float
    val g: Float
    val b: Float

    r = when {
        t <= 66f -> 1f
        else -> {
            val x = t - 60f
            (329.698727446f * (x.toFloat().pow(-0.1332047592f))).coerceIn(0f, 255f) / 255f
        }
    }

    g = when {
        t <= 66f -> {
            val x = t
            (99.4708025861f * x.toFloat().pow(-0.1332047592f)).coerceIn(0f, 255f) / 255f
        }
        else -> {
            val x = t - 60f
            (288.1221695283f * (x.toFloat().pow(-0.0755148492f))).coerceIn(0f, 255f) / 255f
        }
    }

    b = when {
        t >= 66f -> 1f
        t <= 19f -> 0f
        else -> {
            val x = t - 10f
            (138.5177312231f * (x.toFloat().pow(-0.1332047592f))).coerceIn(0f, 255f) / 255f
        }
    }

    return Color(r, g, b)
}

private fun tintToColor(tint: Float): Color {
    val normalized = (tint + 150f) / 300f // 0=magenta, 0.5=neutral, 1=green
    return when {
        normalized < 0.5f -> {
            val t = normalized * 2f
            Color(
                red = lerp(0.8f, 0.9f, t),
                green = lerp(0.4f, 0.85f, t),
                blue = lerp(0.7f, 0.85f, t)
            )
        }
        else -> {
            val t = (normalized - 0.5f) * 2f
            Color(
                red = lerp(0.9f, 0.5f, t),
                green = lerp(0.85f, 0.9f, t),
                blue = lerp(0.85f, 0.5f, t)
            )
        }
    }
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun Float.pow(exp: Float): Float = kotlin.math.pow(this.toDouble(), exp.toDouble()).toFloat()
