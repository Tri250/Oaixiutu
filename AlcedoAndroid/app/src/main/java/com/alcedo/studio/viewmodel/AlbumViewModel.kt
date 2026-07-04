package com.alcedo.studio.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.coroutines.launch

class AlbumViewModel : ViewModel() {
    private val sleeveRepository = AppModule.sleeveRepository
    private val imageRepository = AppModule.imageRepository
    private val importService = AppModule.importService
    private val thumbnailService = AppModule.thumbnailService
    private val searchService = AppModule.searchService
    private val aiService = AppModule.aiService

    // ── Image list state ──

    private val _images = MutableStateFlow<List<ImageModel>>(emptyList())
    val images: StateFlow<List<ImageModel>> = _images

    private val _filteredImages = MutableStateFlow<List<ImageModel>>(emptyList())
    val filteredImages: StateFlow<List<ImageModel>> = _filteredImages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    // ── Selection ──

    private val _selectedImages = mutableStateListOf<Long>()
    val selectedImages: List<Long> get() = _selectedImages

    // ── Search ──

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _showSearch = mutableStateOf(false)
    val showSearch get() = _showSearch

    private val _semanticSearchEnabled = mutableStateOf(false)
    val semanticSearchEnabled get() = _semanticSearchEnabled

    private val _searchResults = MutableStateFlow<List<RankedSearchResult>>(emptyList())
    val searchResults: StateFlow<List<RankedSearchResult>> = _searchResults

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    // ── Sort & Filter ──

    private val _sortMode = MutableStateFlow(SortMode.DATE)
    val sortMode: StateFlow<SortMode> = _sortMode

    private val _currentFilter = mutableStateOf<FilterState?>(null)
    val currentFilter get() = _currentFilter

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

