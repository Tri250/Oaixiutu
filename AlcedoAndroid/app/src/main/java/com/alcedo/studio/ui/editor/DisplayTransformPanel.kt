package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
 * Display transform panel. Controls the output colour space, display rendering
 * transform (OpenDRT / ACES), EOTF and peak luminance. These set how the
 * pipeline maps scene-referred linear into the display-referred preview.
 */
@Composable
fun DisplayTransformPanel(
    params: AdjustmentParams,
    onUpdate: (String, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = Strings.res
    var tfExpanded by remember { mutableStateOf(false) }
    var csExpanded by remember { mutableStateOf(false) }
    var eotfExpanded by remember { mutableStateOf(false) }

    val transforms = listOf("OpenDRT" to s.openDrt, "ACES" to s.acesOpenDrt)
    val colorSpaces = listOf("sRGB", "Display P3", "Rec2020", "AdobeRGB")
    val eotfs = listOf("sRGB", "BT.1886", "PQ", "HLG")

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.controlSpacing)) {
        SectionHeader(title = s.displayTransform)

        // Display transform
        Box {
            OutlinedTextField(
                value = params.displayTransform,
                onValueChange = {},
                readOnly = true,
                label = { Text(s.displayTransform) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = { TextButton(onClick = { tfExpanded = true }) { Text("▾") } },
            )
            DropdownMenu(expanded = tfExpanded, onDismissRequest = { tfExpanded = false }) {
                transforms.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { tfExpanded = false },
                    )
                }
            }
        }

        // Output colour space
        Box {
            OutlinedTextField(
                value = params.outputColorSpace,
                onValueChange = {},
                readOnly = true,
                label = { Text(s.outputColorSpace) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = { TextButton(onClick = { csExpanded = true }) { Text("▾") } },
            )
            DropdownMenu(expanded = csExpanded, onDismissRequest = { csExpanded = false }) {
                colorSpaces.forEach { cs ->
                    DropdownMenuItem(
                        text = { Text(cs) },
                        onClick = { csExpanded = false },
                    )
                }
            }
        }

        // EOTF
        Box {
            OutlinedTextField(
                value = params.displayEotf,
                onValueChange = {},
                readOnly = true,
                label = { Text(s.eotf) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = { TextButton(onClick = { eotfExpanded = true }) { Text("▾") } },
            )
            DropdownMenu(expanded = eotfExpanded, onDismissRequest = { eotfExpanded = false }) {
                eotfs.forEach { eotf ->
                    DropdownMenuItem(
                        text = { Text(eotf) },
                        onClick = { eotfExpanded = false },
                    )
                }
            }
        }

        SectionHeader(title = s.peakLuminance)
        AdjustmentSlider(
            label = s.peakLuminance,
            value = params.peakLuminanceNits.toFloat(),
            defaultValue = 100f,
            range = 100f..1000f,
            valueFormatter = { "%.0f nits".format(it) },
            onValueChange = { onUpdate("peakLuminanceNits", it) },
        )
        Text(
            text = colorSpaceNote(params.outputColorSpace),
            style = MaterialTheme.typography.labelSmall,
            color = AlcedoColors.TextTertiary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun colorSpaceNote(cs: String): String =
    when (cs) {
        "Display P3" -> "Wide gamut (P3 primaries)."
        "Rec2020" -> "Ultra-wide gamut (Rec.2020)."
        "AdobeRGB" -> "Wide gamut (Adobe RGB)."
        else -> "Standard gamut (sRGB)."
    }
