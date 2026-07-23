package com.alcedo.studio.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.alcedo.studio.data.model.ImageModel
import com.alcedo.studio.data.model.ImageType
import com.alcedo.studio.i18n.stringRes
import com.alcedo.studio.ui.common.LiquidGlassSurface
import com.alcedo.studio.ui.theme.AlcedoAnimation
import com.alcedo.studio.ui.theme.AlcedoIconSize
import com.alcedo.studio.ui.theme.AlcedoRadius
import com.alcedo.studio.ui.theme.AlcedoSpacing

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

    // Resolve i18n labels in the @Composable scope for use in non-composable helpers
    val labels = InspectorLabels(
        make = stringRes { inspectorMake },
        model = stringRes { inspectorModel },
        lens = stringRes { inspectorLens },
        focalLength = stringRes { inspectorFocalLengthMm },
        equiv35mm = stringRes { inspector35mmEquiv },
        maxAperture = stringRes { inspectorMaxAperture },
        shutter = stringRes { inspectorShutter },
        aperture = stringRes { inspectorApertureLabel },
        exposureEv = stringRes { inspectorExposureEv },
        colorSpace = stringRes { inspectorColorSpaceLabel },
        captureDate = stringRes { inspectorCaptureDate },
        bitDepth = stringRes { inspectorBitDepth },
        whiteBalance = stringRes { inspectorWhiteBalanceLabel },
        demosaic = stringRes { inspectorDemosaic },
        defaultDemosaic = stringRes { inspectorDefaultDemosaic },
        flightHeight = stringRes { inspectorFlightHeight },
        gpsMode = stringRes { inspectorGpsMode },
        gimbalPitch = stringRes { inspectorGimbalPitch },
        aiScene = stringRes { inspectorAiScene },
        multiFrame = stringRes { inspectorMultiFrame }
    )

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
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AlcedoSpacing.md)
    ) {
        // ── File Info ──────────────────────────────────────────────
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                Text(
                    stringRes { inspectorFileInfo },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                if (image.imageName.isNotEmpty()) {
                    InspectorRow(stringRes { inspectorFile }, image.imageName)
                }
                if (image.fileSize > 0) {
                    InspectorRow(stringRes { inspectorSize }, formatFileSize(image.fileSize))
                }
                val dims = exif.imageSize.ifEmpty {
                    if (image.width > 0 && image.height > 0) "${image.width} × ${image.height}" else ""
                }
                if (dims.isNotEmpty()) {
                    InspectorRow(stringRes { inspectorDimensions }, if (isRaw) "$dims ${stringRes { inspectorRawLabel }}" else dims)
                }
                if (image.imageType.name.isNotEmpty()) {
                    InspectorRow(stringRes { inspectorFormat }, image.imageType.name)
                }
            }
        }

        // ── Camera ─────────────────────────────────────────────────
        val cameraEntries = buildCameraEntries(exif, labels)
        if (cameraEntries.isNotEmpty()) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                    Text(
                        stringRes { inspectorCamera },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                    cameraEntries.forEach { (key, value) ->
                        InspectorRow(key, value)
                    }
                }
            }
        }

        // ── Lens ───────────────────────────────────────────────────
        val lensEntries = buildLensEntries(exif, labels)
        if (lensEntries.isNotEmpty()) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                    Text(
                        stringRes { inspectorLens },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                    lensEntries.forEach { (key, value) ->
                        InspectorRow(key, value)
                    }
                }
            }
        }

        // ── Capture Parameters ─────────────────────────────────────
        val captureEntries = buildCaptureEntries(exif, labels)
        if (captureEntries.isNotEmpty()) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                    Text(
                        stringRes { inspectorCapture },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                    captureEntries.forEach { (key, value) ->
                        InspectorRow(key, value)
                    }
                }
            }
        }

        // ── Color & Time ───────────────────────────────────────────
        val colorTimeEntries = buildColorTimeEntries(exif, labels)
        if (colorTimeEntries.isNotEmpty()) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                    Text(
                        stringRes { inspectorColorTime },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                    colorTimeEntries.forEach { (key, value) ->
                        InspectorRow(key, value)
                    }
                }
            }
        }

        // ── RAW-specific info ──────────────────────────────────────
        if (isRaw) {
            val rawEntries = buildRawEntries(exif, labels)
            if (rawEntries.isNotEmpty()) {
                LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                        Text(
                            stringRes { inspectorRawSection },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                        rawEntries.forEach { (key, value) ->
                            InspectorRow(key, value)
                        }
                    }
                }
            }
        }

        // ── DJI-specific info ──────────────────────────────────────
        if (isDji) {
            val djiEntries = buildDjiEntries(exif, labels)
            if (djiEntries.isNotEmpty()) {
                LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                        Text(
                            stringRes { inspectorDjiFlight },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                        djiEntries.forEach { (key, value) ->
                            InspectorRow(key, value)
                        }
                    }
                }
            }
        }

        // ── AI Scene (Huawei/Xiaomi) ───────────────────────────────
        val aiEntries = buildAiEntries(exif, labels)
        if (aiEntries.isNotEmpty()) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                    Text(
                        stringRes { inspectorAiScene },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                    aiEntries.forEach { (key, value) ->
                        InspectorRow(key, value)
                    }
                }
            }
        }

        // ── GPS ────────────────────────────────────────────────────
        if (exif.gpsLatitude.isNotEmpty() || exif.gpsLongitude.isNotEmpty()) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                    Text(
                        stringRes { inspectorGpsSection },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                    if (exif.gpsLatitude.isNotEmpty()) {
                        InspectorRow(stringRes { inspectorLatitude }, exif.gpsLatitude)
                    }
                    if (exif.gpsLongitude.isNotEmpty()) {
                        InspectorRow(stringRes { inspectorLongitude }, exif.gpsLongitude)
                    }
                    if (exif.gpsAltitude.isNotEmpty()) {
                        InspectorRow(stringRes { inspectorAltitude }, exif.gpsAltitude)
                    }
                }
            }
        }

        // ── EXIF Data (raw view + edit) ────────────────────────────
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringRes { inspectorExifData },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (image.imagePath.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = { showExifEditor = true },
                            contentPadding = PaddingValues(horizontal = AlcedoSpacing.md, vertical = AlcedoSpacing.xs)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(AlcedoIconSize.sm)
                            )
                            Spacer(Modifier.width(AlcedoSpacing.sm))
                            Text(
                                stringRes { exifEditTitle },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                val entries = buildExifEntries(exif, labels)
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
                Column(modifier = Modifier.padding(AlcedoSpacing.md)) {
                    Text(
                        stringRes { inspectorRating },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(AlcedoSpacing.xs))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AlcedoSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        (1..5).forEach { star ->
                            Icon(
                                if (star <= rating) Icons.Default.Star else Icons.Default.StarOutline,
                                contentDescription = null,
                                tint = if (star <= rating) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(AlcedoIconSize.md)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildExifEntries(
    exif: com.alcedo.studio.data.model.ExifDisplayMetaData,
    labels: InspectorLabels
): List<Pair<String, String>> {
    val entries = mutableListOf<Pair<String, String>>()
    if (exif.cameraMake.isNotEmpty()) entries += labels.make to exif.cameraMake
    if (exif.cameraModel.isNotEmpty()) entries += labels.model to exif.cameraModel
    if (exif.lensModel.isNotEmpty()) entries += labels.lens to exif.lensModel
    if (exif.focalLength.isNotEmpty()) entries += labels.focalLength to exif.focalLength
    if (exif.aperture.isNotEmpty()) entries += labels.aperture to exif.aperture
    if (exif.shutterSpeed.isNotEmpty()) entries += labels.shutter to exif.shutterSpeed
    if (exif.iso.isNotEmpty()) entries += "ISO" to exif.iso
    if (exif.captureDate.isNotEmpty()) entries += labels.captureDate to exif.captureDate
    return entries
}

/** 相机信息：品牌 + 型号 */
private fun buildCameraEntries(
    exif: com.alcedo.studio.data.model.ExifDisplayMetaData,
    labels: InspectorLabels
): List<Pair<String, String>> {
    val entries = mutableListOf<Pair<String, String>>()
    if (exif.cameraMake.isNotEmpty()) entries += labels.make to exif.cameraMake
    if (exif.cameraModel.isNotEmpty()) entries += labels.model to exif.cameraModel
    return entries
}

/** 镜头信息：型号 + 焦距 + 最大光圈 */
private fun buildLensEntries(
    exif: com.alcedo.studio.data.model.ExifDisplayMetaData,
    labels: InspectorLabels
): List<Pair<String, String>> {
    val entries = mutableListOf<Pair<String, String>>()
    if (exif.lensModel.isNotEmpty()) entries += labels.model to exif.lensModel
    if (exif.focalLength.isNotEmpty()) entries += labels.focalLength to exif.focalLength.let {
        if (it.endsWith("mm")) it else "$it mm"
    }
    if (exif.focalLength35mm.isNotEmpty()) entries += labels.equiv35mm to exif.focalLength35mm.let {
        if (it.endsWith("mm")) it else "$it mm"
    }
    if (exif.maxAperture.isNotEmpty()) entries += labels.maxAperture to "f/${exif.maxAperture}"
    return entries
}

/** 拍摄参数：ISO + 快门 + 光圈 + 曝光补偿 + 焦距 */
private fun buildCaptureEntries(
    exif: com.alcedo.studio.data.model.ExifDisplayMetaData,
    labels: InspectorLabels
): List<Pair<String, String>> {
    val entries = mutableListOf<Pair<String, String>>()
    if (exif.iso.isNotEmpty()) entries += "ISO" to exif.iso
    if (exif.shutterSpeed.isNotEmpty()) entries += labels.shutter to formatShutterSpeed(exif.shutterSpeed)
    if (exif.aperture.isNotEmpty()) entries += labels.aperture to "f/${exif.aperture}"
    if (exif.exposureCompensation.isNotEmpty()) entries += labels.exposureEv to "${exif.exposureCompensation} EV"
    if (exif.focalLength.isNotEmpty()) entries += labels.focalLength to exif.focalLength.let {
        if (it.endsWith("mm")) it else "$it mm"
    }
    return entries
}

/** 色彩与时间：色彩空间 + 拍摄日期 */
private fun buildColorTimeEntries(
    exif: com.alcedo.studio.data.model.ExifDisplayMetaData,
    labels: InspectorLabels
): List<Pair<String, String>> {
    val entries = mutableListOf<Pair<String, String>>()
    if (exif.colorSpace.isNotEmpty()) entries += labels.colorSpace to formatColorSpace(exif.colorSpace)
    if (exif.captureDate.isNotEmpty()) entries += labels.captureDate to exif.captureDate
    return entries
}

/** RAW 特有信息：位深 + 白平衡 + 去马赛克算法 */
private fun buildRawEntries(
    exif: com.alcedo.studio.data.model.ExifDisplayMetaData,
    labels: InspectorLabels
): List<Pair<String, String>> {
    val entries = mutableListOf<Pair<String, String>>()
    if (exif.bitsPerSample > 0) {
        entries += labels.bitDepth to "${exif.bitsPerSample}-bit"
    }
    if (exif.whiteBalanceMode.isNotEmpty()) {
        entries += labels.whiteBalance to exif.whiteBalanceMode
    }
    if (exif.demosaicAlgorithm.isNotEmpty()) {
        entries += labels.demosaic to exif.demosaicAlgorithm
    } else {
        entries += labels.demosaic to labels.defaultDemosaic
    }
    return entries
}

/** 大疆特有信息：飞行高度 + GPS 模式 + 云台角度 */
private fun buildDjiEntries(
    exif: com.alcedo.studio.data.model.ExifDisplayMetaData,
    labels: InspectorLabels
): List<Pair<String, String>> {
    val entries = mutableListOf<Pair<String, String>>()
    if (exif.djiFlightHeight.isNotEmpty()) entries += labels.flightHeight to exif.djiFlightHeight
    if (exif.djiGpsMode.isNotEmpty()) entries += labels.gpsMode to exif.djiGpsMode
    if (exif.djiGimbalPitch.isNotEmpty()) entries += labels.gimbalPitch to exif.djiGimbalPitch
    return entries
}

/** AI 场景识别 + 多帧合成（华为/小米） */
private fun buildAiEntries(
    exif: com.alcedo.studio.data.model.ExifDisplayMetaData,
    labels: InspectorLabels
): List<Pair<String, String>> {
    val entries = mutableListOf<Pair<String, String>>()
    if (exif.aiScene.isNotEmpty()) entries += labels.aiScene to exif.aiScene
    if (exif.multiFrameInfo.isNotEmpty()) entries += labels.multiFrame to exif.multiFrameInfo
    return entries
}

/**
 * Holds i18n label strings resolved in a @Composable scope so they can be
 * passed into non-composable helper functions.
 */
private data class InspectorLabels(
    val make: String,
    val model: String,
    val lens: String,
    val focalLength: String,
    val equiv35mm: String,
    val maxAperture: String,
    val shutter: String,
    val aperture: String,
    val exposureEv: String,
    val colorSpace: String,
    val captureDate: String,
    val bitDepth: String,
    val whiteBalance: String,
    val demosaic: String,
    val defaultDemosaic: String,
    val flightHeight: String,
    val gpsMode: String,
    val gimbalPitch: String,
    val aiScene: String,
    val multiFrame: String
)

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
            .padding(vertical = AlcedoSpacing.xs),
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
        else -> "${bytes} B"
    }
}
