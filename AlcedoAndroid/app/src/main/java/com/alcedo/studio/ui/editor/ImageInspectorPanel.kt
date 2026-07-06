package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alcedo.studio.data.model.ImageModel
import com.alcedo.studio.data.model.ImageType
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.common.LiquidGlassSurface

@Composable
fun ImageInspectorPanel(
    image: ImageModel?,
    modifier: Modifier = Modifier
) {
    if (image == null) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringRes { inspectorNoImage },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val exif = image.exifDisplay
    var showExifEditor by remember { mutableStateOf(false) }

    // 判断是否为 RAW 文件
    val isRaw = image.imageType in setOf(
        ImageType.ARW, ImageType.CR2, ImageType.CR3,
        ImageType.NEF, ImageType.DNG
    )
    // 判断是否为大疆相机
    val isDji = exif.cameraMake.contains("DJI", ignoreCase = true)

    if (showExifEditor && image.imagePath.isNotEmpty()) {
        ExifEditorDialog(
            filePath = image.imagePath,
            onDismiss = { showExifEditor = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── File Info ──────────────────────────────────────────────
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    stringRes { inspectorFileInfo },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (image.imageName.isNotEmpty()) {
                    InspectorRow("File", image.imageName)
                }
                if (image.fileSize > 0) {
                    InspectorRow("Size", formatFileSize(image.fileSize))
                }
                val dims = exif.imageSize.ifEmpty {
                    if (image.width > 0 && image.height > 0) "${image.width} × ${image.height}" else ""
                }
                if (dims.isNotEmpty()) {
                    InspectorRow("Dimensions", if (isRaw) "$dims (RAW)" else dims)
                }
                if (image.imageType.name.isNotEmpty()) {
                    InspectorRow("Format", image.imageType.name)
                }
            }
        }

        // ── Camera ─────────────────────────────────────────────────
        val cameraEntries = buildCameraEntries(exif)
        if (cameraEntries.isNotEmpty()) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Camera",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    cameraEntries.forEach { (key, value) ->
                        InspectorRow(key, value)
                    }
                }
            }
        }

        // ── Lens ───────────────────────────────────────────────────
        val lensEntries = buildLensEntries(exif)
        if (lensEntries.isNotEmpty()) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Lens",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    lensEntries.forEach { (key, value) ->
                        InspectorRow(key, value)
                    }
                }
            }
        }

        // ── Capture Parameters ─────────────────────────────────────
        val captureEntries = buildCaptureEntries(exif)
        if (captureEntries.isNotEmpty()) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Capture",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    captureEntries.forEach { (key, value) ->
                        InspectorRow(key, value)
                    }
                }
            }
        }

        // ── Color & Time ───────────────────────────────────────────
        val colorTimeEntries = buildColorTimeEntries(exif)
        if (colorTimeEntries.isNotEmpty()) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Color & Time",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    colorTimeEntries.forEach { (key, value) ->
                        InspectorRow(key, value)
                    }
                }
            }
        }

        // ── RAW-specific info ──────────────────────────────────────
        if (isRaw) {
            val rawEntries = buildRawEntries(exif)
            if (rawEntries.isNotEmpty()) {
                LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "RAW",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        rawEntries.forEach { (key, value) ->
                            InspectorRow(key, value)
                        }
                    }
                }
            }
        }

        // ── DJI-specific info ──────────────────────────────────────
        if (isDji) {
            val djiEntries = buildDjiEntries(exif)
            if (djiEntries.isNotEmpty()) {
                LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "DJI Flight",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        djiEntries.forEach { (key, value) ->
                            InspectorRow(key, value)
                        }
                    }
                }
            }
        }

        // ── AI Scene (Huawei/Xiaomi) ───────────────────────────────
        val aiEntries = buildAiEntries(exif)
        if (aiEntries.isNotEmpty()) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "AI Scene",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    aiEntries.forEach { (key, value) ->
                        InspectorRow(key, value)
                    }
                }
            }
        }

        // ── GPS ────────────────────────────────────────────────────
        if (exif.gpsLatitude.isNotEmpty() || exif.gpsLongitude.isNotEmpty()) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "GPS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (exif.gpsLatitude.isNotEmpty()) {
                        InspectorRow("Latitude", exif.gpsLatitude)
                    }
                    if (exif.gpsLongitude.isNotEmpty()) {
                        InspectorRow("Longitude", exif.gpsLongitude)
                    }
                    if (exif.gpsAltitude.isNotEmpty()) {
                        InspectorRow("Altitude", exif.gpsAltitude)
                    }
                }
            }
        }

        // ── EXIF Data (raw view + edit) ────────────────────────────
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringRes { inspectorExifData },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (image.imagePath.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = { showExifEditor = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringRes { exifEditTitle },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                val entries = buildExifEntries(exif)
                if (entries.isEmpty()) {
                    Text(
                        stringRes { inspectorNoExif },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    entries.forEach { (key, value) ->
                        InspectorRow(key, value)
                    }
                }
            }
        }

        // ── Rating ─────────────────────────────────────────────────
        val rating = exif.rating.coerceIn(0, 5)
        if (rating > 0) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringRes { inspectorRating },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        (1..5).forEach { star ->
                            Icon(
                                if (star <= rating) Icons.Default.Star else Icons.Default.StarOutline,
                                contentDescription = null,
                                tint = if (star <= rating) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildExifEntries(exif: com.alcedo.studio.data.model.ExifDisplayMetaData): List<Pair<String, String>> {
    val entries = mutableListOf<Pair<String, String>>()
    if (exif.cameraMake.isNotEmpty()) entries += "Camera Make" to exif.cameraMake
    if (exif.cameraModel.isNotEmpty()) entries += "Camera Model" to exif.cameraModel
    if (exif.lensModel.isNotEmpty()) entries += "Lens" to exif.lensModel
    if (exif.focalLength.isNotEmpty()) entries += "Focal Length" to exif.focalLength
    if (exif.aperture.isNotEmpty()) entries += "Aperture" to exif.aperture
    if (exif.shutterSpeed.isNotEmpty()) entries += "Shutter Speed" to exif.shutterSpeed
    if (exif.iso.isNotEmpty()) entries += "ISO" to exif.iso
    if (exif.captureDate.isNotEmpty()) entries += "Capture Date" to exif.captureDate
    return entries
}

/** 相机信息：品牌 + 型号 */
private fun buildCameraEntries(exif: com.alcedo.studio.data.model.ExifDisplayMetaData): List<Pair<String, String>> {
    val entries = mutableListOf<Pair<String, String>>()
    if (exif.cameraMake.isNotEmpty()) entries += "Make" to exif.cameraMake
    if (exif.cameraModel.isNotEmpty()) entries += "Model" to exif.cameraModel
    return entries
}

/** 镜头信息：型号 + 焦距 + 最大光圈 */
private fun buildLensEntries(exif: com.alcedo.studio.data.model.ExifDisplayMetaData): List<Pair<String, String>> {
    val entries = mutableListOf<Pair<String, String>>()
    if (exif.lensModel.isNotEmpty()) entries += "Model" to exif.lensModel
    if (exif.focalLength.isNotEmpty()) entries += "Focal Length" to exif.focalLength.let {
        if (it.endsWith("mm")) it else "$it mm"
    }
    if (exif.focalLength35mm.isNotEmpty()) entries += "35mm Equiv" to exif.focalLength35mm.let {
        if (it.endsWith("mm")) it else "$it mm"
    }
    if (exif.maxAperture.isNotEmpty()) entries += "Max Aperture" to "f/${exif.maxAperture}"
    return entries
}

/** 拍摄参数：ISO + 快门 + 光圈 + 曝光补偿 + 焦距 */
private fun buildCaptureEntries(exif: com.alcedo.studio.data.model.ExifDisplayMetaData): List<Pair<String, String>> {
    val entries = mutableListOf<Pair<String, String>>()
    if (exif.iso.isNotEmpty()) entries += "ISO" to exif.iso
    if (exif.shutterSpeed.isNotEmpty()) entries += "Shutter" to formatShutterSpeed(exif.shutterSpeed)
    if (exif.aperture.isNotEmpty()) entries += "Aperture" to "f/${exif.aperture}"
    if (exif.exposureCompensation.isNotEmpty()) entries += "Exposure" to "${exif.exposureCompensation} EV"
    if (exif.focalLength.isNotEmpty()) entries += "Focal" to exif.focalLength.let {
        if (it.endsWith("mm")) it else "$it mm"
    }
    return entries
}

/** 色彩与时间：色彩空间 + 拍摄日期 */
private fun buildColorTimeEntries(exif: com.alcedo.studio.data.model.ExifDisplayMetaData): List<Pair<String, String>> {
    val entries = mutableListOf<Pair<String, String>>()
    if (exif.colorSpace.isNotEmpty()) entries += "Color Space" to formatColorSpace(exif.colorSpace)
    if (exif.captureDate.isNotEmpty()) entries += "Capture Date" to exif.captureDate
    return entries
}

/** RAW 特有信息：位深 + 白平衡 + 去马赛克算法 */
private fun buildRawEntries(exif: com.alcedo.studio.data.model.ExifDisplayMetaData): List<Pair<String, String>> {
    val entries = mutableListOf<Pair<String, String>>()
    if (exif.bitsPerSample > 0) {
        entries += "Bit Depth" to "${exif.bitsPerSample}-bit"
    }
    if (exif.whiteBalanceMode.isNotEmpty()) {
        entries += "White Balance" to exif.whiteBalanceMode
    }
    if (exif.demosaicAlgorithm.isNotEmpty()) {
        entries += "Demosaic" to exif.demosaicAlgorithm
    } else {
        // 默认显示当前使用的去马赛克算法
        entries += "Demosaic" to "AHD (default)"
    }
    return entries
}

/** 大疆特有信息：飞行高度 + GPS 模式 + 云台角度 */
private fun buildDjiEntries(exif: com.alcedo.studio.data.model.ExifDisplayMetaData): List<Pair<String, String>> {
    val entries = mutableListOf<Pair<String, String>>()
    if (exif.djiFlightHeight.isNotEmpty()) entries += "Flight Height" to exif.djiFlightHeight
    if (exif.djiGpsMode.isNotEmpty()) entries += "GPS Mode" to exif.djiGpsMode
    if (exif.djiGimbalPitch.isNotEmpty()) entries += "Gimbal Pitch" to exif.djiGimbalPitch
    return entries
}

/** AI 场景识别 + 多帧合成（华为/小米） */
private fun buildAiEntries(exif: com.alcedo.studio.data.model.ExifDisplayMetaData): List<Pair<String, String>> {
    val entries = mutableListOf<Pair<String, String>>()
    if (exif.aiScene.isNotEmpty()) entries += "AI Scene" to exif.aiScene
    if (exif.multiFrameInfo.isNotEmpty()) entries += "Multi-Frame" to exif.multiFrameInfo
    return entries
}

/** 格式化快门速度（"0.004" → "1/250", "1.5" → "1.5s"） */
private fun formatShutterSpeed(value: String): String {
    val seconds = value.toFloatOrNull() ?: return value
    return when {
        seconds == 0f -> value
        seconds < 1f -> {
            val denom = (1f / seconds).toInt()
            "1/$denom"
        }
        else -> "${"%.1f".format(seconds)}s"
    }
}

/** 格式化色彩空间代码 */
private fun formatColorSpace(value: String): String {
    return when (value) {
        "1" -> "sRGB"
        "2" -> "Adobe RGB"
        "65535" -> "Uncalibrated"
        else -> value.ifEmpty { "Unknown" }
    }
}

@Composable
private fun InspectorRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
}
