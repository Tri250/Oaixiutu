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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalView
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.HapticFeedback
import com.alcedo.studio.ui.common.LiquidGlassSurface
import com.alcedo.studio.viewmodel.EditorViewModel

@Composable
fun ColorPanel(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
    focusMode: FocusModeState = FocusModeState()
) {
    val params by remember { viewModel.params }

    // 专注模式下用于切换活跃小节的小节标签
    val focusSections = listOf(
        "color.wheels" to stringRes { editorColorWheels },
        "color.hsl" to stringRes { editorHsl },
        "color.mixer" to stringRes { editorChannelMixer }
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FocusSectionChips(focusMode = focusMode, sections = focusSections)

        // ── Color Wheels (CDL) ─────────────────────────────────────
        if (focusMode.shouldShowSection("color.wheels")) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                ColorWheelsSection(viewModel = viewModel, params = params)
            }
        }

        // ── HSL ────────────────────────────────────────────────────
        if (focusMode.shouldShowSection("color.hsl")) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                HslSection(viewModel = viewModel, params = params)
            }
        }

        // ── Channel Mixer ──────────────────────────────────────────
        if (focusMode.shouldShowSection("color.mixer")) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                ChannelMixerSection(viewModel = viewModel, params = params)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Color Wheels Section
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ColorWheelsSection(
    viewModel: EditorViewModel,
    params: com.alcedo.studio.data.model.PipelineParams
) {
    var selectedWheel by remember { mutableStateOf(ColorWheelType.LIFT) }
    val view = LocalView.current

    Column(modifier = Modifier.padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringRes { editorColorWheels },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(
                onClick = {
                    HapticFeedback.heavyClick(view)
                    viewModel.resetColorWheels()
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringRes { colorResetWheels },
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Wheel type selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ColorWheelType.entries.forEach { type ->
                FilterChip(
                    selected = selectedWheel == type,
                    onClick = {
                        HapticFeedback.click(view)
                        selectedWheel = type
                    },
                    label = { Text(type.label()) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // CDL Color Wheel for selected type
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

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CdlColorWheel(
                wheelType = selectedWheel,
                r = r,
                g = g,
                b = b,
                onColorChanged = { newR, newG, newB ->
                    when (selectedWheel) {
                        ColorWheelType.LIFT -> viewModel.updateColorWheelLift(floatArrayOf(newR, newG, newB))
                        ColorWheelType.GAMMA -> viewModel.updateColorWheelGamma(floatArrayOf(newR, newG, newB))
                        ColorWheelType.GAIN -> viewModel.updateColorWheelGain(floatArrayOf(newR, newG, newB))
                    }
                },
                modifier = Modifier.fillMaxWidth(0.7f),
                wheelSizeDp = 200.dp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // RGB value readout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf(
                    "R" to r to Color.Red,
                    "G" to g to Color(0xFF4CAF50),
                    "B" to b to Color(0xFF4488FF)
                ).forEach { (pair, color) ->
                    val (label, value) = pair
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = color
                        )
                        Text(
                            "%.3f".format(value),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Fine-tune sliders for RGB
            val valueRange = when (selectedWheel) {
                ColorWheelType.LIFT -> -0.5f..0.5f
                ColorWheelType.GAMMA -> 0f..2f
                ColorWheelType.GAIN -> 0f..2f
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "R" to r to Color.Red,
                    "G" to g to Color(0xFF4CAF50),
                    "B" to b to Color(0xFF4488FF)
                ).forEach { (pair, color) ->
                    val (label, value) = pair
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = color
                        )
                        Slider(
                            value = value,
                            onValueChange = { newVal ->
                                HapticFeedback.tick(view)
                                when (selectedWheel) {
                                    ColorWheelType.LIFT -> {
                                        val arr = floatArrayOf(
                                            if (label == "R") newVal else params.colorWheelLiftR,
                                            if (label == "G") newVal else params.colorWheelLiftG,
                                            if (label == "B") newVal else params.colorWheelLiftB
                                        )
                                        viewModel.updateColorWheelLift(arr)
                                    }
                                    ColorWheelType.GAMMA -> {
                                        val arr = floatArrayOf(
                                            if (label == "R") newVal else params.colorWheelGammaR,
                                            if (label == "G") newVal else params.colorWheelGammaG,
                                            if (label == "B") newVal else params.colorWheelGammaB
                                        )
                                        viewModel.updateColorWheelGamma(arr)
                                    }
                                    ColorWheelType.GAIN -> {
                                        val arr = floatArrayOf(
                                            if (label == "R") newVal else params.colorWheelGainR,
                                            if (label == "G") newVal else params.colorWheelGainG,
                                            if (label == "B") newVal else params.colorWheelGainB
                                        )
                                        viewModel.updateColorWheelGain(arr)
                                    }
                                }
                            },
                            valueRange = valueRange,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Reset button for current wheel
        OutlinedButton(
            onClick = {
                HapticFeedback.heavyClick(view)
                when (selectedWheel) {
                    ColorWheelType.LIFT -> viewModel.updateColorWheelLift(floatArrayOf(0f, 0f, 0f))
                    ColorWheelType.GAMMA -> viewModel.updateColorWheelGamma(floatArrayOf(1f, 1f, 1f))
                    ColorWheelType.GAIN -> viewModel.updateColorWheelGain(floatArrayOf(1f, 1f, 1f))
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringRes { editorReset }.format(selectedWheel.label()))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// HSL Section
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun HslSection(
    viewModel: EditorViewModel,
    params: com.alcedo.studio.data.model.PipelineParams
) {
    val view = LocalView.current
    val hslChannelNames = listOf(
        stringRes { editorColorRed },
        stringRes { editorColorOrange },
        stringRes { editorColorYellow },
        stringRes { editorColorGreen },
        stringRes { editorColorCyan },
        stringRes { editorColorBlue },
        stringRes { editorColorPurple },
        stringRes { editorColorMagenta }
    )
    val hslChannelIndices = listOf(0, 1, 2, 3, 4, 5, 6, 7)

    var expandedChannel by remember { mutableStateOf(-1) }

    Column(modifier = Modifier.padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringRes { editorHsl },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(
                onClick = {
                    HapticFeedback.heavyClick(view)
                    viewModel.resetHsl()
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringRes { colorResetHsl },
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        val hueColors = listOf(
            Color(0xFFE53935),
            Color(0xFFFF9800),
            Color(0xFFFFEB3B),
            Color(0xFF4CAF50),
            Color(0xFF00BCD4),
            Color(0xFF2196F3),
            Color(0xFF9C27B0),
            Color(0xFFE91E63)
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            hslChannelNames.forEachIndexed { idx, name ->
                val index = hslChannelIndices[idx]
                val isExpanded = expandedChannel == index

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
                                label = stringRes { editorHue },
                                value = params.hslHueShift[index],
                                range = -180f..180f,
                                onValueChange = { viewModel.updateHslHueShift(index, it) },
                                defaultValue = 0f,
                                valueDisplayTransform = { "%.0f°".format(it) }
                            )
                            AdjustmentSlider(
                                label = stringRes { editorSaturation },
                                value = params.hslSaturationScale[index],
                                range = 0f..2f,
                                onValueChange = { viewModel.updateHslSaturationScale(index, it) },
                                defaultValue = 1f
                            )
                            AdjustmentSlider(
                                label = stringRes { editorLuminance },
                                value = params.hslLuminanceScale[index],
                                range = 0f..2f,
                                onValueChange = { viewModel.updateHslLuminanceScale(index, it) },
                                defaultValue = 1f
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Channel Mixer Section
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ChannelMixerSection(
    viewModel: EditorViewModel,
    params: com.alcedo.studio.data.model.PipelineParams
) {
    val view = LocalView.current
    val outputLabels = listOf(
        stringRes { editorOutputR },
        stringRes { editorOutputG },
        stringRes { editorOutputB }
    )

    Column(modifier = Modifier.padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringRes { editorChannelMixer },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(
                onClick = {
                    HapticFeedback.heavyClick(view)
                    viewModel.updateChannelMixer(
                        floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
                        false
                    )
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringRes { colorResetMixer },
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = params.channelMixerMonochrome,
                onCheckedChange = {
                    viewModel.updateChannelMixer(params.channelMixerMatrix, it)
                }
            )
            Text(stringRes { editorMonochrome }, style = MaterialTheme.typography.bodyMedium)
        }

        val labels = listOf("R", "G", "B")
        val matrix = params.channelMixerMatrix

        outputLabels.forEachIndexed { row, rowLabel ->
            Text(
                rowLabel,
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
                                HapticFeedback.tick(view)
                                val newMatrix = matrix.clone()
                                newMatrix[row * 3 + col] = it
                                viewModel.updateChannelMixer(newMatrix, params.channelMixerMonochrome)
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

enum class ColorWheelType {
    LIFT,
    GAMMA,
    GAIN;

    @androidx.compose.runtime.Composable
    fun label(): String = when (this) {
        LIFT -> stringRes { editorLift }
        GAMMA -> stringRes { editorGamma }
        GAIN -> stringRes { editorGain }
    }
}
