package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alcedo.studio.data.model.AdjustmentParams
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.SectionHeader
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * Effects panel. Film grain (amount/size), halation and LUT loading. Each
 * effect maps to fields in [AdjustmentParams] pushed live via [onUpdate].
 */
@Composable
fun EffectsPanel(
    params: AdjustmentParams,
    onUpdate: (String, Float) -> Unit,
    modifier: Modifier = Modifier,
    onLoadLut: () -> Unit = {},
    onClearLut: () -> Unit = {},
) {
    val s = Strings.res
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.controlSpacing)) {
        SectionHeader(title = s.filmGrain)
        AdjustmentSlider(
            label = s.grainAmount,
            value = params.filmGrainAmount,
            defaultValue = 0f,
            range = 0f..100f,
            valueFormatter = { "%.0f".format(it) },
            onValueChange = { onUpdate("filmGrainAmount", it) },
        )
        AdjustmentSlider(
            label = s.grainSize,
            value = params.filmGrainSize,
            defaultValue = 1f,
            range = 0.5f..4f,
            valueFormatter = { "%.1f".format(it) },
            onValueChange = { onUpdate("filmGrainSize", it) },
        )

        SectionHeader(title = s.halation)
        AdjustmentSlider(
            label = s.halationIntensity,
            value = params.halationAmount,
            defaultValue = 0f,
            range = 0f..100f,
            valueFormatter = { "%.0f".format(it) },
            onValueChange = { onUpdate("halationAmount", it) },
        )

        SectionHeader(title = s.luts)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm),
        ) {
            OutlinedButton(onClick = onLoadLut, modifier = Modifier.weight(1f)) {
                Text(s.loadLut)
            }
            if (params.lutPath != null) {
                OutlinedButton(onClick = onClearLut, modifier = Modifier.weight(1f)) {
                    Text("✕")
                }
            }
        }
        AdjustmentSlider(
            label = s.lutIntensity,
            value = params.lutIntensity,
            defaultValue = 1f,
            range = 0f..1f,
            valueFormatter = { "%d%%".format((it * 100).toInt()) },
            onValueChange = { onUpdate("lutIntensity", it) },
            enabled = params.lutPath != null,
        )
        if (params.lutPath != null) {
            Text(
                text = params.lutPath!!.substringAfterLast('/'),
                color = AlcedoColors.TextTertiary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
