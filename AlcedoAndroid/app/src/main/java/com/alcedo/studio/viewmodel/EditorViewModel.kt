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
                outputPath = img.imagePath
            )
            exportService.exportImage(img.imagePath, settings, finalBitmap)
        }
    }

    fun export(settings: ExportSettings) {
        viewModelScope.launch {
            val img = _imageModel.value ?: return@launch
            val finalBitmap = _previewBitmap.value ?: return@launch
            exportService.exportImage(img.imagePath, settings, finalBitmap)
        }
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
}