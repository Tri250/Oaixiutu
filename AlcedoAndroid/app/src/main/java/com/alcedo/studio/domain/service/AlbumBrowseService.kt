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

// ================================================================
// Filter models for album browsing
// ================================================================

enum class FileCategory {
    RAW, JPEG, PNG, TIFF, WEBP, BMP, HEIC, GIF, OTHER
}

data class FileTypeFilter(
    val categories: Set<FileCategory> = emptySet()
) {
    fun matches(imageType: ImageType?): Boolean {
        if (categories.isEmpty()) return true
        if (imageType == null) return false
        val category = imageTypeToFileCategory(imageType)
        return category in categories
    }

    companion object {
        fun imageTypeToFileCategory(type: ImageType): FileCategory = when (type) {
            ImageType.ARW, ImageType.CR2, ImageType.CR3, ImageType.NEF, ImageType.DNG -> FileCategory.RAW
            ImageType.JPEG -> FileCategory.JPEG
            ImageType.PNG -> FileCategory.PNG
            ImageType.TIFF -> FileCategory.TIFF
            ImageType.WEBP -> FileCategory.WEBP
            ImageType.BMP -> FileCategory.BMP
            ImageType.HEIC, ImageType.HEIF -> FileCategory.HEIC
            ImageType.GIF -> FileCategory.GIF
            else -> FileCategory.OTHER
        }
    }
}

data class RatingFilter(
    val minRating: Int = 0,
    val maxRating: Int = 5
) {
    fun matches(rating: Int): Boolean {
        return rating in minRating..maxRating
    }
}

data class DateRangeFilter(
    val startDate: Long = 0L,
    val endDate: Long = Long.MAX_VALUE
) {
    fun matches(timestamp: Long): Boolean {
        return timestamp in startDate..endDate
    }
}

data class AlbumBrowseFilter(
    val fileTypeFilter: FileTypeFilter = FileTypeFilter(),
    val ratingFilter: RatingFilter = RatingFilter(),
    val dateRangeFilter: DateRangeFilter = DateRangeFilter(),
    val collectionId: Long? = null
)

data class PaginatedResult<T>(
    val items: List<T>,
    val totalCount: Int,
    val offset: Int,
    val limit: Int,
    val hasMore: Boolean
)

