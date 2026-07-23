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
import com.alcedo.studio.ui.theme.AlcedoAnimation
import com.alcedo.studio.ui.theme.AlcedoIconSize
import com.alcedo.studio.ui.theme.AlcedoSpacing
import com.alcedo.studio.viewmodel.EditorViewModel

@Composable
fun LensCorrectionPanel(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val params by viewModel.params
    val image by viewModel.imageModel.collectAsState()

    // 自动检测镜头型号
    var autoDetectedProfile by remember { mutableStateOf<LensCorrectionDatabase.LensProfile?>(null) }
    var isAutoDetecting by remember { mutableStateOf(false) }

    // 从 EXIF 自动检测
    LaunchedEffect(image) {
        isAutoDetecting = true
        autoDetectedProfile = image?.let { img ->
            LensCorrectionDatabase.findProfile(
                make = img.exifDisplay.cameraMake,
                model = img.exifDisplay.lensModel?.ifBlank { img.exifDisplay.cameraModel } ?: img.exifDisplay.cameraModel,
                focalLength = img.exifDisplay.focalLength.toFloatOrNull(),
                aperture = img.exifDisplay.aperture.toFloatOrNull()
            )
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
                            "自动检测到镜头",
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
                        Text("应用")
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
                        Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(AlcedoIconSize.sm), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                AdjustmentSlider(label = "K1", value = params.lensK1, range = -0.2f..0.2f, onValueChange = {
                    viewModel.updateLensCorrection(it, params.lensK2, params.lensK3, params.lensP1, params.lensP2)
                }, defaultValue = 0f)
                Spacer(modifier = Modifier.height(AlcedoSpacing.md))
                AdjustmentSlider(label = "K2", value = params.lensK2, range = -0.2f..0.2f, onValueChange = {
                    viewModel.updateLensCorrection(params.lensK1, it, params.lensK3, params.lensP1, params.lensP2)
                }, defaultValue = 0f)
                Spacer(modifier = Modifier.height(AlcedoSpacing.md))
                AdjustmentSlider(label = "K3", value = params.lensK3, range = -0.2f..0.2f, onValueChange = {
                    viewModel.updateLensCorrection(params.lensK1, params.lensK2, it, params.lensP1, params.lensP2)
                }, defaultValue = 0f)
                Spacer(modifier = Modifier.height(AlcedoSpacing.md))
                AdjustmentSlider(label = "P1", value = params.lensP1, range = -0.1f..0.1f, onValueChange = {
                    viewModel.updateLensCorrection(params.lensK1, params.lensK2, params.lensK3, it, params.lensP2)
                }, defaultValue = 0f)
                Spacer(modifier = Modifier.height(AlcedoSpacing.md))
                AdjustmentSlider(label = "P2", value = params.lensP2, range = -0.1f..0.1f, onValueChange = {
                    viewModel.updateLensCorrection(params.lensK1, params.lensK2, params.lensK3, params.lensP1, it)
                }, defaultValue = 0f)
            }
        }
    }
}
