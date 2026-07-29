package com.alcedo.studio.ui.ai

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alcedo.studio.data.model.ImageItem
import com.alcedo.studio.domain.repository.ImageRepository
import com.alcedo.studio.domain.service.AiSidecarRuntimeService
import com.alcedo.studio.domain.service.SearchService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Context.searchDataStore by preferencesDataStore(name = "alcedo_search")
private val RECENT_SEARCHES_KEY = stringPreferencesKey("recent_searches")
private const val MAX_RECENT_SEARCHES = 8

/**
 * Semantic search ViewModel. Runs a free-text query through [SearchService],
 * resolving the resulting image ids into [ImageItem]s for the result grid.
 * Persists recent successful queries in DataStore and surfaces the live CLIP
 * model status from [AiSidecarRuntimeService].
 */
@HiltViewModel
class AiSearchViewModel @Inject constructor(
    private val searchService: SearchService,
    private val imageRepository: ImageRepository,
    private val sidecarRuntime: AiSidecarRuntimeService,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** Snapshot of the semantic search model readiness for the status bar. */
    data class ModelStatus(
        val isReady: Boolean,
        val modelName: String,
        val isDownloading: Boolean,
    )

    data class SearchUiState(
        val query: String = "",
        val isSearching: Boolean = false,
        val results: List<SearchResult> = emptyList(),
        val recentSearches: List<String> = emptyList(),
        val modelStatus: ModelStatus = ModelStatus(
            isReady = false,
            modelName = "CLIP ViT-B/32",
            isDownloading = false,
        ),
        val error: String? = null,
    )

    data class SearchResult(
        val image: ImageItem,
        val score: Float,
        val matchedTags: List<String>,
        val fromSemantic: Boolean,
    )

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        loadRecentSearches()
        // Mirror the sidecar runtime state (model ready / downloading) into the
        // UI so the status bar reflects the real CLIP model availability.
        viewModelScope.launch {
            sidecarRuntime.state.collect { runtime ->
                val asset = sidecarRuntime.defaultClipAsset()
                _uiState.update {
                    it.copy(
                        modelStatus = ModelStatus(
                            isReady = runtime.ready,
                            modelName = asset.name,
                            isDownloading = runtime.downloadingModelIds.isNotEmpty(),
                        ),
                    )
                }
            }
        }
    }

    fun updateQuery(text: String) {
        _uiState.update { it.copy(query = text) }
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) {
            _uiState.update { it.copy(results = emptyList()) }
            return
        }
        // Check if model is ready before searching
        if (!_uiState.value.modelStatus.isReady) {
            _uiState.update { it.copy(error = "AI model not ready. Please download and activate a model first.", isSearching = false) }
            return
        }
        _uiState.update { it.copy(isSearching = true, error = null) }
        viewModelScope.launch {
            runCatching { searchService.search(query, 100) }
                .onSuccess { serviceResults ->
                    val resolved = serviceResults.mapNotNull { sr ->
                        imageRepository.getImage(sr.imageId)?.let { img ->
                            SearchResult(img, sr.score, sr.matchedTags, sr.fromSemantic)
                        }
                    }
                    _uiState.update { it.copy(isSearching = false, results = resolved) }
                    addRecentSearch(query)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSearching = false, error = e.message ?: "Search failed") }
                }
        }
    }

    /** Populate the query field from a recent search and run it. */
    fun searchRecent(query: String) {
        _uiState.update { it.copy(query = query) }
        search()
    }

    fun clearQuery() {
        _uiState.update { it.copy(query = "", results = emptyList()) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearRecentSearches() {
        _uiState.update { it.copy(recentSearches = emptyList()) }
        viewModelScope.launch {
            runCatching {
                context.searchDataStore.edit { it.remove(RECENT_SEARCHES_KEY) }
            }
        }
    }

    private fun loadRecentSearches() {
        viewModelScope.launch {
            runCatching {
                val raw = context.searchDataStore.data.first()[RECENT_SEARCHES_KEY].orEmpty()
                val list = raw.split('\n').filter { it.isNotBlank() }
                _uiState.update { it.copy(recentSearches = list) }
            }
        }
    }

    private suspend fun addRecentSearch(query: String) {
        runCatching {
            val current = context.searchDataStore.data.first()[RECENT_SEARCHES_KEY].orEmpty()
                .split('\n').filter { it.isNotBlank() }.toMutableList()
            current.remove(query)
            current.add(0, query)
            val trimmed = current.take(MAX_RECENT_SEARCHES)
            context.searchDataStore.edit { it[RECENT_SEARCHES_KEY] = trimmed.joinToString("\n") }
            _uiState.update { it.copy(recentSearches = trimmed) }
        }
    }
}
