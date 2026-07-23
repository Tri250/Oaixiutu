package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.*
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.HapticFeedback
import com.alcedo.studio.ui.common.SectionHeader
import com.alcedo.studio.ui.theme.AlcedoSpacing

enum class OdtMethod { ACES, OPENDRT }

enum class OutputColorSpace { SRGB, P3, REC2020 }

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
    val odtMethod = when (params.displayTransform.colorScience) {
        ColorScience.OPENDRT -> OdtMethod.OPENDRT
        else -> OdtMethod.ACES
    }

    val outputColorSpace = when (params.displayTransform.displayColorSpace) {
        ColorSpace.DISPLAY_P3 -> OutputColorSpace.P3
        ColorSpace.REC2020 -> OutputColorSpace.REC2020
        else -> OutputColorSpace.SRGB
    }

    val openDrtLook = params.displayTransform.openDrtLook
    val openDrtTonescale = params.displayTransform.openDrtTonescale
    var creativeWhitePoint by remember { mutableStateOf(CreativeWhitePoint.D65) }
    val view = LocalView.current

    Column(
        modifier = modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AlcedoSpacing.md)
    ) {
        // ODT Method
        SectionHeader(title = stringRes { displayTransformOdtMethod }) {
            Column(verticalArrangement = Arrangement.spacedBy(AlcedoSpacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(AlcedoSpacing.xs)) {
                    OdtMethod.entries.forEach { method ->
                        FilterChip(
                            selected = odtMethod == method,
                            onClick = {
                                HapticFeedback.click(view)
                                val newColorScience = when (method) {
                                    OdtMethod.ACES -> ColorScience.ACES20
                                    OdtMethod.OPENDRT -> ColorScience.OPENDRT
                                }
                                onParamsChanged(
                                    params.copy(
                                        displayTransform = params.displayTransform.copy(
                                            colorScience = newColorScience
                                        )
                                    )
                                )
                            },
                            label = { Text(method.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }

        // Output Color Space
        SectionHeader(title = stringRes { displayTransformOutputColorSpace }) {
            Column(verticalArrangement = Arrangement.spacedBy(AlcedoSpacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(AlcedoSpacing.xs)) {
                    OutputColorSpace.entries.forEach { cs ->
                        FilterChip(
                            selected = outputColorSpace == cs,
                            onClick = {
                                HapticFeedback.click(view)
                                val newColorSpace = when (cs) {
                                    OutputColorSpace.SRGB -> ColorSpace.SRGB
                                    OutputColorSpace.P3 -> ColorSpace.DISPLAY_P3
                                    OutputColorSpace.REC2020 -> ColorSpace.REC2020
                                }
                                onParamsChanged(
                                    params.copy(
                                        displayTransform = params.displayTransform.copy(
                                            displayColorSpace = newColorSpace
                                        )
                                    )
                                )
                            },
                            label = { Text(cs.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }

        // Peak Luminance
        SectionHeader(title = stringRes { displayTransformPeakLuminance }) {
            AdjustmentSlider(
                label = stringRes { displayTransformPeakLuminance },
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
            SectionHeader(title = stringRes { displayTransformOpenDrtLook }) {
                Column(verticalArrangement = Arrangement.spacedBy(AlcedoSpacing.sm)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AlcedoSpacing.xs)
                    ) {
                        com.alcedo.studio.data.model.OpenDrtLook.entries.forEach { look ->
                            FilterChip(
                                selected = openDrtLook == look,
                                onClick = {
                                    HapticFeedback.click(view)
                                    onParamsChanged(
                                        params.copy(
                                            displayTransform = params.displayTransform.copy(
                                                openDrtLook = look
                                            )
                                        )
                                    )
                                },
                                label = { Text(look.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }

            SectionHeader(title = stringRes { displayTransformOpenDrtTonescale }) {
                Column(verticalArrangement = Arrangement.spacedBy(AlcedoSpacing.sm)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AlcedoSpacing.xs)
                    ) {
                        com.alcedo.studio.data.model.OpenDrtTonescale.entries.forEach { ts ->
                            FilterChip(
                                selected = openDrtTonescale == ts,
                                onClick = {
                                    HapticFeedback.click(view)
                                    onParamsChanged(
                                        params.copy(
                                            displayTransform = params.displayTransform.copy(
                                                openDrtTonescale = ts
                                            )
                                        )
                                    )
                                },
                                label = { Text(ts.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }

        // Creative White Point
        SectionHeader(title = stringRes { displayTransformCreativeWhitePoint }) {
            Column(verticalArrangement = Arrangement.spacedBy(AlcedoSpacing.sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AlcedoSpacing.xs)
                ) {
                    CreativeWhitePoint.entries.forEach { wp ->
                        FilterChip(
                            selected = creativeWhitePoint == wp,
                            onClick = {
                                HapticFeedback.click(view)
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
