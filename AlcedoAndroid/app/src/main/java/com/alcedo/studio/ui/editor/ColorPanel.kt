package com.alcedo.studio.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.PipelineParams
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.SectionHeader

@Composable
fun ColorPanel(
    params: PipelineParams,
    onParamsChanged: (PipelineParams) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorWheelMode = remember { mutableStateOf(ColorWheelMode.HUE_RING_WITH_TRIANGLE) }
    var selectedWheel by remember { mutableStateOf(ColorWheelType.LIFT) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Color Wheels
        SectionHeader(title = "Color Wheels") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ColorWheelType.entries.forEach { type ->
                    FilterChip(
                        selected = selectedWheel == type,
                        onClick = { selectedWheel = type },
                        label = { Text(type.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val (r, g, b) = when (selectedWheel) {
                    ColorWheelType.LIFT -> Triple(
                        params.colorWheelLiftR,
                        params.colorWheelLiftG,
                        params.colorWheelLiftB
                    )
                    ColorWheelType.GAMMA -> Triple(
                        params.colorWheelGammaR,
                        params.colorWheelGammaG,
                        params.colorWheelGammaB
                    )
                    ColorWheelType.GAIN -> Triple(
                        params.colorWheelGainR,
                        params.colorWheelGainG,
                        params.colorWheelGainB
                    )
                }

                // Simplified color wheel display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "R" to r to androidx.compose.ui.graphics.Color.Red,
                        "G" to g to androidx.compose.ui.graphics.Color(0xFF4CAF50),
                        "B" to b to androidx.compose.ui.graphics.Color(0xFF4488FF)
                    ).forEach { (pair, color) ->
                        val (label, value) = pair
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
                            Slider(
                                value = value,
                                onValueChange = { newVal ->
                                    val newParams = when (selectedWheel) {
                                        ColorWheelType.LIFT -> params.copy(
                                            colorWheelLiftR = if (label == "R") newVal else params.colorWheelLiftR,
                                            colorWheelLiftG = if (label == "G") newVal else params.colorWheelLiftG,
                                            colorWheelLiftB = if (label == "B") newVal else params.colorWheelLiftB
                                        )
                                        ColorWheelType.GAMMA -> params.copy(
                                            colorWheelGammaR = if (label == "R") newVal else params.colorWheelGammaR,
                                            colorWheelGammaG = if (label == "G") newVal else params.colorWheelGammaG,
                                            colorWheelGammaB = if (label == "B") newVal else params.colorWheelGammaB
                                        )
                                        ColorWheelType.GAIN -> params.copy(
                                            colorWheelGainR = if (label == "R") newVal else params.colorWheelGainR,
                                            colorWheelGainG = if (label == "G") newVal else params.colorWheelGainG,
                                            colorWheelGainB = if (label == "B") newVal else params.colorWheelGainB
                                        )
                                    }
                                    onParamsChanged(newParams)
                                },
                                valueRange = when (selectedWheel) {
                                    ColorWheelType.LIFT -> -0.5f..0.5f
                                    ColorWheelType.GAMMA -> 0f..2f
                                    ColorWheelType.GAIN -> 0f..2f
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // HSL Color Channels
        SectionHeader(title = "HSL") {
            val hslChannels = listOf(
                "Red" to 0, "Orange" to 1, "Yellow" to 2, "Green" to 3,
                "Cyan" to 4, "Blue" to 5, "Purple" to 6, "Magenta" to 7
            )

            var expandedChannel by remember { mutableStateOf(-1) }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                hslChannels.forEach { (name, index) ->
                    val isExpanded = expandedChannel == index
                    val hueColors = listOf(
                        androidx.compose.ui.graphics.Color(0xFFE53935),
                        androidx.compose.ui.graphics.Color(0xFFFF9800),
                        androidx.compose.ui.graphics.Color(0xFFFFEB3B),
                        androidx.compose.ui.graphics.Color(0xFF4CAF50),
                        androidx.compose.ui.graphics.Color(0xFF00BCD4),
                        androidx.compose.ui.graphics.Color(0xFF2196F3),
                        androidx.compose.ui.graphics.Color(0xFF9C27B0),
                        androidx.compose.ui.graphics.Color(0xFFE91E63)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isExpanded)
                                MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surface
                        ),
                        onClick = { expandedChannel = if (isExpanded) -1 else index }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.foundation.Canvas(modifier = Modifier.size(12.dp)) {
                                    drawCircle(color = hueColors[index])
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(name, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                text = "H:${"%.0f".format(params.hslHueShift[index])}° " +
                                    "S:${"%.1f".format(params.hslSaturationScale[index])} " +
                                    "L:${"%.1f".format(params.hslLuminanceScale[index])}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isExpanded) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AdjustmentSlider(
                                    label = "Hue",
                                    value = params.hslHueShift[index],
                                    range = -180f..180f,
                                    onValueChange = {
                                        val newArr = params.hslHueShift.clone()
                                        newArr[index] = it
                                        onParamsChanged(params.copy(hslHueShift = newArr))
                                    },
                                    defaultValue = 0f,
                                    valueDisplayTransform = { "%.0f°".format(it) }
                                )
                                AdjustmentSlider(
                                    label = "Saturation",
                                    value = params.hslSaturationScale[index],
                                    range = 0f..2f,
                                    onValueChange = {
                                        val newArr = params.hslSaturationScale.clone()
                                        newArr[index] = it
                                        onParamsChanged(params.copy(hslSaturationScale = newArr))
                                    },
                                    defaultValue = 1f
                                )
                                AdjustmentSlider(
                                    label = "Luminance",
                                    value = params.hslLuminanceScale[index],
                                    range = 0f..2f,
                                    onValueChange = {
                                        val newArr = params.hslLuminanceScale.clone()
                                        newArr[index] = it
                                        onParamsChanged(params.copy(hslLuminanceScale = newArr))
                                    },
                                    defaultValue = 1f
                                )
                            }
                        }
                    }
                }
            }
        }

        // Channel Mixer
        SectionHeader(title = "Channel Mixer") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = params.channelMixerMonochrome,
                        onCheckedChange = {
                            onParamsChanged(params.copy(channelMixerMonochrome = it))
                        }
                    )
                    Text("Monochrome", style = MaterialTheme.typography.bodyMedium)
                }

                val labels = listOf("R", "G", "B")
                val matrix = params.channelMixerMatrix

                labels.forEachIndexed { row, rowLabel ->
                    Text(
                        "Output $rowLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        labels.forEachIndexed { col, colLabel ->
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    colLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Slider(
                                    value = matrix[row * 3 + col],
                                    onValueChange = {
                                        val newMatrix = matrix.clone()
                                        newMatrix[row * 3 + col] = it
                                        onParamsChanged(params.copy(channelMixerMatrix = newMatrix))
                                    },
                                    valueRange = -2f..2f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class ColorWheelType(val label: String) {
    LIFT("Lift"),
    GAMMA("Gamma"),
    GAIN("Gain")
}