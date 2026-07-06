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
import com.alcedo.studio.viewmodel.EditorViewModel
import java.io.File

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
        "effects.grain" to stringRes { editorSectionFilmGrain },
        "effects.halation" to stringRes { editorSectionHalation },
        "effects.sharpen" to stringRes { editorSectionSharpen },
        "effects.clarity" to stringRes { editorSectionClarity },
        "effects.vignette" to stringRes { editorSectionVignette },
        "effects.lut" to stringRes { editorSectionLut }
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FocusSectionChips(focusMode = focusMode, sections = focusSections)

        // ── Film Grain ─────────────────────────────────────────────
        if (focusMode.shouldShowSection("effects.grain")) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringRes { editorSectionFilmGrain },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = {
                            HapticFeedback.heavyClick(view)
                            viewModel.updateFilmGrain(0f)
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringRes { effectsResetGrain },
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                AdjustmentSlider(
                    label = stringRes { editorIntensity },
                    value = params.filmGrainIntensity,
                    range = 0f..1f,
                    onValueChange = { viewModel.updateFilmGrain(it) },
                    defaultValue = 0f
                )
            }
        }
        }

        // ── Halation ──────────────────────────────────────────────
        if (focusMode.shouldShowSection("effects.halation")) {
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringRes { editorSectionHalation },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = {
                            HapticFeedback.heavyClick(view)
                            viewModel.updateHalation(0f, 0.8f, 10f, 0.7f)
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringRes { effectsResetHalation },
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                AdjustmentSlider(
                    label = stringRes { editorIntensity },
                    value = params.halationIntensity,
                    range = 0f..1f,
                    onValueChange = {
                        viewModel.updateHalation(
                            it, params.halationThreshold,
                            params.halationSpread, params.halationRedBias
                        )
                    },
                    defaultValue = 0f
                )
                AdjustmentSlider(
                    label = stringRes { editorSpread },
                    value = params.halationSpread,
                    range = 0f..50f,
                    onValueChange = {
                        viewModel.updateHalation(
                            params.halationIntensity, params.halationThreshold,
                            it, params.halationRedBias
                        )
                    },
                    defaultValue = 10f,
                    valueDisplayTransform = { "%.0f".format(it) }
                )
                AdjustmentSlider(
                    label = stringRes { editorThreshold },
                    value = params.halationThreshold,
                    range = 0f..1f,
                    onValueChange = {
                        viewModel.updateHalation(
                            params.halationIntensity, it,
                            params.halationSpread, params.halationRedBias
                        )
                    },
                    defaultValue = 0.8f
                )
                AdjustmentSlider(
                    label = stringRes { editorRedBias },
                    value = params.halationRedBias,
                    range = 0f..1f,
                    onValueChange = {
                        viewModel.updateHalation(
                            params.halationIntensity, params.halationThreshold,
                            params.halationSpread, it
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
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringRes { editorSectionSharpen },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = {
                            HapticFeedback.heavyClick(view)
                            viewModel.updateSharpen(0f)
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringRes { effectsResetSharpen },
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                AdjustmentSlider(
                    label = stringRes { editorAmount },
                    value = params.sharpenAmount,
                    range = 0f..2f,
                    onValueChange = { viewModel.updateSharpen(it) },
                    defaultValue = 0f
                )
            }
        }
        }

        // ── Clarity ───────────────────────────────────────────────
        if (focusMode.shouldShowSection("effects.clarity")) {
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringRes { editorSectionClarity },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = {
                            HapticFeedback.heavyClick(view)
                            viewModel.updateClarity(0f)
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringRes { effectsResetClarity },
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                AdjustmentSlider(
                    label = stringRes { editorAmount },
                    value = params.clarityAmount,
                    range = -1f..1f,
                    onValueChange = { viewModel.updateClarity(it, params.clarityRadius) },
                    defaultValue = 0f
                )
                AdjustmentSlider(
                    label = stringRes { editorRadius },
                    value = params.clarityRadius,
                    range = 1f..50f,
                    onValueChange = { viewModel.updateClarity(params.clarityAmount, it) },
                    defaultValue = 15f,
                    valueDisplayTransform = { "%.0f".format(it) }
                )
            }
        }
        }

        // ── Vignette ──────────────────────────────────────────────
        if (focusMode.shouldShowSection("effects.vignette")) {
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringRes { editorSectionVignette },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = {
                            HapticFeedback.heavyClick(view)
                            viewModel.updateParams(params.copy(lensVignetteStrength = 0f))
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringRes { effectsResetVignette },
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                AdjustmentSlider(
                    label = stringRes { effectsStrength },
                    value = params.lensVignetteStrength,
                    range = 0f..1f,
                    onValueChange = {
                        viewModel.updateParams(params.copy(lensVignetteStrength = it))
                    },
                    defaultValue = 0f
                )
            }
        }
        }

        // ── LUT ───────────────────────────────────────────────────
        if (focusMode.shouldShowSection("effects.lut")) {
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringRes { editorSectionLut },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                HapticFeedback.heavyClick(view)
                                viewModel.updateLut(false, "")
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringRes { effectsResetLut },
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = params.lutEnabled,
                            onCheckedChange = {
                                HapticFeedback.click(view)
                                viewModel.updateLut(it, params.lutPath)
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                if (params.lutEnabled) {
                    OutlinedButton(
                        onClick = {
                            lutPickerLauncher.launch(
                                arrayOf("*/*")
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
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
private fun copyLutToInternalStorage(context: Context, uri: Uri): String? {
    return try {
        val lutsDir = File(context.filesDir, "luts")
        if (!lutsDir.exists()) lutsDir.mkdirs()

        // Derive a file name from the URI or use a timestamp-based name
        val fileName = uri.lastPathSegment?.substringAfterLast('/')
            ?: "lut_${System.currentTimeMillis()}.cube"

        val outFile = File(lutsDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: return null

        outFile.absolutePath
    } catch (e: Exception) {
        null
    }
}
