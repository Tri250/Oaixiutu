package com.alcedo.studio.viewmodel

import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alcedo.studio.data.model.*
import com.alcedo.studio.di.AppModule
import com.alcedo.studio.domain.service.*
import com.alcedo.studio.ui.album.FilterState
import com.alcedo.studio.ui.album.SortMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AlbumViewModel : ViewModel() {
    private val sleeveRepository = AppModule.sleeveRepository
    private val imageRepository = AppModule.imageRepository
    private val importService = AppModule.importService
    private val thumbnailService = AppModule.thumbnailService
    private val searchService = AppModule.searchService
    private val aiService = AppModule.aiService

    private val _images = MutableStateFlow<List<ImageModel>>(emptyList())
    val images: StateFlow<List<ImageModel>> = _images

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _selectedImages = mutableStateListOf<UInt>()
    val selectedImages: List<UInt> get() = _selectedImages

    private val _showSearch = mutableStateOf(false)
    val showSearch get() = _showSearch

    private val _semanticSearchEnabled = mutableStateOf(false)
    val semanticSearchEnabled get() = _semanticSearchEnabled

    private val _folders = MutableStateFlow<List<SleeveFolder>>(emptyList())
    val folders: StateFlow<List<SleeveFolder>> = _folders

    private val _sortMode = MutableStateFlow(SortMode.DATE)
    val sortMode: StateFlow<SortMode> = _sortMode

    private val _currentFilter = mutableStateOf<FilterState?>(null)
    val currentFilter get() = _currentFilter

    private val _selectedFolderId = mutableStateOf<UInt?>(null)
    val selectedFolderId get() = _selectedFolderId

    init {
        loadImages()
        loadFolders()
    }

    fun loadImages() {
        viewModelScope.launch {
            _isLoading.value = true
            _images.value = imageRepository.getAllImages()
            _isLoading.value = false
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _images.value = imageRepository.getAllImages()
            _isRefreshing.value = false
        }
    }

    fun loadFolders() {
        viewModelScope.launch {
            _folders.value = sleeveRepository.getAllFolders()
        }
    }

    fun selectFolder(folderId: UInt) {
        _selectedFolderId.value = folderId
        viewModelScope.launch {
            _isLoading.value = true
            if (folderId == 0u) {
                _images.value = imageRepository.getAllImages()
            } else {
                val folder = sleeveRepository.getFolder(folderId)
                if (folder != null) {
                    val elementIds = folder.listElements()
                    val allImages = imageRepository.getAllImages()
                    // Filter images by folder contents
                    _images.value = allImages.filter { img ->
                        elementIds.any { /* Match by element ID */ false }
                    }
                }
            }
            _isLoading.value = false
        }
    }

    fun importImage(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            importService.importImage(uri)
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

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            loadImages()
            return
        }
        viewModelScope.launch {
            val results = searchService.search(query, _semanticSearchEnabled.value)
            val imageIds = results.map { it.imageId }.toSet()
            _images.value = imageRepository.getAllImages().filter { it.imageId in imageIds }
        }
    }

    fun toggleSemanticSearch() {
        _semanticSearchEnabled.value = !_semanticSearchEnabled.value
    }

    fun toggleImageSelection(imageId: UInt) {
        if (_selectedImages.contains(imageId)) {
            _selectedImages.remove(imageId)
        } else {
            _selectedImages.add(imageId)
        }
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

    fun toggleSearch() {
        _showSearch.value = !_showSearch.value
        if (!_showSearch.value) {
            _searchQuery.value = ""
            loadImages()
        }
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
        viewModelScope.launch {
            val sorted = when (mode) {
                SortMode.DATE -> _images.value.sortedByDescending { it.imageId }
                SortMode.NAME -> _images.value.sortedBy { it.imageName }
                SortMode.RATING -> _images.value // Would sort by rating field
                SortMode.TYPE -> _images.value.sortedBy { it.imageType.ordinal }
            }
            _images.value = sorted
        }
    }

    fun applyFilter(filter: FilterState) {
        _currentFilter.value = filter
        viewModelScope.launch {
            var filtered = imageRepository.getAllImages()

            if (filter.cameraMakes.isNotEmpty()) {
                filtered = filtered.filter { img ->
                    filter.cameraMakes.any { make ->
                        img.exifDisplay.cameraMake.contains(make, ignoreCase = true)
                    }
                }
            }

            if (filter.cameraModels.isNotEmpty()) {
                filtered = filtered.filter { img ->
                    filter.cameraModels.any { model ->
                        img.exifDisplay.cameraModel.contains(model, ignoreCase = true)
                    }
                }
            }

            if (filter.lensModel.isNotEmpty()) {
                filtered = filtered.filter { img ->
                    img.exifDisplay.lensModel.contains(filter.lensModel, ignoreCase = true)
                }
            }

            if (filter.rating > 0) {
                // Filter by rating when available
            }

            if (filter.fileTypes.isNotEmpty()) {
                filtered = filtered.filter { img ->
                    filter.fileTypes.any { type ->
                        img.imageType.name.equals(type, ignoreCase = true)
                    }
                }
            }

            _images.value = filtered
        }
    }

    fun resetFilters() {
        _currentFilter.value = null
        loadImages()
    }
}