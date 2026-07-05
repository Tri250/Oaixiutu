package com.alcedo.studio.domain.service

import android.content.Context
import androidx.sqlite.db.SimpleSQLiteQuery
import com.alcedo.studio.data.local.*
import com.alcedo.studio.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

data class RankedSearchResult(
    val imageId: Long,
    val score: Float,
    val resultType: ResultType,
    val matchedField: String = "",
    val highlight: String = "",
    val image: ImageModel? = null
)

data class SearchHistoryEntry(
    val query: String,
    val timestamp: Instant = Instant.now(),
    val resultCount: Int = 0
)

data class SearchSuggestion(
    val text: String,
    val type: SearchSuggestionType
)

enum class SearchSuggestionType {
    HISTORY, LABEL, EXIF_FIELD, CAMERA, LENS
}

data class SearchState(
    val query: String = "",
    val results: List<RankedSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val totalResults: Int = 0,
    val searchType: List<ResultType> = emptyList(),
    val suggestions: List<SearchSuggestion> = emptyList()
)

class SearchService(
    private val context: Context,
    private val aiService: AiService,
    private val metadataDao: ImageMetadataDao,
    private val labelDao: SemanticLabelDao,
    private val elementDao: SleeveElementDao
) {
    companion object {
        private const val TAG = "SearchService"
        private const val MAX_SEARCH_HISTORY = 50
        private const val FUZZY_THRESHOLD = 0.7f
        private const val EXACT_BOOST = 1.5f
        private const val FUZZY_MAX_DISTANCE = 2
        private const val MAX_SUGGESTIONS = 10
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private val searchHistory = mutableListOf<SearchHistoryEntry>()

    // ── Main Search ──

    suspend fun search(query: SearchQuery): List<RankedSearchResult> = withContext(Dispatchers.Default) {
        _searchState.value = _searchState.value.copy(query = query.rawQuery, isSearching = true, results = emptyList())

        if (query.rawQuery.isBlank()) {
            _searchState.value = _searchState.value.copy(isSearching = false)
            return@withContext emptyList()
        }

        val results = mutableListOf<RankedSearchResult>()
        val allMetadata = metadataDao.getAllMetadata()
        val allImages = allMetadata.map { ImageModel.fromMetadataEntity(it) }
        val queryLower = query.rawQuery.lowercase().trim()

        val queryClassification = searchQueryClassifier.classify(query.rawQuery)

        // 1. Exact filename/path search (highest priority)
        if (queryClassification.isExactName) {
            val exactResults = allImages.filter {
                it.imageName.equals(query.rawQuery, ignoreCase = true) ||
                it.imagePath.contains(query.rawQuery, ignoreCase = true)
            }.map { image ->
                RankedSearchResult(
                    imageId = image.imageId,
                    score = 1.0f * EXACT_BOOST,
                    resultType = ResultType.EXACT,
                    matchedField = "filename",
                    highlight = highlightMatch(image.imageName, query.rawQuery),
                    image = image
                )
            }
            results.addAll(exactResults)
        }

        // 2. FTS search on element names
        val ftsResults = elementDao.ftsSearchElements(SimpleSQLiteQuery(query.rawQuery, null))
        results.addAll(ftsResults.map { element ->
            val image = allImages.find { it.imageId == element.elementId }
            RankedSearchResult(
                imageId = element.elementId,
                score = 0.95f,
                resultType = ResultType.EXACT,
                matchedField = "filename",
                highlight = highlightMatch(element.elementName, query.rawQuery),
                image = image
            )
        })

        // 3. Fuzzy search with typo tolerance
        if (query.enableFuzzy && queryClassification.isFreeText) {
            val fuzzyResults = allImages
                .filter { image ->
                    fuzzyMatch(image.imageName, queryLower) >= FUZZY_THRESHOLD ||
                    fuzzyMatch(image.imagePath, queryLower) >= FUZZY_THRESHOLD
                }
                .map { image ->
                    val nameScore = fuzzyMatch(image.imageName, queryLower)
                    val pathScore = fuzzyMatch(image.imagePath, queryLower)
                    RankedSearchResult(
                        imageId = image.imageId,
                        score = maxOf(nameScore, pathScore),
                        resultType = ResultType.EXACT,
                        matchedField = if (nameScore > pathScore) "filename" else "path",
                        highlight = highlightMatch(image.imageName, query.rawQuery),
                        image = image
                    )
                }
                .filter { it.score >= FUZZY_THRESHOLD }
            results.addAll(fuzzyResults)
        }

        // 4. EXIF field search
        if (query.enableExif && queryClassification.isExifQuery) {
            val exifResults = allImages.filter { image ->
                val exif = image.exifDisplay
                (queryClassification.exifCameraMake && exif.cameraMake.contains(query.rawQuery, ignoreCase = true)) ||
                (queryClassification.exifCameraModel && exif.cameraModel.contains(query.rawQuery, ignoreCase = true)) ||
                (queryClassification.exifLensModel && exif.lensModel.contains(query.rawQuery, ignoreCase = true)) ||
                (queryClassification.exifFocalLength && exif.focalLength.contains(query.rawQuery)) ||
                (queryClassification.exifAperture && exif.aperture.contains(query.rawQuery)) ||
                (queryClassification.exifIso && exif.iso.contains(query.rawQuery)) ||
                (queryClassification.exifCaptureDate && exif.captureDate.contains(query.rawQuery))
            }.map { image ->
                RankedSearchResult(
                    imageId = image.imageId,
                    score = 0.9f,
                    resultType = ResultType.EXIF,
                    matchedField = "exif",
                    image = image
                )
            }
            results.addAll(exifResults)
        }

        // 5. Label search
        if (query.enableLabel && queryClassification.isLabelQuery) {
            val labelResults = labelDao.searchLabels(query.rawQuery)
            labelResults.forEach { label ->
                val image = allImages.find { it.imageId == label.imageId }
                if (image != null) {
                    results.add(
                        RankedSearchResult(
                            imageId = label.imageId,
                            score = label.confidence,
                            resultType = ResultType.LABEL,
                            matchedField = "label",
                            image = image
                        )
                    )
                }
            }
        }

        // 6. Semantic search (delegates to AiService)
        if (query.enableSemantic && queryClassification.isSemanticQuery) {
            val semanticResults = aiService.searchByText(query.rawQuery, query.maxResults)
                .map { sr ->
                    RankedSearchResult(
                        imageId = sr.imageId.toLong(),
                        score = sr.score,
                        resultType = ResultType.SEMANTIC,
                        matchedField = "semantic",
                        image = allImages.find { it.imageId == sr.imageId.toLong() }
                    )
                }
            results.addAll(semanticResults)
        }

        // Apply filters
        val filtered = applyFilters(results, query.filterCombo)

        // Deduplicate and rank
        val ranked = filtered
            .distinctBy { it.imageId }
            .sortedByDescending { it.score }
            .take(query.maxResults)

        if (ranked.isNotEmpty()) {
            addToHistory(query.rawQuery, ranked.size)
        }

        generateSuggestions(query.rawQuery, allImages)

        _searchState.value = _searchState.value.copy(
            results = ranked,
            isSearching = false,
            totalResults = ranked.size,
            searchType = ranked.map { it.resultType }.distinct()
        )

        ranked
    }

    // ── EXIF Search ──

    suspend fun searchByExif(filter: ExifFilter): List<ImageModel> = withContext(Dispatchers.IO) {
        val allMetadata = metadataDao.getMetadataByExifFilter(
            cameraMake = filter.cameraMake,
            cameraModel = filter.cameraModel,
            lensModel = filter.lensModel,
            minFocalLength = filter.minFocalLength,
            maxFocalLength = filter.maxFocalLength,
            minAperture = filter.minAperture,
            maxAperture = filter.maxAperture,
            minIso = filter.minIso,
            maxIso = filter.maxIso,
            minShutterSpeed = filter.minShutterSpeed,
            maxShutterSpeed = filter.maxShutterSpeed,
            dateFrom = filter.dateFrom,
            dateTo = filter.dateTo,
            limit = Int.MAX_VALUE,
            offset = 0
        )
        allMetadata.map { ImageModel.fromMetadataEntity(it) }
    }

    // ── Combined Search with Ranking ──

    suspend fun combinedSearch(
        textQuery: String,
        exifFilter: ExifFilter? = null,
        imageFilter: ImageFilter? = null,
        enableSemantic: Boolean = false,
        maxResults: Int = 50
    ): List<RankedSearchResult> = withContext(Dispatchers.Default) {
        val query = SearchQuery(
            rawQuery = textQuery,
            enableFuzzy = true,
            enableSemantic = enableSemantic,
            enableExif = true,
            enableLabel = true,
            filterCombo = null,
            maxResults = maxResults
        )
        search(query)
    }

    // ── Search Result Highlighting ──

    fun highlightMatch(text: String, query: String): String {
        if (query.isBlank()) return text
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()
        val index = lowerText.indexOf(lowerQuery)
        if (index < 0) return text

        return buildString {
            if (index > 0) append(text.substring(0, index))
            append("<b>")
            append(text.substring(index, index + query.length))
            append("</b>")
            if (index + query.length < text.length) {
                append(text.substring(index + query.length))
            }
        }
    }

    // ── Search History ──

    fun getSearchHistory(): List<SearchHistoryEntry> = searchHistory.toList()

    fun clearSearchHistory() { searchHistory.clear() }

    fun removeFromHistory(query: String) {
        searchHistory.removeAll { it.query.equals(query, ignoreCase = true) }
    }

    private fun addToHistory(query: String, resultCount: Int) {
        searchHistory.removeAll { it.query.equals(query, ignoreCase = true) }
        searchHistory.add(0, SearchHistoryEntry(query, Instant.now(), resultCount))
        if (searchHistory.size > MAX_SEARCH_HISTORY) {
            searchHistory.removeAt(searchHistory.size - 1)
        }
    }

    // ── Suggestions ──

    fun getSuggestions(): List<SearchSuggestion> = _searchState.value.suggestions

    private suspend fun generateSuggestions(query: String, allImages: List<ImageModel>) {
        if (query.isBlank()) {
            val suggestions = searchHistory.take(MAX_SUGGESTIONS).map { entry ->
                SearchSuggestion(entry.query, SearchSuggestionType.HISTORY)
            }
            _searchState.value = _searchState.value.copy(suggestions = suggestions)
            return
        }

        val suggestions = mutableListOf<SearchSuggestion>()

        searchHistory
            .filter { it.query.contains(query, ignoreCase = true) }
            .take(3)
            .forEach { suggestions.add(SearchSuggestion(it.query, SearchSuggestionType.HISTORY)) }

        val uniqueCameras = allImages
            .map { it.exifDisplay.cameraModel }
            .filter { it.isNotEmpty() && it.contains(query, ignoreCase = true) }
            .distinct()
            .take(3)
        uniqueCameras.forEach { suggestions.add(SearchSuggestion(it, SearchSuggestionType.CAMERA)) }

        val uniqueLenses = allImages
            .map { it.exifDisplay.lensModel }
            .filter { it.isNotEmpty() && it.contains(query, ignoreCase = true) }
            .distinct()
            .take(2)
        uniqueLenses.forEach { suggestions.add(SearchSuggestion(it, SearchSuggestionType.LENS)) }

        val uniqueLabels = aiService.getAllUniqueLabels()
            .filter { it.contains(query, ignoreCase = true) }
            .take(2)
        uniqueLabels.forEach { suggestions.add(SearchSuggestion(it, SearchSuggestionType.LABEL)) }

        _searchState.value = _searchState.value.copy(
            suggestions = suggestions.take(MAX_SUGGESTIONS)
        )
    }

    // ── Get EXIF Statistics ──

    suspend fun getExifStatistics(): ExifStatistics = withContext(Dispatchers.IO) {
        val allMetadata = metadataDao.getAllMetadata()
        val allImages = allMetadata.map { ImageModel.fromMetadataEntity(it) }
        val cameras = mutableMapOf<String, Int>()
        val lenses = mutableMapOf<String, Int>()
        val apertures = mutableMapOf<String, Int>()
        val focalLengths = mutableMapOf<String, Int>()

        for (image in allImages) {
            val exif = image.exifDisplay
            if (exif.cameraModel.isNotEmpty()) cameras[exif.cameraModel] = (cameras[exif.cameraModel] ?: 0) + 1
            if (exif.lensModel.isNotEmpty()) lenses[exif.lensModel] = (lenses[exif.lensModel] ?: 0) + 1
            if (exif.aperture.isNotEmpty()) apertures[exif.aperture] = (apertures[exif.aperture] ?: 0) + 1
            if (exif.focalLength.isNotEmpty()) focalLengths[exif.focalLength] = (focalLengths[exif.focalLength] ?: 0) + 1
        }

        ExifStatistics(
            cameras = cameras.entries.sortedByDescending { it.value }.take(20).associate { it.key to it.value },
            lenses = lenses.entries.sortedByDescending { it.value }.take(20).associate { it.key to it.value },
            apertures = apertures.entries.sortedByDescending { it.value }.take(10).associate { it.key to it.value },
            focalLengths = focalLengths.entries.sortedByDescending { it.value }.take(10).associate { it.key to it.value }
        )
    }

    data class ExifStatistics(
        val cameras: Map<String, Int> = emptyMap(),
        val lenses: Map<String, Int> = emptyMap(),
        val apertures: Map<String, Int> = emptyMap(),
        val focalLengths: Map<String, Int> = emptyMap()
    )

    // ── Private Helpers ──

    private fun applyFilters(
        results: List<RankedSearchResult>,
        filterCombo: FilterCombo?
    ): List<RankedSearchResult> {
        if (filterCombo == null) return results
        return results
    }

    private fun fuzzyMatch(text: String, query: String): Float {
        if (text.isBlank() || query.isBlank()) return 0f
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()

        if (lowerText.contains(lowerQuery)) return 1.0f

        val distance = levenshteinDistance(lowerText, lowerQuery)
        val maxLen = maxOf(lowerText.length, lowerQuery.length)
        if (maxLen == 0) return 0f

        return 1f - (distance.toFloat() / maxLen)
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        if (s1.length > 100 || s2.length > 100) {
            return if (s1.contains(s2) || s2.contains(s1)) 0 else FUZZY_MAX_DISTANCE + 1
        }
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        return dp[s1.length][s2.length]
    }

    // ── Cleanup ──

    fun clearResults() {
        _searchState.value = SearchState()
    }

    fun shutdown() {
        scope.cancel()
    }
}

// Dependencies used by SearchService
private val searchQueryClassifier = SearchQueryClassifier()