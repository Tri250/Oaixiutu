package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alcedo.studio.domain.service.LensCorrectionDatabase
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.common.AdjustmentSlider
import com.alcedo.studio.ui.common.LiquidGlassSurface
import com.alcedo.studio.ui.theme.AlcedoIconSize
import com.alcedo.studio.ui.theme.AlcedoSpacing
import com.alcedo.studio.viewmodel.EditorViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LensCorrectionPanel(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val params by viewModel.params
    val image by viewModel.imageModel.collectAsStateWithLifecycle()

    // 自动检测镜头型号
    var autoDetectedProfile by remember { mutableStateOf<LensCorrectionDatabase.LensProfile?>(null) }
    var isAutoDetecting by remember { mutableStateOf(false) }

    // 从 EXIF 自动检测
    LaunchedEffect(image) {
        isAutoDetecting = true
        autoDetectedProfile = try {
            image?.let { img ->
                LensCorrectionDatabase.findProfile(
                    make = img.exifDisplay.cameraMake,
                    model = img.exifDisplay.lensModel.ifBlank { img.exifDisplay.cameraModel },
                    focalLength = img.exifDisplay.focalLength.toFloatOrNull(),
                    aperture = img.exifDisplay.aperture.toFloatOrNull()
                )
            }
        } catch (_: Exception) {
            null
        }
        isAutoDetecting = false
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AlcedoSpacing.md)
    ) {
        // 自动检测结果
        autoDetectedProfile?.let { profile ->
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(AlcedoSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringRes { inspectorAutoDetectedLens },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${profile.make} ${profile.model}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Button(onClick = {
                        viewModel.updateLensCorrection(
                            profile.k1, profile.k2, profile.k3, profile.p1, profile.p2
                        )
                    }) {
                        Text(stringRes { applyButton })
                    }
                }
            }
        }

        if (isAutoDetecting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // 手动调整
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringRes { editorSectionLensCorrection },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = { viewModel.updateLensCorrection(0f, 0f, 0f, 0f, 0f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = stringRes { resetButton }, modifier = Modifier.size(AlcedoIconSize.sm), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                AdjustmentSlider(label = stringRes { k1Distortion }, value = params.lensK1, range = -0.2f..0.2f, onValueChange = {
                    viewModel.updateLensCorrectionK1Drag(it)
                }, onValueChangeFinished = {
                    viewModel.updateLensCorrection(it, params.lensK2, params.lensK3, params.lensP1, params.lensP2)
                }, defaultValue = 0f)
                AdjustmentSlider(label = stringRes { k2Distortion }, value = params.lensK2, range = -0.2f..0.2f, onValueChange = {
                    viewModel.updateLensCorrectionK2Drag(it)
                }, onValueChangeFinished = {
                    viewModel.updateLensCorrection(params.lensK1, it, params.lensK3, params.lensP1, params.lensP2)
                }, defaultValue = 0f)
                AdjustmentSlider(label = stringRes { k3Distortion }, value = params.lensK3, range = -0.2f..0.2f, onValueChange = {
                    viewModel.updateLensCorrectionK3Drag(it)
                }, onValueChangeFinished = {
                    viewModel.updateLensCorrection(params.lensK1, params.lensK2, it, params.lensP1, params.lensP2)
                }, defaultValue = 0f)
                AdjustmentSlider(label = stringRes { p1Tangential }, value = params.lensP1, range = -0.1f..0.1f, onValueChange = {
                    viewModel.updateLensCorrectionP1Drag(it)
                }, onValueChangeFinished = {
                    viewModel.updateLensCorrection(params.lensK1, params.lensK2, params.lensK3, it, params.lensP2)
                }, defaultValue = 0f)
                AdjustmentSlider(label = stringRes { p2Tangential }, value = params.lensP2, range = -0.1f..0.1f, onValueChange = {
                    viewModel.updateLensCorrectionP2Drag(it)
                }, onValueChangeFinished = {
                    viewModel.updateLensCorrection(params.lensK1, params.lensK2, params.lensK3, params.lensP1, it)
                }, defaultValue = 0f)
            }
        }

        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                Text(
                    stringRes { lensAdvanced },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(AlcedoSpacing.sm))
                AdjustmentSlider(
                    label = stringRes { lensOpticalCenterX },
                    value = params.lensCx,
                    range = 0f..1f,
                    onValueChange = { viewModel.updateLensAdvanced(cx = it) },
                    defaultValue = 0.5f
                )
                AdjustmentSlider(
                    label = stringRes { lensOpticalCenterY },
                    value = params.lensCy,
                    range = 0f..1f,
                    onValueChange = { viewModel.updateLensAdvanced(cy = it) },
                    defaultValue = 0.5f
                )
                AdjustmentSlider(
                    label = stringRes { lensFocalRatio },
                    value = params.lensFocalRatio,
                    range = 0.5f..2f,
                    onValueChange = { viewModel.updateLensAdvanced(focalRatio = it) },
                    defaultValue = 1f
                )
                AdjustmentSlider(
                    label = stringRes { lensVignetteStrength },
                    value = params.lensVignetteStrength,
                    range = 0f..1f,
                    onValueChange = { viewModel.updateLensAdvanced(vignetteStrength = it) },
                    defaultValue = 0f
                )
            }
        }
    }
}
