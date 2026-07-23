package com.alcedo.studio.ui.editor

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.alcedo.studio.ui.theme.AlcedoIconSize
import com.alcedo.studio.ui.theme.AlcedoSpacing

@Composable
fun LmtPanel(
    onLmtChanged: (Boolean, String, Float) -> Unit,
    lmtEnabled: Boolean,
    lmtPath: String,
    lmtIntensity: Float,
    modifier: Modifier = Modifier
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
                        Spacer(modifier = Modifier.width(8.dp))
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
                            Spacer(modifier = Modifier.width(4.dp))
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
                        label = stringRes { lmtIntensity },
                        value = lmtIntensity,
                        range = 0f..1f,
                        onValueChange = { onLmtChanged(lmtEnabled, lmtPath, it) },
                        defaultValue = 1f
                    )
                }
            }
        }

        // Built-in presets
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                Text(
                    stringRes { lmtBuiltInPresets },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                Text(
                    stringRes { lmtImportLut },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
