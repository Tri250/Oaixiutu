package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.PipelineParams
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.SectionHeader

enum class OdtMethod { ACES, OPENDRT }

enum class OutputColorSpace { SRGB, P3, REC2020 }

enum class OpenDrtLook(val label: String) {
    STANDARD("Standard"),
    ARRIBA("Arriba"),
    SYLVAN("Sylvan"),
    COLORFUL("Colorful"),
    AERY("Aery"),
    DYSTOPIC("Dystopic"),
    UMBRA("Umbra")
}

enum class OpenDrtTonescale(val label: String) {
    STANDARD("Standard"),
    SOFT("Soft"),
    MEDIUM("Medium"),
    HARD("Hard"),
    FILMIC("Filmic")
}

enum class CreativeWhitePoint(val label: String, val cct: Int) {
    D93("D93", 9300),
    D75("D75", 7500),
    D65("D65", 6500),
    D60("D60", 6000),
    D55("D55", 5500),
    D50("D50", 5000)
}

@Composable
fun DisplayTransformPanel(
    params: PipelineParams,
    onParamsChanged: (PipelineParams) -> Unit,
    modifier: Modifier = Modifier
) {
    var odtMethod by remember { mutableStateOf(OdtMethod.ACES) }
    var outputColorSpace by remember { mutableStateOf(OutputColorSpace.SRGB) }
    var openDrtLook by remember { mutableStateOf(OpenDrtLook.STANDARD) }
    var openDrtTonescale by remember { mutableStateOf(OpenDrtTonescale.STANDARD) }
    var creativeWhitePoint by remember { mutableStateOf(CreativeWhitePoint.D65) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ODT Method
        SectionHeader(title = "ODT Method") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OdtMethod.entries.forEach { method ->
                        FilterChip(
                            selected = odtMethod == method,
                            onClick = { odtMethod = method },
                            label = { Text(method.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }

        // Output Color Space
        SectionHeader(title = "Output Color Space") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutputColorSpace.entries.forEach { cs ->
                        FilterChip(
                            selected = outputColorSpace == cs,
                            onClick = { outputColorSpace = cs },
                            label = { Text(cs.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }

        // Peak Luminance
        SectionHeader(title = "Peak Luminance") {
            AdjustmentSlider(
                label = "Peak Luminance",
                value = params.displayTransform.peakLuminance,
                range = 50f..1000f,
                onValueChange = {
                    onParamsChanged(
                        params.copy(
                            displayTransform = params.displayTransform.copy(peakLuminance = it)
                        )
                    )
                },
                defaultValue = 100f,
                valueDisplayTransform = { "${it.toInt()} nits" }
            )
        }

        // OpenDRT settings (only shown when ODT method is OpenDRT)
        if (odtMethod == OdtMethod.OPENDRT) {
            SectionHeader(title = "OpenDRT Look") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OpenDrtLook.entries.forEach { look ->
                            FilterChip(
                                selected = openDrtLook == look,
                                onClick = { openDrtLook = look },
                                label = { Text(look.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }

            SectionHeader(title = "OpenDRT Tonescale") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OpenDrtTonescale.entries.forEach { ts ->
                            FilterChip(
                                selected = openDrtTonescale == ts,
                                onClick = { openDrtTonescale = ts },
                                label = { Text(ts.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }

        // Creative White Point
        SectionHeader(title = "Creative White Point") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CreativeWhitePoint.entries.forEach { wp ->
                        FilterChip(
                            selected = creativeWhitePoint == wp,
                            onClick = {
                                creativeWhitePoint = wp
                                onParamsChanged(
                                    params.copy(whiteBalanceTemp = wp.cct.toFloat())
                                )
                            },
                            label = {
                                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                    Text(wp.label, style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        "${wp.cct}K",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
