package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import com.alcedo.studio.i18n.Strings
import com.alcedo.studio.ui.common.SectionHeader
import com.alcedo.studio.pipeline.LutUtils
import com.alcedo.studio.ui.theme.AlcedoColors
import com.alcedo.studio.ui.theme.DesignTokens

/**
 * LMT (Look Modification Transform) panel. Lists available .cube LMT files and
 * applies the selected one to the pipeline. LMTs are colour-rendering shapers
 * applied before the display transform.
 */
@Composable
fun LmtPanel(
    modifier: Modifier = Modifier,
    onApplyLmt: (String) -> Unit = {},
    onLoadLmt: () -> Unit = {},
) {
    val s = Strings.res
    var lutInfo by remember { mutableStateOf<LutUtils.CubeLut?>(null) }
    var lutError by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.spacingSm)) {
        SectionHeader(title = s.panelLmt)
        OutlinedButton(onClick = onLoadLmt, modifier = Modifier.fillMaxWidth()) {
            Text(s.loadLut, color = AlcedoColors.AccentBlue)
        }

        // LUT info display
        lutInfo?.let { lut ->
            Card(
                colors = CardDefaults.cardColors(containerColor = AlcedoColors.SurfaceElevated),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(DesignTokens.spacingMd)) {
                    Text(
                        text = lut.title.ifBlank { "Untitled LUT" },
                        style = MaterialTheme.typography.labelMedium,
                        color = AlcedoColors.AccentBlue,
                    )
                    Text(
                        text = "Size: ${lut.size}×${lut.size}×${lut.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AlcedoColors.TextSecondary,
                    )
                }
            }
        }

        lutError?.let { err ->
            Text(err, color = AlcedoColors.Danger, style = MaterialTheme.typography.bodySmall)
        }

        Text(
            text = "Apply a Look Modification Transform (.cube) to shape scene-referred colour before the display render.",
            color = AlcedoColors.TextTertiary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
