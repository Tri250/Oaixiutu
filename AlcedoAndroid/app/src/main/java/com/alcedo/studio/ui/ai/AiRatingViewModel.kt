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

/** LLM provider options offered on the AI rating screen. */
enum class LlmProvider(val displayName: String) {
    OPENAI("OpenAI"),
    ANTHROPIC("Anthropic"),
    VOLCENGINE("Volcengine"),
}

/**
 * AI culling/rating ViewModel. Loads the top-rated images, runs batch culling
 * over a selection, and surfaces per-image [AiRating] details. Owns the
 * provider/strictness configuration so it survives recomposition and can drive
 * the culling pipeline.
 */
@HiltViewModel
class AiRatingViewModel @Inject constructor(
    private val aiRatingService: AiRatingService,
    private val imageRepository: ImageRepository,
) : ViewModel() {

    data class RatingUiState(
        val topRated: List<RatingEntry> = emptyList(),
        val isLoading: Boolean = false,
        val isCulling: Boolean = false,
        val culledCount: Int = 0,
        val cullTotal: Int = 0,
        val selectedDetail: AiRating? = null,
        val selectedProvider: LlmProvider = LlmProvider.OPENAI,
        val strictness: Float = 0.5f,
        val isApplyingRatings: Boolean = false,
        val message: String? = null,
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
            _uiState.update { it.copy(isLoading = true) }
            runCatching { aiRatingService.topRated(50) }
                .onSuccess { ratings ->
                    val entries = ratings.mapNotNull { r ->
                        imageRepository.getImage(r.imageId)?.let { img -> RatingEntry(img, r) }
                    }
                    _uiState.update { it.copy(topRated = entries, isLoading = false) }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load ratings") } }
        }
    }

    fun setSelectedProvider(provider: LlmProvider) {
        _uiState.update { it.copy(selectedProvider = provider) }
    }

    fun setStrictness(value: Float) {
        _uiState.update { it.copy(strictness = value.coerceIn(0f, 1f)) }
    }

    /**
     * Run batch analysis (culling) over the currently displayed top-rated
     * images using the selected provider and strictness. Re-runs the rating
     * pipeline so scores reflect the latest configuration.
     */
    fun analyzeSelected() {
        val images = _uiState.value.topRated.map { it.image }
        if (images.isEmpty() || _uiState.value.isCulling) {
            if (images.isEmpty()) {
                _uiState.update { it.copy(message = "No images to analyze yet") }
            }
            return
        }
        cullBatch(images)
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

    /**
     * Write the AI-suggested star ratings back to the metadata of every rated
     * image in the current list. This is the user-confirmed "Apply Ratings to
     * EXIF" action.
     */
    fun applyRatingsToExif() {
        val entries = _uiState.value.topRated
        if (entries.isEmpty() || _uiState.value.isApplyingRatings) return
        _uiState.update { it.copy(isApplyingRatings = true) }
        viewModelScope.launch {
            var applied = 0
            entries.forEach { entry ->
                val stars = entry.rating?.suggestedRating ?: entry.rating?.stars() ?: return@forEach
                runCatching { imageRepository.setRating(entry.image.id, stars) }
                    .onSuccess { applied++ }
            }
            _uiState.update {
                it.copy(
                    isApplyingRatings = false,
                    message = "Applied ratings to $applied image${if (applied == 1) "" else "s"}",
                )
            }
        }
    }

    fun showDetail(imageId: String) {
        viewModelScope.launch {
            runCatching { aiRatingService.getRating(imageId) }
                .onSuccess { rating -> _uiState.update { it.copy(selectedDetail = rating) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message ?: "Failed to load rating") } }
        }
    }

    fun dismissDetail() {
        _uiState.update { it.copy(selectedDetail = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
