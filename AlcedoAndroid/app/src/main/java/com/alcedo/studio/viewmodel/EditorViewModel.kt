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

class EditorViewModel(private val imageId: String) : ViewModel() {
    private val imageRepository = AppModule.imageRepository
    private val editHistoryRepository = AppModule.editHistoryRepository
    private val pipelineService = AppModule.pipelineService
    private val exportService = AppModule.exportService

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
}
