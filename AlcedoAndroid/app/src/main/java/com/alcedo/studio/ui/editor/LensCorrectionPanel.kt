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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 自动检测结果
        autoDetectedProfile?.let { profile ->
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
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
                        viewModel.updateParams(params.copy(
                            lensK1 = profile.k1,
                            lensK2 = profile.k2,
                            lensK3 = profile.k3,
                            lensP1 = profile.p1,
                            lensP2 = profile.p2
                        ))
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
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringRes { editorSectionLensCorrection },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = { viewModel.updateParams(params.copy(lensK1 = 0f, lensK2 = 0f, lensK3 = 0f, lensP1 = 0f, lensP2 = 0f)) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                AdjustmentSlider(label = "K1", value = params.lensK1, range = -0.2f..0.2f, onValueChange = { viewModel.updateParams(params.copy(lensK1 = it)) }, defaultValue = 0f)
                AdjustmentSlider(label = "K2", value = params.lensK2, range = -0.2f..0.2f, onValueChange = { viewModel.updateParams(params.copy(lensK2 = it)) }, defaultValue = 0f)
                AdjustmentSlider(label = "K3", value = params.lensK3, range = -0.2f..0.2f, onValueChange = { viewModel.updateParams(params.copy(lensK3 = it)) }, defaultValue = 0f)
                AdjustmentSlider(label = "P1", value = params.lensP1, range = -0.1f..0.1f, onValueChange = { viewModel.updateParams(params.copy(lensP1 = it)) }, defaultValue = 0f)
                AdjustmentSlider(label = "P2", value = params.lensP2, range = -0.1f..0.1f, onValueChange = { viewModel.updateParams(params.copy(lensP2 = it)) }, defaultValue = 0f)
            }
        }
    }
}
