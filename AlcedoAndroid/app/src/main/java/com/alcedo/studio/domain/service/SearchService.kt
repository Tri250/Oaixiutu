package com.alcedo.studio.domain.service

import android.content.Context
import com.alcedo.studio.data.local.*
import com.alcedo.studio.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RankedSearchResult(
    val imageId: Long,
    val score: Float = 0f
)

class SearchService(
    private val context: Context,
    private val aiService: AiService,
    private val metadataDao: ImageMetadataDao,
    private val labelDao: SemanticLabelDao,
    private val elementDao: SleeveElementDao
) {

    private val _searchResults = MutableStateFlow<List<UInt>>(emptyList())
    val searchResults: StateFlow<List<UInt>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    suspend fun search(query: SearchQuery): List<UInt> = emptyList()

    suspend fun semanticSearch(queryText: String, limit: Int = 50): List<UInt> = emptyList()

    suspend fun keywordSearch(queryText: String, limit: Int = 50): List<UInt> = emptyList()

    suspend fun hybridSearch(queryText: String, limit: Int = 50): List<UInt> = emptyList()

    suspend fun combinedSearch(
        textQuery: String,
        enableSemantic: Boolean = false,
        maxResults: Int = 100
    ): List<RankedSearchResult> = emptyList()

    fun getSearchResults(): List<UInt> = _searchResults.value

    fun clearResults() {
        _searchResults.value = emptyList()
    }
}
