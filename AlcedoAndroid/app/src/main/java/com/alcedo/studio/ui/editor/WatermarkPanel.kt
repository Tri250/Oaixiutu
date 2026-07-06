package com.alcedo.studio.ui.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alcedo.studio.domain.service.WatermarkConfig
import com.alcedo.studio.domain.service.WatermarkPosition
import com.alcedo.studio.domain.service.WatermarkService
import com.alcedo.studio.domain.service.WatermarkType
import com.alcedo.studio.i18n.stringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full-screen dialog that configures a [WatermarkConfig] with a live preview rendered by
 * [WatermarkService]. The caller owns the config state and is notified via [onConfigChange].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatermarkPanelDialog(
    initialConfig: WatermarkConfig,
    onConfigChange: (WatermarkConfig) -> Unit,
    onDismiss: () -> Unit,
    onSavePreset: (WatermarkConfig) -> Unit
) {
    var config by remember { mutableStateOf(initialConfig) }
    val context = LocalContext.current
    val service = remember { WatermarkService() }

    // Sample bitmap used as the preview backdrop (a soft diagonal gradient).
    val sampleBitmap = remember {
        createSampleGradient(720, 480)
    }

    // Live preview bitmap, regenerated whenever the config changes.
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(config) {
        previewBitmap = withContext(Dispatchers.Default) {
            service.previewWatermark(sampleBitmap, config.copy(enabled = true))
        }
    }

    // Image picker for image / text+logo watermark types.
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val path = copyUriToCache(context, uri)
            if (path != null) {
                config = config.copy(imagePath = path)
                onConfigChange(config)
            }
        }
    }

    fun update(newConfig: WatermarkConfig) {
        config = newConfig
        onConfigChange(newConfig)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringRes { watermarkTitle }) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        },
                        actions = {
                            TextButton(onClick = { onSavePreset(config) }) {
                                Icon(Icons.Default.Bookmark, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text(stringRes { watermarkSavePreset })
                            }
                        }
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ── Enable toggle ──
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.WaterDrop, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                stringRes { watermarkTitle },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Switch(
                                checked = config.enabled,
                                onCheckedChange = { update(config.copy(enabled = it)) }
                            )
                        }
                    }

                    // ── Live preview ──
                    Text(
                        stringRes { watermarkPreview },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        previewBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        } ?: CircularProgressIndicator()
                    }

                    // All controls below are disabled when watermark is off, but still visible.
                    val controlsEnabled = config.enabled

                    // ── Type selector ──
                    Text(stringRes { watermarkTitle }, style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = config.type == WatermarkType.TEXT,
                            onClick = { update(config.copy(type = WatermarkType.TEXT)) },
                            enabled = controlsEnabled,
                            label = { Text(stringRes { watermarkTypeText }) }
                        )
                        FilterChip(
                            selected = config.type == WatermarkType.IMAGE,
                            onClick = { update(config.copy(type = WatermarkType.IMAGE)) },
                            enabled = controlsEnabled,
                            label = { Text(stringRes { watermarkTypeImage }) }
                        )
                        FilterChip(
                            selected = config.type == WatermarkType.TEXT_WITH_LOGO,
                            onClick = { update(config.copy(type = WatermarkType.TEXT_WITH_LOGO)) },
                            enabled = controlsEnabled,
                            label = { Text(stringRes { watermarkTypeTextWithLogo }) }
                        )
                    }

                    // ── Text input ──
                    if (config.type == WatermarkType.TEXT || config.type == WatermarkType.TEXT_WITH_LOGO) {
                        OutlinedTextField(
                            value = config.text,
                            onValueChange = { update(config.copy(text = it)) },
                            label = { Text(stringRes { watermarkText }) },
                            enabled = controlsEnabled,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // ── Image picker ──
                    if (config.type == WatermarkType.IMAGE || config.type == WatermarkType.TEXT_WITH_LOGO) {
                        OutlinedButton(
                            onClick = { imagePicker.launch(arrayOf("image/*")) },
                            enabled = controlsEnabled,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringRes { watermarkImage })
                        }
                        config.imagePath?.let { path ->
                            Text(
                                text = path.substringAfterLast('/'),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    // ── Font size slider ──
                    if (config.type != WatermarkType.IMAGE) {
                        SliderRow(
                            label = stringRes { watermarkFontSize },
                            value = config.fontSize,
                            range = 8f..72f,
                            enabled = controlsEnabled,
                            valueText = config.fontSize.toInt().toString(),
                            onValueChange = { update(config.copy(fontSize = it)) }
                        )
                    }

                    // ── Color picker ──
                    if (config.type != WatermarkType.IMAGE) {
                        Text(stringRes { watermarkColor }, style = MaterialTheme.typography.labelMedium)
                        ColorPickerRow(
                            selected = config.color,
                            enabled = controlsEnabled,
                            onColorSelected = { update(config.copy(color = it)) }
                        )
                    }

                    // ── Opacity slider ──
                    SliderRow(
                        label = stringRes { watermarkOpacity },
                        value = config.opacity * 100f,
                        range = 0f..100f,
                        enabled = controlsEnabled,
                        valueText = "${config.opacity.times(100).toInt()}%",
                        onValueChange = { update(config.copy(opacity = it / 100f)) }
                    )

                    // ── Position selector (icon grid) ──
                    Text(stringRes { watermarkPosition }, style = MaterialTheme.typography.labelMedium)
                    PositionGrid(
                        selected = config.position,
                        enabled = controlsEnabled,
                        onSelect = { update(config.copy(position = it)) }
                    )

                    // ── Margin slider ──
                    SliderRow(
                        label = stringRes { watermarkMargin },
                        value = config.margin,
                        range = 0f..100f,
                        enabled = controlsEnabled,
                        valueText = "${config.margin.toInt()}px",
                        onValueChange = { update(config.copy(margin = it)) }
                    )

                    // ── Rotation slider ──
                    SliderRow(
                        label = stringRes { watermarkRotation },
                        value = config.rotation,
                        range = -45f..45f,
                        enabled = controlsEnabled,
                        valueText = "${config.rotation.toInt()}°",
                        onValueChange = { update(config.copy(rotation = it)) }
                    )

                    // ── Shadow & Border toggles ──
                    if (config.type != WatermarkType.IMAGE) {
                        ToggleRow(
                            label = stringRes { watermarkShadow },
                            checked = config.hasShadow,
                            enabled = controlsEnabled,
                            onCheckedChange = { update(config.copy(hasShadow = it)) }
                        )
                        ToggleRow(
                            label = stringRes { watermarkBorder },
                            checked = config.hasBorder,
                            enabled = controlsEnabled,
                            onCheckedChange = { update(config.copy(hasBorder = it)) }
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

// ----------------------------------------------------------------
// Sub-components
// ----------------------------------------------------------------

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    valueText: String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun ColorPickerRow(
    selected: Int,
    enabled: Boolean,
    onColorSelected: (Int) -> Unit
) {
    val presets = listOf(
        Color.WHITE, Color.BLACK, Color.RED, Color.YELLOW,
        Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA,
        Color.LTGRAY, Color.DKGRAY
    )
    var showCustom by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { c ->
                ColorSwatch(
                    color = c,
                    selected = selected == c,
                    enabled = enabled,
                    onClick = { onColorSelected(c) }
                )
            }
        }
        TextButton(
            onClick = { showCustom = !showCustom },
            enabled = enabled
        ) {
            Icon(Icons.Default.Palette, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(if (showCustom) "▲ Custom" else "▼ Custom")
        }
        if (showCustom) {
            val r = (selected shr 16) and 0xFF
            val g = (selected shr 8) and 0xFF
            val b = selected and 0xFF
            Column {
                ChannelSlider("R", r, enabled) { nr ->
                    onColorSelected(Color.argb(255, nr, g, b))
                }
                ChannelSlider("G", g, enabled) { ng ->
                    onColorSelected(Color.argb(255, r, ng, b))
                }
                ChannelSlider("B", b, enabled) { nb ->
                    onColorSelected(Color.argb(255, r, g, nb))
                }
            }
        }
    }
}

@Composable
private fun ChannelSlider(
    label: String,
    value: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(20.dp), style = MaterialTheme.typography.labelSmall)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..255f,
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )
        Text(
            value.toString(),
            modifier = Modifier.width(36.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun ColorSwatch(
    color: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val composeColor = androidx.compose.ui.graphics.Color(
        red = ((color shr 16) and 0xFF) / 255f,
        green = ((color shr 8) and 0xFF) / 255f,
        blue = (color and 0xFF) / 255f,
        alpha = ((color shr 24) and 0xFF) / 255f
    )
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(composeColor)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = borderColor,
                shape = CircleShape
            )
            .clickable(enabled = enabled, onClick = onClick)
    )
}

@Composable
private fun PositionGrid(
    selected: WatermarkPosition,
    enabled: Boolean,
    onSelect: (WatermarkPosition) -> Unit
) {
    val positions = listOf(
        WatermarkPosition.TOP_LEFT to stringRes { watermarkPositionTopLeft },
        WatermarkPosition.TOP_RIGHT to stringRes { watermarkPositionTopRight },
        WatermarkPosition.CENTER to stringRes { watermarkPositionCenter },
        WatermarkPosition.BOTTOM_LEFT to stringRes { watermarkPositionBottomLeft },
        WatermarkPosition.BOTTOM_CENTER to stringRes { watermarkPositionBottomCenter },
        WatermarkPosition.BOTTOM_RIGHT to stringRes { watermarkPositionBottomRight }
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        positions.forEach { (pos, label) ->
            PositionCell(
                position = pos,
                label = label,
                selected = selected == pos,
                enabled = enabled,
                onSelect = { onSelect(pos) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PositionCell(
    position: WatermarkPosition,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val dotAlignment = when (position) {
        WatermarkPosition.TOP_LEFT -> Alignment.TopStart
        WatermarkPosition.TOP_RIGHT -> Alignment.TopEnd
        WatermarkPosition.CENTER -> Alignment.Center
        WatermarkPosition.BOTTOM_LEFT -> Alignment.BottomStart
        WatermarkPosition.BOTTOM_CENTER -> Alignment.BottomCenter
        WatermarkPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onSelect)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = dotAlignment
        ) {
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ----------------------------------------------------------------
// Helpers
// ----------------------------------------------------------------

/** Builds a soft diagonal gradient bitmap to use as the watermark preview backdrop. */
private fun createSampleGradient(width: Int, height: Int): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = android.graphics.Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(
        0f, 0f, width.toFloat(), height.toFloat(),
        intArrayOf(0xFF334155.toInt(), 0xFF64748B.toInt(), 0xFF94A3B8.toInt()),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    return bmp
}

/** Copies a content Uri image into the cache dir and returns the absolute file path. */
private fun copyUriToCache(
    context: android.content.Context,
    uri: android.net.Uri
): String? {
    return runCatching {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        val outFile = java.io.File(context.cacheDir, "wm_logo_${System.currentTimeMillis()}.png")
        outFile.outputStream().use { output -> input.copyTo(output) }
        input.close()
        outFile.absolutePath
    }.getOrNull()
}