data class AlbumState(
    val currentFolderId: Long = 0L,
    val folderPath: List<SleeveFolder> = emptyList(),
    val elements: List<AlbumElement> = emptyList(),
    val filteredElements: List<AlbumElement> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val viewMode: ViewMode = ViewMode.GRID,
    val sortField: SortField = SortField.DATE,
    val sortOrder: SortOrder = SortOrder.DESCENDING,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val thumbnailGridColumns: Int = 3,
    val currentPage: Int = 0,
    val totalPages: Int = 1,
    val pageSize: Int = 50,
    val activeFilter: AlbumBrowseFilter = AlbumBrowseFilter(),
    val collectionId: Long? = null
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
    val fileSize: Long = 0L,
    val captureDate: Long = 0L,
    val filePath: String = ""
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

    // ================================================================
    // Folder Tree Navigation
    // ================================================================

    suspend fun navigateToFolder(folderId: Long) = withContext(Dispatchers.IO) {
        _albumState.value = _albumState.value.copy(isLoading = true)

        try {
            val folder = sleeveRepository.getElement(folderId)
            val folderPath = buildFolderPath(folderId)
            val children = sleeveRepository.getChildren(folderId)
            val elements = children.map { element ->
                mapToAlbumElement(element)
            }.sortedWith(getSortComparator())

            val filtered = applyFilters(elements)

            _albumState.value = _albumState.value.copy(
                currentFolderId = folderId,
                folderPath = folderPath,
                elements = elements,
                filteredElements = filtered,
                isLoading = false,
                currentPage = 0,
                totalPages = maxOf(1, (filtered.size + _albumState.value.pageSize - 1) / _albumState.value.pageSize)
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

    // ================================================================
    // Collection-based browsing
    // ================================================================

    /**
     * Browse images in a specific collection.
     */
    suspend fun browseCollection(collectionId: Long) = withContext(Dispatchers.IO) {
        _albumState.value = _albumState.value.copy(isLoading = true, collectionId = collectionId)

        try {
            val result = sleeveRepository.getImagesInCollection(collectionId, 0, Int.MAX_VALUE)
            val elements = mutableListOf<AlbumElement>()

            for (imageId in result.items) {
                val metadata = imageRepository.getImageMetadata(imageId)
                if (metadata != null) {
                    elements.add(metadataToAlbumElement(metadata))
                }
            }

            val filtered = applyFilters(elements)
            _albumState.value = _albumState.value.copy(
                elements = elements,
                filteredElements = filtered,
                isLoading = false,
                currentPage = 0,
                totalPages = maxOf(1, (filtered.size + _albumState.value.pageSize - 1) / _albumState.value.pageSize)
            )
        } catch (e: Exception) {
            _albumState.value = _albumState.value.copy(isLoading = false)
        }
    }

    /**
     * Exit collection browsing and return to folder view.
     */
    suspend fun exitCollectionView() = withContext(Dispatchers.IO) {
        _albumState.value = _albumState.value.copy(collectionId = null)
        navigateToFolder(_albumState.value.currentFolderId)
    }

    // ================================================================
    // Pagination with offset + limit
    // ================================================================

    /**
     * Get a paginated result for the current filtered elements.
     */
    suspend fun getPaginatedElements(
        offset: Int = 0,
        limit: Int = _albumState.value.pageSize
    ): PaginatedResult<AlbumElement> = withContext(Dispatchers.IO) {
        val filtered = _albumState.value.filteredElements
        val totalCount = filtered.size
        val end = minOf(offset + limit, totalCount)
        val items = if (offset < totalCount) filtered.subList(offset, end) else emptyList()

        // Load thumbnails for the page
        for (element in items) {
            if (element.imageId != null && element.thumbnail == null) {
                val imageIdLong = element.imageId.toLong()
                val thumbnailResult = thumbnailService.loadThumbnail(imageIdLong)
                val bitmap = when (thumbnailResult) {
                    is ThumbnailService.ThumbnailResult.Success -> thumbnailResult.bitmap
                    is ThumbnailService.ThumbnailResult.Placeholder -> thumbnailResult.bitmap
                    is ThumbnailService.ThumbnailResult.Error -> null
                }
                if (bitmap != null) {
                    val updatedElements = _albumState.value.filteredElements.toMutableList()
                    val index = updatedElements.indexOfFirst { it.elementId == element.elementId }
                    if (index >= 0) {
                        updatedElements[index] = element.copy(thumbnail = bitmap)
                        _albumState.value = _albumState.value.copy(filteredElements = updatedElements)
                    }
                }
            }
        }

        PaginatedResult(
            items = items,
            totalCount = totalCount,
            offset = offset,
            limit = limit,
            hasMore = end < totalCount
        )
    }

    /**
     * Load a specific page.
     */
    suspend fun loadPage(page: Int) = withContext(Dispatchers.IO) {
        val state = _albumState.value
        val startIndex = page * state.pageSize
        val endIndex = minOf(startIndex + state.pageSize, state.filteredElements.size)
        val pageElements = state.filteredElements.subList(startIndex, endIndex)

        // Load thumbnails for this page
        for (element in pageElements) {
            if (element.imageId != null && element.thumbnail == null) {
                val thumbnailResult = thumbnailService.loadThumbnail(element.imageId.toLong())
                val bitmap = when (thumbnailResult) {
                    is ThumbnailService.ThumbnailResult.Success -> thumbnailResult.bitmap
                    is ThumbnailService.ThumbnailResult.Placeholder -> thumbnailResult.bitmap
                    is ThumbnailService.ThumbnailResult.Error -> null
                }
                if (bitmap != null) {
                    val updatedElements = state.filteredElements.toMutableList()
                    val index = updatedElements.indexOfFirst { it.elementId == element.elementId }
                    if (index >= 0) {
                        updatedElements[index] = element.copy(thumbnail = bitmap)
                        _albumState.value = state.copy(filteredElements = updatedElements)
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
        val collectionId = _albumState.value.collectionId
        if (collectionId != null) {
            browseCollection(collectionId)
        } else {
            navigateToFolder(_albumState.value.currentFolderId)
        }
        _albumState.value = _albumState.value.copy(isRefreshing = false)
    }

    // ================================================================
    // Filtering
    // ================================================================

    /**
     * Apply a filter to the current album view.
     */
    suspend fun applyFilter(filter: AlbumBrowseFilter) = withContext(Dispatchers.IO) {
        _albumState.value = _albumState.value.copy(activeFilter = filter)
        reapplyFilters()
    }

    /**
     * Filter by file type.
     */
    suspend fun filterByFileType(categories: Set<FileCategory>) = withContext(Dispatchers.IO) {
        val currentFilter = _albumState.value.activeFilter
        val newFilter = currentFilter.copy(fileTypeFilter = FileTypeFilter(categories))
        applyFilter(newFilter)
    }

    /**
     * Filter by rating range.
     */
    suspend fun filterByRating(minRating: Int, maxRating: Int = 5) = withContext(Dispatchers.IO) {
        val currentFilter = _albumState.value.activeFilter
        val newFilter = currentFilter.copy(ratingFilter = RatingFilter(minRating, maxRating))
        applyFilter(newFilter)
    }

    /**
     * Filter by date range.
     */
    suspend fun filterByDateRange(startDate: Long, endDate: Long) = withContext(Dispatchers.IO) {
        val currentFilter = _albumState.value.activeFilter
        val newFilter = currentFilter.copy(dateRangeFilter = DateRangeFilter(startDate, endDate))
        applyFilter(newFilter)
    }

    /**
     * Clear all filters.
     */
    suspend fun clearFilters() = withContext(Dispatchers.IO) {
        applyFilter(AlbumBrowseFilter())
    }

    private fun applyFilters(elements: List<AlbumElement>): List<AlbumElement> {
        val filter = _albumState.value.activeFilter
        return elements.filter { element ->
            // File type filter
            if (!filter.fileTypeFilter.matches(element.imageType)) return@filter false
            // Rating filter
            if (!filter.ratingFilter.matches(element.rating)) return@filter false
            // Date range filter
            val timestamp = if (element.captureDate > 0) element.captureDate
                else element.addedTime.toEpochMilli()
            if (!filter.dateRangeFilter.matches(timestamp)) return@filter false
            true
        }
    }

    private suspend fun reapplyFilters() = withContext(Dispatchers.IO) {
        val elements = _albumState.value.elements
        val filtered = applyFilters(elements).sortedWith(getSortComparator())
        _albumState.value = _albumState.value.copy(
            filteredElements = filtered,
            currentPage = 0,
            totalPages = maxOf(1, (filtered.size + _albumState.value.pageSize - 1) / _albumState.value.pageSize)
        )
    }

    // ================================================================
    // Image Selection (Single / Multi)
    // ================================================================

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

        val updatedElements = state.filteredElements.map { element ->
            element.copy(isSelected = element.elementId in newSelected)
        }

        _albumState.value = state.copy(
            selectedIds = newSelected,
            filteredElements = updatedElements
        )
    }

    fun selectAll() {
        val state = _albumState.value
        val allIds = state.filteredElements.filter { it.elementType == ElementType.FILE }
            .map { it.elementId }.toSet()
        val updatedElements = state.filteredElements.map { it.copy(isSelected = true) }
        _albumState.value = state.copy(selectedIds = allIds, filteredElements = updatedElements)
    }

    fun deselectAll() {
        val state = _albumState.value
        val updatedElements = state.filteredElements.map { it.copy(isSelected = false) }
        _albumState.value = state.copy(selectedIds = emptySet(), filteredElements = updatedElements)
    }

    fun getSelectedElements(): List<AlbumElement> {
        return _albumState.value.filteredElements.filter { it.isSelected }
    }

    fun getSelectedImageIds(): List<Long> {
        return getSelectedElements().mapNotNull { it.elementId }
    }

    fun getSelectionCount(): Int = _albumState.value.selectedIds.size

    // ================================================================
    // Drag-and-Drop Reordering
    // ================================================================

    suspend fun moveElement(elementId: Long, fromIndex: Int, toIndex: Int) = withContext(Dispatchers.IO) {
        val state = _albumState.value
        val mutableElements = state.filteredElements.toMutableList()
        if (fromIndex in mutableElements.indices && toIndex in mutableElements.indices) {
            val element = mutableElements.removeAt(fromIndex)
            mutableElements.add(toIndex, element)
            _albumState.value = state.copy(filteredElements = mutableElements)
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
        val elementMap = state.filteredElements.associateBy { it.elementId }
        val reordered = newOrder.mapNotNull { elementMap[it] }
        _albumState.value = state.copy(filteredElements = reordered)
    }

    // ================================================================
    // Sort Options
    // ================================================================

    fun setSorting(field: SortField, order: SortOrder = SortOrder.ASCENDING) {
        _albumState.value = _albumState.value.copy(sortField = field, sortOrder = order)
        scope.launch {
            reapplyFilters()
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

    // ================================================================
    // View Mode
    // ================================================================

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

    // ================================================================
    // Quick Actions
    // ================================================================

    suspend fun performQuickAction(elementId: Long, action: QuickAction): Boolean = withContext(Dispatchers.IO) {
        val element = _albumState.value.filteredElements.find { it.elementId == elementId } ?: return@withContext false

        when (action) {
            QuickAction.DELETE -> {
                val imageId = element.imageId
                if (imageId != null) {
                    imageRepository.deleteImage(imageId.toLong())
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
        val updatedElements = state.filteredElements.map { element ->
            if (element.elementId == elementId) element.copy(rating = rating) else element
        }

        _albumState.value = state.copy(filteredElements = updatedElements)
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

    // ================================================================
    // Page Size Management
    // ================================================================

    fun setPageSize(size: Int) {
        _albumState.value = _albumState.value.copy(pageSize = size.coerceIn(10, 200))
    }

    fun getPageSize(): Int = _albumState.value.pageSize

    // ================================================================
    // Private Helpers
    // ================================================================

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
                val image = element.image ?: element.imageId?.let { imageRepository.getImage(it.toLong()) }
                AlbumElement(
                    elementId = element.elementId,
                    elementName = element.elementName,
                    elementType = ElementType.FILE,
                    imageId = element.imageId?.toUInt(),
                    imageType = image?.imageType,
                    thumbnail = element.imageId?.let { id ->
                        val result = thumbnailService.loadThumbnail(id)
                        when (result) {
                            is ThumbnailService.ThumbnailResult.Success -> result.bitmap
                            is ThumbnailService.ThumbnailResult.Placeholder -> result.bitmap
                            is ThumbnailService.ThumbnailResult.Error -> null
                        }
                    },
                    exifDisplay = image?.exifDisplay,
                    addedTime = element.addedTime,
                    fileSize = image?.imagePath?.let { java.io.File(it).length() } ?: 0L,
                    captureDate = image?.exifData?.let {
                        try {
                            it.dateTime?.let { dateStr ->
                                val sdf = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US)
                                sdf.parse(dateStr.toString())?.time ?: 0L
                            }
                        } catch (_: Exception) { 0L }
                    } ?: 0L,
                    filePath = element.filePath
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

    private fun metadataToAlbumElement(metadata: ImageMetadataEntity): AlbumElement {
        return AlbumElement(
            elementId = metadata.imageId,
            elementName = metadata.imageName,
            elementType = ElementType.FILE,
            imageId = metadata.imageId.toUInt(),
            imageType = ImageType.entries.getOrElse(metadata.imageType) { ImageType.DEFAULT },
            rating = metadata.rating,
            addedTime = Instant.ofEpochMilli(metadata.importedAt),
            fileSize = metadata.fileSize,
            captureDate = metadata.captureDate,
            filePath = metadata.imagePath
        )
    }

    // ================================================================
    // Cleanup
    // ================================================================

    fun shutdown() {
        scope.cancel()
        cachedElements.clear()
    }
}
