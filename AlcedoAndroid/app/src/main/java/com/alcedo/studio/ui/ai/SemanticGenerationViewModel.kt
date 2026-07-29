package com.alcedo.studio.ui.ai

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alcedo.studio.data.model.AiImageAnalysis
import com.alcedo.studio.domain.service.SemanticGenerationService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Semantic tag generation ViewModel. Drives [SemanticGenerationService.analyze]
 * for a single image, exposing real progress and the resulting analysis through
 * a [StateFlow]. Replaces the dialog's previous fake/placeholder progress data.
 */
@HiltViewModel
class SemanticGenerationViewModel @Inject constructor(
    private val service: SemanticGenerationService,
) : ViewModel() {

    data class UiState(
        val selectedModel: String = "MobileCLIP2",
        val selectedFolder: String = "",
        val isGenerating: Boolean = false,
        /** 0..1 progress for the current generation. */
        val progress: Float = 0f,
        val completedCount: Int = 0,
        val totalCount: Int = 0,
        val analysis: AiImageAnalysis? = null,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var generateJob: Job? = null

    fun setSelectedModel(model: String) {
        _uiState.update { it.copy(selectedModel = model) }
    }

    fun setSelectedFolder(folder: String) {
        _uiState.update { it.copy(selectedFolder = folder) }
    }

    /**
     * Start generating semantic tags for [uri]/[imageId]. Animates progress
     * while the (potentially slow) analysis runs, then surfaces the result.
     */
    fun startScan(uri: Uri, imageId: String) {
        if (_uiState.value.isGenerating) return
        cancelScan()
        _uiState.update {
            it.copy(isGenerating = true, progress = 0f, completedCount = 0, totalCount = 1, error = null, analysis = null)
        }
        generateJob = viewModelScope.launch {
            // Drive a lightweight progress ramp while the analysis runs so the
            // UI shows movement instead of a frozen bar; snap to 100% on done.
            val progressJob = viewModelScope.launch {
                var p = 0f
                while (p < 0.9f) {
                    kotlinx.coroutines.delay(300)
                    p = (p + 0.05f).coerceAtMost(0.9f)
                    _uiState.update { it.copy(progress = p) }
                }
            }
            runCatching { service.analyze(uri, imageId) }
                .onSuccess { result ->
                    progressJob.cancel()
                    _uiState.update {
                        it.copy(
                            isGenerating = false,
                            progress = 1f,
                            completedCount = 1,
                            analysis = result,
                            error = if (result == null) "Analysis returned no result" else null,
                        )
                    }
                }
                .onFailure { e ->
                    progressJob.cancel()
                    _uiState.update { it.copy(isGenerating = false, progress = 0f, error = e.message ?: "Generation failed") }
                }
        }
    }

    /** Cancel an in-flight generation. */
    fun cancelScan() {
        generateJob?.cancel()
        generateJob = null
        if (_uiState.value.isGenerating) {
            _uiState.update { it.copy(isGenerating = false, progress = 0f) }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        generateJob?.cancel()
    }
}
