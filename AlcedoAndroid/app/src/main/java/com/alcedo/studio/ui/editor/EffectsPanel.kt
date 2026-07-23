package com.alcedo.studio.ui.editor

import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.HapticFeedback
import com.alcedo.studio.ui.common.LiquidGlassSurface
import com.alcedo.studio.ui.theme.AlcedoAnimation
import com.alcedo.studio.ui.theme.AlcedoIconSize
import com.alcedo.studio.ui.theme.AlcedoSpacing
import com.alcedo.studio.viewmodel.EditorViewModel

@Composable
fun EffectsPanel(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
    focusMode: FocusModeState = FocusModeState()
) {
    val params by remember { viewModel.params }
    val context = LocalContext.current
    val view = LocalView.current

    val lutPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val realPath = copyLutToInternalStorage(context, uri)
        if (realPath != null) {
            viewModel.updateLut(true, realPath)
        }
    }

    // 专注模式下用于切换活跃小节的小节标签
    val focusSections = listOf(
        "effects.luma_denoise" to stringRes { editorSectionLuminanceDenoise },
        "effects.chroma_denoise" to stringRes { editorSectionChromaDenoise },
        "effects.grain" to stringRes { editorSectionFilmGrain },
        "effects.halation" to stringRes { editorSectionHalation },
        "effects.sharpen" to stringRes { editorSectionSharpen },
        "effects.clarity" to stringRes { editorSectionClarity },
        "effects.vignette" to stringRes { editorSectionVignette },
        "effects.lut" to stringRes { editorSectionLut }
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AlcedoSpacing.md)
    ) {
        FocusSectionChips(focusMode = focusMode, sections = focusSections)

        // ── Luminance Denoise ──
        if (focusMode.shouldShowSection("effects.luma_denoise")) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringRes { editorSectionLuminanceDenoise },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = {
                                HapticFeedback.heavyClick(view)
                                viewModel.updateLuminanceDenoise(0f, 0.5f)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringRes { effectsResetLumaDenoise },
                                modifier = Modifier.size(AlcedoIconSize.sm),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                    AdjustmentSlider(
                        label = stringRes { editorStrength },
                        value = params.luminanceDenoiseStrength,
                        range = 0f..1f,
                        onValueChange = { viewModel.updateLuminanceDenoiseStrengthDrag(it) },
                        onValueChangeFinished = { viewModel.updateLuminanceDenoise(params.luminanceDenoiseStrength, params.luminanceDenoiseDetail) },
                        defaultValue = 0f
                    )
                    AdjustmentSlider(
                        label = stringRes { editorDetailPreserve },
                        value = params.luminanceDenoiseDetail,
                        range = 0f..1f,
                        onValueChange = { viewModel.updateLuminanceDenoiseDetailDrag(it) },
                        onValueChangeFinished = { viewModel.updateLuminanceDenoise(params.luminanceDenoiseStrength, params.luminanceDenoiseDetail) },
                        defaultValue = 0.5f
                    )
                }
            }
        }

        // ── Chroma Denoise ──
        if (focusMode.shouldShowSection("effects.chroma_denoise")) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringRes { editorSectionChromaDenoise },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = {
                                HapticFeedback.heavyClick(view)
                                viewModel.updateChromaDenoise(0f, 0.5f)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringRes { effectsResetChromaDenoise },
                                modifier = Modifier.size(AlcedoIconSize.sm),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                    AdjustmentSlider(
                        label = stringRes { editorStrength },
                        value = params.chromaDenoiseStrength,
                        range = 0f..1f,
                        onValueChange = { viewModel.updateChromaDenoiseStrengthDrag(it) },
                        onValueChangeFinished = { viewModel.updateChromaDenoise(params.chromaDenoiseStrength, params.chromaDenoiseThreshold) },
                        defaultValue = 0f
                    )
                    AdjustmentSlider(
                        label = stringRes { editorColorThreshold },
                        value = params.chromaDenoiseThreshold,
                        range = 0f..1f,
                        onValueChange = { viewModel.updateChromaDenoiseThresholdDrag(it) },
                        onValueChangeFinished = { viewModel.updateChromaDenoise(params.chromaDenoiseStrength, params.chromaDenoiseThreshold) },
                        defaultValue = 0.5f
                    )
                }
            }
        }

        // ── Film Grain ─────────────────────────────────────────────
        if (focusMode.shouldShowSection("effects.grain")) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringRes { editorSectionFilmGrain },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = {
                                HapticFeedback.heavyClick(view)
                                viewModel.updateFilmGrain(0f)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringRes { effectsResetGrain },
                                modifier = Modifier.size(AlcedoIconSize.sm),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                AdjustmentSlider(
                    label = stringRes { editorIntensity },
                    value = params.filmGrainIntensity,
                    range = 0f..1f,
                    onValueChange = { viewModel.updateFilmGrainDrag(it) },
                    onValueChangeFinished = { viewModel.updateFilmGrain(params.filmGrainIntensity) },
                    defaultValue = 0f
                )
            }
        }
        }

        // ── Halation ──────────────────────────────────────────────
        if (focusMode.shouldShowSection("effects.halation")) {
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringRes { editorSectionHalation },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = {
                            HapticFeedback.heavyClick(view)
                            viewModel.updateHalation(0f, 0.8f, 10f, 0.7f)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringRes { effectsResetHalation },
                            modifier = Modifier.size(AlcedoIconSize.sm),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                AdjustmentSlider(
                    label = stringRes { editorIntensity },
                    value = params.halationIntensity,
                    range = 0f..1f,
                    onValueChange = { viewModel.updateHalationIntensityDrag(it) },
                    onValueChangeFinished = {
                        viewModel.updateHalation(
                            params.halationIntensity, params.halationThreshold,
                            params.halationSpread, params.halationRedBias
                        )
                    },
                    defaultValue = 0f
                )
                AdjustmentSlider(
                    label = stringRes { editorSpread },
                    value = params.halationSpread,
                    range = 0f..50f,
                    onValueChange = { viewModel.updateHalationSpreadDrag(it) },
                    onValueChangeFinished = {
                        viewModel.updateHalation(
                            params.halationIntensity, params.halationThreshold,
                            params.halationSpread, params.halationRedBias
                        )
                    },
                    defaultValue = 10f,
                    valueDisplayTransform = { "%.0f".format(it) }
                )
                AdjustmentSlider(
                    label = stringRes { editorThreshold },
                    value = params.halationThreshold,
                    range = 0f..1f,
                    onValueChange = { viewModel.updateHalationThresholdDrag(it) },
                    onValueChangeFinished = {
                        viewModel.updateHalation(
                            params.halationIntensity, params.halationThreshold,
                            params.halationSpread, params.halationRedBias
                        )
                    },
                    defaultValue = 0.8f
                )
                AdjustmentSlider(
                    label = stringRes { editorRedBias },
                    value = params.halationRedBias,
                    range = 0f..1f,
                    onValueChange = { viewModel.updateHalationRedBiasDrag(it) },
                    onValueChangeFinished = {
                        viewModel.updateHalation(
                            params.halationIntensity, params.halationThreshold,
                            params.halationSpread, params.halationRedBias
                        )
                    },
                    defaultValue = 0.7f
                )
            }
        }
        }

        // ── Sharpen ───────────────────────────────────────────────
        if (focusMode.shouldShowSection("effects.sharpen")) {
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringRes { editorSectionSharpen },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = {
                            HapticFeedback.heavyClick(view)
                            viewModel.updateSharpen(0f)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringRes { effectsResetSharpen },
                            modifier = Modifier.size(AlcedoIconSize.sm),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                AdjustmentSlider(
                    label = stringRes { editorAmount },
                    value = params.sharpenAmount,
                    range = 0f..2f,
                    onValueChange = { viewModel.updateSharpenDrag(it) },
                    onValueChangeFinished = { viewModel.updateSharpen(params.sharpenAmount) },
                    defaultValue = 0f
                )
                // P2-8 锐化蒙版可视化：切换显示边缘蒙版叠加层
                val showSharpeningMask by viewModel.showSharpeningMask.collectAsStateWithLifecycle()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            HapticFeedback.click(view)
                            viewModel.setShowSharpeningMask(!showSharpeningMask)
                        }
                        .padding(top = AlcedoSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (showSharpeningMask) Icons.Default.Visibility
                        else Icons.Default.VisibilityOff,
                        contentDescription = stringRes { effectsShowSharpeningMask },
                        tint = if (showSharpeningMask) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(AlcedoIconSize.md)
                    )
                    Spacer(modifier = Modifier.width(AlcedoSpacing.sm))
                    Text(
                        text = stringRes { effectsShowSharpeningMask },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (showSharpeningMask) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        }

        // ── Clarity ───────────────────────────────────────────────
        if (focusMode.shouldShowSection("effects.clarity")) {
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringRes { editorSectionClarity },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = {
                            HapticFeedback.heavyClick(view)
                            viewModel.updateClarity(0f)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringRes { effectsResetClarity },
                            modifier = Modifier.size(AlcedoIconSize.sm),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                AdjustmentSlider(
                    label = stringRes { editorAmount },
                    value = params.clarityAmount,
                    range = -1f..1f,
                    onValueChange = { viewModel.updateClarityDrag(it, params.clarityRadius) },
                    onValueChangeFinished = { viewModel.updateClarity(params.clarityAmount, params.clarityRadius) },
                    defaultValue = 0f
                )
                AdjustmentSlider(
                    label = stringRes { editorRadius },
                    value = params.clarityRadius,
                    range = 1f..50f,
                    onValueChange = { viewModel.updateClarity(params.clarityAmount, it, intermediate = true) },
                    onValueChangeFinished = { viewModel.updateClarity(params.clarityAmount, params.clarityRadius) },
                    defaultValue = 15f,
                    valueDisplayTransform = { "%.0f".format(it) }
                )
            }
        }
        }

        // ── Vignette ──────────────────────────────────────────────
        if (focusMode.shouldShowSection("effects.vignette")) {
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringRes { editorSectionVignette },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = {
                            HapticFeedback.heavyClick(view)
                            viewModel.updateVignette(0f)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringRes { effectsResetVignette },
                            modifier = Modifier.size(AlcedoIconSize.sm),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                AdjustmentSlider(
                    label = stringRes { effectsStrength },
                    value = params.lensVignetteStrength,
                    range = 0f..1f,
                    onValueChange = { viewModel.updateVignetteDrag(it) },
                    onValueChangeFinished = { viewModel.updateVignette(params.lensVignetteStrength) },
                    defaultValue = 0f
                )
            }
        }
        }

        // ── LUT ───────────────────────────────────────────────────
        if (focusMode.shouldShowSection("effects.lut")) {
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringRes { editorSectionLut },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                HapticFeedback.heavyClick(view)
                                viewModel.updateLut(false, "")
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringRes { effectsResetLut },
                                modifier = Modifier.size(AlcedoIconSize.sm),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(AlcedoSpacing.xs))
                        Switch(
                            checked = params.lutEnabled,
                            onCheckedChange = {
                                HapticFeedback.click(view)
                                viewModel.updateLut(it, params.lutPath)
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                if (params.lutEnabled) {
                    OutlinedButton(
                        onClick = {
                            lutPickerLauncher.launch(
                                arrayOf("*/*")
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(AlcedoIconSize.sm))
                        Spacer(modifier = Modifier.width(AlcedoSpacing.sm))
                        Text(
                            if (params.lutPath.isEmpty()) stringRes { editorSelectLut }
                            else params.lutPath.substringAfterLast('/')
                        )
                    }
                }
            }
        }
        }
    }
}

/**
 * Copy a LUT file from a content URI to the app's internal storage and return
 * the absolute path. Returns null if the copy fails.
 */
private fun copyLutToInternalStorage(context: Context, uri: Uri): String? =
    copyLutToInternal(context, uri)
