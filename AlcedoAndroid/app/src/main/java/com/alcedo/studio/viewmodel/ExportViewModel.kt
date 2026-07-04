package com.alcedo.studio.viewmodel

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alcedo.studio.data.model.*
import com.alcedo.studio.di.AppModule
import com.alcedo.studio.domain.service.ExportService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExportViewModel : ViewModel() {
    private val exportService = AppModule.exportService

    // Export settings state
    var format by mutableStateOf(ExportFormat.JPEG)
    var quality by mutableIntStateOf(95)
    var colorSpace by mutableStateOf(ColorSpace.SRGB)
    var embedIcc by mutableStateOf(true)
    var includeMetadata by mutableStateOf(true)
    var isHdr by mutableStateOf(false)
    var maxDimension by mutableStateOf("")
    var maxWidth by mutableStateOf("")
    var maxHeight by mutableStateOf("")
    var bitDepth by mutableIntStateOf(8)
    var outputPath by mutableStateOf("/sdcard/Pictures/AlcedoStudio")

    // Batch export state
    var showBatchExport by mutableStateOf(false)
    var batchImageIds by mutableStateOf(listOf<String>())

    // Export progress from service
    val exportProgress: StateFlow<ExportService.ExportProgress> = exportService.exportProgress

    // Derived export state
    val isExporting: Boolean
        get() = exportProgress.value.status == ExportService.ExportStatus.EXPORTING

    // Export result
    private val _lastResult = MutableStateFlow<ExportService.ExportResult?>(null)
    val lastResult: StateFlow<ExportService.ExportResult?> = _lastResult.asStateFlow()

    private val _batchResult = MutableStateFlow<ExportService.ExportBatchResult?>(null)
    val batchResult: StateFlow<ExportService.ExportBatchResult?> = _batchResult.asStateFlow()

    fun buildSettings(sourceExifPath: String? = null): ExportSettings {
        return ExportSettings(
            format = format,
            quality = quality,
            colorSpace = colorSpace,
            embedIcc = embedIcc,
            includeMetadata = includeMetadata,
            maxDimension = maxDimension.toIntOrNull(),
            maxWidth = maxWidth.toIntOrNull(),
            maxHeight = maxHeight.toIntOrNull(),
            isHdr = isHdr,
            bitDepth = bitDepth,
            outputPath = outputPath,
            sourceExifPath = sourceExifPath
        )
    }

    fun exportSingle(sourcePath: String, processedBitmap: Bitmap? = null) {
        viewModelScope.launch {
            val settings = buildSettings(sourceExifPath = sourcePath)
            val result = exportService.exportImage(sourcePath, settings, processedBitmap)
            _lastResult.value = result
        }
    }

    fun exportBatch(items: List<ExportService.ExportBatchItem>) {
        viewModelScope.launch {
            val settings = buildSettings()
            val result = exportService.exportBatch(items, settings)
            _batchResult.value = result
        }
    }

    fun cancelExport() {
        exportService.cancelExport()
    }

    fun resetState() {
        exportService.resetProgress()
        _lastResult.value = null
        _batchResult.value = null
    }

    override fun onCleared() {
        super.onCleared()
        exportService.cancelExport()
    }
}