    private val _thumbnailCache = MutableStateFlow<Map<Long, Bitmap>>(emptyMap())
    val thumbnailCache: StateFlow<Map<Long, Bitmap>> = _thumbnailCache

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
        checkPermissions()
        loadImages()
        loadFolders()
        loadCollections()
    }

    // ================================================================
    // Permission handling
    // ================================================================

    fun checkPermissions() {
        val appContext = AppModule.applicationContext
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
            _permissionRationale.value = "Permission denied: ${denied.joinToString()}"
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
                    context = AppModule.applicationContext,
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
            }
            _isLoading.value = false
        }
    }

    // ================================================================
    // SAF directory import (Android 10+)
    // ================================================================

    fun importFromSafDirectory(treeUri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val files = SafHelper.listDirectory(AppModule.applicationContext, treeUri)
                val imageFiles = files.filter { !it.isDirectory }
                for (file in imageFiles) {
                    importService.importImage(file.uri)
                }
                loadImages()
                loadFolders()
            } catch (_: Exception) {
                // SAF import failure
            }
            _isLoading.value = false
        }
    }

    // ================================================================
    // Photo Picker import
    // ================================================================

    fun importFromPhotoPicker(uris: List<Uri>) {
        viewModelScope.launch {
            _isLoading.value = true
            for (uri in uris) {
                importService.importImage(uri)
            }
            loadImages()
            loadFolders()
            _isLoading.value = false
        }
    }

    // ================================================================
    // Scoped Storage-aware directory import
    // ================================================================

    fun importDirectoryScoped(path: String? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // On Android 10+, use SAF instead of direct file path
            _permissionRationale.value = "Please select a directory using the system picker"
            // The UI should launch SAF picker and call importFromSafDirectory() with the result
        } else {
            // Legacy: direct file path access
            if (path != null) {
                importDirectory(path)
            }
        }
    }

    // ================================================================
    // Image loading
    // ================================================================

    fun loadImages() {
        viewModelScope.launch {
            _isLoading.value = true
            val allImages = imageRepository.getAllImages()
            _images.value = allImages
            _imageCount.value = allImages.size
            _totalSize.value = allImages.sumOf { it.fileSize }
            applyCurrentSortAndFilter(allImages)
            _isLoading.value = false
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val allImages = imageRepository.getAllImages()
            _images.value = allImages
            _imageCount.value = allImages.size
            _totalSize.value = allImages.sumOf { it.fileSize }
            applyCurrentSortAndFilter(allImages)
            _isRefreshing.value = false
        }
    }

    // ================================================================
    // Folder navigation
    // ================================================================

    fun loadFolders() {
        viewModelScope.launch {
            _folders.value = sleeveRepository.getRootElements()
                .filterIsInstance<SleeveFolder>()
        }
    }

    fun navigateToFolder(folderId: Long?) {
        _currentFolderId.value = folderId
        viewModelScope.launch {
            _isLoading.value = true
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
                applyCurrentSortAndFilter(imagesInFolder)

                // Update breadcrumbs
                val folder = sleeveRepository.getElement(folderId) as? SleeveFolder
                val currentBreadcrumbs = _folderBreadcrumbs.value.toMutableList()
                if (currentBreadcrumbs.none { it.folderId == folderId }) {
                    currentBreadcrumbs.add(FolderBreadcrumb(folderId, folder?.elementName ?: "Folder"))
                    _folderBreadcrumbs.value = currentBreadcrumbs
                }
            }
            _isLoading.value = false
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
            val parentId = _currentFolderId.value
            sleeveRepository.createFolder(name, parentId)
            loadFolders()
            if (parentId != null) {
                navigateToFolder(parentId)
            }
        }
    }

    // ================================================================
    // Import
    // ================================================================

    fun importFromGallery(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            importService.importImage(uri)
            loadImages()
            loadFolders()
            _isLoading.value = false
        }
    }

    fun importFromStorage(uris: List<Uri>) {
        viewModelScope.launch {
            _isLoading.value = true
            for (uri in uris) {
                importService.importImage(uri)
            }
            loadImages()
            loadFolders()
            _isLoading.value = false
        }
    }

    fun importDirectory(path: String) {
        viewModelScope.launch {
            _isLoading.value = true
            importService.importDirectory(path)
            loadImages()
            loadFolders()
            _isLoading.value = false
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
                val allImages = imageRepository.getAllImages()
                _filteredImages.value = allImages.filter {
                    it.imageName.contains(query, ignoreCase = true) ||
                    it.imagePath.contains(query, ignoreCase = true)
                }
            }
            _isSearching.value = false
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
            }
            _isSearching.value = false
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
        applyCurrentSortAndFilter(_filteredImages.value.ifEmpty { _images.value })
    }

    private fun applyCurrentSortAndFilter(images: List<ImageModel>) {
        val sorted = when (_sortMode.value) {
            SortMode.DATE -> images.sortedByDescending { it.imageId }
            SortMode.NAME -> images.sortedBy { it.imageName }
            SortMode.RATING -> {
                val ratingsById = run {
                    val ratings = mutableListOf<RatingEntity>()
                    images.forEach { img ->
                        val r = runCatching { sleeveRepository.getRating(img.imageId) }.getOrNull()
                        if (r != null) ratings.add(r)
                    }
                    ratings.associateBy { it.imageId }
                }
                images.sortedByDescending { ratingsById[it.imageId]?.rating ?: 0 }
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
            result = result.filter { img ->
                val ratingEntity = runCatching { sleeveRepository.getRating(img.imageId) }.getOrNull()
                (ratingEntity?.rating ?: 0) >= filter.rating
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
        if (_thumbnailCache.value.containsKey(imageId)) return
        if (_thumbnailsLoading.contains(imageId)) return

        _thumbnailsLoading.add(imageId)
        viewModelScope.launch {
            try {
                val bitmap = thumbnailService.loadThumbnail(imageId)
                if (bitmap != null) {
                    val updated = _thumbnailCache.value.toMutableMap()
                    updated[imageId] = bitmap
                    _thumbnailCache.value = updated
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
        if (_selectedImages.contains(imageId)) {
            _selectedImages.remove(imageId)
        } else {
            _selectedImages.add(imageId)
        }
    }

    fun selectAll() {
        _selectedImages.clear()
        _selectedImages.addAll(_filteredImages.value.map { it.imageId })
    }

    fun clearSelection() {
        _selectedImages.clear()
    }

    fun deleteSelected() {
        viewModelScope.launch {
            _selectedImages.forEach { imageRepository.deleteImage(it) }
            clearSelection()
            loadImages()
            loadFolders()
        }
    }

    fun rateSelected(rating: Int) {
        viewModelScope.launch {
            for (imageId in _selectedImages) {
                sleeveRepository.setRating(imageId, rating)
            }
        }
    }

    fun addSelectedToCollection(collectionId: Long) {
        viewModelScope.launch {
            for (imageId in _selectedImages) {
                sleeveRepository.addImageToCollection(collectionId, imageId)
            }
        }
    }

    // ================================================================
    // Collections
    // ================================================================

    fun loadCollections() {
        viewModelScope.launch {
            _collections.value = sleeveRepository.getAllCollections()
        }
    }

    fun createCollection(name: String, description: String = "") {
        viewModelScope.launch {
            sleeveRepository.createCollection(name, description)
            loadCollections()
        }
    }

    fun deleteCollection(collectionId: Long) {
        viewModelScope.launch {
            sleeveRepository.deleteCollection(collectionId)
            loadCollections()
        }
    }

    // ================================================================
    // Rating
    // ================================================================

    fun setRating(imageId: Long, rating: Int) {
        viewModelScope.launch {
            sleeveRepository.setRating(imageId, rating)
        }
    }

    // ================================================================
    // AI features
    // ================================================================

    fun generateLabelsForImage(imageId: Long) {
        viewModelScope.launch {
            val image = imageRepository.getImage(imageId) ?: return@launch
            val thumbnail = thumbnailService.loadThumbnail(imageId) ?: return@launch
            aiService.generateLabels(imageId.toUInt(), thumbnail)
        }
    }

    // ================================================================
    // Export helpers
    // ================================================================

    fun getSelectedImagePaths(): List<String> {
        return _filteredImages.value
            .filter { it.imageId in _selectedImages }
            .map { it.imagePath }
    }

    // ================================================================
    // Internal
    // ================================================================

    override fun onCleared() {
        super.onCleared()
        _thumbnailCache.value.values.forEach { it.recycle() }
        _thumbnailCache.value = emptyMap()
    }
}

data class FolderBreadcrumb(
    val folderId: Long,
    val name: String
)
