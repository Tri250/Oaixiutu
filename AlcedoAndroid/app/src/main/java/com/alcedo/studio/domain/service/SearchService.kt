package com.alcedo.studio.domain.service

import com.alcedo.studio.data.model.*
import com.alcedo.studio.domain.repository.ImageRepository
import com.alcedo.studio.domain.repository.SleeveRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SearchService(
    private val sleeveRepository: SleeveRepository,
    private val imageRepository: ImageRepository,
    private val aiService: AiService
) {

    suspend fun search(
        query: String,
        enableSemantic: Boolean = false,
        filter: FilterCombo? = null
    ): List<SearchResult> = withContext(Dispatchers.Default) {
        val results = mutableListOf<SearchResult>()

        // Exact name/path search
        val allImages = imageRepository.getAllImages()
        allImages.filter {
            it.imageName.contains(query, ignoreCase = true) ||
            it.imagePath.contains(query, ignoreCase = true)
        }.forEach {
            results.add(SearchResult(it.imageId, 1.0f, ResultType.EXACT))
        }

        // Semantic search
        if (enableSemantic) {
            val semanticResults = aiService.searchByText(query)
            results.addAll(semanticResults)
        }

        // Apply filters
        filter?.let { combo ->
            // Filter implementation would go here
        }

        results.distinctBy { it.imageId }.sortedByDescending { it.score }
    }

    suspend fun searchByExif(filter: ExifFilter): List<ImageModel> = withContext(Dispatchers.IO) {
        imageRepository.getAllImages().filter { image ->
            val exif = image.exifDisplay
            filter.cameraMake?.let { exif.cameraMake.contains(it, ignoreCase = true) } ?: true &&
            filter.cameraModel?.let { exif.cameraModel.contains(it, ignoreCase = true) } ?: true &&
            filter.lensModel?.let { exif.lensModel.contains(it, ignoreCase = true) } ?: true
        }
    }
}
