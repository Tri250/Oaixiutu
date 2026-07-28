package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.WatermarkConfig
import com.alcedo.studio.data.model.WatermarkPosition
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.SectionHeader
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * Watermark panel. Editable text, opacity, scale, font size and position for
 * the export watermark. Toggling enabled routes through [onConfigChange].
 */
@Composable
fun WatermarkPanel(
    config: WatermarkConfig,
    onConfigChange: (WatermarkConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = Strings.res
    var posExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.controlSpacing)) {
        SectionHeader(title = s.watermark)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(s.watermark, modifier = Modifier.weight(1f), color = AlcedoColors.TextSecondary)
            Switch(
                checked = config.enabled,
                onCheckedChange = { onConfigChange(config.copy(enabled = it)) },
            )
        }

        OutlinedTextField(
            value = config.text,
            onValueChange = { onConfigChange(config.copy(text = it)) },
            label = { Text(s.watermark) },
            singleLine = true,
            enabled = config.enabled,
            modifier = Modifier.fillMaxWidth(),
        )

        AdjustmentSlider(
            label = s.opacity,
            value = config.opacity,
            defaultValue = 0.8f,
            range = 0f..1f,
            valueFormatter = { "%d%%".format((it * 100).toInt()) },
            onValueChange = { onConfigChange(config.copy(opacity = it)) },
            enabled = config.enabled,
        )
        AdjustmentSlider(
            label = s.luminanceAmount,
            value = config.scale,
            defaultValue = 0.1f,
            range = 0.02f..0.5f,
            valueFormatter = { "%d%%".format((it * 100).toInt()) },
            onValueChange = { onConfigChange(config.copy(scale = it)) },
            enabled = config.enabled,
        )

        Box {
            OutlinedTextField(
                value = config.position.name,
                onValueChange = {},
                readOnly = true,
                label = { Text("Position") },
                enabled = config.enabled,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = { TextButton(onClick = { posExpanded = true }) { Text("▾") } },
            )
            DropdownMenu(expanded = posExpanded, onDismissRequest = { posExpanded = false }) {
                WatermarkPosition.entries.forEach { pos ->
                    DropdownMenuItem(
                        text = { Text(pos.name.replace('_', ' ')) },
                        onClick = {
                            onConfigChange(config.copy(position = pos))
                            posExpanded = false
                        },
                    )
                }
            }
        }
    }
}
