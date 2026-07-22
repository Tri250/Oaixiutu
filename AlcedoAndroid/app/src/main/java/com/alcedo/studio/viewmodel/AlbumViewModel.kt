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
import com.alcedo.studio.service.TaskNotificationHelper
import com.alcedo.studio.permission.PermissionHelper
import com.alcedo.studio.storage.MediaStoreHelper
import com.alcedo.studio.storage.PhotoPickerHelper
import com.alcedo.studio.storage.SafHelper
import com.alcedo.studio.ui.album.FilterState
import com.alcedo.studio.ui.album.SortMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
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
    private val batchEditService by lazy { AppModule.batchEditService }
    private val presetService by lazy { AppModule.presetService }
    private val albumBrowseService by lazy { AppModule.albumBrowseService }
    private val imageAnalysisService by lazy { AppModule.imageAnalysisService }
    private val aiSidecarRuntimeService by lazy { AppModule.aiSidecarRuntimeService }

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

    /** S-2 修复: 清空 permissionRationale 状态 */
    fun clearPermissionRationale() {
        _permissionRationale.value = null
    }

    /** S-8 修复: 清空 batchExportResult 状态,避免对话框重复弹出 */
    fun clearBatchExportResult() {
        _batchExportResult.value = null
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

    /** Returns the cached thumbnail for [imageId], or null if not yet loaded or recycled. */
    fun getThumbnail(imageId: Long): Bitmap? {
        val bitmap = thumbnailLruCache.get(imageId)
        if (bitmap != null && bitmap.isRecycled) {
            thumbnailLruCache.remove(imageId)
            return null
        }
        return bitmap
    }

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

    // ── Snackbar events ──
    private val _snackbarEvent = MutableSharedFlow<String>()
    val snackbarEvent: SharedFlow<String> = _snackbarEvent.asSharedFlow()

    fun showSnackbar(message: String) {
        viewModelScope.launch {
            _snackbarEvent.emit(message)
        }
    }

    // ── Import progress ──

    private val _importProgress = MutableStateFlow<ImportProgress?>(null)
    val importProgress: StateFlow<ImportProgress?> = _importProgress.asStateFlow()

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
            val context = AppModule.context
            try {
                // Persist tree URI permission so we can access files later
                val resolver = context.contentResolver
                try {
                    resolver.takePersistableUriPermission(
                        treeUri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // Some providers don't support persistable permission
                }

                // 显示扫描进度,让用户知道正在扫描目录
                _importProgress.value = ImportProgress(0, 0, "scanning")

                // Use recursive listing to find all image files in the
                // directory tree (including subdirectories), filtered by
                // image extensions to avoid importing non-image files.
                val imageFiles = SafHelper.listImageFilesRecursive(context, treeUri)

                if (imageFiles.isEmpty()) {
                    _permissionRationale.value = "所选目录中没有图片文件"
                    _importProgress.value = null
                    return@launch
                }

                val uris = imageFiles.map { it.uri }
                _importProgress.value = ImportProgress(0, uris.size, "importing")
                TaskNotificationHelper.notifyImportProgress(context, 0, uris.size)
                val result = importService.importTwoPhase(uris)
                if (result.successCount == 0) {
                    if (result.duplicateCount > 0 && result.errorCount == 0) {
                        _permissionRationale.value = "所选图片已存在于图库中"
                    } else {
                        _permissionRationale.value = "导入失败：${result.errorCount} 个文件无法导入"
                    }
                } else {
                    showSnackbar("已导入 ${result.successCount} 张图片")
                }
                _importProgress.value = ImportProgress(result.successCount, uris.size, "completed")
                TaskNotificationHelper.notifyImportComplete(context, result.successCount)
                loadImagesInternal()
                loadFoldersInternal()
                // 完成后延迟清理进度状态,避免进度条永久残留
                kotlinx.coroutines.delay(1500)
                _importProgress.value = null
            } catch (e: CancellationException) {
                _importProgress.value = null
                throw e
            } catch (e: Throwable) {
                android.util.Log.e("AlbumVM", "importFromSafDirectory failed", e)
                _permissionRationale.value = "目录导入失败: ${e.message ?: "未知错误"}"
                _importProgress.value = null
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
            val context = AppModule.context
            try {
                // Take persistable URI permissions so URIs remain accessible
                // even if the Activity is recreated during import
                val resolver = context.contentResolver
                for (uri in uris) {
                    try {
                        resolver.takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: SecurityException) {
                        // Some providers don't support persistable permission
                    }
                }

                // Use two-phase import for better UX (fast scan + background metadata)
                _importProgress.value = ImportProgress(0, uris.size, "importing")
                TaskNotificationHelper.notifyImportProgress(context, 0, uris.size)
                val result = importService.importTwoPhase(uris)
                if (result.successCount == 0) {
                    if (result.duplicateCount > 0 && result.errorCount == 0) {
                        _permissionRationale.value = "所选图片已存在于图库中"
                    } else {
                        _permissionRationale.value = "导入失败：${result.errorCount} 个文件无法导入"
                    }
                } else {
                    showSnackbar("已导入 ${result.successCount} 张图片")
                }
                _importProgress.value = ImportProgress(result.successCount, uris.size, "completed")
                TaskNotificationHelper.notifyImportComplete(context, result.successCount)
                loadImagesInternal()
                loadFoldersInternal()
                // 完成后延迟清理进度状态,避免进度条永久残留
                kotlinx.coroutines.delay(1500)
                _importProgress.value = null
            } catch (e: CancellationException) {
                _importProgress.value = null
                throw e
            } catch (e: Throwable) {
                android.util.Log.e("AlbumVM", "importFromPhotoPicker failed", e)
                _permissionRationale.value = "导入失败: ${e.message ?: "未知错误"}"
                _importProgress.value = null
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
        viewModelScope.launch { loadImagesInternal() }
    }

    private suspend fun loadImagesInternal() {
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
                // 下拉刷新同时刷新文件夹列表 (B-2 修复)
                loadFolders()
                // 通过 AlbumBrowseService 预热浏览状态
                albumBrowseService.setPageSize(PAGE_SIZE)
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
        viewModelScope.launch { loadFoldersInternal() }
    }

    private suspend fun loadFoldersInternal() {
        try {
            _folders.value = sleeveRepository.getRootElements()
                .filterIsInstance<SleeveFolder>()
        } catch (e: Throwable) {
            android.util.Log.e("AlbumVM", "Coroutine failed", e)
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
            val context = AppModule.context
            try {
                // 使用两阶段导入替代逐个 importImage,提升批量导入性能和 UI 响应 (S11 修复)
                _importProgress.value = ImportProgress(0, uris.size, "importing")
                TaskNotificationHelper.notifyImportProgress(context, 0, uris.size)
                val result = importService.importTwoPhase(uris)
                if (result.successCount == 0) {
                    if (result.duplicateCount > 0 && result.errorCount == 0) {
                        _permissionRationale.value = "所选图片已存在于图库中"
                    } else {
                        _permissionRationale.value = "导入失败：${result.errorCount} 个文件无法导入"
                    }
                } else {
                    showSnackbar("已导入 ${result.successCount} 张图片")
                }
                _importProgress.value = ImportProgress(result.successCount, uris.size, "completed")
                TaskNotificationHelper.notifyImportComplete(context, result.successCount)
                loadImages()
                loadFolders()
                kotlinx.coroutines.delay(1500)
                _importProgress.value = null
            } catch (e: CancellationException) {
                _importProgress.value = null
                throw e
            } catch (e: Throwable) {
                android.util.Log.e("AlbumVM", "importFromStorage failed", e)
                _permissionRationale.value = "导入失败: ${e.message ?: "未知错误"}"
                _importProgress.value = null
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
            // 日期排序使用真实拍摄日期 (G-2 修复),
            // captureDate 格式 "yyyy:MM:dd HH:mm:ss" 可直接字符串比较
            // 空值排到末尾
            SortMode.DATE -> images.sortedWith(
                compareByDescending<ImageModel> { it.exifDisplay.captureDate.isNotEmpty() }
                    .thenByDescending { it.exifDisplay.captureDate }
            )
            SortMode.NAME -> images.sortedBy { it.imageName }
            SortMode.RATING -> images.sortedByDescending {
                it.exifDisplay.rating
            }
            SortMode.TYPE -> images.sortedBy { it.imageType.ordinal }
        }
        val filter = _currentFilter.value
        // 搜索激活时不覆盖 _filteredImages (S-6 修复):
        // loadMoreImages 调用此方法时,若正在搜索,应保留搜索结果
        if (_searchQuery.value.isBlank()) {
            _filteredImages.value = if (filter != null) applyFilter(sorted, filter) else sorted
        }
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
                val count = ids.size
                ids.map { id ->
                    async {
                        // 完整删除链路 (F-2 修复):
                        // 1. 删除 SleeveFile 记录 (避免孤儿数据)
                        // 2. 删除 metadata 记录
                        // 3. 清理缩略图缓存
                        // 物理文件不删除 (SAF/MediaStore URI 无写权限时静默跳过)
                        try {
                            sleeveRepository.removeElementByImageId(id)
                        } catch (_: Throwable) { /* SleeveFile 可能不存在 */ }
                        imageRepository.deleteImage(id)
                    }
                }.awaitAll()
                clearSelection()
                loadImages()
                loadFolders()
                showSnackbar("已删除 $count 张图片")
            } catch (e: Throwable) {
                android.util.Log.e("AlbumVM", "Coroutine failed", e)
                _permissionError.value = "删除失败: ${e.message ?: "未知错误"}"
            }
        }
    }

    /**
     * 单张图片删除 (F-1 修复): 上下文菜单"删除"按钮应调用此方法而非 toggleImageSelection
     */
    fun deleteImage(imageId: Long) {
        viewModelScope.launch {
            try {
                try {
                    sleeveRepository.removeElementByImageId(imageId)
                } catch (_: Throwable) { }
                imageRepository.deleteImage(imageId)
                loadImages()
                loadFolders()
            } catch (e: Throwable) {
                android.util.Log.e("AlbumVM", "deleteImage failed", e)
                _permissionError.value = "删除失败: ${e.message ?: "未知错误"}"
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
                // 评分后刷新 UI (S-1 修复): 更新 _images 中对应图片的 rating
                val updated = _images.value.map { img ->
                    if (img.imageId == imageId) {
                        img.copy(exifDisplay = img.exifDisplay.copy(rating = rating))
                    } else img
                }
                _images.value = updated
                applyCurrentSortAndFilter(updated)
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
                // 通过 AiSidecarRuntimeService 检查 AI 运行时状态
                val runtimeReady = try {
                    aiSidecarRuntimeService.isReady()
                } catch (_: Throwable) {
                    false
                }
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
            val context = AppModule.context
            try {
                for ((index, imageId) in ids.withIndex()) {
                    TaskNotificationHelper.notifyAiTaggingProgress(context, index + 1, ids.size)
                    val image = imageRepository.getImage(imageId) ?: continue
                    val result = thumbnailService.loadThumbnail(imageId)
                    if (result is ThumbnailService.ThumbnailResult.Success) {
                        aiService.generateLabels(imageId.toUInt(), result.bitmap)
                    }
                }
                TaskNotificationHelper.notifyAiTaggingComplete(context, ids.size)
                showSnackbar("已为 ${ids.size} 张图片生成标签")
            } catch (e: Throwable) {
                android.util.Log.e("AlbumVM", "Coroutine failed", e)
            }
        }
    }

    /** 使用 ImageAnalysisService 批量分析图片（描述 + 评分） */
    fun analyzeImagesBatch(ids: List<Long>, tasks: Set<ImageAnalysisTask> = setOf(ImageAnalysisTask.DESCRIBE, ImageAnalysisTask.SCORE)) {
        viewModelScope.launch {
            val context = AppModule.context
            try {
                val items = ids.mapNotNull { id ->
                    imageRepository.getImage(id)?.let { img ->
                        ImageAnalysisItem(
                            imageId = id,
                            imagePath = img.imagePath
                        )
                    }
                }
                if (items.isEmpty()) return@launch

                val options = ImageAnalysisOptions(tasks = tasks)

                imageAnalysisService.startAnalysis(
                    items = items,
                    options = options,
                    onProgress = { progress ->
                        TaskNotificationHelper.notifyAiTaggingProgress(
                            context,
                            progress.currentIndex + 1,
                            progress.totalCount
                        )
                    },
                    onFinished = { results ->
                        TaskNotificationHelper.notifyAiTaggingComplete(context, results.size)
                        showSnackbar("已完成 ${results.size} 张图片的 AI 分析")
                        loadImages()
                    }
                )
            } catch (e: Throwable) {
                android.util.Log.e("AlbumVM", "analyzeImagesBatch failed", e)
            }
        }
    }

    /** 批量为多张图片设置评分 */
    fun rateImages(ids: List<Long>, rating: Int) {
        viewModelScope.launch {
            val context = AppModule.context
            try {
                for ((index, imageId) in ids.withIndex()) {
                    TaskNotificationHelper.notifyAiRatingProgress(context, index + 1, ids.size)
                    sleeveRepository.setRating(imageId, rating)
                }
                TaskNotificationHelper.notifyAiRatingComplete(context, ids.size)
                showSnackbar("评分完成")
                // 批量评分后刷新 UI (S-1 修复)
                val idSet = ids.toSet()
                val updated = _images.value.map { img ->
                    if (img.imageId in idSet) {
                        img.copy(exifDisplay = img.exifDisplay.copy(rating = rating))
                    } else img
                }
                _images.value = updated
                applyCurrentSortAndFilter(updated)
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
    // Batch Edit (RapidRAW-inspired copy / paste adjustments)
    // ================================================================

    /** Holds the adjustments copied from the source image (clipboard). */
    private val _clipboardParams = MutableStateFlow<PipelineParams?>(null)
    val clipboardParams: StateFlow<PipelineParams?> = _clipboardParams.asStateFlow()

    /** Image id of the most recently copied source, for UI hints ("Copied from X"). */
    private val _clipboardSourceId = MutableStateFlow<Long?>(null)
    val clipboardSourceId: StateFlow<Long?> = _clipboardSourceId.asStateFlow()

    /** Live progress of any ongoing batch edit operation. */
    private val _batchEditProgress = MutableStateFlow<BatchEditProgress>(BatchEditProgress())
    val batchEditProgress: StateFlow<BatchEditProgress> = _batchEditProgress.asStateFlow()

    /** Snackbar feedback for the batch edit panel. */
    private val _batchEditMessage = MutableStateFlow<String?>(null)
    val batchEditMessage: StateFlow<String?> = _batchEditMessage.asStateFlow()

    /** Available presets for the batch preset picker. */
    private val _batchPresets = MutableStateFlow<List<PresetWithThumbnail>>(emptyList())
    val batchPresets: StateFlow<List<PresetWithThumbnail>> = _batchPresets.asStateFlow()

    init {
        // Mirror BatchEditService progress into the ViewModel-exposed flow so
        // the UI can observe a single source of truth.
        // If batchEditService is not ready or its progress flow throws, the
        // app must not crash — fall back to an empty progress state.
        viewModelScope.launch {
            try {
                batchEditService.progress.collect { p ->
                    _batchEditProgress.value = p
                }
            } catch (e: Throwable) {
                Log.e("AlbumVM", "Failed to collect batchEditService.progress", e)
            }
        }
    }

    fun clearBatchEditMessage() {
        _batchEditMessage.value = null
    }

    /**
     * Copy adjustments from the first selected image into the in-memory
     * clipboard. The caller (UI) decides which image is the "first" by
     * passing its id; typically the lowest id in the selection set.
     */
    fun copyAdjustments() {
        val sourceId = _selectedImages.value.firstOrNull() ?: run {
            _batchEditMessage.value = "No source image selected"
            return
        }
        viewModelScope.launch {
            try {
                val params = batchEditService.copyAdjustments(sourceId.toString())
                _clipboardParams.value = params
                _clipboardSourceId.value = sourceId
                _batchEditMessage.value = "Adjustments copied from image $sourceId"
                showSnackbar("调整已复制")
            } catch (e: Throwable) {
                Log.e("AlbumVM", "copyAdjustments failed", e)
                _batchEditMessage.value = "Copy failed: ${e.message ?: "unknown error"}"
            }
        }
    }

    /** Paste the clipboard adjustments to every currently-selected image. */
    fun pasteAdjustments() {
        val targets = _selectedImages.value.map { it.toString() }
        if (targets.isEmpty()) {
            _batchEditMessage.value = "Select images to paste onto first"
            return
        }
        val params = _clipboardParams.value ?: run {
            _batchEditMessage.value = "No adjustments copied yet"
            return
        }
        viewModelScope.launch {
            val outcome = batchEditService.pasteAdjustments(targets, params)
            _batchEditMessage.value = messageForOutcome(outcome, "Paste")
            showSnackbar("调整已粘贴到 ${targets.size} 张图片")
        }
    }

    /** Selective paste — only the categories enabled in [filter] are applied. */
    fun pastePartialAdjustments(filter: AdjustmentFilter) {
        val targets = _selectedImages.value.map { it.toString() }
        if (targets.isEmpty()) {
            _batchEditMessage.value = "Select images to paste onto first"
            return
        }
        val params = _clipboardParams.value ?: run {
            _batchEditMessage.value = "No adjustments copied yet"
            return
        }
        viewModelScope.launch {
            val outcome = batchEditService.pastePartialAdjustments(targets, params, filter)
            _batchEditMessage.value = messageForOutcome(outcome, "Selective paste")
        }
    }

    /** Reset adjustments for every currently-selected image. */
    fun resetBatchAdjustments() {
        val targets = _selectedImages.value.map { it.toString() }
        if (targets.isEmpty()) {
            _batchEditMessage.value = "Select images to reset first"
            return
        }
        viewModelScope.launch {
            val outcome = batchEditService.resetAdjustments(targets)
            _batchEditMessage.value = messageForOutcome(outcome, "Reset")
            showSnackbar("调整已重置")
        }
    }

    /** Apply a preset to every currently-selected image. */
    fun applyPresetBatch(presetId: Long) {
        val targets = _selectedImages.value.map { it.toString() }
        if (targets.isEmpty()) {
            _batchEditMessage.value = "Select images first"
            return
        }
        viewModelScope.launch {
            val outcome = batchEditService.applyPresetBatch(presetService, presetId, targets)
            _batchEditMessage.value = messageForOutcome(outcome, "Apply preset")
        }
    }

    /** Sync adjustments from [sourceImageId] to all other selected images. */
    fun syncAdjustments(sourceImageId: Long) {
        val targets = _selectedImages.value
            .filter { it != sourceImageId }
            .map { it.toString() }
        if (targets.isEmpty()) {
            _batchEditMessage.value = "Select more than one image to sync"
            return
        }
        viewModelScope.launch {
            val outcome = batchEditService.syncAdjustments(sourceImageId.toString(), targets)
            _batchEditMessage.value = messageForOutcome(outcome, "Sync")
        }
    }

    /** Load available presets for the batch preset picker. */
    fun loadBatchPresets() {
        viewModelScope.launch {
            try {
                presetService.ensureBuiltInPresetsInitialized()
                presetService.getAllPresets().collect { presets ->
                    _batchPresets.value = presets
                }
            } catch (e: Throwable) {
                Log.e("AlbumVM", "loadBatchPresets failed", e)
            }
        }
    }

    private fun messageForOutcome(outcome: BatchEditOutcome, op: String): String = when (outcome) {
        is BatchEditOutcome.Success -> "$op succeeded (${outcome.affected} images)"
        is BatchEditOutcome.Partial -> "$op partial: ${outcome.message}"
        is BatchEditOutcome.Failure -> "$op failed: ${outcome.message}"
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

data class ImportProgress(
    val current: Int = 0,
    val total: Int = 0,
    val phase: String = "scanning" // "scanning" | "importing" | "completed"
)
