package com.alcedo.studio.viewmodel

import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alcedo.studio.data.model.*
import com.alcedo.studio.di.AppModule
import com.alcedo.studio.domain.service.*
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

    private val _selectedImages = mutableStateListOf<UInt>()
    val selectedImages: List<UInt> get() = _selectedImages

    private val _showSearch = mutableStateOf(false)
    val showSearch get() = _showSearch

    private val _semanticSearchEnabled = mutableStateOf(false)
    val semanticSearchEnabled get() = _semanticSearchEnabled

    init {
        loadImages()
    }

    fun loadImages() {
        viewModelScope.launch {
            _isLoading.value = true
            _images.value = imageRepository.getAllImages()
            _isLoading.value = false
        }
    }

    fun importImage(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            importService.importImage(uri)
            loadImages()
            _isLoading.value = false
        }
    }

    fun importDirectory(path: String) {
        viewModelScope.launch {
            _isLoading.value = true
            importService.importDirectory(path)
            loadImages()
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
        }
    }

    fun toggleSearch() {
        _showSearch.value = !_showSearch.value
        if (!_showSearch.value) {
            _searchQuery.value = ""
            loadImages()
        }
    }
}
