package com.alcedo.studio.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import com.alcedo.studio.data.model.PipelineParams
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.HapticFeedback
import com.alcedo.studio.ui.common.SectionHeader
import com.alcedo.studio.ui.theme.AlcedoAnimation
import com.alcedo.studio.ui.theme.AlcedoIconSize
import com.alcedo.studio.ui.theme.AlcedoSpacing

private val HUE_PROFILE_COLORS = listOf(
    Color(0xFFE53935), // Red
    Color(0xFFFF9800), // Orange
    Color(0xFFFFEB3B), // Yellow
    Color(0xFF4CAF50), // Green
    Color(0xFF00BCD4), // Cyan
    Color(0xFF2196F3), // Blue
    Color(0xFF9C27B0), // Purple
    Color(0xFFE91E63)  // Magenta
)

@Composable
private fun hueProfileNames(): List<String> = listOf(
    stringRes { editorColorRed },
    stringRes { editorColorOrange },
    stringRes { editorColorYellow },
    stringRes { editorColorGreen },
    stringRes { editorColorCyan },
    stringRes { editorColorBlue },
    stringRes { editorColorPurple },
    stringRes { editorColorMagenta }
)

@Composable
fun HlsProfilePanel(
    params: PipelineParams,
    onParamsChanged: (PipelineParams) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedProfile by remember { mutableIntStateOf(0) }
    val profileNames = hueProfileNames()
    val view = LocalView.current
    val alcedoColors = LocalAlcedoColors.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AlcedoSpacing.md)
    ) {
        // Hue ring indicator
        SectionHeader(title = stringRes { hlsProfileTitle }) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AlcedoSpacing.sm)
            ) {
                // Visual hue ring
                HueRingIndicator(
                    selectedProfile = selectedProfile,
                    hueRanges = params.hslHueRanges,
                    hueWidth = params.hslHueWidth,
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .aspectRatio(1f)
                )

                // 8 hue profile buttons in circular arrangement
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(AlcedoSpacing.xs)
                ) {
                    profileNames.forEachIndexed { index, name ->
                        val isSelected = selectedProfile == index
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                HapticFeedback.click(view)
                                selectedProfile = index
                            },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(AlcedoSpacing.xs)
                                ) {
                                    Canvas(modifier = Modifier.size(AlcedoIconSize.xs)) {
                                        drawCircle(color = HUE_PROFILE_COLORS[index])
                                    }
                                    Text(
                                        name,
                                        style = AlcedoFontRoles.uiOverline
                                    )
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = HUE_PROFILE_COLORS[index].copy(alpha = 0.2f),
                                selectedLabelColor = HUE_PROFILE_COLORS[index]
                            )
                        )
                    }
                }
            }
        }

        // Per-profile adjustments
        SectionHeader(title = profileNames[selectedProfile]) {
            Column(verticalArrangement = Arrangement.spacedBy(AlcedoSpacing.sm)) {
                // Hue shift
                AdjustmentSlider(
                    label = stringRes { hlsHueShift },
                    value = params.hslHueShift[selectedProfile],
                    range = -180f..180f,
                    onValueChange = {
                        val newArr = params.hslHueShift.clone()
                        newArr[selectedProfile] = it
                        onParamsChanged(params.copy(hslHueShift = newArr))
                    },
                    defaultValue = 0f,
                    valueDisplayTransform = { "%.0f°".format(it) }
                )

                // Lightness
                AdjustmentSlider(
                    label = stringRes { hlsLightness },
                    value = params.hslLuminanceScale[selectedProfile],
                    range = 0f..2f,
                    onValueChange = {
                        val newArr = params.hslLuminanceScale.clone()
                        newArr[selectedProfile] = it
                        onParamsChanged(params.copy(hslLuminanceScale = newArr))
                    },
                    defaultValue = 1f
                )

                // Saturation
                AdjustmentSlider(
                    label = stringRes { hlsSaturation },
                    value = params.hslSaturationScale[selectedProfile],
                    range = 0f..2f,
                    onValueChange = {
                        val newArr = params.hslSaturationScale.clone()
                        newArr[selectedProfile] = it
                        onParamsChanged(params.copy(hslSaturationScale = newArr))
                    },
                    defaultValue = 1f
                )
            }
        }

        // Hue range per profile
        SectionHeader(title = stringRes { hlsHueRange }) {
            Column(verticalArrangement = Arrangement.spacedBy(AlcedoSpacing.sm)) {
                AdjustmentSlider(
                    label = stringRes { hlsHueRange },
                    value = params.hslHueWidth,
                    range = 10f..180f,
                    onValueChange = {
                        onParamsChanged(params.copy(hslHueWidth = it))
                    },
                    defaultValue = 60f,
                    valueDisplayTransform = { "%.0f°".format(it) }
                )

                // Summary of all profiles
                profileNames.forEachIndexed { index, name ->
                    val hueShift = params.hslHueShift[index]
                    val satScale = params.hslSaturationScale[index]
                    val lumScale = params.hslLuminanceScale[index]
                    val isModified = hueShift != 0f || satScale != 1f || lumScale != 1f

                    if (isModified) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AlcedoSpacing.sm)
                        ) {
                            Canvas(modifier = Modifier.size(AlcedoIconSize.xs)) {
                                drawCircle(color = HUE_PROFILE_COLORS[index])
                            }
                            Text(
                                name,
                                style = AlcedoFontRoles.uiCaption,
                                color = alcedoColors.text
                            )
                            Text(
                                "H:${"%.0f".format(hueShift)}° " +
                                    "S:${"%.1f".format(satScale)} " +
                                    "L:${"%.1f".format(lumScale)}",
                                style = AlcedoFontRoles.uiCaption,
                                color = alcedoColors.textMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HueRingIndicator(
    selectedProfile: Int,
    hueRanges: FloatArray,
    hueWidth: Float,
    modifier: Modifier = Modifier
) {
    val surfaceContainerColor = alcedoColors.surfaceContainer
    val onSurfaceColor = alcedoColors.text
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = size.width / 2f * 0.9f
        val innerRadius = outerRadius * 0.65f

        // Draw hue ring
        val segments = 360
        val sweepAngle = 360f / segments
        for (i in 0 until segments) {
            val hue = i.toFloat()
            val color = Color.hsv(hue, 1f, 1f)
            drawArc(
                color = color,
                startAngle = hue - sweepAngle / 2f - 90f,
                sweepAngle = sweepAngle,
                useCenter = true,
                topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                size = Size(outerRadius * 2, outerRadius * 2)
            )
        }

        // Clear inner circle
        drawCircle(
            color = surfaceContainerColor,
            radius = innerRadius,
            center = center
        )

        // Highlight selected profile range on the ring
        val profileAngle = hueRanges.getOrElse(selectedProfile) { selectedProfile * 45f }
        val halfWidth = hueWidth / 2f
        val startAngle = profileAngle - halfWidth
        val endAngle = profileAngle + halfWidth

        // Draw highlighted arc for selected profile
        drawArc(
            color = HUE_PROFILE_COLORS[selectedProfile].copy(alpha = 0.4f),
            startAngle = startAngle - 90f,
            sweepAngle = endAngle - startAngle,
            useCenter = true,
            topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
            size = Size(outerRadius * 2, outerRadius * 2)
        )
        drawCircle(
            color = surfaceContainerColor,
            radius = innerRadius,
            center = center
        )

        // Ring borders
        drawCircle(
            color = onSurfaceColor.copy(alpha = 0.2f),
            radius = outerRadius,
            center = center,
            style = Stroke(width = 1f)
        )
        drawCircle(
            color = onSurfaceColor.copy(alpha = 0.15f),
            radius = innerRadius,
            center = center,
            style = Stroke(width = 1f)
        )

        // Profile indicator markers on the ring
        for (i in HUE_PROFILE_COLORS.indices) {
            val angle = Math.toRadians((hueRanges.getOrElse(i) { i * 45f } - 90f).toDouble())
            val midRadius = (outerRadius + innerRadius) / 2f
            val markerX = center.x + midRadius * kotlin.math.cos(angle).toFloat()
            val markerY = center.y + midRadius * kotlin.math.sin(angle).toFloat()

            drawCircle(
                color = if (i == selectedProfile) onSurfaceColor else HUE_PROFILE_COLORS[i],
                radius = if (i == selectedProfile) 6f else 4f,
                center = Offset(markerX, markerY),
                style = if (i == selectedProfile) Stroke(width = 2f) else Stroke(width = 1f)
            )
        }
    }
}
