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
 * transform (OpenDRT / ACES 2.0), EOTF, peak luminance, and surround.
 * These set how the pipeline maps scene-referred linear into the display-referred
 * preview.
 */
@Composable
fun DisplayTransformPanel(
    params: AdjustmentParams,
    onUpdate: (String, Float) -> Unit,
    onUpdateString: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val s = Strings.res
    var tfExpanded by remember { mutableStateOf(false) }
    var csExpanded by remember { mutableStateOf(false) }
    var eotfExpanded by remember { mutableStateOf(false) }
    var surroundExpanded by remember { mutableStateOf(false) }

    val transforms = listOf("OpenDRT" to s.openDrt, "ACES2" to s.aces2)
    val colorSpaces = listOf(
        "sRGB",
        "Rec.709",
        "Display P3",
        "P3-D65",
        "P3-D60",
        "P3-DCI",
        "XYZ",
        "Rec.2020",
        "AdobeRGB",
    )
    val eotfs = listOf(
        "sRGB" to "sRGB",
        "BT.1886" to "BT.1886",
        "Gamma 2.2" to "Gamma 2.2",
        "ST 2084 PQ" to "ST 2084 PQ",
        "HLG" to "HLG",
        "Gamma 2.6" to "Gamma 2.6",
    )
    val surrounds = listOf(
        "Dim" to s.surroundDim,
        "Average" to s.surroundAverage,
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.controlSpacing)) {
        SectionHeader(title = s.displayTransform)

        // Display transform (OpenDRT / ACES 2.0)
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
                        onClick = {
                            onUpdateString("displayTransform", value)
                            tfExpanded = false
                        },
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
                        onClick = {
                            onUpdateString("outputColorSpace", cs)
                            csExpanded = false
                        },
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
                eotfs.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onUpdateString("displayEotf", value)
                            eotfExpanded = false
                        },
                    )
                }
            }
        }

        // Surround
        Box {
            OutlinedTextField(
                value = s.surroundDim,
                onValueChange = {},
                readOnly = true,
                label = { Text(s.surround) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = { TextButton(onClick = { surroundExpanded = true }) { Text("▾") } },
            )
            DropdownMenu(expanded = surroundExpanded, onDismissRequest = { surroundExpanded = false }) {
                surrounds.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onUpdateString("surround", value)
                            surroundExpanded = false
                        },
                    )
                }
            }
        }

        SectionHeader(title = s.peakLuminance)
        AdjustmentSlider(
            label = s.peakLuminance,
            value = params.peakLuminanceNits.toFloat(),
            defaultValue = 100f,
            range = 100f..10000f,
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
        "Display P3", "P3-D65" -> "Wide gamut (P3-D65 primaries)."
        "P3-D60" -> "Wide gamut (P3-D60 primaries, cinema white point)."
        "P3-DCI" -> "Wide gamut (DCI-P3, D63 white point)."
        "XYZ" -> "CIE XYZ (connection space, no gamut limitation)."
        "Rec.2020" -> "Ultra-wide gamut (Rec.2020)."
        "Rec.709" -> "Standard gamut (Rec.709, same as sRGB primaries)."
        "AdobeRGB" -> "Wide gamut (Adobe RGB)."
        else -> "Standard gamut (sRGB)."
    }
