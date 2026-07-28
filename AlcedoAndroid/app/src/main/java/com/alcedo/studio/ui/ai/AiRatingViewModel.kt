package com.alcedo.studio.ui.ai

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alcedo.studio.data.model.AiRating
import com.alcedo.studio.data.model.ImageItem
import com.alcedo.studio.domain.repository.ImageRepository
import com.alcedo.studio.domain.service.AiRatingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AI culling/rating ViewModel. Loads the top-rated images, runs batch culling
 * over a selection, and surfaces per-image [AiRating] details.
 */
@HiltViewModel
class AiRatingViewModel @Inject constructor(
    private val aiRatingService: AiRatingService,
    private val imageRepository: ImageRepository,
) : ViewModel() {

    data class RatingUiState(
        val topRated: List<RatingEntry> = emptyList(),
        val isCulling: Boolean = false,
        val culledCount: Int = 0,
        val cullTotal: Int = 0,
        val selectedDetail: AiRating? = null,
        val error: String? = null,
    )

    data class RatingEntry(
        val image: ImageItem,
        val rating: AiRating?,
    )

    private val _uiState = MutableStateFlow(RatingUiState())
    val uiState: StateFlow<RatingUiState> = _uiState.asStateFlow()

    init { loadTopRated() }

    fun loadTopRated() {
        viewModelScope.launch {
            runCatching { aiRatingService.topRated(50) }
                .onSuccess { ratings ->
                    val entries = ratings.mapNotNull { r ->
                        imageRepository.getImage(r.imageId)?.let { img -> RatingEntry(img, r) }
                    }
                    _uiState.update { it.copy(topRated = entries) }
                }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun cullBatch(images: List<ImageItem>) {
        if (images.isEmpty()) return
        _uiState.update { it.copy(isCulling = true, culledCount = 0, cullTotal = images.size, error = null) }
        viewModelScope.launch {
            val items = images.map { Uri.parse(it.originalUri) to it.id }
            val metadata = images.associate { it.id to mapOf("iso" to (it.iso?.toString() ?: "")) }
            runCatching {
                aiRatingService.cullBatch(items, metadata) { done, total ->
                    _uiState.update { it.copy(culledCount = done, cullTotal = total) }
                }
            }.onSuccess {
                _uiState.update { it.copy(isCulling = false) }
                loadTopRated()
            }.onFailure { e ->
                _uiState.update { it.copy(isCulling = false, error = e.message ?: "Cull failed") }
            }
        }
    }

    fun showDetail(imageId: String) {
        viewModelScope.launch {
            runCatching { aiRatingService.getRating(imageId) }
                .onSuccess { rating -> _uiState.update { it.copy(selectedDetail = rating) } }
        }
    }

    fun dismissDetail() {
        _uiState.update { it.copy(selectedDetail = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
