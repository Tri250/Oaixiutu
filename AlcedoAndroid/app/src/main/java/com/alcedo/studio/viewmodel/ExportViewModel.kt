package com.alcedo.studio.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alcedo.studio.data.model.ColorSpace
import com.alcedo.studio.data.model.ExportFormat
import com.alcedo.studio.data.model.ExportSettings
import com.alcedo.studio.data.model.PipelineParams
import com.alcedo.studio.di.AppModule
import com.alcedo.studio.domain.service.ExportService
import com.alcedo.studio.domain.service.PipelineService
import com.alcedo.studio.ndk.AlcedoNdkBridge
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExportViewModel : ViewModel() {
    private val exportService by lazy { AppModule.exportService }
    private val pipelineService by lazy { AppModule.pipelineService }
    private val imageRepository by lazy { AppModule.imageRepository }

    // ── Export settings state ──

    private val _settings = MutableStateFlow(ExportSettings())
    val settings: StateFlow<ExportSettings> = _settings.asStateFlow()

    // ── Export progress ──

    val exportProgress: StateFlow<ExportService.ExportProgress> = exportService.exportProgress

    // ── Export result ──

    private val _lastExportResult = MutableStateFlow<ExportService.ExportResult?>(null)
    val lastExportResult: StateFlow<ExportService.ExportResult?> = _lastExportResult.asStateFlow()

    // ── Batch export ──

    private val _batchItems = MutableStateFlow<List<BatchExportItem>>(emptyList())
    val batchItems: StateFlow<List<BatchExportItem>> = _batchItems

    private val _batchProgress = MutableStateFlow(BatchExportProgress())
    val batchProgress: StateFlow<BatchExportProgress> = _batchProgress

    // ── Available sizes ──

    private val _sourceDimensions = MutableStateFlow<Pair<Int, Int>?>(null)
    val sourceDimensions: StateFlow<Pair<Int, Int>?> = _sourceDimensions

    // ── Resize mode ──

    private val _resizeMode = MutableStateFlow(ResizeMode.NONE)
    val resizeMode: StateFlow<ResizeMode> = _resizeMode.asStateFlow()

    // ── UI state ──

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting

    private val _showFormatOptions = MutableStateFlow(false)
    val showFormatOptions: StateFlow<Boolean> = _showFormatOptions.asStateFlow()

    private val _showResizeOptions = MutableStateFlow(false)
    val showResizeOptions: StateFlow<Boolean> = _showResizeOptions.asStateFlow()

    private val _showColorSpaceOptions = MutableStateFlow(false)
    val showColorSpaceOptions: StateFlow<Boolean> = _showColorSpaceOptions.asStateFlow()

    // ── Convenience properties for ExportScreen ──

    var format: ExportFormat
        get() = _settings.value.format
        set(value) { _settings.value = _settings.value.copy(format = value) }

    var quality: Int
        get() = _settings.value.quality
        set(value) { _settings.value = _settings.value.copy(quality = value.coerceIn(1, 100)) }

    var bitDepth: Int
        get() = _settings.value.bitDepth
        set(value) { _settings.value = _settings.value.copy(bitDepth = value) }

    var colorSpace: ColorSpace
        get() = _settings.value.colorSpace
        set(value) { _settings.value = _settings.value.copy(colorSpace = value) }

    var embedIcc: Boolean
        get() = _settings.value.embedIcc
        set(value) { _settings.value = _settings.value.copy(embedIcc = value) }

    var includeMetadata: Boolean
        get() = _settings.value.includeMetadata
        set(value) { _settings.value = _settings.value.copy(includeMetadata = value) }

    var isHdr: Boolean
        get() = _settings.value.isHdr
        set(value) { _settings.value = _settings.value.copy(isHdr = value) }

    var hassebladWatermark: Boolean
        get() = _settings.value.hassebladWatermark
        set(value) { _settings.value = _settings.value.copy(hassebladWatermark = value) }

    var maxDimension: String
        get() = _settings.value.maxDimension?.toString() ?: ""
        set(value) { _settings.value = _settings.value.copy(maxDimension = value.toIntOrNull()) }

    var maxWidth: String
        get() = _settings.value.maxWidth?.toString() ?: ""
        set(value) { _settings.value = _settings.value.copy(maxWidth = value.toIntOrNull()) }

    var maxHeight: String
        get() = _settings.value.maxHeight?.toString() ?: ""
        set(value) { _settings.value = _settings.value.copy(maxHeight = value.toIntOrNull()) }

    var outputPath: String
        get() = _settings.value.outputPath ?: ""
        set(value) { _settings.value = _settings.value.copy(outputPath = value) }

    var showBatchExport: Boolean = false

    val batchImageIds: List<String>
        get() = _batchItems.value.map { it.imageId.toString() }

    val lastResult: StateFlow<ExportService.ExportResult?> get() = lastExportResult

    private val _batchResult = MutableStateFlow<ExportService.ExportBatchResult?>(null)
    val batchResult: StateFlow<ExportService.ExportBatchResult?> = _batchResult.asStateFlow()

    // ================================================================
    // Export presets
    // ================================================================

    private val _presets = MutableStateFlow<List<ExportPreset>>(emptyList())
    val presets: StateFlow<List<ExportPreset>> = _presets.asStateFlow()

    private val prefs = AppModule.context.getSharedPreferences("export_presets", Context.MODE_PRIVATE)

    init {
        loadPresets()
    }

    fun savePreset(name: String) {
        val preset = ExportPreset(
            name = name,
            format = format,
            quality = quality,
            colorSpace = colorSpace,
            maxDimension = _settings.value.maxDimension ?: 0,
            embedIcc = embedIcc,
            includeMetadata = includeMetadata
        )
        val current = _presets.value.toMutableList()
        current.removeAll { it.name == name }
        current.add(preset)
        _presets.value = current
        persistPresets()
    }

    fun loadPreset(preset: ExportPreset) {
        _settings.value = _settings.value.copy(
            format = preset.format,
            quality = preset.quality,
            colorSpace = preset.colorSpace,
            maxDimension = preset.maxDimension,
            embedIcc = preset.embedIcc,
            includeMetadata = preset.includeMetadata
        )
    }

    fun deletePreset(name: String) {
        _presets.value = _presets.value.filterNot { it.name == name }
        persistPresets()
    }

    private fun loadPresets() {
        val json = prefs.getString("presets", null) ?: return
        try {
            val type = object : TypeToken<List<ExportPreset>>() {}.type
            _presets.value = Gson().fromJson(json, type)
        } catch (e: Exception) {
            Log.e("ExportVM", "加载预设失败", e)
        }
    }

    private fun persistPresets() {
        val json = Gson().toJson(_presets.value)
        prefs.edit().putString("presets", json).apply()
    }

    // ================================================================
    // Settings manipulation
    // ================================================================

    fun updateFormat(format: ExportFormat) {
        _settings.value = _settings.value.copy(format = format)
    }

    fun updateQuality(quality: Int) {
        _settings.value = _settings.value.copy(quality = quality.coerceIn(1, 100))
    }

    fun updateColorSpace(colorSpace: ColorSpace) {
        _settings.value = _settings.value.copy(colorSpace = colorSpace)
    }

    fun updateResizeMode(mode: ResizeMode) {
        _resizeMode.value = mode
    }

    fun updateMaxWidth(width: Int?) {
        _settings.value = _settings.value.copy(maxWidth = width)
    }

    fun updateMaxHeight(height: Int?) {
        _settings.value = _settings.value.copy(maxHeight = height)
    }

    fun updateMaxDimension(dimension: Int?) {
        _settings.value = _settings.value.copy(maxDimension = dimension)
    }

    fun updateOutputPath(path: String) {
        _settings.value = _settings.value.copy(outputPath = path)
    }

    fun updateBitDepth(bitDepth: Int) {
        _settings.value = _settings.value.copy(bitDepth = bitDepth)
    }

    fun updateHdr(isHdr: Boolean) {
        _settings.value = _settings.value.copy(isHdr = isHdr)
    }

    fun updateIncludeMetadata(include: Boolean) {
        _settings.value = _settings.value.copy(includeMetadata = include)
    }

    fun updateEmbedIcc(embed: Boolean) {
        _settings.value = _settings.value.copy(embedIcc = embed)
    }

    // ================================================================
    // Toggle option panels
    // ================================================================

    fun toggleFormatOptions() {
        _showFormatOptions.value = !_showFormatOptions.value
    }

    fun toggleResizeOptions() {
        _showResizeOptions.value = !_showResizeOptions.value
    }

    fun toggleColorSpaceOptions() {
        _showColorSpaceOptions.value = !_showColorSpaceOptions.value
    }

    // ================================================================
    // Load source image dimensions
    // ================================================================

    fun loadSourceDimensions(imagePath: String) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imagePath, options)
        _sourceDimensions.value = Pair(options.outWidth, options.outHeight)
    }

    // ================================================================
    // Calculate output dimensions
    // ================================================================

    fun calculateOutputDimensions(): Pair<Int, Int> {
        val source = _sourceDimensions.value ?: return Pair(0, 0)
        val settings = _settings.value
        val srcW = source.first
        val srcH = source.second

        val maxDim = settings.maxDimension
        val maxW = settings.maxWidth
        val maxH = settings.maxHeight

        if (maxDim != null && maxDim > 0) {
            val longEdge = maxOf(srcW, srcH)
            if (longEdge > maxDim) {
                val scale = maxDim.toFloat() / longEdge
                return Pair((srcW * scale).toInt(), (srcH * scale).toInt())
            }
            return source
        }

        if (maxW != null && maxH != null) {
            return when (_resizeMode.value) {
                ResizeMode.NONE -> source
                ResizeMode.FIT -> {
                    val scale = minOf(maxW.toFloat() / srcW, maxH.toFloat() / srcH)
                    Pair((srcW * scale).toInt(), (srcH * scale).toInt())
                }
                ResizeMode.FILL -> {
                    val scale = maxOf(maxW.toFloat() / srcW, maxH.toFloat() / srcH)
                    Pair((srcW * scale).toInt(), (srcH * scale).toInt())
                }
                ResizeMode.EXACT -> Pair(maxW, maxH)
            }
        }

        if (maxW != null && maxW > 0 && maxH == null) {
            if (srcW > maxW) {
                val scale = maxW.toFloat() / srcW
                return Pair(maxW, (srcH * scale).toInt())
            }
            return source
        }

        if (maxH != null && maxH > 0 && maxW == null) {
            if (srcH > maxH) {
                val scale = maxH.toFloat() / srcH
                return Pair((srcW * scale).toInt(), maxH)
            }
            return source
        }

        return source
    }

    // ================================================================
    // Bitmap sampling helper
    // ================================================================

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
    // Single image export
    // ================================================================

    fun exportImage(
        imagePath: String,
        params: PipelineParams,
        sourceExifPath: String? = null
    ) {
        viewModelScope.launch {
            _isExporting.value = true
            var bitmap: Bitmap? = null
            var processedBitmap: Bitmap? = null
            try {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                    BitmapFactory.decodeFile(imagePath, this)
                    inSampleSize = calculateInSampleSize(
                        outWidth, outHeight,
                        _settings.value.maxWidth ?: 0,
                        _settings.value.maxHeight ?: 0
                    )
                    inJustDecodeBounds = false
                }
                bitmap = BitmapFactory.decodeFile(imagePath, options)
                if (bitmap == null) {
                    _lastExportResult.value = ExportService.ExportResult.Error("Failed to decode image")
                    return@launch
                }

                processedBitmap = pipelineService.applyPipeline(bitmap, params)

                val exportSettings = _settings.value.copy(
                    sourceExifPath = sourceExifPath ?: imagePath
                )

                val result = exportService.exportImage(imagePath, exportSettings, processedBitmap)
                _lastExportResult.value = result
            } catch (e: Throwable) {
                android.util.Log.e("ExportVM", "exportImage failed", e)
            } finally {
                processedBitmap?.recycle()
                if (bitmap != null && bitmap !== processedBitmap) {
                    bitmap.recycle()
                }
                _isExporting.value = false
            }
        }
    }

    fun exportImageWithBitmap(
        imagePath: String,
        processedBitmap: Bitmap
    ) {
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val exportSettings = _settings.value.copy(
                    sourceExifPath = imagePath
                )

                val result = exportService.exportImage(imagePath, exportSettings, processedBitmap)
                _lastExportResult.value = result
            } catch (e: Throwable) {
                android.util.Log.e("ExportVM", "exportImageWithBitmap failed", e)
            } finally {
                _isExporting.value = false
            }
        }
    }

    // ================================================================
    // Batch export
    // ================================================================

    fun addBatchItem(imageId: Long, params: PipelineParams) {
        val current = _batchItems.value.toMutableList()
        if (current.none { it.imageId == imageId }) {
            current.add(BatchExportItem(imageId = imageId, params = params))
            _batchItems.value = current
        }
    }

    fun removeBatchItem(imageId: Long) {
        _batchItems.value = _batchItems.value.filter { it.imageId != imageId }
    }

    fun clearBatchItems() {
        _batchItems.value = emptyList()
    }

    fun addImageIdsToBatch(imageIds: List<Long>, params: PipelineParams) {
        val current = _batchItems.value.toMutableList()
        val existingIds = current.map { it.imageId }.toSet()
        for (id in imageIds) {
            if (id !in existingIds) {
                current.add(BatchExportItem(imageId = id, params = params))
            }
        }
        _batchItems.value = current
    }

    fun exportBatch() {
        viewModelScope.launch {
            _isExporting.value = true
            _batchProgress.value = BatchExportProgress(
                totalItems = _batchItems.value.size,
                completedItems = 0,
                currentItem = 0
            )
            try {
                val items = _batchItems.value
                val totalItems = items.size
                val exportItems = mutableListOf<ExportService.ExportBatchItem>()

                for ((index, item) in items.withIndex()) {
                    val image = imageRepository.getImage(item.imageId)
                    if (image != null) {
                        val options = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                            BitmapFactory.decodeFile(image.imagePath, this)
                            inSampleSize = calculateInSampleSize(
                                outWidth, outHeight,
                                _settings.value.maxWidth ?: 4096,
                                _settings.value.maxHeight ?: 4096
                            )
                            inJustDecodeBounds = false
                        }
                        val bitmap = BitmapFactory.decodeFile(image.imagePath, options)
                        if (bitmap != null) {
                            val processed = pipelineService.applyPipeline(bitmap, item.params)
                            exportItems.add(
                                ExportService.ExportBatchItem(
                                    sourcePath = image.imagePath,
                                    processedBitmap = processed
                                )
                            )
                        }
                    }
                    _batchProgress.value = _batchProgress.value.copy(
                        currentItem = index + 1
                    )
                    // Also update the service's exportProgress during the decode phase
                    val decodeFraction = (index + 1).toFloat() / totalItems
                    exportService.updateDecodeProgress(
                        totalItems = totalItems,
                        currentIndex = index,
                        currentItemName = image?.imageName ?: "Decoding...",
                        decodeFraction = decodeFraction
                    )
                }

                val exportSettings = _settings.value
                exportService.exportBatch(exportItems, exportSettings)

                _batchProgress.value = _batchProgress.value.copy(completedItems = items.size)
            } catch (e: Throwable) {
                android.util.Log.e("ExportVM", "exportBatch failed", e)
            } finally {
                _isExporting.value = false
            }
        }
    }

    // ================================================================
    // ExportScreen-compatible API
    // ================================================================

    fun resetState() {
        _lastExportResult.value = null
        _batchResult.value = null
    }

    fun exportSingle(sourcePath: String) {
        exportImage(sourcePath, PipelineParams())
    }

    fun exportBatch(items: List<ExportService.ExportBatchItem>) {
        viewModelScope.launch {
            _isExporting.value = true
            _batchProgress.value = _batchProgress.value.copy(
                totalItems = items.size,
                completedItems = 0,
                currentItem = 0
            )
            try {
                val exportSettings = _settings.value
                val result = exportService.exportBatch(items, exportSettings)
                _batchResult.value = result
                _batchProgress.value = _batchProgress.value.copy(completedItems = items.size)
            } catch (e: Throwable) {
                android.util.Log.e("ExportVM", "exportBatch(items) failed", e)
            } finally {
                _isExporting.value = false
            }
        }
    }

    // ================================================================
    // Export by image id (resolves imageId → file path via repository)
    // ================================================================

    fun exportSingleById(imageId: String) {
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val image = imageRepository.getImage(imageId.toLongOrNull() ?: 0L)
                val sourcePath = image?.imagePath ?: imageId
                val settings = _settings.value.copy(
                    maxDimension = _settings.value.maxDimension ?: 0,
                    maxWidth = _settings.value.maxWidth ?: 0,
                    maxHeight = _settings.value.maxHeight ?: 0
                )
                val result = exportService.exportImage(sourcePath, settings)
                _lastExportResult.value = result
            } catch (e: Exception) {
                _lastExportResult.value = ExportService.ExportResult.Error(e.message ?: "导出失败")
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun exportBatchByIds(imageIds: List<String>) {
        viewModelScope.launch {
            _isExporting.value = true
            val items = imageIds.mapNotNull { id ->
                val image = imageRepository.getImage(id.toLongOrNull() ?: 0L)
                image?.let { ExportService.ExportBatchItem(sourcePath = it.imagePath) }
            }
            if (items.isNotEmpty()) {
                val result = exportService.exportBatch(items, _settings.value)
                _batchResult.value = result
            }
            _isExporting.value = false
        }
    }

    // ================================================================
    // Cancel export
    // ================================================================

    fun cancelExport() {
        exportService.cancelExport()
        _isExporting.value = false
    }

    // ================================================================
    // Clear result
    // ================================================================

    fun clearResult() {
        _lastExportResult.value = null
    }

    // ================================================================
    // Format-specific validation
    // ================================================================

    fun isQualityApplicable(): Boolean {
        return _settings.value.format in listOf(
            ExportFormat.JPEG,
            ExportFormat.ULTRA_HDR
        )
    }

    fun isResizeApplicable(): Boolean {
        return _settings.value.format != ExportFormat.EXR
    }

    fun isColorSpaceApplicable(): Boolean {
        return _settings.value.format in listOf(ExportFormat.TIFF, ExportFormat.EXR)
    }

    fun isHdrApplicable(): Boolean {
        return _settings.value.format in listOf(ExportFormat.TIFF, ExportFormat.EXR, ExportFormat.ULTRA_HDR)
    }

    // ================================================================
    // Estimated file size
    // ================================================================

    fun estimateFileSize(): Long {
        val dims = calculateOutputDimensions()
        val pixelCount = dims.first.toLong() * dims.second.toLong()
        val bytesPerPixel = when (_settings.value.format) {
            ExportFormat.JPEG -> 3L * _settings.value.quality / 100
            ExportFormat.PNG -> 4L
            ExportFormat.TIFF -> if (_settings.value.bitDepth == 16) 6L else 3L
            ExportFormat.EXR -> 8L // half-float RGBA
            ExportFormat.ULTRA_HDR -> 3L * _settings.value.quality / 100
        }
        return pixelCount * bytesPerPixel
    }
}

// ================================================================
// Supporting types
// ================================================================

enum class ResizeMode {
    NONE, FIT, FILL, EXACT
}

data class BatchExportItem(
    val imageId: Long,
    val params: PipelineParams
)

data class BatchExportProgress(
    val totalItems: Int = 0,
    val completedItems: Int = 0,
    val currentItem: Int = 0
) {
    val progressFraction: Float
        get() = if (totalItems > 0) completedItems.toFloat() / totalItems else 0f

    val isComplete: Boolean
        get() = completedItems >= totalItems && totalItems > 0
}

data class ExportPreset(
    val name: String,
    val format: ExportFormat,
    val quality: Int,
    val colorSpace: ColorSpace,
    val maxDimension: Int,
    val embedIcc: Boolean,
    val includeMetadata: Boolean
)
