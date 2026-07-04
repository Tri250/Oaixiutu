package com.alcedo.studio.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alcedo.studio.data.model.*
import com.alcedo.studio.di.AppModule
import com.alcedo.studio.domain.service.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

class EditorViewModel(private val imageId: String) : ViewModel() {
    private val imageRepository = AppModule.imageRepository
    private val editHistoryRepository = AppModule.editHistoryRepository
    private val pipelineService = AppModule.pipelineService
    private val exportService = AppModule.exportService
    private val thumbnailService = AppModule.thumbnailService

    private val _imageModel = MutableStateFlow<ImageModel?>(null)
    val imageModel: StateFlow<ImageModel?> = _imageModel

    private val _originalBitmap = MutableStateFlow<Bitmap?>(null)
    val originalBitmap: StateFlow<Bitmap?> = _originalBitmap

    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap

    private val _params = mutableStateOf(PipelineParams())
    val params get() = _params

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    private val _history = MutableStateFlow<EditHistory?>(null)
    val history: StateFlow<EditHistory?> = _history

    private val _workingVersion = mutableStateOf(WorkingVersion())
    val workingVersion get() = _workingVersion

    private val _showHistogram = MutableStateFlow(true)
    val showHistogram: StateFlow<Boolean> = _showHistogram

    private val _showWaveform = MutableStateFlow(false)
    val showWaveform: StateFlow<Boolean> = _showWaveform

    private val _isCompareMode = MutableStateFlow(false)
    val isCompareMode: StateFlow<Boolean> = _isCompareMode

    // Export progress exposed from service
    val exportProgress: StateFlow<ExportService.ExportProgress> = exportService.exportProgress

    private val _lastExportResult = MutableStateFlow<ExportService.ExportResult?>(null)
    val lastExportResult: StateFlow<ExportService.ExportResult?> = _lastExportResult.asStateFlow()

    init {
        loadImage()
    }

    private fun loadImage() {
        viewModelScope.launch {
            val id = imageId.toUIntOrNull() ?: return@launch
            val img = imageRepository.getImage(id)
            _imageModel.value = img
            img?.let {
                val bitmap = BitmapFactory.decodeFile(it.imagePath)
                _originalBitmap.value = bitmap
                _previewBitmap.value = bitmap
                _history.value = editHistoryRepository.getHistory(id)
                    ?: EditHistory(boundImageId = id)
                _workingVersion.value = WorkingVersion(
                    boundImageId = id,
                    versionId = _history.value?.activeVersionId ?: ""
                )
            }
        }
    }

    fun updateParams(newParams: PipelineParams) {
        _params.value = newParams
        regeneratePreview()
    }

    fun regeneratePreview() {
        viewModelScope.launch {
            _isProcessing.value = true
            val source = _originalBitmap.value ?: return@launch
            _previewBitmap.value = pipelineService.applyPipeline(source, _params.value)
            _isProcessing.value = false
        }
    }

    fun undo() {
        _workingVersion.value.undo()
        // Reconstruct params from working version
    }

    fun redo() {
        _workingVersion.value.redo()
    }

    fun commitChanges() {
        viewModelScope.launch {
            val img = _imageModel.value ?: return@launch
            val finalBitmap = _previewBitmap.value ?: return@launch
            val settings = ExportSettings(
                format = ExportFormat.JPEG,
                quality = 95,
                outputPath = img.imagePath,
                sourceExifPath = img.imagePath,
                includeMetadata = true
            )
            val result = exportService.exportImage(img.imagePath, settings, finalBitmap)
            _lastExportResult.value = result
        }
    }

    fun export(settings: ExportSettings) {
        viewModelScope.launch {
            val img = _imageModel.value ?: return@launch
            val finalBitmap = _previewBitmap.value ?: return@launch
            val settingsWithExif = settings.copy(sourceExifPath = img.imagePath)
            val result = exportService.exportImage(img.imagePath, settingsWithExif, finalBitmap)
            _lastExportResult.value = result
        }
    }

    fun exportBatch(items: List<ExportService.ExportBatchItem>, settings: ExportSettings) {
        viewModelScope.launch {
            exportService.exportBatch(items, settings)
        }
    }

    fun cancelExport() {
        exportService.cancelExport()
    }

    // History management
    fun switchVersion(versionId: String) {
        _history.value?.let { hist ->
            hist.setActiveVersion(versionId)
            _history.value = hist
            _workingVersion.value = WorkingVersion(
                boundImageId = hist.boundImageId,
                versionId = versionId
            )
            // Reconstruct params from version
            val version = hist.getVersion(versionId)
            version?.materializedParams?.let { jsonParams ->
                reconstructParamsFromJson(jsonParams)
            }
        }
    }

