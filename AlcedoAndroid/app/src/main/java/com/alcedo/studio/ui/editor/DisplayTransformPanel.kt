package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.ColorScience
import com.alcedo.studio.data.model.ColorSpace
import com.alcedo.studio.data.model.EOTF
import com.alcedo.studio.ui.common.AdjustmentSlider

@Composable
fun DisplayTransformPanel(
    colorScience: ColorScience,
    eotf: EOTF,
    outputColorSpace: ColorSpace,
    peakLuminance: Float,
    displayBrightness: Float,
    displayGamma: Float,
    filmGrainIntensity: Float,
    halationIntensity: Float,
    halationThreshold: Float,
    halationSpread: Float,
    halationRedBias: Float,
    onColorScienceChange: (ColorScience) -> Unit,
    onEotfChange: (EOTF) -> Unit,
    onOutputColorSpaceChange: (ColorSpace) -> Unit,
    onPeakLuminanceChange: (Float) -> Unit,
    onDisplayBrightnessChange: (Float) -> Unit,
    onDisplayGammaChange: (Float) -> Unit,
    onFilmGrainIntensityChange: (Float) -> Unit,
    onHalationIntensityChange: (Float) -> Unit,
    onHalationThresholdChange: (Float) -> Unit,
    onHalationSpreadChange: (Float) -> Unit,
    onHalationRedBiasChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Color Science / Output Transform
        Text("Output Transform", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ColorScience.entries.forEach { cs ->
                FilterChip(
                    selected = colorScience == cs,
                    onClick = { onColorScienceChange(cs) },
                    label = { Text(cs.name, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // EOTF
        Text("EOTF (Electro-Optical Transfer Function)", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            EOTF.entries.forEach { e ->
                FilterChip(
                    selected = eotf == e,
                    onClick = { onEotfChange(e) },
                    label = { Text(e.name, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // Output Color Space
        Text("Output Color Space", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(ColorSpace.SRGB, ColorSpace.DISPLAY_P3, ColorSpace.REC2020, ColorSpace.ACES)
                .forEach { cs ->
                    FilterChip(
                        selected = outputColorSpace == cs,
                        onClick = { onOutputColorSpaceChange(cs) },
                        label = { Text(cs.name, style = MaterialTheme.typography.labelSmall) }
                    )
                }
        }

        HorizontalDivider()

        // Display Controls
        Text("Display Controls", style = MaterialTheme.typography.labelLarge)

        AdjustmentSlider(
            label = "Peak Luminance",
            value = peakLuminance,
            range = 50f..10000f,
            onValueChange = onPeakLuminanceChange,
            defaultValue = 100f,
            valueDisplayTransform = { "${it.toInt()} nits" }
        )

        AdjustmentSlider(
            label = "Display Brightness",
            value = displayBrightness,
            range = -2f..2f,
            onValueChange = onDisplayBrightnessChange,
            defaultValue = 0f
        )

        AdjustmentSlider(
            label = "Display Gamma",
            value = displayGamma,
            range = 0.5f..3f,
            onValueChange = onDisplayGammaChange,
            defaultValue = 1f,
            valueDisplayTransform = { "%.2f".format(it) }
        )

        HorizontalDivider()

        // Film Grain
        Text("Film Grain", style = MaterialTheme.typography.labelLarge)

        AdjustmentSlider(
            label = "Intensity",
            value = filmGrainIntensity,
            range = 0f..1f,
            onValueChange = onFilmGrainIntensityChange,
            defaultValue = 0f,
            valueDisplayTransform = { "%.2f".format(it) }
        )

        HorizontalDivider()

        // Halation
        Text("Halation", style = MaterialTheme.typography.labelLarge)

        AdjustmentSlider(
            label = "Intensity",
            value = halationIntensity,
            range = 0f..1f,
            onValueChange = onHalationIntensityChange,
            defaultValue = 0f,
            valueDisplayTransform = { "%.2f".format(it) }
        )

        AdjustmentSlider(
            label = "Threshold",
            value = halationThreshold,
            range = 0f..1f,
            onValueChange = onHalationThresholdChange,
            defaultValue = 0.8f,
            valueDisplayTransform = { "%.2f".format(it) }
        )

        AdjustmentSlider(
            label = "Spread",
            value = halationSpread,
            range = 0f..50f,
            onValueChange = onHalationSpreadChange,
            defaultValue = 10f,
            valueDisplayTransform = { "%.1f".format(it) }
        )

        AdjustmentSlider(
            label = "Red Bias",
            value = halationRedBias,
            range = 0f..1f,
            onValueChange = onHalationRedBiasChange,
            defaultValue = 0.7f,
            valueDisplayTransform = { "%.2f".format(it) }
        )
    }
}
