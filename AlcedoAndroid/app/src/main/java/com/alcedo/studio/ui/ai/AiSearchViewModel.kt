package com.alcedo.studio.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alcedo.studio.data.model.ImageItem
import com.alcedo.studio.domain.repository.ImageRepository
import com.alcedo.studio.domain.service.SearchService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Semantic search ViewModel. Runs a free-text query through [SearchService],
 * resolving the resulting image ids into [ImageItem]s for the result grid.
 */
@HiltViewModel
class AiSearchViewModel @Inject constructor(
    private val searchService: SearchService,
    private val imageRepository: ImageRepository,
) : ViewModel() {

    data class SearchUiState(
        val query: String = "",
        val isSearching: Boolean = false,
        val results: List<SearchResult> = emptyList(),
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

    fun updateQuery(text: String) {
        _uiState.update { it.copy(query = text) }
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) {
            _uiState.update { it.copy(results = emptyList()) }
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
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSearching = false, error = e.message ?: "Search failed") }
                }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
