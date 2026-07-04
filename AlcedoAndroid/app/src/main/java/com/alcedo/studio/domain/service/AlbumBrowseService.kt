package com.alcedo.studio.domain.service

import android.graphics.Bitmap
import com.alcedo.studio.data.model.*
import com.alcedo.studio.domain.repository.ImageRepository
import com.alcedo.studio.domain.repository.SleeveRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

enum class SortField {
    NAME, DATE, RATING, TYPE, SIZE
}

enum class SortOrder {
    ASCENDING, DESCENDING
}

enum class ViewMode {
    GRID, LIST
}

enum class QuickAction {
    RATE_1, RATE_2, RATE_3, RATE_4, RATE_5,
    DELETE, EXPORT, COPY, MOVE
}

data class AlbumState(
    val currentFolderId: Long = 0L,
    val folderPath: List<SleeveFolder> = emptyList(),
    val elements: List<AlbumElement> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val viewMode: ViewMode = ViewMode.GRID,
    val sortField: SortField = SortField.DATE,
    val sortOrder: SortOrder = SortOrder.DESCENDING,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val thumbnailGridColumns: Int = 3,
    val currentPage: Int = 0,
    val totalPages: Int = 1,
    val pageSize: Int = 50
)

data class AlbumElement(
    val elementId: Long,
    val elementName: String,
    val elementType: ElementType,
    val imageId: UInt? = null,
    val imageType: ImageType? = null,
    val thumbnail: Bitmap? = null,
    val exifDisplay: ExifDisplayMetaData? = null,
    val rating: Int = 0,
    val isSelected: Boolean = false,
    val addedTime: Instant = Instant.now(),
    val fileSize: Long = 0L
)

