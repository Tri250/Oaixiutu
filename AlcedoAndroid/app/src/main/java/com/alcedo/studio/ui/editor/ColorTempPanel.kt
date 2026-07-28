package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.alcedo.studio.data.model.AdjustmentParams
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.SectionHeader
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * White balance panel: temperature (2000K–15000K) and tint sliders with WB
 * presets (Daylight, Cloudy, Shade, Tungsten, Fluorescent, Flash, As Shot,
 * Custom). Selecting a preset seeds temperature/tint and pushes both to the
 * pipeline.
 */
@Composable
fun ColorTempPanel(
    params: AdjustmentParams,
    onUpdate: (String, Float) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = Strings.res
    var presetExpanded by remember { mutableStateOf(false) }
    // Presets map to Kelvin temperature + tint offset
    val presets = listOf(
        "As Shot" to (5500f to 0f),
        "Daylight" to (5500f to 0f),
        "Cloudy" to (6500f to 0f),
        "Shade" to (7500f to 5f),
        "Tungsten" to (3200f to 0f),
        "Fluorescent" to (4000f to 10f),
        "Flash" to (5500f to 0f),
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.controlSpacing)) {
        SectionHeader(title = s.whiteBalance)

        // Preset chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingXs),
        ) {
            presets.forEach { (name, _) ->
                FilterChip(
                    selected = false,
                    onClick = {
                        val (_, pair) = presets.first { it.first == name }
                        onUpdate("temperature", kelvinToSlider(pair.first))
                        onUpdate("tint", pair.second / 100f)
                        onCommit()
                    },
                    label = { Text(name, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }

        // Temperature slider mapped to Kelvin (2000K–15000K)
        val tempKelvin = sliderToKelvin(params.temperature)
        AdjustmentSlider(
            label = s.temperature, value = params.temperature, defaultValue = 0f,
            range = -1f..1f,
            valueFormatter = { "%dK".format(sliderToKelvin(it).toInt()) },
            onValueChange = { onUpdate("temperature", it) },
            onValueChangeFinished = onCommit,
        )
        AdjustmentSlider(
            label = s.tint, value = params.tint, defaultValue = 0f,
            range = -1f..1f, valueFormatter = { "%+.0f".format(it * 100f) },
            onValueChange = { onUpdate("tint", it) },
            onValueChangeFinished = onCommit,
        )
    }
}

/** Map slider value (-1..1) to Kelvin (2000..15000). */
private fun sliderToKelvin(slider: Float): Float {
    // 0 = 5500K, -1 = 2000K, +1 = 15000K
    return if (slider <= 0f) {
        5500f + slider * 3500f  // 5500 → 2000
    } else {
        5500f + slider * 9500f  // 5500 → 15000
    }
}

/** Map Kelvin (2000..15000) to slider (-1..1). */
private fun kelvinToSlider(kelvin: Float): Float {
    return if (kelvin <= 5500f) {
        (kelvin - 5500f) / 3500f
    } else {
        (kelvin - 5500f) / 9500f
    }
}
