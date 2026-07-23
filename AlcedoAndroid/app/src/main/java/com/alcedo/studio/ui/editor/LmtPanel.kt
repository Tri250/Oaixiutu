package com.alcedo.studio.ui.editor

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.alcedo.studio.data.model.PipelineParams
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.HapticFeedback
import com.alcedo.studio.ui.common.LiquidGlassSurface
import com.alcedo.studio.ui.theme.AlcedoIconSize
import com.alcedo.studio.ui.theme.AlcedoRadius
import com.alcedo.studio.ui.theme.AlcedoSpacing
import com.alcedo.studio.viewmodel.EditorViewModel

/**
 * Software-based built-in LUT look presets that modify PipelineParams to
 * simulate classic LUT effects without requiring external .cube files.
 */
private data class BuiltinLutPreset(
    val id: String,
    val nameKey: StringResources.() -> String,
    val color: androidx.compose.ui.graphics.Color,
    val params: PipelineParams
)

private val BUILTIN_LUT_PRESETS = listOf(
    BuiltinLutPreset(
        id = "cinematic_teal_orange",
        nameKey = { lmtPresetCinematicTeal },
        color = androidx.compose.ui.graphics.Color(0xFF2A9D8F),
        params = PipelineParams(
            contrast = 0.18f,
            saturation = 0.1f,
            vibrance = 0.15f,
            highlights = -0.15f,
            shadows = 0.1f,
            whiteBalanceTemp = 6800f,
            whiteBalanceTint = 6f,
            tintHighlightHue = 30f,
            tintHighlightStrength = 0.22f,
            tintShadowHue = 195f,
            tintShadowStrength = 0.28f,
            tintBalance = 0.1f,
            clarityAmount = 0.15f,
            sigmoidContrast = 0.12f
        )
    ),
    BuiltinLutPreset(
        id = "vintage_film",
        nameKey = { lmtPresetVintageFilm },
        color = androidx.compose.ui.graphics.Color(0xFFC97B3A),
        params = PipelineParams(
            contrast = -0.12f,
            saturation = -0.22f,
            vibrance = -0.1f,
            shadows = 0.18f,
            highlights = -0.1f,
            whiteBalanceTemp = 7200f,
            whiteBalanceTint = 8f,
            filmGrainIntensity = 0.18f,
            halationIntensity = 0.25f,
            halationThreshold = 0.82f,
            halationRedBias = 0.65f,
            tintHighlightHue = 35f,
            tintHighlightStrength = 0.12f,
            tintShadowHue = 210f,
            tintShadowStrength = 0.08f,
            lensVignetteStrength = 0.25f
        )
    ),
    BuiltinLutPreset(
        id = "bw_high_contrast",
        nameKey = { lmtPresetBwContrast },
        color = androidx.compose.ui.graphics.Color(0xFF4A4A4A),
        params = PipelineParams(
            contrast = 0.45f,
            saturation = -1f,
            vibrance = -1f,
            highlights = -0.2f,
            shadows = -0.15f,
            clarityAmount = 0.3f,
            sharpenAmount = 0.2f,
            channelMixerMonochrome = true,
            channelMixerMatrix = floatArrayOf(
                0.3f, 0.59f, 0.11f,
                0.3f, 0.59f, 0.11f,
                0.3f, 0.59f, 0.11f
            ),
            sigmoidContrast = 0.2f
        )
    ),
    BuiltinLutPreset(
        id = "moody_blue",
        nameKey = { lmtPresetMoodyBlue },
        color = androidx.compose.ui.graphics.Color(0xFF3A6B9F),
        params = PipelineParams(
            contrast = 0.2f,
            saturation = -0.18f,
            vibrance = -0.05f,
            shadows = -0.1f,
            highlights = -0.15f,
            whiteBalanceTemp = 5200f,
            whiteBalanceTint = -10f,
            exposure = -0.15f,
            tintShadowHue = 210f,
            tintShadowStrength = 0.25f,
            tintHighlightHue = 200f,
            tintHighlightStrength = 0.1f,
            colorWheelLiftB = 0.04f,
            colorWheelLiftR = -0.03f,
            clarityAmount = 0.1f
        )
    ),
    BuiltinLutPreset(
        id = "warm_sunset",
        nameKey = { lmtPresetWarmSunset },
        color = androidx.compose.ui.graphics.Color(0xFFE07A3A),
        params = PipelineParams(
            contrast = 0.1f,
            saturation = 0.18f,
            vibrance = 0.25f,
            highlights = -0.08f,
            shadows = 0.1f,
            whiteBalanceTemp = 7800f,
            whiteBalanceTint = 12f,
            exposure = 0.12f,
            tintHighlightHue = 32f,
            tintHighlightStrength = 0.2f,
            tintShadowHue = 25f,
            tintShadowStrength = 0.1f,
            halationIntensity = 0.12f,
            halationRedBias = 0.8f
        )
    ),
    BuiltinLutPreset(
        id = "desaturated_fade",
        nameKey = { lmtPresetDesaturated },
        color = androidx.compose.ui.graphics.Color(0xFF8E8E8E),
        params = PipelineParams(
            contrast = -0.2f,
            saturation = -0.3f,
            vibrance = -0.1f,
            shadows = 0.22f,
            highlights = -0.05f,
            whiteBalanceTemp = 7000f,
            whiteBalanceTint = 6f,
            exposure = 0.1f,
            tintHighlightHue = 40f,
            tintHighlightStrength = 0.1f,
            tintShadowHue = 220f,
            tintShadowStrength = 0.05f,
            filmGrainIntensity = 0.1f,
            halationIntensity = 0.08f
        )
    ),
    BuiltinLutPreset(
        id = "film_grain_classic",
        nameKey = { lmtPresetFilmGrain },
        color = androidx.compose.ui.graphics.Color(0xFF9E8A6E),
        params = PipelineParams(
            contrast = 0.05f,
            saturation = -0.15f,
            vibrance = -0.05f,
            shadows = 0.08f,
            whiteBalanceTemp = 6600f,
            whiteBalanceTint = 4f,
            filmGrainIntensity = 0.32f,
            halationIntensity = 0.18f,
            halationThreshold = 0.8f,
            halationSpread = 12f,
            halationRedBias = 0.6f,
            tintShadowHue = 220f,
            tintShadowStrength = 0.06f,
            lensVignetteStrength = 0.18f
        )
    ),
    BuiltinLutPreset(
        id = "hdr_natural",
        nameKey = { lmtPresetHdrNatural },
        color = androidx.compose.ui.graphics.Color(0xFF5CB670),
        params = PipelineParams(
            contrast = 0.35f,
            saturation = 0.2f,
            vibrance = 0.22f,
            highlights = -0.35f,
            shadows = 0.45f,
            midtones = 0.1f,
            clarityAmount = 0.4f,
            sharpenAmount = 0.15f,
            sigmoidContrast = 0.15f,
            whiteBalanceTemp = 6500f
        )
    )
)

