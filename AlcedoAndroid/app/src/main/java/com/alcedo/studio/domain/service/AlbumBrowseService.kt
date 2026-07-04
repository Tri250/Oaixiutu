package com.alcedo.studio.domain.service

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BrowseResult(
    val items: List<BrowseItem> = emptyList(),
    val hasMore: Boolean = false,
    val nextOffset: Int = 0
)

data class BrowseItem(
    val id: UInt,
    val name: String = "",
    val path: String = "",
    val isFolder: Boolean = false,
    val thumbnailPath: String? = null
)

class AlbumBrowseService(private val context: Context) {

    private val _browseResult = MutableStateFlow<BrowseResult>(BrowseResult())
    val browseResult: StateFlow<BrowseResult> = _browseResult.asStateFlow()

    suspend fun browse(folderId: UInt, offset: Int = 0, limit: Int = 50): BrowseResult = BrowseResult()

    suspend fun browseRecent(limit: Int = 50): BrowseResult = BrowseResult()

    suspend fun browseFavorites(limit: Int = 50): BrowseResult = BrowseResult()

    suspend fun browseRated(limit: Int = 50): BrowseResult = BrowseResult()

    fun getCurrentResult(): BrowseResult = _browseResult.value
}
