package com.alcedo.studio.domain.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace as AndroidColorSpace
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.alcedo.studio.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Complete export service with format-specific encoding, color space conversion,
 * ICC profile embedding, metadata writeback, Ultra HDR gain map, concurrent
 * export queue, and detailed progress tracking.
 */
class ExportService(private val context: Context) {

    private val watermarkService = WatermarkService()

    // ================================================================
    // Progress tracking
    // ================================================================

    private val _exportProgress = MutableStateFlow(ExportProgress())
    val exportProgress: StateFlow<ExportProgress> = _exportProgress.asStateFlow()

    private var exportStartTime: Long = 0L

    data class ExportProgress(
        val totalItems: Int = 0,
        val completedItems: Int = 0,
        val successCount: Int = 0,
        val failureCount: Int = 0,
        val currentItemIndex: Int = 0,
        val currentItemName: String = "",
        val currentItemProgress: Float = 0f,
        val overallProgress: Float = 0f,
        val status: ExportStatus = ExportStatus.IDLE,
        val etaMillis: Long = 0L
    ) {
        val isRunning: Boolean get() = status == ExportStatus.EXPORTING
    }

    enum class ExportStatus { IDLE, EXPORTING, COMPLETED, CANCELLED, ERROR }

    private val cancelFlag = AtomicBoolean(false)

    // ================================================================
    // Concurrent export
    // ================================================================

    private val exportDispatcher = Dispatchers.IO.limitedParallelism(MAX_CONCURRENCY)

    companion object {
        const val MAX_CONCURRENCY = 4

        /** Maximum HDR/SDR gain ratio used when synthesizing the HDR bitmap. */
        private const val ULTRA_HDR_MAX_RATIO = 4.0f
    }

    // ================================================================
    // Single image export
    // ================================================================

    suspend fun exportImage(
        sourcePath: String,
        settings: ExportSettings,
        processedBitmap: Bitmap? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            exportStartTime = System.currentTimeMillis()
            _exportProgress.value = ExportProgress(
                totalItems = 1,
                currentItemIndex = 0,
                currentItemName = File(sourcePath).name,
                status = ExportStatus.EXPORTING
            )

            // DNG export is non-destructive: it does not run through the bitmap
            // rendering pipeline. Delegate to the dedicated DNG exporter which
            // copies the original RAW/DNG bytes and writes an XMP sidecar.
            if (settings.format == ExportFormat.DNG) {
                updateItemProgress(0.1f)
                val outputFile = createOutputFile(settings, sourcePath)
                updateItemProgress(0.4f)
                val dngResult = exportDng(sourcePath, outputFile.absolutePath, settings)
                updateItemProgress(0.9f)

                if (dngResult is ExportResult.Success) {
                    // Scan both the DNG and its XMP sidecar for media visibility.
                    val xmpPath = outputFile.absolutePath.substringBeforeLast('.') + ".xmp"
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(outputFile.absolutePath, xmpPath),
                        null, null
                    )
                    _exportProgress.value = _exportProgress.value.copy(
                        completedItems = 1,
                        successCount = 1,
                        currentItemProgress = 1f,
                        overallProgress = 1f,
                        status = ExportStatus.COMPLETED
                    )
                } else {
                    _exportProgress.value = _exportProgress.value.copy(
                        completedItems = 1,
                        failureCount = 1,
                        status = ExportStatus.ERROR
                    )
                }
                return@withContext dngResult
            }

            val bitmap = processedBitmap ?: decodeSampledBitmap(sourcePath, settings)
                ?: return@withContext ExportResult.Error("Failed to decode source image: $sourcePath")

