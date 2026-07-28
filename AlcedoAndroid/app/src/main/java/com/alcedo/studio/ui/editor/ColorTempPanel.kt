package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * White balance panel: temperature and tint sliders with a WB preset dropdown
 * (Daylight, Cloudy, Shade, Tungsten, Fluorescent, Flash, As Shot, Custom).
 * Selecting a preset seeds temperature/tint and pushes both to the pipeline.
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
    val presets = listOf(
        "As Shot" to (0f to 0f),
        "Daylight" to (10f to 0f),
        "Cloudy" to (25f to 0f),
        "Shade" to (40f to 5f),
        "Tungsten" to (-30f to 0f),
        "Fluorescent" to (-15f to 10f),
        "Flash" to (15f to 0f),
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.controlSpacing)) {
        SectionHeader(title = s.whiteBalance)
        Box {
            OutlinedTextField(
                value = "WB Preset",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = { TextButton(onClick = { presetExpanded = true }) { Text("▾") } },
            )
            DropdownMenu(expanded = presetExpanded, onDismissRequest = { presetExpanded = false }) {
                presets.forEach { (name, pair) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            onUpdate("temperature", pair.first)
                            onUpdate("tint", pair.second)
                            onCommit()
                            presetExpanded = false
                        },
                    )
                }
            }
        }
        AdjustmentSlider(
            label = s.temperature, value = params.temperature, defaultValue = 0f,
            range = -100f..100f, valueFormatter = { "%+.0f".format(it) },
            onValueChange = { onUpdate("temperature", it) }, onValueChangeFinished = onCommit,
        )
        AdjustmentSlider(
            label = s.tint, value = params.tint, defaultValue = 0f,
            range = -100f..100f, valueFormatter = { "%+.0f".format(it) },
            onValueChange = { onUpdate("tint", it) }, onValueChangeFinished = onCommit,
        )
    }
}
