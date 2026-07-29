package com.alcedo.studio.ui.ai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alcedo.studio.data.model.AiModelAsset
import com.alcedo.studio.domain.service.AiSidecarRuntimeService
import com.alcedo.studio.domain.service.ModelAssetCatalog
import com.alcedo.studio.domain.service.ModelDownloadService
import com.alcedo.studio.util.ContextProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Context.aiModelPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "alcedo_ai_models")
private val DEFAULT_CLIP_MODEL_KEY = stringPreferencesKey("default_clip_model_id")

/**
 * AI model manager ViewModel. Surfaces the model catalogue ([ModelAssetCatalog])
 * with live download/load state from [AiSidecarRuntimeService] and exposes
 * download/delete/set-default actions. Real per-download progress is mirrored
 * from [ModelDownloadService].
 */
@HiltViewModel
class AiModelManagerViewModel @Inject constructor(
    private val sidecarRuntime: AiSidecarRuntimeService,
    private val modelDownloadService: ModelDownloadService,
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
        /** Live download fraction in [0,1] for this model, if downloading. */
        val downloadFraction: Float = 0f,
    )

    private val _uiState = MutableStateFlow(ModelUiState())
    val uiState: StateFlow<ModelUiState> = _uiState.asStateFlow()

    private val dataStore: DataStore<Preferences>?
        get() = runCatching { ContextProvider.requireContext().aiModelPrefsDataStore }.getOrNull()

    init {
        refresh()
        // Restore the persisted default CLIP model so the user's choice survives
        // process restarts. Unknown ids (e.g. a since-deleted model) are ignored.
        viewModelScope.launch {
            val persistedId = runCatching { dataStore?.data?.first()?.get(DEFAULT_CLIP_MODEL_KEY) }.getOrNull()
            if (persistedId != null && ModelAssetCatalog.ALL.any { it.id == persistedId }) {
                _uiState.update { it.copy(defaultClipId = persistedId) }
            }
        }
        // Mirror load/download state from the sidecar runtime.
        viewModelScope.launch {
            sidecarRuntime.state.collect { runtime ->
                refresh(runtime.loadedModelIds, runtime.downloadingModelIds)
            }
        }
        // Mirror real download progress (bytes read / total) from the download
        // service so the model cards show an accurate progress bar.
        viewModelScope.launch {
            modelDownloadService.progress.collect { p ->
                if (p == null) {
                    _uiState.update { it.copy(activeDownloadId = null, downloadProgress = 0f) }
                    refresh()
                } else {
                    val fraction = if (p.totalBytes > 0) {
                        (p.bytesRead.toFloat() / p.totalBytes).coerceIn(0f, 1f)
                    } else 0f
                    _uiState.update {
                        it.copy(
                            activeDownloadId = if (p.done) null else p.modelId,
                            downloadProgress = if (p.done) 0f else fraction,
                            error = p.error?.let { err -> downloadErrorMessage(err) } ?: it.error,
                        )
                    }
                    refresh()
                }
            }
        }
    }

    private fun refresh(
        loaded: Set<String> = sidecarRuntime.state.value.loadedModelIds,
        downloading: Set<String> = sidecarRuntime.state.value.downloadingModelIds,
    ) {
        val activeId = _uiState.value.activeDownloadId
        val activeFraction = _uiState.value.downloadProgress
        val entries = ModelAssetCatalog.ALL.map { asset ->
            ModelEntry(
                asset = asset,
                isDownloaded = sidecarRuntime.isModelPresent(asset),
                isLoaded = asset.id in loaded,
                isDownloading = asset.id in downloading,
                downloadFraction = if (asset.id == activeId) activeFraction else 0f,
            )
        }
        _uiState.update { it.copy(models = entries) }
    }

    fun download(asset: AiModelAsset) {
        viewModelScope.launch {
            _uiState.update { it.copy(activeDownloadId = asset.id, downloadProgress = 0f, error = null) }
            runCatching { sidecarRuntime.ensureLoaded(asset) }
                .onSuccess { _uiState.update { it.copy(activeDownloadId = null, downloadProgress = 0f) } }
                .onFailure { e -> _uiState.update { it.copy(activeDownloadId = null, downloadProgress = 0f, error = e.message) } }
            refresh()
        }
    }

    fun delete(asset: AiModelAsset) {
        viewModelScope.launch {
            runCatching {
                if (sidecarRuntime.isModelPresent(asset)) {
                    sidecarRuntime.unload(asset.id)
                    sidecarRuntime.localPathFor(asset).delete()
                }
            }.onSuccess {
                // If the deleted model was the default, fall back to the catalogue default
                // and clear the persisted preference so the stale id isn't restored.
                if (_uiState.value.defaultClipId == asset.id) {
                    _uiState.update { it.copy(defaultClipId = ModelAssetCatalog.CLIP_VIT_BASE_PATCH32.id) }
                    runCatching { dataStore?.edit { it.remove(DEFAULT_CLIP_MODEL_KEY) } }
                }
                refresh()
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message ?: "Failed to delete model") }
            }
        }
    }

    /** Mark [asset] as the default CLIP model for semantic search. */
    fun setDefaultModel(asset: AiModelAsset) {
        _uiState.update { it.copy(defaultClipId = asset.id) }
        viewModelScope.launch {
            runCatching { dataStore?.edit { it[DEFAULT_CLIP_MODEL_KEY] = asset.id } }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun downloadErrorMessage(code: String): String = when (code) {
        "sha_mismatch" -> "Download failed: file integrity check failed"
        "write_failed" -> "Download failed: could not write model file"
        else -> "Download failed: $code"
    }
}