    fun createVersion(displayName: String) {
        _history.value?.let { hist ->
            val newVersionId = hist.createVersion(displayName)
            _history.value = hist
            editHistoryRepository.saveHistory(hist)
        }
    }

    fun deleteVersion(versionId: String) {
        _history.value?.let { hist ->
            if (hist.versionStorage.size > 1) {
                hist.versionStorage.remove(versionId)
                hist.versionOrder.removeAll { it.versionId == versionId }
                if (hist.activeVersionId == versionId) {
                    hist.activeVersionId = hist.defaultVersionId
                }
                _history.value = hist
                editHistoryRepository.saveHistory(hist)
            }
        }
    }

    fun renameVersion(versionId: String, newName: String) {
        _history.value?.let { hist ->
            hist.getVersion(versionId)?.let { version ->
                val updated = version.copy(displayName = newName)
                hist.versionStorage[versionId] = updated
                _history.value = hist
                editHistoryRepository.saveHistory(hist)
            }
        }
    }

    fun cloneHistory() {
        _history.value?.let { hist ->
            val cloned = hist.cloneForFile(hist.boundImageId)
            _history.value = cloned
            editHistoryRepository.saveHistory(cloned)
        }
    }

    private fun reconstructParamsFromJson(json: JsonObject) {
        // Reconstruct PipelineParams from JSON
        // This is a simplified implementation
        try {
            val exposure = json["exposure"]?.jsonPrimitive?.floatOrNull ?: 0f
            val contrast = json["contrast"]?.jsonPrimitive?.floatOrNull ?: 0f
            _params.value = _params.value.copy(
                exposure = exposure,
                contrast = contrast
            )
        } catch (_: Exception) {
            // Fallback to default params
        }
    }

    fun toggleHistogram() {
        _showHistogram.value = !_showHistogram.value
    }

    fun toggleWaveform() {
        _showWaveform.value = !_showWaveform.value
    }

    fun toggleCompareMode() {
        _isCompareMode.value = !_isCompareMode.value
    }

    // ================================================================
    // Auto Exposure
    // ================================================================

    fun applyAutoExposure() {
        viewModelScope.launch {
            val bitmap = _originalBitmap.value ?: return@launch
            val width = bitmap.width
            val height = bitmap.height
            val pixelCount = width * height

            // Convert bitmap to float array
            val pixels = IntArray(pixelCount)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val floatPixels = FloatArray(pixelCount * 4)
            for (i in 0 until pixelCount) {
                val pixel = pixels[i]
                floatPixels[i * 4]     = ((pixel shr 16) and 0xFF) / 255.0f
                floatPixels[i * 4 + 1] = ((pixel shr 8) and 0xFF) / 255.0f
                floatPixels[i * 4 + 2] = (pixel and 0xFF) / 255.0f
                floatPixels[i * 4 + 3] = ((pixel shr 24) and 0xFF) / 255.0f
            }

            val ev = pipelineService.computeAutoExposure(
                floatPixels, width, height, 4,
                _params.value.autoExposureTargetPercentile,
                _params.value.autoExposureTargetLuminance
            )

            _params.value = _params.value.copy(
                autoExposureEnabled = true,
                autoExposureValue = ev,
                exposure = ev
            )
            regeneratePreview()
        }
    }

    // ================================================================
    // Pipeline Snapshot
    // ================================================================

    private var snapshotHandle: Long = 0

    fun createPipelineSnapshot() {
        viewModelScope.launch {
            val bitmap = _originalBitmap.value ?: return@launch
            val width = bitmap.width
            val height = bitmap.height
            val pixelCount = width * height

            val pixels = IntArray(pixelCount)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val floatPixels = FloatArray(pixelCount * 4)
            for (i in 0 until pixelCount) {
                val pixel = pixels[i]
                floatPixels[i * 4]     = ((pixel shr 16) and 0xFF) / 255.0f
                floatPixels[i * 4 + 1] = ((pixel shr 8) and 0xFF) / 255.0f
                floatPixels[i * 4 + 2] = (pixel and 0xFF) / 255.0f
                floatPixels[i * 4 + 3] = ((pixel shr 24) and 0xFF) / 255.0f
            }

            // Release previous snapshot
            if (snapshotHandle != 0L) {
                pipelineService.releaseSnapshot(snapshotHandle)
            }

            snapshotHandle = pipelineService.createSnapshot(
                floatPixels, width, height, 4, _params.value)
        }
    }

    fun releasePipelineSnapshot() {
        if (snapshotHandle != 0L) {
            pipelineService.releaseSnapshot(snapshotHandle)
            snapshotHandle = 0
        }
    }

    override fun onCleared() {
        super.onCleared()
        releasePipelineSnapshot()
    }
}