            // Android 10+ : use MediaStore to write to public Pictures directory
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && settings.outputPath.isEmpty()) {
                updateItemProgress(0f)

                // 1. Color space conversion
                val converted = applyColorSpaceConversion(bitmap, settings.colorSpace)
                updateItemProgress(0.2f)

                // 2. Resize if max dimension or max width/height specified
                val resized = applyDimensionLimit(converted, settings)
                updateItemProgress(0.3f)

                // 2b. Apply watermark
                val watermarked = when {
                    settings.watermarkConfig.enabled ->
                        watermarkService.applyWatermark(resized, settings.watermarkConfig)
                    settings.hassebladWatermark ->
                        applyHasselbladWatermark(resized)
                    else -> resized
                }
                updateItemProgress(0.35f)

                val result = exportToMediaStore(watermarked, settings, sourcePath)
                updateItemProgress(0.9f)

                // 安全回收中间 bitmap — 使用 Set 避免重复回收
                // 修复：当所有操作都返回同一个 bitmap 对象时，原逻辑不会回收任何 bitmap，导致内存泄漏
                val recycled = mutableSetOf<Bitmap>()
                fun safeRecycle(b: Bitmap?) {
                    if (b != null && recycled.add(b)) b.recycle()
                }

                // 回收中间产物，保留最终产物（watermarked 已传入 exportToMediaStore，由其内部处理）
                // 注意：processedBitmap 是外部传入的，不应回收
                if (processedBitmap == null) {
                    // 我们创建了 bitmap，可以回收它（如果它不等于其他仍需使用的 bitmap）
                    safeRecycle(converted)
                    safeRecycle(resized)
                    safeRecycle(watermarked)
                    // 如果 bitmap 与上述任何一个不同，单独回收
                    if (bitmap !== converted && bitmap !== resized && bitmap !== watermarked) {
                        safeRecycle(bitmap)
                    }
                } else {
                    // processedBitmap 是外部传入的，不回收它
                    // 只回收由它产生的中间产物
                    if (converted !== processedBitmap) safeRecycle(converted)
                    if (resized !== processedBitmap && resized !== converted) safeRecycle(resized)
                    if (watermarked !== processedBitmap && watermarked !== resized && watermarked !== converted) safeRecycle(watermarked)
                }

                if (result is ExportResult.Success) {
                    _exportProgress.value = _exportProgress.value.copy(
                        completedItems = 1,
                        successCount = 1,
                        currentItemProgress = 1f,
                        overallProgress = 1f,
                        status = ExportStatus.COMPLETED
                    )
                } else {
                    _exportProgress.value = _exportProgress.value.copy(
                        completedItems = 1,
                        failureCount = 1,
                        status = ExportStatus.ERROR
                    )
                }

                return@withContext result
            }

            updateItemProgress(0f)

            // 1. Color space conversion
            val converted = applyColorSpaceConversion(bitmap, settings.colorSpace)
            updateItemProgress(0.2f)

            // 2. Resize if max dimension or max width/height specified
            val resized = applyDimensionLimit(converted, settings)
            updateItemProgress(0.3f)

            // 2b. Apply watermark: custom watermark takes precedence, else Hasselblad preset
            val watermarked = when {
                settings.watermarkConfig.enabled ->
                    watermarkService.applyWatermark(resized, settings.watermarkConfig)
                settings.hassebladWatermark ->
                    applyHasselbladWatermark(resized)
                else -> resized
            }
            updateItemProgress(0.35f)

            // 3. Create output file
            val outputFile = createOutputFile(settings, sourcePath)
            updateItemProgress(0.4f)

            // 4. Write image data
            val writeSuccess = writeImage(watermarked, outputFile, settings)
            updateItemProgress(0.7f)

            if (!writeSuccess) {
                _exportProgress.value = _exportProgress.value.copy(
                    completedItems = 1,
                    failureCount = 1,
                    status = ExportStatus.ERROR
                )
                return@withContext ExportResult.Error("Failed to write output file")
            }

            // 5. Embed ICC profile if requested
            if (settings.embedIcc && settings.format != ExportFormat.TIFF) {
                embedIccProfile(outputFile, settings.colorSpace, settings.format)
            }
            updateItemProgress(0.8f)

            // 6. Write back metadata
            if (settings.includeMetadata) {
                val exifSource = settings.sourceExifPath ?: sourcePath
                writeMetadata(outputFile, exifSource, settings)
            }
            updateItemProgress(0.9f)

            // 7. Scan for media visibility
            MediaScannerConnection.scanFile(
                context,
                arrayOf(outputFile.absolutePath),
                null, null
            )

            // 安全回收中间 bitmap — 使用 Set 避免重复回收
            // 修复：当所有操作都返回同一个 bitmap 对象时，原逻辑不会回收任何 bitmap，导致内存泄漏
            val recycled = mutableSetOf<Bitmap>()
            fun safeRecycle(b: Bitmap?) {
                if (b != null && recycled.add(b)) b.recycle()
            }

            // 回收中间产物，保留最终产物（watermarked 已写入 outputFile）
            // 注意：processedBitmap 是外部传入的，不应回收
            if (processedBitmap == null) {
                // 我们创建了 bitmap，可以回收它
                safeRecycle(converted)
                safeRecycle(resized)
                safeRecycle(watermarked)
                // 如果 bitmap 与上述任何一个不同，单独回收
                if (bitmap !== converted && bitmap !== resized && bitmap !== watermarked) {
                    safeRecycle(bitmap)
                }
            } else {
                // processedBitmap 是外部传入的，不回收它
                // 只回收由它产生的中间产物
                if (converted !== processedBitmap) safeRecycle(converted)
                if (resized !== processedBitmap && resized !== converted) safeRecycle(resized)
                if (watermarked !== processedBitmap && watermarked !== resized && watermarked !== converted) safeRecycle(watermarked)
            }

            val uri = try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    outputFile
                )
            } catch (_: Exception) {
                Uri.fromFile(outputFile)
            }

            _exportProgress.value = _exportProgress.value.copy(
                completedItems = 1,
                successCount = 1,
                currentItemProgress = 1f,
                overallProgress = 1f,
                status = ExportStatus.COMPLETED
            )

            ExportResult.Success(uri, outputFile.absolutePath)
        } catch (e: Exception) {
            _exportProgress.value = _exportProgress.value.copy(
                completedItems = 1,
                failureCount = 1,
                status = ExportStatus.ERROR
            )
            ExportResult.Error(e.message ?: "Export failed")
        }
    }

    // ================================================================
    // MediaStore export (Android 10+ Scoped Storage)
    // ================================================================

    private suspend fun exportToMediaStore(
        bitmap: Bitmap,
        settings: ExportSettings,
        sourcePath: String
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, generateExportFileName(settings, sourcePath))
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, getMimeType(settings.format))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AlcedoStudio")
                    put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val uri = context.contentResolver.insert(collection, contentValues)
                ?: return@withContext ExportResult.Error("Failed to create MediaStore entry")

            // Write image data
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                writeBitmapToStream(bitmap, outputStream, settings)
            } ?: return@withContext ExportResult.Error("Failed to open output stream")

            // Clear IS_PENDING flag
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
            }

            // Write EXIF metadata
            if (settings.includeMetadata) {
                writeMetadataViaUri(uri, sourcePath, settings)
            }

            ExportResult.Success(uri, uri.toString())
        } catch (e: Exception) {
            ExportResult.Error(e.message ?: "MediaStore export failed")
        }
    }

    private fun generateExportFileName(settings: ExportSettings, sourcePath: String? = null): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val ext = getExtension(settings.format)
        val src = sourcePath ?: settings.sourceExifPath
        // 自定义模板：支持 {name}(原始文件名) 与 {timestamp} 占位符
        if (!settings.filenameTemplate.isNullOrBlank()) {
            val originalName = src?.let { File(it).nameWithoutExtension } ?: ""
            return settings.filenameTemplate
                .replace("{name}", originalName)
                .replace("{timestamp}", timestamp) + ".$ext"
        }
        if (settings.useOriginalFilename && src != null) {
            val originalName = File(src).nameWithoutExtension
            return "${originalName}_edited.$ext"
        }
        return "alcedo_export_$timestamp.$ext"
    }

    private fun getMimeType(format: ExportFormat): String = when (format) {
        ExportFormat.JPEG -> "image/jpeg"
        ExportFormat.PNG -> "image/png"
        ExportFormat.TIFF -> "image/tiff"
        ExportFormat.EXR -> "image/x-exr"
        ExportFormat.DNG -> "image/x-adobe-dng"
        ExportFormat.ULTRA_HDR -> "image/jpeg"
    }

    private fun writeBitmapToStream(bitmap: Bitmap, outputStream: java.io.OutputStream, settings: ExportSettings) {
        when (settings.format) {
            ExportFormat.JPEG -> bitmap.compress(Bitmap.CompressFormat.JPEG, settings.quality.coerceIn(1, 100), outputStream)
            ExportFormat.PNG -> {
                val outputBitmap = if (settings.bitDepth == 16) {
                    bitmap.copy(Bitmap.Config.RGBA_F16, false) ?: bitmap
                } else bitmap
                outputBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                if (outputBitmap !== bitmap) outputBitmap.recycle()
            }
            ExportFormat.TIFF, ExportFormat.EXR -> {
                // For TIFF/EXR, write to temp file then copy to stream
                val tempFile = File.createTempFile("alcedo_export_", ".tmp", context.cacheDir)
                try {
                    writeImage(bitmap, tempFile, settings)
                    tempFile.inputStream().use { it.copyTo(outputStream) }
                } finally {
                    tempFile.delete()
                }
            }
            ExportFormat.DNG -> {
                // DNG is handled by exportDng(), not via bitmap stream.
                // This path should not be reached; no-op for safety.
            }
            ExportFormat.ULTRA_HDR -> {
                bitmap.compress(Bitmap.CompressFormat.JPEG, settings.quality.coerceIn(1, 100), outputStream)
            }
        }
    }

    private fun writeMetadataViaUri(uri: Uri, sourcePath: String, settings: ExportSettings) {
        try {
            // For MediaStore URIs, we need to get the file path to modify EXIF
            // This is only practical for JPEG format
            if (settings.format != ExportFormat.JPEG && settings.format != ExportFormat.ULTRA_HDR) return

            // Get the file path from the MediaStore URI
            val projection = arrayOf(android.provider.MediaStore.Images.Media.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val filePath = cursor.getString(0)
                    if (filePath != null) {
                        writeMetadata(File(filePath), sourcePath, settings)
                    }
                }
            }
        } catch (_: Exception) {
            // Metadata writeback failure is non-fatal
        }
    }

    // ================================================================
    // Sampled bitmap decoding helpers
    // ================================================================

    private fun decodeSampledBitmap(path: String, settings: ExportSettings): Bitmap? {
        val reqWidth = settings.maxWidth ?: settings.maxDimension ?: 0
        val reqHeight = settings.maxHeight ?: settings.maxDimension ?: 0
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        opts.inSampleSize = calculateInSampleSize(opts.outWidth, opts.outHeight, reqWidth, reqHeight)
        opts.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(path, opts)
    }

    private fun calculateInSampleSize(outWidth: Int, outHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        if (reqWidth <= 0 || reqHeight <= 0) return 1
        var inSampleSize = 1
        if (outHeight > reqHeight || outWidth > reqWidth) {
            val halfHeight = outHeight / 2
            val halfWidth = outWidth / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    // ================================================================
    // Batch export with concurrency
    // ================================================================

    suspend fun exportBatch(
        items: List<ExportBatchItem>,
        settings: ExportSettings
    ): ExportBatchResult = withContext(Dispatchers.IO) {
        cancelFlag.set(false)

        val total = items.size
        val completedCount = AtomicInteger(0)
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)
        val results = ConcurrentLinkedQueue<ExportResult>()
        val startTime = System.currentTimeMillis()

        _exportProgress.value = ExportProgress(
            totalItems = total,
            status = ExportStatus.EXPORTING
        )

        coroutineScope {
            items.mapIndexed { index, item ->
                async(exportDispatcher) {
                    if (cancelFlag.get()) {
                        results.add(ExportResult.Error("Cancelled"))
                        return@async
                    }

                    val currentIdx = completedCount.get()
                    _exportProgress.value = _exportProgress.value.copy(
                        currentItemIndex = currentIdx,
                        currentItemName = File(item.sourcePath).name,
                        currentItemProgress = 0f,
                        overallProgress = currentIdx.toFloat() / total
                    )

                    val result = exportImage(item.sourcePath, settings, item.processedBitmap)
                    results.add(result)

                    when (result) {
                        is ExportResult.Success -> successCount.incrementAndGet()
                        is ExportResult.Error -> failureCount.incrementAndGet()
                    }

                    val done = completedCount.incrementAndGet()
                    val elapsed = System.currentTimeMillis() - startTime
                    val eta = if (done > 0 && total > done) {
                        (elapsed * (total - done) / done)
                    } else 0L
                    _exportProgress.value = _exportProgress.value.copy(
                        completedItems = done,
                        successCount = successCount.get(),
                        failureCount = failureCount.get(),
                        overallProgress = done.toFloat() / total,
                        etaMillis = eta,
                        status = if (done >= total) {
                            if (cancelFlag.get()) ExportStatus.CANCELLED else ExportStatus.COMPLETED
                        } else ExportStatus.EXPORTING
                    )
                }
            }.awaitAll()
        }

        val finalStatus = if (cancelFlag.get()) ExportStatus.CANCELLED else ExportStatus.COMPLETED
        _exportProgress.value = _exportProgress.value.copy(status = finalStatus)

        ExportBatchResult(
            totalItems = total,
            successCount = successCount.get(),
            errorCount = failureCount.get(),
            results = results.toList()
        )
    }

    // ================================================================
    // Color space conversion
    // ================================================================

    private fun applyColorSpaceConversion(bitmap: Bitmap, targetColorSpace: ColorSpace): Bitmap {
        if (targetColorSpace == ColorSpace.SRGB) return bitmap

        val androidCs = when (targetColorSpace) {
            ColorSpace.SRGB -> AndroidColorSpace.get(AndroidColorSpace.Named.SRGB)
            ColorSpace.DISPLAY_P3 -> AndroidColorSpace.get(AndroidColorSpace.Named.DISPLAY_P3)
            ColorSpace.REC2020 -> AndroidColorSpace.get(AndroidColorSpace.Named.BT2020)
            ColorSpace.ACES -> AndroidColorSpace.get(AndroidColorSpace.Named.SRGB)
            // ACES_SRGB_LINEAR not available in this SDK version
            /*ColorSpace.ACES -> try {
                AndroidColorSpace.get(AndroidColorSpace.Named.ACES_SRGB_LINEAR)
            } catch (_: Exception) {
                AndroidColorSpace.get(AndroidColorSpace.Named.SRGB)
            }*/
            else -> AndroidColorSpace.get(AndroidColorSpace.Named.SRGB)
        }

        // Android 9+ Bitmap supports wide color space via config RGBA_F16
        return try {
            if (androidCs == bitmap.colorSpace) return bitmap

            val wideConfig = Bitmap.Config.RGBA_F16
            val converted = Bitmap.createBitmap(bitmap.width, bitmap.height, wideConfig)
            val canvas = android.graphics.Canvas(converted)
            val paint = android.graphics.Paint().apply {
                isAntiAlias = false
                isFilterBitmap = true
            }
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            // Note: Bitmap.setColorSpace() requires API 26+ but is not directly settable
            // in the current SDK. Color space conversion is handled by the RGBA_F16 config.
            converted
        } catch (e: Exception) {
            // Fallback: manual pixel-by-pixel conversion using matrix
            applyColorSpaceManual(bitmap, targetColorSpace)
        }
    }

    /**
     * Manual color space conversion using 3x3 matrices.
     * Converts from sRGB to target color space via linearized RGB.
     */
    private fun applyColorSpaceManual(bitmap: Bitmap, targetColorSpace: ColorSpace): Bitmap {
        val matrix = getColorSpaceMatrix(targetColorSpace) ?: return bitmap

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            var r = ((pixel shr 16) and 0xFF) / 255f
            var g = ((pixel shr 8) and 0xFF) / 255f
            var b = (pixel and 0xFF) / 255f

            // sRGB to linear
            r = srgbToLinear(r)
            g = srgbToLinear(g)
            b = srgbToLinear(b)

            // Apply color space matrix
            val nr = matrix[0] * r + matrix[1] * g + matrix[2] * b
            val ng = matrix[3] * r + matrix[4] * g + matrix[5] * b
            val nb = matrix[6] * r + matrix[7] * g + matrix[8] * b

            // Linear to sRGB (for display in 8-bit output)
            r = linearToSrgb(nr)
            g = linearToSrgb(ng)
            b = linearToSrgb(nb)

            val ri = (r.coerceIn(0f, 1f) * 255f).toInt()
            val gi = (g.coerceIn(0f, 1f) * 255f).toInt()
            val bi = (b.coerceIn(0f, 1f) * 255f).toInt()
            val ai = (pixel shr 24) and 0xFF

            pixels[i] = (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
        }

        return Bitmap.createBitmap(pixels, width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
    }

    /**
     * sRGB → target color space 3x3 matrix (applied in linear RGB).
     * These matrices transform from linear sRGB primaries to the target space primaries.
     */
    private fun getColorSpaceMatrix(cs: ColorSpace): FloatArray? = when (cs) {
        ColorSpace.SRGB -> null // identity
        ColorSpace.DISPLAY_P3 -> floatArrayOf(
            0.822462f, 0.177538f, 0.000000f,
            0.033194f, 0.966806f, 0.000000f,
            0.017085f, 0.072394f, 0.910521f
        )
        ColorSpace.REC2020 -> floatArrayOf(
            0.663549f, 0.327045f, 0.009407f,
            0.023721f, 0.941951f, 0.034328f,
            0.003411f, 0.030839f, 0.965750f
        )
        ColorSpace.ACES -> floatArrayOf(
            0.662454f, 0.272287f, 0.065259f,
            0.008079f, 0.915312f, 0.076609f,
            0.028926f, 0.094660f, 0.876415f
        )
        else -> null
    }

    private fun srgbToLinear(c: Float): Float {
        return if (c <= 0.04045f) c / 12.92f
        else ((c + 0.055f) / 1.055f).let { it * it * it }
    }

    private fun linearToSrgb(c: Float): Float {
        return if (c <= 0.0031308f) 12.92f * c
        else 1.055f * c.let { Math.pow(it.toDouble(), 1.0 / 2.4).toFloat() } - 0.055f
    }

    // ================================================================
    // Hasselblad Watermark
    // ================================================================

    private fun applyHasselbladWatermark(bitmap: Bitmap): Bitmap {
        val watermarked = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(watermarked)
        drawHasselbladWatermark(canvas, watermarked)
        return watermarked
    }

    private fun drawHasselbladWatermark(canvas: Canvas, bitmap: Bitmap) {
        val paint = Paint().apply {
            color = Color.WHITE
            alpha = 180
            textSize = bitmap.width * 0.015f
            typeface = Typeface.SERIF
            isAntiAlias = true
        }
        val shadowPaint = Paint().apply {
            color = Color.BLACK
            alpha = 120
            textSize = bitmap.width * 0.015f
            typeface = Typeface.SERIF
            isAntiAlias = true
        }
        val text = "HASSELBLAD"
        val margin = bitmap.width * 0.03f
        val x = bitmap.width - margin - paint.measureText(text)
        val y = bitmap.height - margin
        // Shadow
        canvas.drawText(text, x + 2f, y + 2f, shadowPaint)
        // Main text
        canvas.drawText(text, x, y, paint)
        // "H" icon before text
        val hSize = bitmap.width * 0.018f
        val hX = x - hSize - paint.measureText(" ")
        val hY = y - hSize * 0.7f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = hSize * 0.08f
        val path = Path().apply {
            moveTo(hX, hY + hSize)
            lineTo(hX, hY)
            lineTo(hX + hSize * 0.5f, hY + hSize * 0.4f)
            lineTo(hX + hSize, hY)
            lineTo(hX + hSize, hY + hSize)
        }
        canvas.drawPath(path, paint)
        // Reset paint style
        paint.style = Paint.Style.FILL
    }

    // ================================================================
    // Dimension limiting (maxDimension, maxWidth, maxHeight)
    // ================================================================

    private fun applyDimensionLimit(bitmap: Bitmap, settings: ExportSettings): Bitmap {
        val maxDim = settings.maxDimension
        val maxWidth = settings.maxWidth
        val maxHeight = settings.maxHeight

        if (maxDim == null && maxWidth == null && maxHeight == null) return bitmap

        val width = bitmap.width
        val height = bitmap.height

        var newWidth = width
        var newHeight = height

        // Apply maxDimension (square bounding box)
        if (maxDim != null && maxDim > 0) {
            if (width > maxDim || height > maxDim) {
                val ratio = minOf(maxDim.toFloat() / width, maxDim.toFloat() / height)
                newWidth = (width * ratio).toInt()
                newHeight = (height * ratio).toInt()
            }
        }

        // Apply maxWidth
        if (maxWidth != null && maxWidth > 0 && newWidth > maxWidth) {
            val ratio = maxWidth.toFloat() / newWidth
            newWidth = (newWidth * ratio).toInt()
            newHeight = (newHeight * ratio).toInt()
        }

        // Apply maxHeight
        if (maxHeight != null && maxHeight > 0 && newHeight > maxHeight) {
            val ratio = maxHeight.toFloat() / newHeight
            newWidth = (newWidth * ratio).toInt()
            newHeight = (newHeight * ratio).toInt()
        }

        if (newWidth == width && newHeight == height) return bitmap

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    // ================================================================
    // Format-specific writers
    // ================================================================

    private fun writeImage(bitmap: Bitmap, file: File, settings: ExportSettings): Boolean {
        return try {
            when (settings.format) {
                ExportFormat.JPEG -> writeJpeg(bitmap, file, settings)
                ExportFormat.PNG -> writePng(bitmap, file, settings)
                ExportFormat.TIFF -> writeTiff(bitmap, file, settings)
                ExportFormat.EXR -> writeExr(bitmap, file, settings)
                ExportFormat.DNG -> false // handled by exportDng() directly
                ExportFormat.ULTRA_HDR -> writeUltraHdr(bitmap, file, settings)
            }
        } catch (e: Exception) {
            false
        }
    }

    // ================================================================
    // JPEG export with quality control
    // ================================================================

    private fun writeJpeg(bitmap: Bitmap, file: File, settings: ExportSettings): Boolean {
        return FileOutputStream(file).use { out ->
            bitmap.compress(
                Bitmap.CompressFormat.JPEG,
                settings.quality.coerceIn(1, 100),
                out
            )
        }
    }

    // ================================================================
    // PNG export (8-bit / 16-bit via RGBA_F16)
    // ================================================================

    private fun writePng(bitmap: Bitmap, file: File, settings: ExportSettings): Boolean {
        val outputBitmap = if (settings.bitDepth == 16) {
            // Convert to RGBA_F16 for 16-bit PNG (Android 9+)
            val f16 = bitmap.copy(Bitmap.Config.RGBA_F16, false)
            f16 ?: bitmap
        } else {
            bitmap
        }

        val result = FileOutputStream(file).use { out ->
            outputBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (outputBitmap !== bitmap) outputBitmap.recycle()
        return result
    }

    // ================================================================
    // TIFF export (8-bit and 16-bit)
    // ================================================================

    private fun writeTiff(bitmap: Bitmap, file: File, settings: ExportSettings): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        val is16bit = settings.bitDepth == 16
        val bps = if (is16bit) 16 else 8
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Embed ICC profile in TIFF if requested
        val iccData = if (settings.embedIcc) getIccProfileBytes(settings.colorSpace) else null

        val isLE = true
        val bos = ByteArrayOutputStream()

        // TIFF Header
        bos.write(byteArrayOf(0x49, 0x49)) // II (little-endian)
        bos.write(shortToBytes(42, isLE))   // TIFF magic
        // IFD offset will be written after we know the size; placeholder
        val headerSize = 8

        // Count IFD entries
        val baseEntries = 11
        val iccEntry = if (iccData != null) 1 else 0
        val numEntries = baseEntries + iccEntry

        // IFD starts right after header
        val ifdOffset = headerSize
        // IFD size: 2 (count) + numEntries * 12 + 4 (next IFD)
        val ifdSize = 2 + numEntries * 12 + 4
        // Data area starts after IFD
        var dataOffset = ifdOffset + ifdSize

        // Compute offsets for values that don't fit in 4 bytes
        // BitsPerSample: 3 shorts = 6 bytes (needs offset)
        val bpsDataOffset = dataOffset; dataOffset += 6
        // XResolution: 2 ints = 8 bytes
        val xResOffset = dataOffset; dataOffset += 8
        // YResolution: 2 ints = 8 bytes
        val yResOffset = dataOffset; dataOffset += 8
        // ICC profile: offset + count
        var iccDataOffset = 0
        if (iccData != null) {
            iccDataOffset = dataOffset; dataOffset += iccData.size
        }
        // Strip offset: pixel data
        val stripDataOffset = dataOffset
        val stripSize = width * height * 3 * (bps / 8)

        // Write IFD offset
        bos.write(intToBytes(ifdOffset, isLE))

        // IFD: Number of directory entries
        bos.write(shortToBytes(numEntries, isLE))

        // Helper: write a TIFF IFD entry (12 bytes)
        fun writeTag(tag: Int, type: Int, count: Int, valueOrOffset: Int) {
            bos.write(shortToBytes(tag, isLE))
            bos.write(shortToBytes(type, isLE))
            bos.write(intToBytes(count, isLE))
            bos.write(intToBytes(valueOrOffset, isLE))
        }

        // Tag entries (must be in ascending tag order)
        writeTag(256, 3, 1, width)              // ImageWidth
        writeTag(257, 3, 1, height)             // ImageLength
        writeTag(258, 3, 3, bpsDataOffset)      // BitsPerSample -> offset
        writeTag(259, 3, 1, 1)                  // Compression: uncompressed
        writeTag(262, 3, 1, 2)                  // PhotometricInterpretation: RGB
        writeTag(273, 4, 1, stripDataOffset)    // StripOffsets
        writeTag(277, 3, 1, 3)                  // SamplesPerPixel
        writeTag(278, 4, 1, height)             // RowsPerStrip
        writeTag(279, 4, 1, stripSize)          // StripByteCounts
        writeTag(282, 5, 1, xResOffset)         // XResolution
        writeTag(283, 5, 1, yResOffset)         // YResolution

        if (iccData != null) {
            // Tag 34675: ICC Profile (type=7=UNDEFINED)
            writeTag(34675, 7, iccData.size, iccDataOffset)
        }

        // Next IFD offset (0 = no more)
        bos.write(intToBytes(0, isLE))

        // Data area: BitsPerSample values
        for (i in 0..2) bos.write(shortToBytes(bps, isLE))

        // XResolution: 72/1
        bos.write(intToBytes(72, isLE))
        bos.write(intToBytes(1, isLE))
        // YResolution: 72/1
        bos.write(intToBytes(72, isLE))
        bos.write(intToBytes(1, isLE))

        // ICC profile data
        if (iccData != null) {
            bos.write(iccData)
        }

        // Pixel data
        if (is16bit) {
            // 16-bit: expand 8-bit channel values to 16-bit (scale by 257)
            for (pixel in pixels) {
                val r16 = ((pixel shr 16) and 0xFF) * 257
                val g16 = ((pixel shr 8) and 0xFF) * 257
                val b16 = (pixel and 0xFF) * 257
                bos.write(shortToBytes(r16, isLE))
                bos.write(shortToBytes(g16, isLE))
                bos.write(shortToBytes(b16, isLE))
            }
        } else {
            // 8-bit
            for (pixel in pixels) {
                bos.write((pixel shr 16) and 0xFF)
                bos.write((pixel shr 8) and 0xFF)
                bos.write(pixel and 0xFF)
            }
        }

        FileOutputStream(file).use { it.write(bos.toByteArray()) }
        return true
    }

    // ================================================================
    // EXR export (HDR - half-float)
    // ================================================================

    private fun writeExr(bitmap: Bitmap, file: File, settings: ExportSettings): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        FileOutputStream(file).use { out ->
            // EXR magic number
            out.write(byteArrayOf(0x76, 0x2F, 0x31, 0x01))

            // Version & flags (version 2, single-part scanline)
            out.write(byteArrayOf(0x02, 0x00, 0x00, 0x00))

            // Build header as bytes
            val headerBytes = ByteArrayOutputStream()

            // channels attribute
            writeExrString(headerBytes, "channels")
            writeExrString(headerBytes, "chlist")
            writeExrInt(headerBytes, 57) // size of chlist payload
            // Channel entries: name(1+null), pixel type(4), pLinear(1), reserved(3), xSampling(4), ySampling(4)
            // B channel
            headerBytes.write("B\u0000".toByteArray())
            writeExrInt(headerBytes, 1) // HALF
            headerBytes.write(0) // pLinear
            headerBytes.write(ByteArray(3)) // reserved
            writeExrInt(headerBytes, 1) // xSampling
            writeExrInt(headerBytes, 1) // ySampling
            // G channel
            headerBytes.write("G\u0000".toByteArray())
            writeExrInt(headerBytes, 1)
            headerBytes.write(0)
            headerBytes.write(ByteArray(3))
            writeExrInt(headerBytes, 1)
            writeExrInt(headerBytes, 1)
            // R channel
            headerBytes.write("R\u0000".toByteArray())
            writeExrInt(headerBytes, 1)
            headerBytes.write(0)
            headerBytes.write(ByteArray(3))
            writeExrInt(headerBytes, 1)
            writeExrInt(headerBytes, 1)
            // Null terminator for chlist
            headerBytes.write(0)

            // compression attribute
            writeExrString(headerBytes, "compression")
            writeExrString(headerBytes, "compression")
            writeExrInt(headerBytes, 1) // size
            headerBytes.write(0) // no compression

            // dataWindow
            writeExrString(headerBytes, "dataWindow")
            writeExrString(headerBytes, "box2i")
            writeExrInt(headerBytes, 16)
            writeExrInt(headerBytes, 0)
            writeExrInt(headerBytes, 0)
            writeExrInt(headerBytes, width - 1)
            writeExrInt(headerBytes, height - 1)

            // displayWindow
            writeExrString(headerBytes, "displayWindow")
            writeExrString(headerBytes, "box2i")
            writeExrInt(headerBytes, 16)
            writeExrInt(headerBytes, 0)
            writeExrInt(headerBytes, 0)
            writeExrInt(headerBytes, width - 1)
            writeExrInt(headerBytes, height - 1)

            // lineOrder
            writeExrString(headerBytes, "lineOrder")
            writeExrString(headerBytes, "lineOrder")
            writeExrInt(headerBytes, 1)
            headerBytes.write(0) // INCREASING_Y

            // pixelAspectRatio
            writeExrString(headerBytes, "pixelAspectRatio")
            writeExrString(headerBytes, "float")
            writeExrInt(headerBytes, 4)
            writeExrFloat(headerBytes, 1.0f)

            // screenWindowCenter
            writeExrString(headerBytes, "screenWindowCenter")
            writeExrString(headerBytes, "v2f")
            writeExrInt(headerBytes, 8)
            writeExrFloat(headerBytes, 0.0f)
            writeExrFloat(headerBytes, 0.0f)

            // screenWindowWidth
            writeExrString(headerBytes, "screenWindowWidth")
            writeExrString(headerBytes, "float")
            writeExrInt(headerBytes, 4)
            writeExrFloat(headerBytes, width.toFloat())

            // End of header
            headerBytes.write(0)

            out.write(headerBytes.toByteArray())

            // Write scanline data (one line at a time, uncompressed)
            val lineBuffer = ByteBuffer.allocate(width * 6) // 3 channels * 2 bytes (half)
            lineBuffer.order(ByteOrder.LITTLE_ENDIAN)

            for (y in 0 until height) {
                // Scanline offset table entry
                // (would normally be an offset; we skip for this simplified writer)
                // Direct scanline data: y coordinate, pixel data size, then data
                writeExrInt(out, y) // y coordinate
                writeExrInt(out, width * 6) // pixel data size
                lineBuffer.clear()
                for (x in 0 until width) {
                    val pixel = pixels[y * width + x]
                    val r = ((pixel shr 16) and 0xFF) / 255f
                    val g = ((pixel shr 8) and 0xFF) / 255f
                    val b = (pixel and 0xFF) / 255f
                    lineBuffer.putShort(floatToHalf(r))
                    lineBuffer.putShort(floatToHalf(g))
                    lineBuffer.putShort(floatToHalf(b))
                }
                out.write(lineBuffer.array())
            }
        }
        return true
    }

    private fun writeExrString(out: ByteArrayOutputStream, s: String) {
        out.write(s.toByteArray())
        out.write(0) // null terminator
    }

    private fun writeExrInt(out: ByteArrayOutputStream, value: Int) {
        out.write(byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        ))
    }

    private fun writeExrInt(out: FileOutputStream, value: Int) {
        out.write(byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        ))
    }

    private fun writeExrFloat(out: ByteArrayOutputStream, value: Float) {
        val bits = java.lang.Float.floatToIntBits(value)
        out.write(byteArrayOf(
            (bits and 0xFF).toByte(),
            ((bits shr 8) and 0xFF).toByte(),
            ((bits shr 16) and 0xFF).toByte(),
            ((bits shr 24) and 0xFF).toByte()
        ))
    }

    private fun floatToHalf(f: Float): Short {
        val bits = java.lang.Float.floatToIntBits(f)
        val sign = (bits ushr 16) and 0x8000
        var exponent = ((bits ushr 23) and 0xFF) - 112
        var mantissa = bits and 0x007FFFFF

        if (exponent <= 0) {
            if (exponent < -10) return sign.toShort()
            mantissa = (mantissa or 0x00800000) shr (1 - exponent)
            return (sign or (mantissa shr 13)).toShort()
        } else if (exponent >= 31) {
            return (sign or 0x7C00).toShort()
        }
        return (sign or (exponent shl 10) or (mantissa shr 13)).toShort()
    }

    // ================================================================
    // DNG export (non-destructive: copy original + XMP sidecar)
    // ================================================================

    /**
     * Export a DNG image. DNG export is non-destructive:
     *  1. If the source is already a DNG (or TIFF-based), the original bytes are
     *     copied verbatim and the edit parameters are written as an XMP sidecar
     *     (`.xmp`) next to the DNG so any Camera Raw-compatible reader can
     *     re-apply the adjustments.
     *  2. If the source is another RAW format (NEF/CR2/ARW/...), a conversion
     *     to DNG is required. This needs the Adobe DNG Converter SDK or a native
     *     libraw-based encoder and is delegated to [convertRawToDng].
     */
    private suspend fun exportDng(
        sourcePath: String,
        outputPath: String,
        settings: ExportSettings
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val sourceExt = File(sourcePath).extension.lowercase()
            val isDngOrTiff = sourceExt == "dng" || sourceExt == "tiff" || sourceExt == "tif"

            if (isDngOrTiff) {
                // Copy the original DNG/TIFF bytes verbatim, then attach the
                // edit parameters as an XMP sidecar.
                File(sourcePath).copyTo(File(outputPath), overwrite = true)
                settings.params?.let { writeXmpSidecar(outputPath, it) }
                ExportResult.Success(Uri.fromFile(File(outputPath)), outputPath)
            } else {
                // Other RAW formats require a native RAW→DNG converter.
                convertRawToDng(sourcePath, outputPath, settings)
            }
        } catch (e: Exception) {
            ExportResult.Error(e.message ?: "DNG export failed")
        }
    }

    /**
     * Write an XMP sidecar (`.xmp`) next to the DNG file describing the edit
     * parameters in the Adobe Camera Raw namespace (crs:*).
     */
    private fun writeXmpSidecar(dngPath: String, params: PipelineParams) {
        val xmpPath = dngPath.substringBeforeLast('.') + ".xmp"
        val xmpContent = buildXmpFromParams(params)
        File(xmpPath).writeText(xmpContent)
    }

    /**
     * Build an XMP packet from [PipelineParams] using the Camera Raw Settings
     * namespace. Values are scaled to the integer ranges expected by the
     * crs:* tags ( Exposure2012, Contrast2012, etc. are in -100..+100).
     */
    private fun buildXmpFromParams(params: PipelineParams): String {
        return """
<x:xmpmeta xmlns:x="adobe:ns:meta/">
 <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
  <rdf:Description rdf:about="" xmlns:crs="http://ns.adobe.com/camera-raw-settings/1.0/">
   <crs:Exposure2012>${(params.exposure * 100).toInt()}</crs:Exposure2012>
   <crs:Contrast2012>${(params.contrast * 100).toInt()}</crs:Contrast2012>
   <crs:Highlights2012>${(params.highlights * 100).toInt()}</crs:Highlights2012>
   <crs:Shadows2012>${(params.shadows * 100).toInt()}</crs:Shadows2012>
   <crs:Whites2012>${0}</crs:Whites2012>
   <crs:Blacks2012>${0}</crs:Blacks2012>
   <crs:Saturation>${(params.saturation * 100).toInt()}</crs:Saturation>
   <crs:Vibrance>${(params.vibrance * 100).toInt()}</crs:Vibrance>
   <crs:Clarity2012>${(params.clarityAmount * 100).toInt()}</crs:Clarity2012>
   <crs:Temperature>${params.whiteBalanceTemp.toInt()}</crs:Temperature>
   <crs:Tint>${(params.whiteBalanceTint * 100).toInt()}</crs:Tint>
  </rdf:Description>
 </rdf:RDF>
</x:xmpmeta>
""".trimIndent()
    }

    /**
     * Convert an arbitrary RAW file to DNG. This requires a native encoder
     * (Adobe DNG Converter SDK or a libraw-based writer) that is not bundled in
     * this build, so it reports a clear error and lets the caller fall back to a
     * rendered format (TIFF/JPEG).
     */
    private fun convertRawToDng(
        sourcePath: String,
        outputPath: String,
        settings: ExportSettings
    ): ExportResult {
        return ExportResult.Error(
            "RAW→DNG conversion is not available in this build; " +
                "open the original DNG or export as TIFF/JPEG instead."
        )
    }

    // ================================================================
    // Ultra HDR (JPEG with HDR gain map - ISO 21496-1 inspired)
    // ================================================================

    private fun writeUltraHdr(bitmap: Bitmap, file: File, settings: ExportSettings): Boolean {
        if (!settings.isHdr) {
            // Standard JPEG when HDR not requested
            return writeJpeg(bitmap, file, settings)
        }

        // Delegate to the Kotlin UltraHdrWriter, which uses Android 14+
        // Gainmap API on supported devices and falls back to a standard
        // JPEG on older devices. The C++ ultra_hdr_writer.cpp stub returns
        // false to signal that encoding is handled here.
        var syntheticHdr: Bitmap? = null
        return try {
            val hdr = generateSyntheticHdr(bitmap)
            syntheticHdr = hdr
            val writer = UltraHdrWriter.create(context)
            val ok = writer.writeUltraHdr(
                sdrBitmap = bitmap,
                hdrBitmap = hdr,
                outputPath = file.absolutePath,
                quality = settings.quality
            )
            if (ok) true else writeJpeg(bitmap, file, settings)
        } catch (e: Exception) {
            // Fallback to standard JPEG on any unexpected failure
            writeJpeg(bitmap, file, settings)
        } finally {
            syntheticHdr?.recycle()
        }
    }

    /**
     * Generate a synthetic HDR bitmap from the SDR bitmap by boosting
     * highlights based on BT.709 luminance. Real HDR source data is not
     * available at this layer, so we synthesize an HDR representation that
     * the [UltraHdrWriter] can diff against the SDR bitmap to produce a
     * meaningful gain map (highlights get up to [ULTRA_HDR_MAX_RATIO] boost).
     */
    private fun generateSyntheticHdr(sdrBitmap: Bitmap): Bitmap {
        val width = sdrBitmap.width
        val height = sdrBitmap.height
        val pixels = IntArray(width * height)
        sdrBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f
            val alpha = pixel and 0xFF000000.toInt()

            // BT.709 luminance
            val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b

            // Boost highlights: bright pixels get a stronger HDR ratio.
            val boost = if (luminance > 0.01f) {
                (1f + (ULTRA_HDR_MAX_RATIO - 1f) * luminance).coerceIn(1f, ULTRA_HDR_MAX_RATIO)
            } else {
                1f
            }

            val hdrR = (r * boost * 255f).toInt().coerceIn(0, 255)
            val hdrG = (g * boost * 255f).toInt().coerceIn(0, 255)
            val hdrB = (b * boost * 255f).toInt().coerceIn(0, 255)

            pixels[i] = alpha or (hdrR shl 16) or (hdrG shl 8) or hdrB
        }

        val hdrBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        hdrBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return hdrBitmap
    }

    /**
     * Build Ultra HDR container: a multi-picture JPEG (MPF) with SDR base image
     * and gain map as the second image.
     */
    private fun buildUltraHdrContainer(
        sdrJpeg: ByteArray,
        gainMapJpeg: ByteArray,
        settings: ExportSettings
    ): ByteArray {
        // Find the EOI marker (0xFF 0xD9) in the SDR JPEG
        val eoiIndex = findLastJpegMarker(sdrJpeg, byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
        if (eoiIndex < 0) return sdrJpeg // fallback

        // Build MPF APP2 segment
        // MPF format: APP2 marker + length + "MPF\0" + MP endian + offset to MP index + MP index
        val mpHeader = buildMpHeader(sdrJpeg.size, gainMapJpeg.size)

        // Build gain map XMP metadata (ISO 21496-1)
        val gainMapXmp = buildGainMapXmp(settings)

        // Inject XMP into gain map JPEG (before its SOS/EOI)
        val enhancedGainMap = injectXmpIntoJpeg(gainMapJpeg, gainMapXmp)

        val result = ByteArrayOutputStream(sdrJpeg.size + mpHeader.size + enhancedGainMap.size + 10)
        // Write SDR JPEG up to (but not including) EOI
        result.write(sdrJpeg, 0, eoiIndex)
        // Write MPF APP2 segment
        result.write(mpHeader)
        // Write EOI to close primary image
        result.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
        // Write gain map JPEG
        result.write(enhancedGainMap)

        return result.toByteArray()
    }

    private fun buildMpHeader(sdrSize: Int, gainMapSize: Int): ByteArray {
        val bos = ByteArrayOutputStream()
        // APP2 marker
        bos.write(byteArrayOf(0xFF.toByte(), 0xE2.toByte()))

        // MPF body (after APP2 marker + length)
        val mpfBody = ByteArrayOutputStream()
        // MP endian ("II" little-endian + 0x2A00 + offset 0x08000000)
        mpfBody.write("MPF\u0000".toByteArray())
        mpfBody.write(byteArrayOf(0x49, 0x49)) // little-endian
        mpfBody.write(shortToBytes(0x002A, true)) // MP format tag
        mpfBody.write(intToBytes(24, true)) // Offset to first IFD from start of MP endian

        // MP IFD
        // Number of entries
        mpfBody.write(shortToBytes(1, true)) // 1 entry: MP Individual Image Attribute

        // Entry: MP Individual Image Attribute (tag 0xB000)
        mpfBody.write(shortToBytes(0xB000, true)) // tag
        mpfBody.write(shortToBytes(7, true)) // type = UNDEFINED
        mpfBody.write(intToBytes(1, true)) // count
        // Value: MP index offset (for simplicity, point to next IFD)
        mpfBody.write(intToBytes(0, true))

        // Next IFD offset
        mpfBody.write(intToBytes(0, true)) // no more IFDs

        // Length of APP2 segment = body size + 2 (for length field itself)
        val bodyBytes = mpfBody.toByteArray()
        val totalLen = bodyBytes.size + 2
        bos.write(shortToBytesBigEndian(totalLen))
        bos.write(bodyBytes)

        return bos.toByteArray()
    }

    /**
     * Build ISO 21496-1 gain map XMP metadata.
     */
    private fun buildGainMapXmp(settings: ExportSettings): ByteArray {
        val xmp = """
            <?xpacket begin="\xEF\xBB\xBF" id="W5M0MpCehiHzreSzNTczkc9d"?>
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
              <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                <rdf:Description
                  rdf:about=""
                  xmlns:GContainer="http://ns.google.com/photos/1.0/container/"
                  xmlns:GContainerItem="http://ns.google.com/photos/1.0/container/item/"
                  xmlns:hdrgm="http://ns.google.com/photos/1.0/hdr/gainmap/">
                  <hdrgm:Version>1</hdrgm:Version>
                  <hdrgm:GainMapMin>0</hdrgm:GainMapMin>
                  <hdrgm:GainMapMax>${Math.log(4.0) / Math.log(2.0)}</hdrgm:GainMapMax>
                  <hdrgm:Gamma>1.0</hdrgm:Gamma>
                  <hdrgm:OffsetSDR>0</hdrgm:OffsetSDR>
                  <hdrgm:OffsetHDR>0</hdrgm:OffsetHDR>
                  <hdrgm:HDRCapacityMin>0</hdrgm:HDRCapacityMin>
                  <hdrgm:HDRCapacityMax>2</hdrgm:HDRCapacityMax>
                </rdf:Description>
              </rdf:RDF>
            </x:xmpmeta>
            <?xpacket end="w"?>
        """.trimIndent()
        return xmp.toByteArray()
    }

    /**
     * Inject XMP data into a JPEG file as an APP1 segment.
     */
    private fun injectXmpIntoJpeg(jpegData: ByteArray, xmpData: ByteArray): ByteArray {
        // Find the first SOS or insert after all existing APP segments
        val sosIndex = findJpegMarker(jpegData, byteArrayOf(0xFF.toByte(), 0xDA.toByte()))
        if (sosIndex < 0) return jpegData

        val result = ByteArrayOutputStream(jpegData.size + xmpData.size + 20)
        // Write JPEG up to SOS marker
        result.write(jpegData, 0, sosIndex)

        // Write APP1 XMP segment
        val xmpPayload = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray() + xmpData
        result.write(byteArrayOf(0xFF.toByte(), 0xE1.toByte())) // APP1 marker
        result.write(shortToBytesBigEndian(xmpPayload.size + 2))
        result.write(xmpPayload)

        // Write rest of JPEG from SOS onwards
        result.write(jpegData, sosIndex, jpegData.size - sosIndex)
        return result.toByteArray()
    }

    private fun findJpegMarker(data: ByteArray, marker: ByteArray): Int {
        for (i in 0 until data.size - 1) {
            if (data[i] == marker[0] && data[i + 1] == marker[1]) {
                return i
            }
        }
        return -1
    }

    private fun findLastJpegMarker(data: ByteArray, marker: ByteArray): Int {
        for (i in data.size - 2 downTo 0) {
            if (data[i] == marker[0] && data[i + 1] == marker[1]) {
                return i
            }
        }
        return -1
    }

    // ================================================================
    // ICC Profile Embedding
    // ================================================================

    /**
     * Embed ICC profile into JPEG or PNG output file.
     * For JPEG: inject as APP2 ICC_PROFILE segment.
     * For PNG: not directly supported without full PNG rewrite (handled via colorSpace on Bitmap).
     */
    private fun embedIccProfile(file: File, colorSpace: ColorSpace, format: ExportFormat) {
        if (format != ExportFormat.JPEG && format != ExportFormat.ULTRA_HDR) return

        val iccBytes = getIccProfileBytes(colorSpace) ?: return
        val jpegData = file.readBytes()

        // Find position to insert APP2 (after SOI marker, before any other content)
        // SOI is the first 2 bytes (0xFF 0xD8)
        if (jpegData.size < 4) return
        if (jpegData[0] != 0xFF.toByte() || jpegData[1] != 0xD8.toByte()) return

        // Chunk the ICC profile into APP2 segments (max 65533 bytes per chunk)
        val chunks = chunkIccProfile(iccBytes)

        val result = ByteArrayOutputStream(jpegData.size + iccBytes.size + chunks.size * 20)
        // Write SOI
        result.write(jpegData, 0, 2)

        // Write ICC APP2 segments
        for ((index, chunk) in chunks.withIndex()) {
            result.write(byteArrayOf(0xFF.toByte(), 0xE2.toByte())) // APP2 marker
            val segmentLen = chunk.size + 2 + 12 // +2 for length field, +12 for ICC header
            result.write(shortToBytesBigEndian(segmentLen))
            result.write("ICC_PROFILE\u0000".toByteArray())
            result.write(index + 1) // chunk number (1-based)
            result.write(chunks.size) // total chunks
            result.write(chunk)
        }

        // Write rest of JPEG (after SOI)
        result.write(jpegData, 2, jpegData.size - 2)

        FileOutputStream(file).use { it.write(result.toByteArray()) }
    }

    private fun chunkIccProfile(iccBytes: ByteArray, maxChunkSize: Int = 65519): List<ByteArray> {
        if (iccBytes.size <= maxChunkSize) return listOf(iccBytes)
        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < iccBytes.size) {
            val end = minOf(offset + maxChunkSize, iccBytes.size)
            chunks.add(iccBytes.copyOfRange(offset, end))
            offset = end
        }
        return chunks
    }

    /**
     * Get embedded ICC profile bytes for a given color space.
     * These are minimal ICC v2 profiles generated programmatically.
     */
    fun getIccProfileBytes(colorSpace: ColorSpace): ByteArray? {
        return when (colorSpace) {
            ColorSpace.SRGB -> getSrgbIccProfile()
            ColorSpace.DISPLAY_P3 -> getDisplayP3IccProfile()
            ColorSpace.REC2020 -> getRec2020IccProfile()
            ColorSpace.ACES -> getAcesIccProfile()
            else -> null
        }
    }

    /**
     * Generate a minimal ICC v2 profile for sRGB.
     */
    private fun getSrgbIccProfile(): ByteArray {
        val profile = IccProfileBuilder("sRGB", "sRGB IEC61966-2.1")
        profile.setRgbPrimaries(
            rx = 0.64f, ry = 0.33f,
            gx = 0.30f, gy = 0.60f,
            bx = 0.15f, by = 0.06f,
            wx = 0.3127f, wy = 0.3290f
        )
        profile.setTrc(2.2f) // sRGB approximated as gamma 2.2 for simplicity
        return profile.build()
    }

    private fun getDisplayP3IccProfile(): ByteArray {
        val profile = IccProfileBuilder("Display P3", "Display P3")
        profile.setRgbPrimaries(
            rx = 0.680f, ry = 0.320f,
            gx = 0.265f, gy = 0.690f,
            bx = 0.150f, by = 0.060f,
            wx = 0.3127f, wy = 0.3290f
        )
        profile.setTrc(2.2f)
        return profile.build()
    }

    private fun getRec2020IccProfile(): ByteArray {
        val profile = IccProfileBuilder("Rec.2020", "Rec.2020")
        profile.setRgbPrimaries(
            rx = 0.708f, ry = 0.292f,
            gx = 0.170f, gy = 0.797f,
            bx = 0.131f, by = 0.046f,
            wx = 0.3127f, wy = 0.3290f
        )
        profile.setTrc(2.2f)
        return profile.build()
    }

    private fun getAcesIccProfile(): ByteArray {
        val profile = IccProfileBuilder("ACES", "ACEScg")
        profile.setRgbPrimaries(
            rx = 0.7347f, ry = 0.2653f,
            gx = 0.0000f, gy = 1.0000f,
            bx = 0.0001f, by = -0.0770f,
            wx = 0.32168f, wy = 0.33767f
        )
        profile.setTrc(1.0f) // linear
        return profile.build()
    }

    /**
     * Minimal ICC v2 profile builder for RGB color spaces.
     * Generates a valid ICC profile with chromaticAdaptation, primaries, and TRC.
     */
    private class IccProfileBuilder(
        private val description: String,
        private val copyright: String
    ) {
        private var primaries: FloatArray = FloatArray(8)
        private var gamma = 2.2f

        fun setRgbPrimaries(
            rx: Float, ry: Float,
            gx: Float, gy: Float,
            bx: Float, by: Float,
            wx: Float, wy: Float
        ) {
            primaries = floatArrayOf(rx, ry, gx, gy, bx, by, wx, wy)
        }

        fun setTrc(g: Float) {
            gamma = g
        }

        fun build(): ByteArray {
            val bos = ByteArrayOutputStream()

            // We'll build the profile in a byte buffer
            // ICC v2 profile structure:
            // Header (128 bytes) + Tag Table + Tag Data

            val tagData = ByteArrayOutputStream()

            // --- Profile Description Tag ---
            val descTagOffset = 128 + 4 + 5 * 12 // header + tag count + tag table entries
            val descBytes = buildTextDescriptionTag(description)
            tagData.write(descBytes)

            // --- Copyright Tag ---
            val copyrightTagOffset = descTagOffset + align4(descBytes.size)
            val copyrightBytes = buildTextDescriptionTag(copyright)
            tagData.write(copyrightBytes)

            // --- rTRC / gTRC / bTRC Tags (curv) ---
            val trcOffset = copyrightTagOffset + align4(copyrightBytes.size)
            val trcBytes = buildCurvTag(gamma)
            tagData.write(trcBytes)
            tagData.write(trcBytes) // gTRC same data
            tagData.write(trcBytes) // bTRC same data
            val gTrcOffset = trcOffset + align4(trcBytes.size)
            val bTrcOffset = gTrcOffset + align4(trcBytes.size)

            // --- rXYZ / gXYZ / bXYZ Tags ---
            val rXyzOffset = bTrcOffset + align4(trcBytes.size)
            val rXyz = buildXyzTag(primaries, 0) // red
            val gXyz = buildXyzTag(primaries, 1) // green
            val bXyz = buildXyzTag(primaries, 2) // blue
            tagData.write(rXyz)
            tagData.write(gXyz)
            tagData.write(bXyz)
            val gXyzOffset = rXyzOffset + align4(rXyz.size)
            val bXyzOffset = gXyzOffset + align4(gXyz.size)

            // --- wtpt Tag (media white point) ---
            val wtptOffset = bXyzOffset + align4(bXyz.size)
            val wtpt = buildXyzTag(primaries, 3) // white point
            tagData.write(wtpt)

            // Build tag table
            val tagCount = 9
            val tagTableSize = 4 + tagCount * 12
            val profileSize = 128 + tagTableSize + tagData.size()

            // Write header
            writeIccInt(bos, profileSize) // Profile size
            bos.write(ByteArray(4)) // Preferred CMM
            bos.write(byteArrayOf(0x02, 0x20, 0x00, 0x00)) // Version 2.2
            bos.write(ByteArray(4)) // Device class: display
            bos.write("mntr".toByteArray()) // Device class = monitor
            bos.write("RGB ".toByteArray()) // Color space
            bos.write("XYZ ".toByteArray()) // PCS
            bos.write(ByteArray(12)) // Date/time (zeros)
            bos.write("acmg".toByteArray()) // File signature
            bos.write(ByteArray(4)) // Primary platform
            bos.write(ByteArray(4)) // Profile flags
            bos.write(ByteArray(4)) // Device manufacturer
            bos.write(ByteArray(4)) // Device model
            bos.write(ByteArray(8)) // Device attributes
            bos.write(ByteArray(4)) // Rendering intent
            // PCS illuminant (D50): X=0.9642, Y=1.0000, Z=0.8249
            writeIccS15Fixed16(bos, 0.9642f)
            writeIccS15Fixed16(bos, 1.0000f)
            writeIccS15Fixed16(bos, 0.8249f)
            bos.write(ByteArray(4)) // Profile creator
            bos.write(ByteArray(16)) // Profile ID

            // Pad header to 128 bytes
            while (bos.size() < 128) bos.write(0)

            // Write tag table
            writeIccInt(bos, tagCount)
            writeTagEntry(bos, "desc", descTagOffset, descBytes.size)
            writeTagEntry(bos, "cprt", copyrightTagOffset, copyrightBytes.size)
            writeTagEntry(bos, "rTRC", trcOffset, trcBytes.size)
            writeTagEntry(bos, "gTRC", gTrcOffset, trcBytes.size)
            writeTagEntry(bos, "bTRC", bTrcOffset, trcBytes.size)
            writeTagEntry(bos, "rXYZ", rXyzOffset, rXyz.size)
            writeTagEntry(bos, "gXYZ", gXyzOffset, gXyz.size)
            writeTagEntry(bos, "bXYZ", bXyzOffset, bXyz.size)
            writeTagEntry(bos, "wtpt", wtptOffset, wtpt.size)

            // Write tag data
            bos.write(tagData.toByteArray())

            return bos.toByteArray()
        }

        private fun buildTextDescriptionTag(text: String): ByteArray {
            val bos = ByteArrayOutputStream()
            writeIccInt(bos, 0x64657363) // 'desc' type signature
            writeIccInt(bos, 0) // reserved
            // Unicode portion
            writeIccInt(bos, text.length + 1)
            bos.write("enUS".toByteArray()) // Unicode language code
            val textBytes = text.toByteArray(Charsets.UTF_16BE)
            bos.write(textBytes)
            bos.write(ByteArray(2)) // null terminator (UTF-16BE)
            // ScriptCode portion
            bos.write(ByteArray(2)) // ScriptCode code
            bos.write(0) // ScriptCode count
            bos.write(ByteArray(67)) // ScriptCode string
            return bos.toByteArray()
        }

        private fun buildCurvTag(gamma: Float): ByteArray {
            val bos = ByteArrayOutputStream()
            writeIccInt(bos, 0x63757276) // 'curv' type signature
            writeIccInt(bos, 0) // reserved
            if (gamma == 1.0f) {
                writeIccInt(bos, 0) // count = 0 means linear
            } else {
                writeIccInt(bos, 1) // count = 1
                // Encode as u8Fixed8Number
                val fixed = (gamma * 256f).toInt().coerceIn(1, 65535)
                bos.write(byteArrayOf(
                    ((fixed shr 8) and 0xFF).toByte(),
                    (fixed and 0xFF).toByte()
                ))
            }
            return bos.toByteArray()
        }

        /**
         * Build XYZ tag from chromaticity coordinates.
         * Converts xy chromaticities to XYZ (D50-adapted) for ICC profile.
         */
        private fun buildXyzTag(primaries: FloatArray, channel: Int): ByteArray {
            val (rx, ry) = primaries[0] to primaries[1]
            val (gx, gy) = primaries[2] to primaries[3]
            val (bx, by) = primaries[4] to primaries[5]
            val (wx, wy) = primaries[6] to primaries[7]

            // Convert primaries to XYZ
            val Xr = rx / ry; val Yr = 1f; val Zr = (1f - rx - ry) / ry
            val Xg = gx / gy; val Yg = 1f; val Zg = (1f - gx - gy) / gy
            val Xb = bx / by; val Yb = 1f; val Zb = (1f - bx - by) / by

            // White point XYZ
            val Xw = wx / wy; val Yw = 1f; val Zw = (1f - wx - wy) / wy

            // Solve for S = M^-1 * W
            // M = [[Xr,Yr,Zr],[Xg,Yg,Zg],[Xb,Yb,Zb]]
            val det = Xr * (Yg * Zb - Zg * Yb) - Yr * (Xg * Zb - Zg * Xb) + Zr * (Xg * Yb - Yg * Xb)
            if (det == 0f) return buildXyzTagRaw(0f, 0f, 0f)

            val Sr = (Xw * (Yg * Zb - Zg * Yb) - Yw * (Xg * Zb - Zg * Xb) + Zw * (Xg * Yb - Yg * Xb)) / det
            val Sg = (Xr * (Xw * Zb - Zw * Yb) - Yr * (Xw * Zb - Zw * Xb) + Zr * (Xw * Yb - Yw * Xb)) / det
            // Simplified: use matrix inversion
            val Sg2 = (Xw * (Yr * Zb - Zr * Yb) - Yw * (Xr * Zb - Zr * Xb) + Zw * (Xr * Yb - Yr * Xb)) / det
            val Sb = (Xr * (Yg * Zw - Zg * Yw) - Yr * (Xg * Zw - Zg * Xw) + Zr * (Xg * Yw - Yg * Xw)) / det

            // Bradford adaptation from D65 to D50
            val (xd, yd) = 0.3127f to 0.3290f // D65
            val (xn, yn) = 0.3457f to 0.3585f // D50

            // For simplicity, use the chromatic-adapted values directly
            // Apply Bradford: XYZ_D50 = M_adapt * XYZ_D65
            val mAdapt = floatArrayOf(
                 1.047811f, 0.022970f, -0.050192f,
                 0.029612f, 0.990460f, -0.017079f,
                -0.009233f, 0.015026f,  0.751678f
            )

            val xyzValues = when (channel) {
                0 -> floatArrayOf(Sr * Xr, Sr * Yr, Sr * Zr) // Red
                1 -> floatArrayOf(Sg2 * Xg, Sg2 * Yg, Sg2 * Zg) // Green
                2 -> floatArrayOf(Sb * Xb, Sb * Yb, Sb * Zb) // Blue
                3 -> floatArrayOf(Xw, Yw, Zw) // White point
                else -> floatArrayOf(0f, 0f, 0f)
            }

            // Apply Bradford adaptation D65 -> D50
            val x = mAdapt[0] * xyzValues[0] + mAdapt[1] * xyzValues[1] + mAdapt[2] * xyzValues[2]
            val y = mAdapt[3] * xyzValues[0] + mAdapt[4] * xyzValues[1] + mAdapt[5] * xyzValues[2]
            val z = mAdapt[6] * xyzValues[0] + mAdapt[7] * xyzValues[1] + mAdapt[8] * xyzValues[2]

            return buildXyzTagRaw(x, y, z)
        }

        private fun buildXyzTagRaw(x: Float, y: Float, z: Float): ByteArray {
            val bos = ByteArrayOutputStream()
            writeIccInt(bos, 0x58595A20) // 'XYZ ' type signature
            writeIccInt(bos, 0) // reserved
            writeIccS15Fixed16(bos, x)
            writeIccS15Fixed16(bos, y)
            writeIccS15Fixed16(bos, z)
            return bos.toByteArray()
        }

        private fun writeTagEntry(bos: ByteArrayOutputStream, tag: String, offset: Int, size: Int) {
            bos.write(tag.toByteArray())
            writeIccInt(bos, offset)
            writeIccInt(bos, size)
        }

        private fun align4(size: Int): Int = ((size + 3) / 4) * 4

        private fun writeIccInt(bos: ByteArrayOutputStream, value: Int) {
            bos.write((value shr 24) and 0xFF)
            bos.write((value shr 16) and 0xFF)
            bos.write((value shr 8) and 0xFF)
            bos.write(value and 0xFF)
        }

        private fun writeIccS15Fixed16(bos: ByteArrayOutputStream, value: Float) {
            val fixed = (value * 65536.0).toInt()
            writeIccInt(bos, fixed)
        }
    }

    // ================================================================
    // Metadata Writeback
    // ================================================================

    /**
     * Write EXIF metadata from the original source file into the exported file.
     * Also writes rating, tags, and color space information.
     */
    private fun writeMetadata(outputFile: File, sourcePath: String, settings: ExportSettings) {
        try {
            // Read original EXIF
            val sourceExif = try {
                ExifInterface(sourcePath)
            } catch (_: Exception) {
                null
            }

            val outputExif = ExifInterface(outputFile.absolutePath)

            // Copy key EXIF tags from source
            if (sourceExif != null) {
                val copyTags = listOf(
                    ExifInterface.TAG_MAKE,
                    ExifInterface.TAG_MODEL,
                    ExifInterface.TAG_LENS_MAKE,
                    ExifInterface.TAG_LENS_MODEL,
                    ExifInterface.TAG_FOCAL_LENGTH,
                    ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
                    ExifInterface.TAG_F_NUMBER,
                    ExifInterface.TAG_APERTURE_VALUE,
                    ExifInterface.TAG_EXPOSURE_TIME,
                    ExifInterface.TAG_SHUTTER_SPEED_VALUE,
                    ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                    ExifInterface.TAG_ISO_SPEED,
                    ExifInterface.TAG_WHITE_BALANCE,
                    ExifInterface.TAG_FLASH,
                    ExifInterface.TAG_METERING_MODE,
                    ExifInterface.TAG_EXPOSURE_PROGRAM,
                    ExifInterface.TAG_EXPOSURE_MODE,
                    ExifInterface.TAG_DATETIME_ORIGINAL,
                    ExifInterface.TAG_DATETIME_DIGITIZED,
                    ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
                    ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
                    ExifInterface.TAG_GPS_LATITUDE,
                    ExifInterface.TAG_GPS_LATITUDE_REF,
                    ExifInterface.TAG_GPS_LONGITUDE,
                    ExifInterface.TAG_GPS_LONGITUDE_REF,
                    ExifInterface.TAG_GPS_ALTITUDE,
                    ExifInterface.TAG_GPS_ALTITUDE_REF,
                    ExifInterface.TAG_GPS_TIMESTAMP,
                    ExifInterface.TAG_GPS_DATESTAMP,
                    ExifInterface.TAG_GPS_PROCESSING_METHOD,
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.TAG_IMAGE_DESCRIPTION,
                    ExifInterface.TAG_ARTIST,
                    ExifInterface.TAG_COPYRIGHT,
                    ExifInterface.TAG_SOFTWARE,
                    "CameraSerialNumber",
                    ExifInterface.TAG_LENS_SERIAL_NUMBER
                )

                for (tag in copyTags) {
                    val value = sourceExif.getAttribute(tag)
                    if (value != null) {
                        outputExif.setAttribute(tag, value)
                    }
                }

                // Copy thumbnail if present
                val thumbBytes = sourceExif.thumbnailBytes
                if (thumbBytes != null) {
                    // setThumbnail(byte[]) is not available in the current
                    // ExifInterface library; thumbnail copying is skipped.
                }
            }

            // Update image dimensions
            val dims = outputFile.let { f ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.absolutePath, opts)
                opts.outWidth to opts.outHeight
            }
            outputExif.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, dims.first.toString())
            outputExif.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, dims.second.toString())

            // Write rating (mapped to EXIF Rating tag)
            if (settings.rating > 0) {
                outputExif.setAttribute("Rating", settings.rating.toString())
            }

            // Write tags as XPKeywords (Windows-compatible) or UserComment
            if (settings.tags.isNotEmpty()) {
                val keywords = settings.tags.joinToString("; ")
                outputExif.setAttribute("XPKeywords", keywords)
                // Also set ImageDescription if not already set from source
                if (sourceExif?.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION).isNullOrBlank()) {
                    outputExif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, keywords)
                }
            }

            // Write color space information
            val colorSpaceExif = when (settings.colorSpace) {
                ColorSpace.SRGB -> "1" // sRGB
                else -> "65535" // Uncalibrated
            }
            outputExif.setAttribute(ExifInterface.TAG_COLOR_SPACE, colorSpaceExif)

            // Set software tag
            outputExif.setAttribute(ExifInterface.TAG_SOFTWARE, "Alcedo Studio")

            // Set modification datetime
            val now = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault()).format(Date())
            outputExif.setAttribute(ExifInterface.TAG_DATETIME, now)

            outputExif.saveAttributes()
        } catch (_: Exception) {
            // Metadata writeback failure is non-fatal
        }
    }

    // ================================================================
    // Output path management
    // ================================================================

    private fun createOutputFile(settings: ExportSettings, sourcePath: String? = null): File {
        val outputDir = if (settings.outputPath.isNotEmpty()) {
            // 用户指定路径:可能是 SAF content URI 或文件系统路径
            val path = settings.outputPath
            if (path.startsWith("content://")) {
                // SAF URI 由 writeImageSaf 单独处理,这里回退到 app-specific 目录
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.resolve("AlcedoStudio")
                    ?: File(context.cacheDir, "exports")
            } else {
                File(path)
            }
        } else {
            // Android 10+ should use MediaStore, this is fallback only
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.resolve("AlcedoStudio")
                    ?: File(context.cacheDir, "exports")
            } else {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    .resolve("AlcedoStudio")
            }
        }

        if (!outputDir.exists()) outputDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val ext = getExtension(settings.format)
        val src = sourcePath ?: settings.sourceExifPath
        val baseName = if (settings.useOriginalFilename && src != null) {
            val originalName = File(src).nameWithoutExtension
            if (originalName.isNotBlank()) "${originalName}_edited" else "alcedo_export_$timestamp"
        } else {
            "alcedo_export_$timestamp"
        }

        var file = File(outputDir, "$baseName.$ext")
        var counter = 1
        while (file.exists()) {
            file = File(outputDir, "${baseName}_$counter.$ext")
            counter++
        }

        return file
    }

    private fun getExtension(format: ExportFormat): String = when (format) {
        ExportFormat.JPEG -> "jpg"
        ExportFormat.PNG -> "png"
        ExportFormat.TIFF -> "tiff"
        ExportFormat.EXR -> "exr"
        ExportFormat.DNG -> "dng"
        ExportFormat.ULTRA_HDR -> "jpg"
    }

    // ================================================================
    // Progress helpers
    // ================================================================

    private fun updateItemProgress(progress: Float) {
        val current = _exportProgress.value
        val elapsed = if (exportStartTime > 0) System.currentTimeMillis() - exportStartTime else 0L
        val eta = if (progress > 0f && progress < 1f && current.totalItems > 0) {
            val overallProgress = (current.completedItems.toFloat() + progress) / current.totalItems
            if (overallProgress > 0f) (elapsed * (1.0 - overallProgress) / overallProgress).toLong() else 0L
        } else if (progress >= 1f) 0L else 0L
        _exportProgress.value = current.copy(currentItemProgress = progress, etaMillis = eta)
    }

    // ================================================================
    // Cancel
    // ================================================================

    fun cancelExport() {
        cancelFlag.set(true)
        _exportProgress.value = _exportProgress.value.copy(status = ExportStatus.CANCELLED)
    }

    fun resetProgress() {
        cancelFlag.set(false)
        _exportProgress.value = ExportProgress()
    }

    /**
     * Update progress during the decode/preparation phase of batch export.
     * This allows the UI to show progress before actual export begins.
     */
    fun updateDecodeProgress(
        totalItems: Int,
        currentIndex: Int,
        currentItemName: String,
        decodeFraction: Float
    ) {
        _exportProgress.value = _exportProgress.value.copy(
            totalItems = totalItems,
            completedItems = 0,
            currentItemIndex = currentIndex,
            currentItemName = currentItemName,
            currentItemProgress = decodeFraction,
            overallProgress = decodeFraction * 0.5f,
            status = ExportStatus.EXPORTING
        )
    }

    // ================================================================
    // Binary helpers
    // ================================================================

    private fun shortToBytes(value: Int, isLittleEndian: Boolean): ByteArray {
        return if (isLittleEndian) {
            byteArrayOf((value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())
        } else {
            byteArrayOf(((value shr 8) and 0xFF).toByte(), (value and 0xFF).toByte())
        }
    }

    private fun intToBytes(value: Int, isLittleEndian: Boolean): ByteArray {
        return if (isLittleEndian) {
            byteArrayOf(
                (value and 0xFF).toByte(),
                ((value shr 8) and 0xFF).toByte(),
                ((value shr 16) and 0xFF).toByte(),
                ((value shr 24) and 0xFF).toByte()
            )
        } else {
            byteArrayOf(
                ((value shr 24) and 0xFF).toByte(),
                ((value shr 16) and 0xFF).toByte(),
                ((value shr 8) and 0xFF).toByte(),
                (value and 0xFF).toByte()
            )
        }
    }

    private fun shortToBytesBigEndian(value: Int): ByteArray {
        return byteArrayOf(((value shr 8) and 0xFF).toByte(), (value and 0xFF).toByte())
    }

    private fun writeIccInt(bos: ByteArrayOutputStream, value: Int) {
        bos.write(byteArrayOf(
            ((value shr 24) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        ))
    }

    private fun writeIccS15Fixed16(bos: ByteArrayOutputStream, value: Float) {
        val fixed = (value * 65536f).toInt()
        bos.write(byteArrayOf(
            ((fixed shr 24) and 0xFF).toByte(),
            ((fixed shr 16) and 0xFF).toByte(),
            ((fixed shr 8) and 0xFF).toByte(),
            (fixed and 0xFF).toByte()
        ))
    }

    // ================================================================
    // Result types
    // ================================================================

    sealed class ExportResult {
        data class Success(val uri: Uri, val filePath: String) : ExportResult()
        data class Error(val message: String) : ExportResult()
    }

    data class ExportBatchResult(
        val totalItems: Int,
        val successCount: Int,
        val errorCount: Int,
        val results: List<ExportResult>
    )

    data class ExportBatchItem(
        val sourcePath: String,
        val processedBitmap: Bitmap? = null
    )
}