@Composable
fun LmtPanel(
    onLmtChanged: (Boolean, String, Float) -> Unit,
    lmtEnabled: Boolean,
    lmtPath: String,
    lmtIntensity: Float,
    modifier: Modifier = Modifier,
    onApplyBuiltinPreset: ((PipelineParams) -> Unit)? = null
) {
    val context = LocalContext.current
    val view = LocalView.current

    val lutPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val realPath = copyLutToInternal(context, uri)
        if (realPath != null) {
            onLmtChanged(true, realPath, lmtIntensity)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AlcedoSpacing.md)
    ) {
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringRes { lmtTitle },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = lmtEnabled,
                        onCheckedChange = {
                            HapticFeedback.click(view)
                            onLmtChanged(it, lmtPath, lmtIntensity)
                        }
                    )
                }

                if (lmtEnabled) {
                    Spacer(modifier = Modifier.height(AlcedoSpacing.sm))

                    // Import LUT button
                    OutlinedButton(
                        onClick = {
                            HapticFeedback.click(view)
                            lutPickerLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(AlcedoIconSize.sm))
                        Spacer(modifier = Modifier.width(AlcedoSpacing.sm))
                        Text(stringRes { lmtImportLut })
                    }

                    // Active LUT path display
                    if (lmtPath.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringRes { lmtActiveLut },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(AlcedoSpacing.xs))
                            Text(
                                lmtPath.substringAfterLast('/'),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Intensity slider
                    Spacer(modifier = Modifier.height(AlcedoSpacing.sm))
                    AdjustmentSlider(
                        label = stringRes { this.lmtIntensity },
                        value = lmtIntensity.coerceIn(0f, 100f),
                        range = 0f..100f,
                        onValueChange = { onLmtChanged(lmtEnabled, lmtPath, it) },
                        defaultValue = 100f,
                        valueDisplayTransform = { "${it.toInt()}%" }
                    )
                }
            }
        }

        // Built-in look presets (software-based, no .cube files required)
        if (onApplyBuiltinPreset != null) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                    Text(
                        stringRes { lmtBuiltInPresets },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                    Text(
                        stringRes { lmtBuiltInPresetsDesc },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(AlcedoSpacing.sm))

                    // Preset chips in a scrollable row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(AlcedoSpacing.sm)
                    ) {
                        BUILTIN_LUT_PRESETS.forEach { preset ->
                            BuiltinPresetChip(
                                preset = preset,
                                onClick = {
                                    HapticFeedback.click(view)
                                    onApplyBuiltinPreset(preset.params)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuiltinPresetChip(
    preset: BuiltinLutPreset,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(AlcedoRadius.sm))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(AlcedoRadius.sm),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .width(80.dp)
                .padding(AlcedoSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Color swatch
            Box(
                modifier = Modifier
                    .size(AlcedoIconSize.xl + 12.dp)
                    .clip(RoundedCornerShape(AlcedoRadius.xs))
                    .background(preset.color)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        RoundedCornerShape(AlcedoRadius.xs)
                    )
            )
            Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
            Text(
                text = stringRes(preset.nameKey),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// Need StringResources import for the nameKey type alias
private typealias StringResources = com.alcedo.studio.i18n.StringResources
