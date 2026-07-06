package com.alcedo.studio.viewmodel

import android.app.Application
import android.content.ContentUris
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alcedo.studio.data.model.*
import com.alcedo.studio.di.AppModule
import com.alcedo.studio.domain.service.*
import com.alcedo.studio.ndk.AiNdkBridge
import com.alcedo.studio.permission.PermissionHelper
import com.alcedo.studio.storage.MediaStoreHelper
import com.alcedo.studio.storage.PhotoPickerHelper
import com.alcedo.studio.storage.SafHelper
import com.alcedo.studio.ui.album.FilterState
import com.alcedo.studio.ui.album.SortMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

class AlbumViewModel : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 50
        private const val THUMBNAIL_CACHE_SIZE = 50
    }

    private val sleeveRepository by lazy { AppModule.sleeveRepository }
    private val imageRepository by lazy { AppModule.imageRepository }
    private val importService by lazy { AppModule.importService }
    private val thumbnailService by lazy { AppModule.thumbnailService }
    private val searchService by lazy { AppModule.searchService }
    private val aiService by lazy { AppModule.aiService }
    private val exportService by lazy { AppModule.exportService }

    // ── Image list state ──

    private val _images = MutableStateFlow<List<ImageModel>>(emptyList())
    val images: StateFlow<List<ImageModel>> = _images

    private val _filteredImages = MutableStateFlow<List<ImageModel>>(emptyList())
    val filteredImages: StateFlow<List<ImageModel>> = _filteredImages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    // ── Pagination ──

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _hasMorePages = MutableStateFlow(true)
    val hasMorePages: StateFlow<Boolean> = _hasMorePages.asStateFlow()

    // ── Selection ──

    private val _selectedImages = MutableStateFlow<Set<Long>>(emptySet())
    val selectedImages: StateFlow<Set<Long>> = _selectedImages.asStateFlow()

    // ── Search ──

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _showSearch = MutableStateFlow(false)
    val showSearch: StateFlow<Boolean> = _showSearch.asStateFlow()

    private val _semanticSearchEnabled = MutableStateFlow(false)
    val semanticSearchEnabled: StateFlow<Boolean> = _semanticSearchEnabled.asStateFlow()

    private val _searchResults = MutableStateFlow<List<RankedSearchResult>>(emptyList())
    val searchResults: StateFlow<List<RankedSearchResult>> = _searchResults

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    // ── Permission error ──

    private val _permissionError = MutableStateFlow<String?>(null)
    val permissionError: StateFlow<String?> = _permissionError.asStateFlow()

    fun setPermissionError(message: String?) {
        _permissionError.value = message
    }

    fun clearPermissionError() {
        _permissionError.value = null
    }

    // ── Sort & Filter ──

    private val _sortMode = MutableStateFlow(SortMode.DATE)
    val sortMode: StateFlow<SortMode> = _sortMode

    private val _currentFilter = MutableStateFlow<FilterState?>(null)
    val currentFilter: StateFlow<FilterState?> = _currentFilter.asStateFlow()

    // ── Folder navigation ──

    private val _folders = MutableStateFlow<List<SleeveFolder>>(emptyList())
    val folders: StateFlow<List<SleeveFolder>> = _folders

    private val _currentFolderId = MutableStateFlow<Long?>(null)
    val currentFolderId: StateFlow<Long?> = _currentFolderId

    private val _currentFolderPath = MutableStateFlow("/")
    val currentFolderPath: StateFlow<String> = _currentFolderPath

    private val _folderBreadcrumbs = MutableStateFlow<List<FolderBreadcrumb>>(emptyList())
    val folderBreadcrumbs: StateFlow<List<FolderBreadcrumb>> = _folderBreadcrumbs

    // ── Thumbnail loading ──

    // LruCache backs the in-memory thumbnail store; a versioned StateFlow lets
    // Compose recompose whenever the cache mutates without exposing the
    // bitmaps themselves through the flow.
    private val thumbnailLruCache = LruCache<Long, Bitmap>(THUMBNAIL_CACHE_SIZE)
    private val _thumbnailCacheVersion = MutableStateFlow(0)
    val thumbnailCacheVersion: StateFlow<Int> = _thumbnailCacheVersion.asStateFlow()

    /** Returns the cached thumbnail for [imageId], or null if not yet loaded. */
    fun getThumbnail(imageId: Long): Bitmap? = thumbnailLruCache.get(imageId)

    private val _thumbnailsLoading = mutableStateListOf<Long>()
    val thumbnailsLoading: List<Long> get() = _thumbnailsLoading

    // ── Collections ──

    private val _collections = MutableStateFlow<List<CollectionEntity>>(emptyList())
    val collections: StateFlow<List<CollectionEntity>> = _collections

    // ── Statistics ──

    private val _imageCount = MutableStateFlow(0)
    val imageCount: StateFlow<Int> = _imageCount

    private val _totalSize = MutableStateFlow(0L)
    val totalSize: StateFlow<Long> = _totalSize

    // ── Permission state ──

    private val _hasMediaPermission = MutableStateFlow(false)
    val hasMediaPermission: StateFlow<Boolean> = _hasMediaPermission

    private val _hasWritePermission = MutableStateFlow(false)
    val hasWritePermission: StateFlow<Boolean> = _hasWritePermission

    private val _permissionRationale = MutableStateFlow<String?>(null)
    val permissionRationale: StateFlow<String?> = _permissionRationale

    init {
        try {
            checkPermissions()
        } catch (e: Throwable) {
            // Permission check should never crash the app.
        }
        // Defer database access off the main thread so a database
        // initialization failure cannot crash Composable composition.
        viewModelScope.launch {
            try {
                loadImages()
                loadFolders()
                loadCollections()
            } catch (_: Throwable) {
                // Swallow startup failures; UI will simply show empty state.
            }
        }
    }

    // ================================================================
    // Permission handling
    // ================================================================

    fun checkPermissions() {
        val appContext = AppModule.context
        _hasMediaPermission.value = PermissionHelper.hasReadMediaAccess(appContext)
        _hasWritePermission.value = PermissionHelper.hasWriteAccess(appContext)
    }

    fun onPermissionResult(results: Map<String, Boolean>) {
        checkPermissions()
        val allGranted = results.values.all { it }
        if (allGranted) {
            _permissionRationale.value = null
            loadImages()
        } else {
            val denied = results.filterValues { !it }.keys
            _permissionRationale.value = "权限被拒绝：${denied.joinToString()}"
        }
    }

    // ================================================================
    // MediaStore-based image loading (Scoped Storage)
    // ================================================================

    fun loadImagesFromMediaStore() {
        viewModelScope.launch {
            if (!_hasMediaPermission.value) return@launch
            _isLoading.value = true
            try {
                val mediaImages = MediaStoreHelper.queryImages(
                    context = AppModule.context,
                    limit = 500
                )
                // Convert MediaStore results to ImageModel via import service
                for (media in mediaImages) {
                    importService.importImage(media.contentUri)
                }
                loadImages()
            } catch (_: Exception) {
                // Fallback to existing repository-based loading
                loadImages()
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ================================================================
    // SAF directory import (Android 10+)
    // ================================================================

    fun importFromSafDirectory(treeUri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val files = SafHelper.listDirectory(AppModule.context, treeUri)
                val imageFiles = files.filter { !it.isDirectory }
                for (file in imageFiles) {
                    importService.importImage(file.uri)
                }
                loadImages()
                loadFolders()
            } catch (_: Exception) {
                // SAF import failure
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ================================================================
    // Photo Picker import
    // ================================================================

    fun importFromPhotoPicker(uris: List<Uri>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Take persistable read permission so thumbnail generation
                // and later access don't throw SecurityException
                val resolver = AppModule.context.contentResolver
                for (uri in uris) {
                    try {
                        resolver.takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: SecurityException) {
                        // Photo Picker URIs may not support persistable permission;
                        // that's OK — we still have read access for this process.
                    }
                }

                // Use two-phase import for better UX (fast scan + background metadata)
                importService.importTwoPhase(uris)
                loadImages()
                loadFolders()
            } catch (e: Throwable) {
                android.util.Log.e("AlbumVM", "importFromPhotoPicker failed", e)
                _permissionRationale.value = "导入失败: ${e.message ?: "未知错误"}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ================================================================
    // Scoped Storage-aware directory import
    // ================================================================

    fun importDirectoryScoped(path: String? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // On Android 10+, traverse MediaStore via content URIs (Scoped Storage)
            viewModelScope.launch {
                _isLoading.value = true
                try {
                    val context = AppModule.context
                    val resolver = context.contentResolver

                    // Optionally filter by relative path when a path is supplied
                    val (selection, selectionArgs) = if (!path.isNullOrBlank()) {
                        val normalized = path.trimEnd('/').removePrefix("/")
                        Pair(
                            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
                            arrayOf("%$normalized%")
                        )
                    } else {
                        Pair<String?, Array<String>?>(null, null)
                    }

                    val projection = arrayOf(MediaStore.Images.Media._ID)
                    val contentUris = mutableListOf<Uri>()

                    resolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        "${MediaStore.Images.Media.DATE_ADDED} DESC"
                    )?.use { cursor ->
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idColumn)
                            contentUris.add(
                                ContentUris.withAppendedId(
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                                )
                            )
                        }
                    }

                    if (contentUris.isEmpty()) {
                        _permissionRationale.value = "请使用系统选择器选择目录"
                        return@launch
                    }

                    // Take persistable read permission for each URI when possible
                    for (uri in contentUris) {
                        try {
                            resolver.takePersistableUriPermission(
                                uri,
                                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        } catch (_: SecurityException) {
                            // MediaStore URIs do not always grant persistable permission; ignore.
                        }
                        try {
                            importService.importImage(uri)
                        } catch (e: Exception) {
                            Log.e("AlbumVM", "Failed to import $uri", e)
                        }
                    }
                    loadImages()
                    loadFolders()
                } catch (e: Throwable) {
                    Log.e("AlbumVM", "importDirectoryScoped failed", e)
                    _permissionRationale.value = "请使用系统选择器选择目录"
                } finally {
                    _isLoading.value = false
                }
            }
        } else {
            // Legacy: direct file path access
            if (path != null) {
                importDirectory(path)
            }
        }
    }

    // ================================================================
    // Image loading (paginated)
    // ================================================================

    fun loadImages() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 重置分页状态：每次 loadImages 都从第一页开始
                _currentPage.value = 0
                val allImages = imageRepository.getAllImages()
                _imageCount.value = allImages.size
                _totalSize.value = allImages.sumOf { it.fileSize }
                val firstPage = allImages.take(PAGE_SIZE)
                _images.value = firstPage
                _hasMorePages.value = allImages.size > PAGE_SIZE
                applyCurrentSortAndFilter(firstPage)
            } catch (e: Throwable) {
                android.util.Log.e("AlbumVM", "loadImages failed", e)
                _images.value = emptyList()
                _filteredImages.value = emptyList()
                _hasMorePages.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMoreImages() {
        if (!_hasMorePages.value) return
        if (_isLoading.value || _isRefreshing.value) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val nextPageIndex = _currentPage.value + 1
                // ImageRepository 没有 paged query，因此取全量后 drop/take 切片
                val allImages = imageRepository.getAllImages()
                val nextPage = allImages.drop(nextPageIndex * PAGE_SIZE).take(PAGE_SIZE)
                if (nextPage.isEmpty()) {
                    _hasMorePages.value = false
                } else {
                    _currentPage.value = nextPageIndex
                    val combined = _images.value + nextPage
                    _images.value = combined
                    _imageCount.value = allImages.size
                    _totalSize.value = allImages.sumOf { it.fileSize }
                    _hasMorePages.value = allImages.size > combined.size
                    applyCurrentSortAndFilter(combined)
                }
            } catch (e: Throwable) {
                android.util.Log.e("AlbumVM", "loadMoreImages failed", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                // 重置分页：refresh 等价于重新加载第一页
                _currentPage.value = 0
                val allImages = imageRepository.getAllImages()
                _imageCount.value = allImages.size
                _totalSize.value = allImages.sumOf { it.fileSize }
                val firstPage = allImages.take(PAGE_SIZE)
                _images.value = firstPage
                _hasMorePages.value = allImages.size > PAGE_SIZE
                applyCurrentSortAndFilter(firstPage)
            } catch (e: Throwable) {
                android.util.Log.e("AlbumVM", "refresh failed", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    // ================================================================
    // Folder navigation
    // ================================================================

    fun loadFolders() {
        viewModelScope.launch {
            try {
                _folders.value = sleeveRepository.getRootElements()
                    .filterIsInstance<SleeveFolder>()
            } catch (e: Throwable) {
                android.util.Log.e("AlbumVM", "Coroutine failed", e)
            }
        }
    }

    fun navigateToFolder(folderId: Long?) {
        _currentFolderId.value = folderId
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (folderId == null) {
                    _currentFolderPath.value = "/"
                    _folderBreadcrumbs.value = emptyList()
                    loadImages()
                } else {
                    val path = sleeveRepository.getFolderPath(folderId)
                    _currentFolderPath.value = path
                    val children = sleeveRepository.getChildren(folderId)
                    val fileChildren = children.filterIsInstance<SleeveFile>()
                    val imagesInFolder = fileChildren.mapNotNull { file ->
                        imageRepository.getImage(file.imageId)
                    }
                    _images.value = imagesInFolder
                    // 文件夹视图不分页，关闭"加载更多"以避免无限滚动把全部图库
                    // 错误地追加到当前文件夹列表里
                    _hasMorePages.value = false
                    applyCurrentSortAndFilter(imagesInFolder)

                    // Update breadcrumbs
                    val folder = sleeveRepository.getElement(folderId) as? SleeveFolder
                    val currentBreadcrumbs = _folderBreadcrumbs.value.toMutableList()
                    if (currentBreadcrumbs.none { it.folderId == folderId }) {
                        currentBreadcrumbs.add(FolderBreadcrumb(folderId, folder?.elementName ?: "Folder"))
                        _folderBreadcrumbs.value = currentBreadcrumbs
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.e("AlbumVM", "navigateToFolder failed", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun navigateToBreadcrumb(index: Int) {
        val breadcrumbs = _folderBreadcrumbs.value
        if (index < 0) {
            navigateToFolder(null)
        } else if (index < breadcrumbs.size) {
            _folderBreadcrumbs.value = breadcrumbs.subList(0, index + 1)
            navigateToFolder(breadcrumbs[index].folderId)
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            try {
                val parentId = _currentFolderId.value
                sleeveRepository.createFolder(name, parentId)
                loadFolders()
                if (parentId != null) {
                    navigateToFolder(parentId)
                }
            } catch (e: Throwable) {
                android.util.Log.e("AlbumVM", "Coroutine failed", e)
            }
        }
    }

    // ================================================================
    // Import
    // ================================================================

    fun importFromGallery(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                importService.importImage(uri)
                loadImages()
                loadFolders()
            } catch (e: Throwable) {
                android.util.Log.e("AlbumVM", "importFromGallery failed", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun importFromStorage(uris: List<Uri>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                for (uri in uris) {
                    importService.importImage(uri)
                }
                loadImages()
                loadFolders()
            } catch (e: Throwable) {
                android.util.Log.e("AlbumVM", "importFromStorage failed", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun importDirectory(path: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                importService.importDirectory(Uri.fromFile(java.io.File(path)))
                loadImages()
                loadFolders()
            } catch (e: Throwable) {
                android.util.Log.e("AlbumVM", "importDirectory failed", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ================================================================
    // Search (text-based + AI semantic search)
    // ================================================================

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            applyCurrentSortAndFilter(_images.value)
            return
        }
        performSearch(query)
    }

    private fun performSearch(query: String) {
        viewModelScope.launch {
            _isSearching.value = true
            try {
                val results = searchService.combinedSearch(
                    textQuery = query,
                    enableSemantic = _semanticSearchEnabled.value,
                    maxResults = 100
                )
                _searchResults.value = results

                val resultImageIds = results.map { it.imageId }.toSet()
                val allImages = imageRepository.getAllImages()
                val matchedImages = allImages.filter { it.imageId in resultImageIds }
                _filteredImages.value = matchedImages
            } catch (_: Exception) {
                // Fallback to name-based search
                try {
                    val allImages = imageRepository.getAllImages()
                    _filteredImages.value = allImages.filter {
                        it.imageName.contains(query, ignoreCase = true) ||
                        it.imagePath.contains(query, ignoreCase = true)
                    }
                } catch (_: Exception) {
                    _filteredImages.value = emptyList()
                }
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun performSemanticSearch(query: String) {
        viewModelScope.launch {
            _isSearching.value = true
            try {
                val textEmbedding = aiService.generateTextEmbedding(query)
                val searchResults = aiService.searchByText(query, topK = 50)
                val imageIds = searchResults.map { it.imageId.toLong() }.toSet()
                val allImages = imageRepository.getAllImages()
                _filteredImages.value = allImages.filter { it.imageId in imageIds }
            } catch (_: Exception) {
                _filteredImages.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun toggleSemanticSearch() {
        _semanticSearchEnabled.value = !_semanticSearchEnabled.value
        val query = _searchQuery.value
        if (query.isNotBlank()) {
            performSearch(query)
        }
    }

    fun toggleSearch() {
        _showSearch.value = !_showSearch.value
        if (!_showSearch.value) {
            _searchQuery.value = ""
            _searchResults.value = emptyList()
            applyCurrentSortAndFilter(_images.value)
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        applyCurrentSortAndFilter(_images.value)
    }

    // ================================================================
    // Sort
    // ================================================================

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
        // Re-apply sort/filter on the original image list, not on the
        // (possibly empty due to filtering) filtered list. Using _images
        // avoids the bug where a legitimate empty filter result causes
        // setSortMode to fall back to showing all images.
        applyCurrentSortAndFilter(_images.value)
    }

    private fun applyCurrentSortAndFilter(images: List<ImageModel>) {
        val sorted = when (_sortMode.value) {
            SortMode.DATE -> images.sortedByDescending { it.imageId }
            SortMode.NAME -> images.sortedBy { it.imageName }
            SortMode.RATING -> images.sortedByDescending {
                it.exifDisplay.rating
            }
            SortMode.TYPE -> images.sortedBy { it.imageType.ordinal }
        }
        val filter = _currentFilter.value
        _filteredImages.value = if (filter != null) applyFilter(sorted, filter) else sorted
    }

    // ================================================================
    // Filter
    // ================================================================

    fun applyFilter(filter: FilterState) {
        _currentFilter.value = filter
        val source = _images.value
        val filtered = applyFilter(source, filter)
        _filteredImages.value = when (_sortMode.value) {
            SortMode.DATE -> filtered.sortedByDescending { it.imageId }
            SortMode.NAME -> filtered.sortedBy { it.imageName }
            SortMode.RATING -> filtered
            SortMode.TYPE -> filtered.sortedBy { it.imageType.ordinal }
        }
    }

    private fun applyFilter(images: List<ImageModel>, filter: FilterState): List<ImageModel> {
        var result = images

        if (filter.cameraMakes.isNotEmpty()) {
            result = result.filter { img ->
                filter.cameraMakes.any { make ->
                    img.exifDisplay.cameraMake.contains(make, ignoreCase = true)
                }
            }
        }

        if (filter.cameraModels.isNotEmpty()) {
            result = result.filter { img ->
                filter.cameraModels.any { model ->
                    img.exifDisplay.cameraModel.contains(model, ignoreCase = true)
                }
            }
        }

        if (filter.lensModel.isNotEmpty()) {
            result = result.filter { img ->
                img.exifDisplay.lensModel.contains(filter.lensModel, ignoreCase = true)
            }
        }

        if (filter.rating > 0) {
            result = result.filter {
                it.exifDisplay.rating >= filter.rating
            }
        }

        if (filter.fileTypes.isNotEmpty()) {
            result = result.filter { img ->
                filter.fileTypes.any { type ->
                    img.imageType.name.equals(type, ignoreCase = true) ||
                    img.mimeType.contains(type, ignoreCase = true)
                }
            }
        }

        if (filter.aiLabels.isNotEmpty()) {
            result = result.filter { img ->
                val labels = aiService.getLabels(img.imageId.toUInt())
                filter.aiLabels.any { filterLabel ->
                    labels.any { it.label.contains(filterLabel, ignoreCase = true) }
                }
            }
        }

        return result
    }

    fun resetFilters() {
        _currentFilter.value = null
        applyCurrentSortAndFilter(_images.value)
    }

    // ================================================================
    // Thumbnail loading
    // ================================================================

    fun loadThumbnail(imageId: Long) {
        if (thumbnailLruCache.get(imageId) != null) return
        if (_thumbnailsLoading.contains(imageId)) return

        _thumbnailsLoading.add(imageId)
        viewModelScope.launch {
            try {
                val result = thumbnailService.loadThumbnail(imageId)
                if (result is ThumbnailService.ThumbnailResult.Success) {
                    thumbnailLruCache.put(imageId, result.bitmap)
                    // 通知 UI 缩略图已更新（bitmap 通过 getThumbnail 直接访问）
                    _thumbnailCacheVersion.value = _thumbnailCacheVersion.value + 1
                }
            } catch (_: Exception) {
                // Thumbnail load failure is non-fatal
            } finally {
                _thumbnailsLoading.remove(imageId)
            }
        }
    }

    fun preloadThumbnails(imageIds: List<Long>) {
        for (id in imageIds) {
            loadThumbnail(id)
        }
    }

    // ================================================================
    // Selection
    // ================================================================

    fun toggleImageSelection(imageId: Long) {
        val current = _selectedImages.value
        _selectedImages.value = if (imageId in current) current - imageId else current + imageId
    }

    fun selectAll() {
        _selectedImages.value = _filteredImages.value.map { it.imageId }.toSet()
    }

    fun clearSelection() {
        _selectedImages.value = emptySet()
    }

    fun deleteSelected() {
        viewModelScope.launch {
            try {
                val ids = _selectedImages.value.toList()
                ids.map { id ->
                    async { imageRepository.deleteImage(id) }
                }.awaitAll()
                clearSelection()
                loadImages()
                loadFolders()
            } catch (e: Throwable) {
                android.util.Log.e("AlbumVM", "Coroutine failed", e)
            }
        }
    }

    fun rateSelected(rating: Int) {
        viewModelScope.launch {
            try {
                for (imageId in _selectedImages.value) {
                    sleeveRepository.setRating(imageId, rating)
                }
            } catch (e: Throwable) {
                android.util.Log.e("AlbumVM", "Coroutine failed", e)
            }
        }
    }

    fun addSelectedToCollection(collectionId: Long) {
        viewModelScope.launch {
            try {
                for (imageId in _selectedImages.value) {
                    sleeveRepository.addImageToCollection(collectionId, imageId)
                }
            } catch (e: Throwable) {
                android.util.Log.e("AlbumVM", "Coroutine failed", e)
            }
        }
    }

    // ================================================================
    // Collections
    // ================================================================

    fun loadCollections() {
        viewModelScope.launch {
            try {
                _collections.value = sleeveRepository.getAllCollections()
            } catch (e: Throwable) {
                android.util.Log.e("AlbumVM", "Coroutine failed", e)
            }
        }
    }

    fun createCollection(name: String, description: String = "") {
        viewModelScope.launch {
            try {
                sleeveRepository.createCollection(name, description)
                loadCollections()
            } catch (e: Throwable) {
                android.util.Log.e("AlbumVM", "Coroutine failed", e)
            }
        }
    }

    fun deleteCollection(collectionId: Long) {
        viewModelScope.launch {
            try {
                sleeveRepository.deleteCollection(collectionId)
                loadCollections()
            } catch (e: Throwable) {
                android.util.Log.e("AlbumVM", "Coroutine failed", e)
            }
        }
    }

    // ================================================================
    // Rating
    // ================================================================

    fun setRating(imageId: Long, rating: Int) {
        viewModelScope.launch {
            try {
                sleeveRepository.setRating(imageId, rating)
            } catch (e: Throwable) {
                android.util.Log.e("AlbumVM", "Coroutine failed", e)
            }
        }
    }

    // ================================================================
    // AI features
    // ================================================================

    fun generateLabelsForImage(imageId: Long) {
        viewModelScope.launch {
            try {
                val image = imageRepository.getImage(imageId) ?: return@launch
                val result = thumbnailService.loadThumbnail(imageId)
                if (result is ThumbnailService.ThumbnailResult.Success) {
                    aiService.generateLabels(imageId.toUInt(), result.bitmap)
                }
            } catch (e: Throwable) {
                android.util.Log.e("AlbumVM", "Coroutine failed", e)
            }
        }
    }

    /** 批量为多张图片生成 AI 标签 */
    fun generateLabelsForImages(ids: List<Long>) {
        viewModelScope.launch {
            try {
                for (imageId in ids) {
                    val image = imageRepository.getImage(imageId) ?: continue
                    val result = thumbnailService.loadThumbnail(imageId)
                    if (result is ThumbnailService.ThumbnailResult.Success) {
                        aiService.generateLabels(imageId.toUInt(), result.bitmap)
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.e("AlbumVM", "Coroutine failed", e)
            }
        }
    }

    /** 批量为多张图片设置评分 */
    fun rateImages(ids: List<Long>, rating: Int) {
        viewModelScope.launch {
            try {
                for (imageId in ids) {
                    sleeveRepository.setRating(imageId, rating)
                }
            } catch (e: Throwable) {
                android.util.Log.e("AlbumVM", "Coroutine failed", e)
            }
        }
    }

    // ================================================================
    // Export helpers
    // ================================================================

    fun getSelectedImagePaths(): List<String> {
        val selected = _selectedImages.value
        return _filteredImages.value
            .filter { it.imageId in selected }
            .map { it.imagePath }
    }

    private val _batchExportResult = MutableStateFlow<ExportService.ExportBatchResult?>(null)
    val batchExportResult: StateFlow<ExportService.ExportBatchResult?> = _batchExportResult.asStateFlow()

    /** 批量导出选中图片 */
    fun exportBatchByIds(ids: List<Long>, settings: ExportSettings) {
        viewModelScope.launch {
            try {
                val items = ids.mapNotNull { id ->
                    imageRepository.getImage(id)?.let {
                        ExportService.ExportBatchItem(sourcePath = it.imagePath)
                    }
                }
                if (items.isNotEmpty()) {
                    _batchExportResult.value = exportService.exportBatch(items, settings)
                }
            } catch (e: Throwable) {
                Log.e("AlbumVM", "Batch export failed", e)
            }
        }
    }

    // ================================================================
    // Internal
    // ================================================================

    override fun onCleared() {
        super.onCleared()
        // LruCache 回收所有缓存的 bitmap，避免内存泄漏
        thumbnailLruCache.snapshot().values.forEach { it.recycle() }
        thumbnailLruCache.evictAll()
    }
}

data class FolderBreadcrumb(
    val folderId: Long,
    val name: String
)
