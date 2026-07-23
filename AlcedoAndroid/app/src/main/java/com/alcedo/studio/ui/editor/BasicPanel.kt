package com.alcedo.studio.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.LiquidGlassSurface
import com.alcedo.studio.ui.theme.AlcedoAnimation
import com.alcedo.studio.ui.theme.AlcedoIconSize
import com.alcedo.studio.ui.theme.AlcedoSpacing
import com.alcedo.studio.viewmodel.EditorViewModel

@Composable
fun BasicPanel(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusMode: FocusModeState = FocusModeState()
) {
    val params by remember { viewModel.params }

    // 专注模式下用于切换活跃小节的小节标签
    val focusSections = listOf(
        "basic.light" to stringRes { editorSectionLight },
        "basic.wb" to stringRes { editorSectionWhiteBalance },
        "basic.presence" to stringRes { editorPresence },
        "basic.split_tone" to stringRes { editorSectionSplitToning }
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AlcedoSpacing.md)
    ) {
        FocusSectionChips(focusMode = focusMode, sections = focusSections)

        // ── Light ──────────────────────────────────────────────────
        if (focusMode.shouldShowSection("basic.light")) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringRes { editorSectionLight },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = {
                                viewModel.updateExposure(0f)
                                viewModel.updateContrast(0f)
                                viewModel.updateHighlights(0f)
                                viewModel.updateShadows(0f)
                                viewModel.updateMidtones(0f)
                                viewModel.updateSigmoidShoulder(0.5f)
                                viewModel.updateShadowBoundary(0.25f)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringRes { editorResetLight },
                                modifier = Modifier.size(AlcedoIconSize.sm),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                    AdjustmentSlider(
                        label = stringRes { editorExposure },
                        value = params.exposure,
                        range = -5f..5f,
                        onValueChange = { viewModel.updateExposure(it) },
                        defaultValue = 0f
                    )
                    AdjustmentSlider(
                        label = stringRes { editorContrast },
                        value = params.contrast,
                        range = -1f..1f,
                        onValueChange = { viewModel.updateContrast(it) },
                        defaultValue = 0f
                    )
                    AdjustmentSlider(
                        label = stringRes { editorHighlights },
                        value = params.highlights,
                        range = -1f..1f,
                        onValueChange = { viewModel.updateHighlights(it) },
                        defaultValue = 0f
                    )
                    AdjustmentSlider(
                        label = stringRes { editorShadows },
                        value = params.shadows,
                        range = -1f..1f,
                        onValueChange = { viewModel.updateShadows(it) },
                        defaultValue = 0f
                    )
                    AdjustmentSlider(
                        label = stringRes { editorSigmoidShoulder },
                        value = params.sigmoidShoulder,
                        range = 0f..1f,
                        onValueChange = { viewModel.updateSigmoidShoulder(it) },
                        defaultValue = 0.5f
                    )
                    AdjustmentSlider(
                        label = stringRes { editorBlacks },
                        value = params.shadowBoundary,
                        range = 0f..0.5f,
                        onValueChange = {
                            viewModel.updateShadowBoundary(it)
                        },
                        defaultValue = 0.25f
                    )
                    AdjustmentSlider(
                        label = stringRes { editorMidtones },
                        value = params.midtones,
                        range = -1f..1f,
                        onValueChange = { viewModel.updateMidtones(it) },
                        defaultValue = 0f
                    )
                }
            }
        }

        // ── White Balance ──────────────────────────────────────────
        if (focusMode.shouldShowSection("basic.wb")) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringRes { editorSectionWhiteBalance },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = {
                                viewModel.updateWhiteBalance(6500f, 0f)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringRes { editorResetWb },
                                modifier = Modifier.size(AlcedoIconSize.sm),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                    AdjustmentSlider(
                        label = stringRes { editorTemperature },
                        value = params.whiteBalanceTemp,
                        range = 2000f..15000f,
                        onValueChange = { viewModel.updateWhiteBalance(it, params.whiteBalanceTint) },
                        defaultValue = 6500f,
                        valueDisplayTransform = { "%.0fK".format(it) }
                    )
                    AdjustmentSlider(
                        label = stringRes { editorTint },
                        value = params.whiteBalanceTint,
                        range = -100f..100f,
                        onValueChange = { viewModel.updateWhiteBalance(params.whiteBalanceTemp, it) },
                        defaultValue = 0f
                    )
                }
            }
        }

        // ── Presence ───────────────────────────────────────────────
        if (focusMode.shouldShowSection("basic.presence")) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringRes { editorPresence },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = {
                                viewModel.updateClarity(0f)
                                viewModel.updateVibrance(0f)
                                viewModel.updateSaturation(0f)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringRes { editorResetPresence },
                                modifier = Modifier.size(AlcedoIconSize.sm),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                    AdjustmentSlider(
                        label = stringRes { editorSectionClarity },
                        value = params.clarityAmount,
                        range = -1f..1f,
                        onValueChange = { viewModel.updateClarity(it) },
                        defaultValue = 0f
                    )
                    AdjustmentSlider(
                        label = stringRes { editorVibrance },
                        value = params.vibrance,
                        range = -1f..1f,
                        onValueChange = { viewModel.updateVibrance(it) },
                        defaultValue = 0f
                    )
                    AdjustmentSlider(
                        label = stringRes { editorSaturation },
                        value = params.saturation,
                        range = -1f..1f,
                        onValueChange = { viewModel.updateSaturation(it) },
                        defaultValue = 0f
                    )
                }
            }
        }

        // ── Split Toning ──────────────────────────────────────────
        if (focusMode.shouldShowSection("basic.split_tone")) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringRes { editorSectionSplitToning },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = {
                                viewModel.updateTint(0f, 0f, 0f, 0f, 0f)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringRes { editorResetSplitTone },
                                modifier = Modifier.size(AlcedoIconSize.sm),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                    AdjustmentSlider(
                        label = stringRes { editorHighlightHue },
                        value = params.tintHighlightHue,
                        range = 0f..360f,
                        onValueChange = {
                            viewModel.updateTint(
                                it, params.tintHighlightStrength,
                                params.tintShadowHue, params.tintShadowStrength,
                                params.tintBalance
                            )
                        },
                        defaultValue = 0f,
                        valueDisplayTransform = { "%.0f°".format(it) }
                    )
                    AdjustmentSlider(
                        label = stringRes { editorHighlightStrength },
                        value = params.tintHighlightStrength,
                        range = 0f..1f,
                        onValueChange = {
                            viewModel.updateTint(
                                params.tintHighlightHue, it,
                                params.tintShadowHue, params.tintShadowStrength,
                                params.tintBalance
                            )
                        },
                        defaultValue = 0f
                    )
                    AdjustmentSlider(
                        label = stringRes { editorShadowHue },
                        value = params.tintShadowHue,
                        range = 0f..360f,
                        onValueChange = {
                            viewModel.updateTint(
                                params.tintHighlightHue, params.tintHighlightStrength,
                                it, params.tintShadowStrength,
                                params.tintBalance
                            )
                        },
                        defaultValue = 0f,
                        valueDisplayTransform = { "%.0f°".format(it) }
                    )
                    AdjustmentSlider(
                        label = stringRes { editorShadowStrength },
                        value = params.tintShadowStrength,
                        range = 0f..1f,
                        onValueChange = {
                            viewModel.updateTint(
                                params.tintHighlightHue, params.tintHighlightStrength,
                                params.tintShadowHue, it,
                                params.tintBalance
                            )
                        },
                        defaultValue = 0f
                    )
                    AdjustmentSlider(
                        label = stringRes { editorBalance },
                        value = params.tintBalance,
                        range = -1f..1f,
                        onValueChange = {
                            viewModel.updateTint(
                                params.tintHighlightHue, params.tintHighlightStrength,
                                params.tintShadowHue, params.tintShadowStrength,
                                it
                            )
                        },
                        defaultValue = 0f
                    )
                }
            }
        }
    }
}
