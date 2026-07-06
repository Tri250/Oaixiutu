package com.alcedo.studio.domain.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import com.alcedo.studio.data.local.*
import com.alcedo.studio.data.model.*
import com.alcedo.studio.domain.service.SleeveFilterService
import com.alcedo.studio.domain.service.SleeveService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Repository that coordinates between SleeveService, SleeveFilterService, and the database.
 * Provides reactive Flow-based queries, handles pagination state, and caches query results.
 */
class SleeveRepository(
    private val sleeveService: SleeveService,
    private val filterService: SleeveFilterService,
    private val elementDao: SleeveElementDao,
    private val fileDao: SleeveFileDao,
    private val folderDao: SleeveFolderDao,
    private val metadataDao: ImageMetadataDao,
    private val collectionDao: CollectionDao,
    private val ratingDao: RatingDao,
    private val labelDao: SemanticLabelDao,
    private val filterDao: FilterDao,
    private val pathResolver: PathResolver,
    private val cacheManager: DentryCacheManager
) {
    // ================================================================
    // Element operations
    // ================================================================

    suspend fun addElement(element: SleeveElement, parentId: Long? = null): Long = withContext(Dispatchers.IO) {
        sleeveService.createElement(
            name = element.elementName,
            type = element.type,
            parentId = parentId,
            imageId = if (element is SleeveFile) element.imageId else null,
            filePath = if (element is SleeveFile) element.filePath else null
        )
    }

    suspend fun getElement(id: Long): SleeveElement? = withContext(Dispatchers.IO) {
        val entity = elementDao.getElementById(id) ?: return@withContext null
        val file = if (entity.elementType == 0) fileDao.getFileByElementId(id) else null
        val folder = if (entity.elementType == 1) folderDao.getFolderByElementId(id) else null
        entity.toDomain(file, folder)
    }

    fun observeElement(id: Long): Flow<SleeveElement?> = flow {
        elementDao.observeElementById(id).collect { entity ->
            if (entity != null) {
                val file = if (entity.elementType == 0) fileDao.getFileByElementId(id) else null
                val folder = if (entity.elementType == 1) folderDao.getFolderByElementId(id) else null
                emit(entity.toDomain(file, folder))
            } else {
                emit(null)
            }
        }
    }

    suspend fun getChildren(parentId: Long): List<SleeveElement> = withContext(Dispatchers.IO) {
        sleeveService.getFolderContents(parentId)
    }

    fun observeChildren(parentId: Long): Flow<List<SleeveElement>> = flow {
        elementDao.observeChildrenByParentId(parentId).collect { entities ->
            val elements = entities.map { entity ->
                val file = if (entity.elementType == 0) fileDao.getFileByElementId(entity.elementId) else null
                val folder = if (entity.elementType == 1) folderDao.getFolderByElementId(entity.elementId) else null
                entity.toDomain(file, folder)
            }
            emit(elements)
        }
    }

    suspend fun getChildrenPaginated(
        parentId: Long,
        page: Int,
        pageSize: Int = 50
    ): SleeveService.PagedResult<SleeveElement> = withContext(Dispatchers.IO) {
        sleeveService.getFolderContentsPaginated(parentId, page, pageSize)
    }

    suspend fun removeElement(id: Long): Boolean = withContext(Dispatchers.IO) {
        sleeveService.deleteElement(id)
    }

    /**
     * 按 imageId 删除 SleeveFile 元素 (F-2 修复):
     * 先查询 element_id,再调用 deleteElement 完整清理 sleeve_files + sleeve_elements 记录
     */
    suspend fun removeElementByImageId(imageId: Long): Boolean = withContext(Dispatchers.IO) {
        val file = sleeveService.getFileByImageId(imageId)
        if (file != null) {
            sleeveService.deleteElement(file.elementId)
        } else {
            false
        }
    }

    suspend fun moveElement(elementId: Long, newParentId: Long?): Boolean = withContext(Dispatchers.IO) {
        sleeveService.moveElement(elementId, newParentId)
    }

    suspend fun renameElement(elementId: Long, newName: String): Boolean = withContext(Dispatchers.IO) {
        sleeveService.renameElement(elementId, newName)
    }

    // ================================================================
    // Folder operations
    // ================================================================

    suspend fun createFolder(name: String, parentId: Long? = null): Long = withContext(Dispatchers.IO) {
        sleeveService.createFolder(name, parentId)
    }

    suspend fun getFolderPath(folderId: Long): String = withContext(Dispatchers.IO) {
        sleeveService.getFolderPath(folderId)
    }

    suspend fun getRootElements(): List<SleeveElement> = withContext(Dispatchers.IO) {
        val entities = elementDao.getRootElements()
        entities.map { entity ->
            val file = if (entity.elementType == 0) fileDao.getFileByElementId(entity.elementId) else null
            val folder = if (entity.elementType == 1) folderDao.getFolderByElementId(entity.elementId) else null
            entity.toDomain(file, folder)
        }
    }

    fun observeRootElements(): Flow<List<SleeveElement>> = flow {
        elementDao.observeRootElements().collect { entities ->
            val elements = entities.map { entity ->
                val file = if (entity.elementType == 0) fileDao.getFileByElementId(entity.elementId) else null
                val folder = if (entity.elementType == 1) folderDao.getFolderByElementId(entity.elementId) else null
                entity.toDomain(file, folder)
            }
            emit(elements)
        }
    }

    // ================================================================
    // Image metadata operations
    // ================================================================

    suspend fun getImageMetadata(imageId: Long): ImageMetadataEntity? = withContext(Dispatchers.IO) {
        metadataDao.getMetadataByImageId(imageId)
    }

    fun observeImageMetadata(imageId: Long): Flow<ImageMetadataEntity?> {
        return metadataDao.observeMetadataByImageId(imageId)
    }

    suspend fun getAllImageMetadata(): List<ImageMetadataEntity> = withContext(Dispatchers.IO) {
        metadataDao.getAllMetadata()
    }

    fun observeAllImageMetadata(): Flow<List<ImageMetadataEntity>> {
        return metadataDao.observeAllMetadata()
    }

    suspend fun getImageMetadataPaginated(
        page: Int,
        pageSize: Int = 50
    ): SleeveService.PagedResult<ImageMetadataEntity> = withContext(Dispatchers.IO) {
        val offset = page * pageSize
        val items = metadataDao.getMetadataPaginated(pageSize, offset)
        val totalCount = metadataDao.getMetadataCount()
        SleeveService.PagedResult(items, totalCount, page, pageSize)
    }

    suspend fun getRecentlyImported(limit: Int = 50): List<ImageMetadataEntity> = withContext(Dispatchers.IO) {
        metadataDao.getRecentlyImported(limit)
    }

    // ================================================================
    // Rating operations
    // ================================================================

    suspend fun setRating(imageId: Long, rating: Int): Boolean = withContext(Dispatchers.IO) {
        sleeveService.setRating(imageId, rating)
    }

    suspend fun getRating(imageId: Long): RatingEntity? = withContext(Dispatchers.IO) {
        ratingDao.getRatingByImageId(imageId)
    }

    fun observeRating(imageId: Long): Flow<RatingEntity?> {
        return ratingDao.observeRatingByImageId(imageId)
    }

    suspend fun getRatingDistribution(): List<RatingDistributionResult> = withContext(Dispatchers.IO) {
        ratingDao.getRatingDistribution()
    }

    // ================================================================
    // Collection operations
    // ================================================================

    suspend fun createCollection(name: String, description: String = ""): Long = withContext(Dispatchers.IO) {
        sleeveService.createCollection(name, description)
    }

    suspend fun deleteCollection(collectionId: Long): Boolean = withContext(Dispatchers.IO) {
        sleeveService.deleteCollection(collectionId)
    }

    suspend fun getAllCollections(): List<CollectionEntity> = withContext(Dispatchers.IO) {
        collectionDao.getAllCollections()
    }

    fun observeAllCollections(): Flow<List<CollectionEntity>> {
        return collectionDao.observeAllCollections()
    }

    suspend fun addImageToCollection(collectionId: Long, imageId: Long): Boolean = withContext(Dispatchers.IO) {
        sleeveService.addImageToCollection(collectionId, imageId)
    }

    suspend fun removeImageFromCollection(collectionId: Long, imageId: Long): Boolean = withContext(Dispatchers.IO) {
        sleeveService.removeImageFromCollection(collectionId, imageId)
    }

    suspend fun getImagesInCollection(
        collectionId: Long,
        page: Int = 0,
        pageSize: Int = 50
    ): SleeveService.PagedResult<Long> = withContext(Dispatchers.IO) {
        sleeveService.getImagesInCollectionPaginated(collectionId, page, pageSize)
    }

    // ================================================================
    // Filter operations
    // ================================================================

    suspend fun filterByExif(
        filter: ExifFilter,
        page: Int = 0,
        pageSize: Int = 50
    ): SleeveFilterService.FilterResult = withContext(Dispatchers.IO) {
        filterService.filterByExif(filter, page, pageSize)
    }

    suspend fun filterByRating(
        minStars: Int = 0,
        exactRating: Int? = null,
        page: Int = 0,
        pageSize: Int = 50
    ): SleeveFilterService.FilterResult = withContext(Dispatchers.IO) {
        filterService.filterByRating(minStars, exactRating, page, pageSize)
    }

    suspend fun filterByLabels(
        includeLabels: List<String>,
        excludeLabels: List<String> = emptyList(),
        page: Int = 0,
        pageSize: Int = 50
    ): SleeveFilterService.FilterResult = withContext(Dispatchers.IO) {
        filterService.filterByLabels(includeLabels, excludeLabels, page, pageSize)
    }

    suspend fun filterByCollection(
        collectionId: Long,
        page: Int = 0,
        pageSize: Int = 50
    ): SleeveFilterService.FilterResult = withContext(Dispatchers.IO) {
        filterService.filterByCollection(collectionId, page, pageSize)
    }

    suspend fun filterCombined(
        combo: FilterCombo,
        page: Int = 0,
        pageSize: Int = 50
    ): SleeveFilterService.FilterResult = withContext(Dispatchers.IO) {
        filterService.filterCombined(combo, page, pageSize)
    }

    suspend fun filterWithAllOptions(
        options: SleeveFilterService.FullFilterOptions,
        page: Int = 0,
        pageSize: Int = 50
    ): SleeveFilterService.FilterResult = withContext(Dispatchers.IO) {
        filterService.filterWithAllOptions(options, page, pageSize)
    }

    // ================================================================
    // Filter preset operations
    // ================================================================

    suspend fun saveFilterPreset(name: String, combo: FilterCombo, isDefault: Boolean = false): Long =
        withContext(Dispatchers.IO) {
            filterService.saveFilterPreset(name, combo, isDefault)
        }

    suspend fun getAllFilterPresets(): List<FilterPresetEntity> = withContext(Dispatchers.IO) {
        filterService.getAllPresets()
    }

    fun observeAllFilterPresets(): Flow<List<FilterPresetEntity>> {
        return filterDao.observeAllPresets()
    }

    suspend fun deleteFilterPreset(presetId: Long) = withContext(Dispatchers.IO) {
        filterService.deleteFilterPreset(presetId)
    }

    // ================================================================
    // Search operations
    // ================================================================

    suspend fun searchElementsByName(query: String): List<SleeveElement> = withContext(Dispatchers.IO) {
        val entities = elementDao.searchElementsByName(query)
        entities.map { entity ->
            val file = if (entity.elementType == 0) fileDao.getFileByElementId(entity.elementId) else null
            val folder = if (entity.elementType == 1) folderDao.getFolderByElementId(entity.elementId) else null
            entity.toDomain(file, folder)
        }
    }

    fun observeSearchElementsByName(query: String): Flow<List<SleeveElement>> = flow {
        elementDao.observeSearchElementsByName(query).collect { entities ->
            val elements = entities.map { entity ->
                val file = if (entity.elementType == 0) fileDao.getFileByElementId(entity.elementId) else null
                val folder = if (entity.elementType == 1) folderDao.getFolderByElementId(entity.elementId) else null
                entity.toDomain(file, folder)
            }
            emit(elements)
        }
    }

    suspend fun ftsSearchElements(query: String): List<SleeveElement> = withContext(Dispatchers.IO) {
        val sanitized = query.replace("\"", "\"\"").replace("'", "''").trim()
        val entities = if (sanitized.isEmpty()) emptyList()
        else elementDao.ftsSearchElements(SimpleSQLiteQuery(
            "SELECT * FROM element_fts WHERE element_fts MATCH ?",
            arrayOf("\"$sanitized\"")
        ))
        entities.map { entity ->
            val file = if (entity.elementType == 0) fileDao.getFileByElementId(entity.elementId) else null
            val folder = if (entity.elementType == 1) folderDao.getFolderByElementId(entity.elementId) else null
            entity.toDomain(file, folder)
        }
    }

    // ================================================================
    // Facet queries
    // ================================================================

    suspend fun getCameraFacets(): List<CameraFacet> = withContext(Dispatchers.IO) {
        filterService.getCameraFacets()
    }

    suspend fun getLensFacets(): List<LensFacet> = withContext(Dispatchers.IO) {
        filterService.getLensFacets()
    }

    suspend fun getDateFacets(): List<DateFacet> = withContext(Dispatchers.IO) {
        filterService.getDateFacets()
    }

    suspend fun getLabelFrequency(limit: Int = 50): List<LabelFrequency> = withContext(Dispatchers.IO) {
        filterService.getLabelFrequency(limit)
    }

    // ================================================================
    // Path resolution
    // ================================================================

    suspend fun resolvePath(elementId: Long): String = withContext(Dispatchers.IO) {
        pathResolver.resolvePath(elementId)
    }

    suspend fun resolvePathToId(path: String): Long? = withContext(Dispatchers.IO) {
        pathResolver.resolvePathToId(path)
    }

    // ================================================================
    // Cache management
    // ================================================================

    suspend fun invalidateCacheForParent(parentId: Long) {
        sleeveService.invalidateCacheForParent(parentId)
    }

    suspend fun clearAllCache() {
        sleeveService.clearAllCache()
    }

    fun getCacheStats(): DentryCacheManager.CacheStats = cacheManager.getStats()

    // ================================================================
    // Statistics
    // ================================================================

    suspend fun getStatistics(): SleeveService.SleeveStatistics = withContext(Dispatchers.IO) {
        sleeveService.getStatistics()
    }

    // ================================================================
    // Batch operations
    // ================================================================

    suspend fun batchDelete(elementIds: List<Long>): Int = withContext(Dispatchers.IO) {
        sleeveService.batchDelete(elementIds)
    }

    suspend fun batchMove(elementIds: List<Long>, newParentId: Long?): Int = withContext(Dispatchers.IO) {
        sleeveService.batchMove(elementIds, newParentId)
    }

    // ================================================================
    // Labelling
    // ================================================================

    suspend fun addLabelsToImage(imageId: Long, labels: List<SemanticLabelEntity>) = withContext(Dispatchers.IO) {
        labelDao.insertLabels(labels)
    }

    suspend fun getLabelsForImage(imageId: Long): List<SemanticLabelEntity> = withContext(Dispatchers.IO) {
        labelDao.getLabelsByImageId(imageId)
    }

    suspend fun deleteLabelsForImage(imageId: Long) = withContext(Dispatchers.IO) {
        labelDao.deleteLabelsByImageId(imageId)
    }
}