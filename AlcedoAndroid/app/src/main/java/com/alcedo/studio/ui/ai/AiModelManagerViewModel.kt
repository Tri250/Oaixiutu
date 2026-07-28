package com.alcedo.studio.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alcedo.studio.data.model.AiModelAsset
import com.alcedo.studio.data.model.AiModelKind
import com.alcedo.studio.domain.service.AiSidecarRuntimeService
import com.alcedo.studio.domain.service.ModelAssetCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AI model manager ViewModel. Surfaces the model catalogue ([ModelAssetCatalog])
 * with live download/load state from [AiSidecarRuntimeService] and exposes
 * download/delete/set-default actions.
 */
@HiltViewModel
class AiModelManagerViewModel @Inject constructor(
    private val sidecarRuntime: AiSidecarRuntimeService,
) : ViewModel() {

    data class ModelUiState(
        val models: List<ModelEntry> = emptyList(),
        val activeDownloadId: String? = null,
        val downloadProgress: Float = 0f,
        val defaultClipId: String = ModelAssetCatalog.CLIP_VIT_BASE_PATCH32.id,
        val error: String? = null,
    )

    data class ModelEntry(
        val asset: AiModelAsset,
        val isDownloaded: Boolean,
        val isLoaded: Boolean,
        val isDownloading: Boolean,
    )

    private val _uiState = MutableStateFlow(ModelUiState())
    val uiState: StateFlow<ModelUiState> = _uiState.asStateFlow()

    init {
        refresh()
        // Mirror load/download state from the sidecar runtime.
        viewModelScope.launch {
            sidecarRuntime.state.collect { runtime ->
                refresh(runtime.loadedModelIds, runtime.downloadingModelIds)
            }
        }
    }

    private fun refresh(
        loaded: Set<String> = sidecarRuntime.state.value.loadedModelIds,
        downloading: Set<String> = sidecarRuntime.state.value.downloadingModelIds,
    ) {
        val entries = ModelAssetCatalog.ALL.map { asset ->
            ModelEntry(
                asset = asset,
                isDownloaded = sidecarRuntime.isModelPresent(asset),
                isLoaded = asset.id in loaded,
                isDownloading = asset.id in downloading,
            )
        }
        _uiState.update { it.copy(models = entries) }
    }

    fun download(asset: AiModelAsset) {
        viewModelScope.launch {
            _uiState.update { it.copy(activeDownloadId = asset.id, error = null) }
            runCatching { sidecarRuntime.ensureLoaded(asset) }
                .onSuccess { _uiState.update { it.copy(activeDownloadId = null) } }
                .onFailure { e -> _uiState.update { it.copy(activeDownloadId = null, error = e.message) } }
        }
    }

    fun delete(asset: AiModelAsset) {
        val file = sidecarRuntime.localPathFor(asset)
        if (sidecarRuntime.isModelPresent(asset)) {
            sidecarRuntime.unload(asset.id)
            file.delete()
        }
        refresh()
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
