package com.alcedo.studio.domain.service

import com.alcedo.studio.data.model.FilterCombo
import com.alcedo.studio.data.model.ImageFlag
import com.alcedo.studio.data.model.SemanticSearchResult
import com.alcedo.studio.data.model.SortDescriptor
import com.alcedo.studio.data.model.SortField
import com.alcedo.studio.domain.repository.ImageRepository
import com.alcedo.studio.utils.ThreadPool
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Semantic + structured search service. Combines CLIP vector search
 * ([ClipInferenceEngine]) with EXIF/rating filters ([SearchQueryClassifier]) to
 * return a unified ranked result set for the AI search screen.
 */
@Singleton
class SearchService @Inject constructor(
    private val classifier: SearchQueryClassifier,
    private val clipEngine: ClipInferenceEngine,
    private val imageRepository: ImageRepository,
    private val sidecarRuntime: AiSidecarRuntimeService,
) {

    data class SearchResult(
        val imageId: String,
        val score: Float,
        val matchedTags: List<String>,
        val fromSemantic: Boolean,
    )

    /** Run a free-text [query] and return ranked results. */
    suspend fun search(query: String, limit: Int = 100): List<SearchResult> = withContext(ThreadPool.aiInference) {
        if (query.isBlank()) return@withContext emptyList()
        val classified = classifier.classify(query)

        val semanticResults = if (classified.hasSemantic) semanticSearch(classified.semanticText, limit) else emptyList()
        val filterResults = if (classified.hasFilters) filterSearch(classified) else emptyList()

        mergeResults(semanticResults, filterResults, classified.hasSemantic, classified.hasFilters, limit)
    }

    private suspend fun semanticSearch(text: String, limit: Int): List<SearchResult> {
        if (!sidecarRuntime.state.value.ready && !clipEngine.ensureReady()) return emptyList()
        val modelId = sidecarRuntime.defaultClipAsset().id
        val queryVec = clipEngine.encodeText(text) ?: return emptyList()
        return clipEngine.search(queryVec, modelId, limit).map { (id, score) ->
            SearchResult(id, score, emptyList(), fromSemantic = true)
        }
    }

    private suspend fun filterSearch(classified: SearchQueryClassifier.ClassifiedQuery): List<SearchResult> {
        val exif = classified.exifFilters
        val filter = FilterCombo(
            searchText = classified.semanticText.takeIf { it.isNotBlank() },
            ratingMin = classified.ratingFilter?.first ?: 0,
            ratingMax = classified.ratingFilter?.last ?: 5,
        )
        val images = imageRepository.queryImages(filter, SortDescriptor(SortField.AI_SCORE), 500, 0)
        return images.map { img ->
            val matchScore = computeFilterScore(img, exif)
            SearchResult(img.id, matchScore, img.aiTags, fromSemantic = false)
        }.filter { it.score > 0f }
    }

    private fun computeFilterScore(
        img: com.alcedo.studio.data.model.ImageItem,
        exif: Map<String, String>,
    ): Float {
        var score = 0.5f
        exif["iso"]?.toIntOrNull()?.let { if (img.iso == it) score += 0.3f }
        exif["aperture"]?.toFloatOrNull()?.let { if (img.aperture == it) score += 0.3f }
        exif["focalLength"]?.toFloatOrNull()?.let { if (img.focalLength == it) score += 0.3f }
        exif["cameraModel"]?.let { if (img.cameraModel?.equals(it, ignoreCase = true) == true) score += 0.4f }
        exif["lensModel"]?.let { if (img.lensModel?.equals(it, ignoreCase = true) == true) score += 0.4f }
        return score.coerceAtMost(1f)
    }

    private fun mergeResults(
        semantic: List<SearchResult>,
        filtered: List<SearchResult>,
        hasSemantic: Boolean,
        hasFilters: Boolean,
        limit: Int,
    ): List<SearchResult> {
        if (!hasSemantic) return filtered.sortedByDescending { it.score }.take(limit)
        if (!hasFilters) return semantic.sortedByDescending { it.score }.take(limit)
        // Intersect: boost items present in both.
        val byId = mutableMapOf<String, SearchResult>()
        semantic.forEach { byId[it.imageId] = it.copy(score = it.score * 0.7f) }
        filtered.forEach { f ->
            byId[f.imageId] = byId[f.imageId]?.let { it.copy(score = it.score + f.score * 0.5f) }
                ?: f.copy(score = f.score * 0.4f)
        }
        return byId.values.sortedByDescending { it.score }.take(limit)
    }

    /** Convert to the persisted [SemanticSearchResult] model. */
    fun toModelResults(results: List<SearchResult>): List<SemanticSearchResult> =
        results.map { SemanticSearchResult(it.imageId, it.score, it.matchedTags, it.fromSemantic) }
}