class AlbumBrowseService(
    private val sleeveRepository: SleeveRepository,
    private val imageRepository: ImageRepository,
    private val thumbnailService: ThumbnailService
) {
    companion object {
        private const val TAG = "AlbumBrowseService"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _albumState = MutableStateFlow(AlbumState())
    val albumState: StateFlow<AlbumState> = _albumState.asStateFlow()

    private var cachedElements = mutableMapOf<Long, List<AlbumElement>>()

    // ── Folder Tree Navigation ──

    suspend fun navigateToFolder(folderId: Long) = withContext(Dispatchers.IO) {
        _albumState.value = _albumState.value.copy(isLoading = true)

        try {
            val folder = sleeveRepository.getElement(folderId)
            val folderPath = buildFolderPath(folderId)
            val children = sleeveRepository.getChildren(folderId)
            val elements = children.map { element ->
                val albumElement = mapToAlbumElement(element)
                albumElement
            }.sortedWith(getSortComparator())

            _albumState.value = _albumState.value.copy(
                currentFolderId = folderId,
                folderPath = folderPath,
                elements = elements,
                isLoading = false,
                currentPage = 0,
                totalPages = maxOf(1, (elements.size + _albumState.value.pageSize - 1) / _albumState.value.pageSize)
            )

            cachedElements[folderId] = elements
        } catch (e: Exception) {
            _albumState.value = _albumState.value.copy(isLoading = false)
        }
    }

    suspend fun navigateToParent() = withContext(Dispatchers.IO) {
        val currentPath = _albumState.value.folderPath
        if (currentPath.size <= 1) return@withContext
        val parentId = currentPath[currentPath.size - 2].elementId
        navigateToFolder(parentId)
    }

    suspend fun navigateToRoot() = withContext(Dispatchers.IO) {
        navigateToFolder(0L)
    }

    fun getCurrentFolderId(): Long = _albumState.value.currentFolderId

    fun getFolderPath(): List<SleeveFolder> = _albumState.value.folderPath

    // ── Thumbnail Grid with Pagination ──

    suspend fun loadPage(page: Int) = withContext(Dispatchers.IO) {
        val state = _albumState.value
        val startIndex = page * state.pageSize
        val endIndex = minOf(startIndex + state.pageSize, state.elements.size)
        val pageElements = state.elements.subList(startIndex, endIndex)

        // Load thumbnails for this page
        for (element in pageElements) {
            if (element.imageId != null && element.thumbnail == null) {
                val thumbnail = thumbnailService.loadThumbnail(element.imageId)
                if (thumbnail != null) {
                    val updatedElements = state.elements.toMutableList()
                    val index = updatedElements.indexOfFirst { it.elementId == element.elementId }
                    if (index >= 0) {
                        updatedElements[index] = element.copy(thumbnail = thumbnail)
                        _albumState.value = state.copy(elements = updatedElements)
                    }
                }
            }
        }

        _albumState.value = state.copy(currentPage = page)
    }

    suspend fun loadNextPage() = withContext(Dispatchers.IO) {
        val state = _albumState.value
        if (state.currentPage < state.totalPages - 1) {
            loadPage(state.currentPage + 1)
        }
    }

    suspend fun refreshCurrentFolder() = withContext(Dispatchers.IO) {
        _albumState.value = _albumState.value.copy(isRefreshing = true)
        navigateToFolder(_albumState.value.currentFolderId)
        _albumState.value = _albumState.value.copy(isRefreshing = false)
    }

    // ── Image Selection (Single / Multi) ──

    fun selectElement(elementId: Long, multiSelect: Boolean = false) {
        val state = _albumState.value
        val newSelected = if (multiSelect) {
            if (elementId in state.selectedIds) {
                state.selectedIds - elementId
            } else {
                state.selectedIds + elementId
            }
        } else {
            if (state.selectedIds.size == 1 && elementId in state.selectedIds) {
                emptySet()
            } else {
                setOf(elementId)
            }
        }

        val updatedElements = state.elements.map { element ->
            element.copy(isSelected = element.elementId in newSelected)
        }

        _albumState.value = state.copy(
            selectedIds = newSelected,
            elements = updatedElements
        )
    }

    fun selectAll() {
        val state = _albumState.value
        val allIds = state.elements.filter { it.elementType == ElementType.FILE }
            .map { it.elementId }.toSet()
        val updatedElements = state.elements.map { it.copy(isSelected = true) }
        _albumState.value = state.copy(selectedIds = allIds, elements = updatedElements)
    }

    fun deselectAll() {
        val state = _albumState.value
        val updatedElements = state.elements.map { it.copy(isSelected = false) }
        _albumState.value = state.copy(selectedIds = emptySet(), elements = updatedElements)
    }

    fun getSelectedElements(): List<AlbumElement> {
        return _albumState.value.elements.filter { it.isSelected }
    }

    fun getSelectedImageIds(): List<Long> {
        return getSelectedElements().mapNotNull { it.elementId }
    }

    fun getSelectionCount(): Int = _albumState.value.selectedIds.size

    // ── Drag-and-Drop Reordering ──

    suspend fun moveElement(elementId: Long, fromIndex: Int, toIndex: Int) = withContext(Dispatchers.IO) {
        val state = _albumState.value
        val mutableElements = state.elements.toMutableList()
        if (fromIndex in mutableElements.indices && toIndex in mutableElements.indices) {
            val element = mutableElements.removeAt(fromIndex)
            mutableElements.add(toIndex, element)
            _albumState.value = state.copy(elements = mutableElements)
        }
    }

    suspend fun moveElementToFolder(elementId: Long, targetFolderId: Long) = withContext(Dispatchers.IO) {
        val element = sleeveRepository.getElement(elementId) ?: return@withContext
        sleeveRepository.removeElement(elementId)
        sleeveRepository.addElement(element, targetFolderId)
        refreshCurrentFolder()
    }

    fun reorderElements(newOrder: List<Long>) {
        val state = _albumState.value
        val elementMap = state.elements.associateBy { it.elementId }
        val reordered = newOrder.mapNotNull { elementMap[it] }
        _albumState.value = state.copy(elements = reordered)
    }

    // ── Sort Options ──

    fun setSorting(field: SortField, order: SortOrder = SortOrder.ASCENDING) {
        _albumState.value = _albumState.value.copy(sortField = field, sortOrder = order)
        scope.launch {
            val state = _albumState.value
            val sorted = state.elements.sortedWith(getSortComparator())
            _albumState.value = state.copy(elements = sorted)
        }
    }

    fun toggleSortOrder() {
        val state = _albumState.value
        val newOrder = if (state.sortOrder == SortOrder.ASCENDING) SortOrder.DESCENDING
        else SortOrder.ASCENDING
        setSorting(state.sortField, newOrder)
    }

    private fun getSortComparator(): Comparator<AlbumElement> {
        val field = _albumState.value.sortField
        val order = _albumState.value.sortOrder

        val comparator: Comparator<AlbumElement> = when (field) {
            SortField.NAME -> compareBy { it.elementName.lowercase() }
            SortField.DATE -> compareBy { it.addedTime }
            SortField.RATING -> compareBy { it.rating }
            SortField.TYPE -> compareBy { it.imageType?.ordinal ?: 0 }
            SortField.SIZE -> compareBy { it.fileSize }
        }

        return if (order == SortOrder.DESCENDING) comparator.reversed() else comparator
    }

    // ── View Mode ──

    fun setViewMode(mode: ViewMode) {
        _albumState.value = _albumState.value.copy(viewMode = mode)
    }

    fun toggleViewMode() {
        val current = _albumState.value.viewMode
        setViewMode(if (current == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID)
    }

    fun setGridColumns(columns: Int) {
        _albumState.value = _albumState.value.copy(
            thumbnailGridColumns = columns.coerceIn(2, 6)
        )
    }

    // ── Quick Actions ──

    suspend fun performQuickAction(elementId: Long, action: QuickAction): Boolean = withContext(Dispatchers.IO) {
        val element = _albumState.value.elements.find { it.elementId == elementId } ?: return@withContext false

        when (action) {
            QuickAction.DELETE -> {
                val imageId = element.imageId
                if (imageId != null) {
                    imageRepository.deleteImage(imageId)
                }
                sleeveRepository.removeElement(elementId)
                refreshCurrentFolder()
                true
            }
            QuickAction.EXPORT -> {
                // Handled by ExportService
                false
            }
            QuickAction.COPY -> {
                // Mark for copy action
                true
            }
            QuickAction.MOVE -> {
                // Mark for move action
                true
            }
            else -> {
                // Rating actions handled by rateImage
                rateImage(elementId, actionToRating(action))
            }
        }
    }

    suspend fun performBatchAction(elementIds: Set<Long>, action: QuickAction): Boolean = withContext(Dispatchers.IO) {
        var success = true
        for (id in elementIds) {
            if (!performQuickAction(id, action)) {
                success = false
            }
        }
        success
    }

    suspend fun rateImage(elementId: Long, rating: Int): Boolean = withContext(Dispatchers.IO) {
        if (rating !in 1..5) return@withContext false

        val state = _albumState.value
        val updatedElements = state.elements.map { element ->
            if (element.elementId == elementId) element.copy(rating = rating) else element
        }

        _albumState.value = state.copy(elements = updatedElements)
        true
    }

    private fun actionToRating(action: QuickAction): Int = when (action) {
        QuickAction.RATE_1 -> 1
        QuickAction.RATE_2 -> 2
        QuickAction.RATE_3 -> 3
        QuickAction.RATE_4 -> 4
        QuickAction.RATE_5 -> 5
        else -> 0
    }

    // ── Page Size Management ──

    fun setPageSize(size: Int) {
        _albumState.value = _albumState.value.copy(pageSize = size.coerceIn(10, 200))
    }

    fun getPageSize(): Int = _albumState.value.pageSize

    // ── Private Helpers ──

    private suspend fun buildFolderPath(folderId: Long): List<SleeveFolder> = withContext(Dispatchers.IO) {
        val path = mutableListOf<SleeveFolder>()
        var currentId = folderId
        val visited = mutableSetOf<Long>()

        while (currentId != 0L && !visited.contains(currentId)) {
            visited.add(currentId)
            val element = sleeveRepository.getElement(currentId)
            if (element is SleeveFolder) {
                path.add(0, element)
                // Navigate up via parent lookup
                break
            } else {
                break
            }
        }
        path
    }

    private suspend fun mapToAlbumElement(element: SleeveElement): AlbumElement = withContext(Dispatchers.IO) {
        when (element) {
            is SleeveFile -> {
                val image = element.image ?: element.imageId?.let { imageRepository.getImage(it.toUInt()) }
                AlbumElement(
                    elementId = element.elementId,
                    elementName = element.elementName,
                    elementType = ElementType.FILE,
                    imageId = element.imageId?.toUInt(),
                    imageType = image?.imageType,
                    thumbnail = element.imageId?.toUInt()?.let { thumbnailService.loadThumbnail(it) },
                    exifDisplay = image?.exifDisplay,
                    addedTime = element.addedTime,
                    fileSize = image?.imagePath?.let { java.io.File(it).length() } ?: 0L
                )
            }
            is SleeveFolder -> {
                AlbumElement(
                    elementId = element.elementId,
                    elementName = element.elementName,
                    elementType = ElementType.FOLDER,
                    addedTime = element.addedTime
                )
            }
            else -> AlbumElement(
                elementId = element.elementId,
                elementName = element.elementName,
                elementType = ElementType.FILE
            )
        }
    }

    // ── Cleanup ──

    fun shutdown() {
        scope.cancel()
        cachedElements.clear()
    }
}