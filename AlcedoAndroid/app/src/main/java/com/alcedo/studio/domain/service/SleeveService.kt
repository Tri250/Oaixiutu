package com.alcedo.studio.domain.service

import androidx.sqlite.db.SimpleSQLiteQuery
import com.alcedo.studio.data.local.*
import com.alcedo.studio.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

/**
 * Core Sleeve file system service.
 * Handles all file system operations: create, delete, move, rename elements,
 * folder hierarchy management, image import, batch operations, collection management,
 * pagination, and cache invalidation.
 */
class SleeveService(
    private val elementDao: SleeveElementDao,
    private val fileDao: SleeveFileDao,
    private val folderDao: SleeveFolderDao,
    private val metadataDao: ImageMetadataDao,
    private val collectionDao: CollectionDao,
    private val ratingDao: RatingDao,
    private val labelDao: SemanticLabelDao,
    private val pathResolver: PathResolver,
    private val cacheManager: DentryCacheManager
) {
    // ================================================================
    // Element CRUD
    // ================================================================

    suspend fun createElement(
        name: String,
        type: ElementType,
        parentId: Long? = null,
        imageId: Long? = null,
        filePath: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val elementId = generateElementId()
        val now = System.currentTimeMillis()

        val element = SleeveElementEntity(
            elementId = elementId,
            elementName = name,
            elementType = if (type == ElementType.FILE) 0 else 1,
            parentId = parentId,
            addedTime = now,
            lastModifiedTime = now
        )

        elementDao.insertElement(element)

        // Insert into FTS
        elementDao.insertFts(ElementFts(elementId = elementId, elementName = name))

        if (type == ElementType.FILE) {
            fileDao.insertFile(
                SleeveFileEntity(
                    elementId = elementId,
                    imageId = imageId ?: elementId,
                    filePath = filePath ?: "",
                    fileExtension = filePath?.substringAfterLast('.', "") ?: ""
                )
            )
        } else {
            folderDao.insertFolder(
                SleeveFolderEntity(elementId = elementId)
            )
        }

        // Update parent folder counts
        parentId?.let { folderDao.recalculateCounts(it) }

        // Invalidate cache
        parentId?.let { cacheManager.invalidate(it) }
        cacheManager.invalidateChain(parentId ?: 0, elementDao)

        elementId
    }

    suspend fun deleteElement(elementId: Long): Boolean = withContext(Dispatchers.IO) {
        val element = elementDao.getElementById(elementId) ?: return@withContext false
        val parentId = element.parentId

        if (element.elementType == 1) {
            // Delete children recursively
            deleteChildrenRecursive(elementId)
        }

        // Delete from FTS
        elementDao.deleteFts(elementId)

        // Delete element
        elementDao.deleteElementById(elementId)

        // Update parent folder counts
        parentId?.let { folderDao.recalculateCounts(it) }

        // Invalidate cache
        parentId?.let { cacheManager.invalidate(it) }
        cacheManager.invalidateChain(parentId ?: 0, elementDao)

        true
    }

    private suspend fun deleteChildrenRecursive(parentId: Long) {
        val children = elementDao.getChildrenByParentId(parentId)
        for (child in children) {
            if (child.elementType == 1) {
                deleteChildrenRecursive(child.elementId)
            }
            elementDao.deleteFts(child.elementId)
            elementDao.deleteElementById(child.elementId)
        }
    }

    suspend fun moveElement(elementId: Long, newParentId: Long?): Boolean = withContext(Dispatchers.IO) {
        val element = elementDao.getElementById(elementId) ?: return@withContext false
        val oldParentId = element.parentId

        elementDao.moveElement(elementId, newParentId)

        // Recalculate counts for both old and new parent
        oldParentId?.let { folderDao.recalculateCounts(it) }
        newParentId?.let { folderDao.recalculateCounts(it) }

        // Invalidate caches
        oldParentId?.let { cacheManager.invalidate(it) }
        newParentId?.let { cacheManager.invalidate(it) }
        cacheManager.invalidateChain(oldParentId ?: 0, elementDao)
        cacheManager.invalidateChain(newParentId ?: 0, elementDao)

        true
    }

    suspend fun renameElement(elementId: Long, newName: String): Boolean = withContext(Dispatchers.IO) {
        val element = elementDao.getElementById(elementId) ?: return@withContext false
        elementDao.renameElement(elementId, newName)

        // Update FTS
        elementDao.deleteFts(elementId)
        elementDao.insertFts(ElementFts(elementId = elementId, elementName = newName))

        // Invalidate parent cache
        element.parentId?.let { cacheManager.invalidate(it) }

        true
    }

    // ================================================================
    // Folder operations
    // ================================================================

    suspend fun createFolder(name: String, parentId: Long? = null): Long = withContext(Dispatchers.IO) {
        createElement(name, ElementType.FOLDER, parentId)
    }

    suspend fun getFolderContents(parentId: Long): List<SleeveElement> = withContext(Dispatchers.IO) {
        // Check cache first
        cacheManager.get(parentId)?.let { cached ->
            return@withContext cached.map { entity ->
                val file = if (entity.elementType == 0) fileDao.getFileByElementId(entity.elementId) else null
                val folder = if (entity.elementType == 1) folderDao.getFolderByElementId(entity.elementId) else null
                entity.toDomain(file, folder)
            }
        }

        val elements = elementDao.getChildrenByParentId(parentId)
        cacheManager.put(parentId, elements)

        elements.map { entity ->
            val file = if (entity.elementType == 0) fileDao.getFileByElementId(entity.elementId) else null
            val folder = if (entity.elementType == 1) folderDao.getFolderByElementId(entity.elementId) else null
            entity.toDomain(file, folder)
        }
    }

    suspend fun getFolderContentsPaginated(
        parentId: Long,
        page: Int,
        pageSize: Int = 50
    ): PagedResult<SleeveElement> = withContext(Dispatchers.IO) {
        val offset = page * pageSize
        val elements = elementDao.getChildrenByParentIdPaginated(parentId, pageSize, offset)
        val totalCount = elementDao.getChildCount(parentId)

        val domainElements = elements.map { entity ->
            val file = if (entity.elementType == 0) fileDao.getFileByElementId(entity.elementId) else null
            val folder = if (entity.elementType == 1) folderDao.getFolderByElementId(entity.elementId) else null
            entity.toDomain(file, folder)
        }

        PagedResult(
            items = domainElements,
            totalCount = totalCount,
            page = page,
            pageSize = pageSize
        )
    }

    suspend fun observeFolderContents(parentId: Long): Flow<List<SleeveElementEntity>> {
        return elementDao.observeChildrenByParentId(parentId)
    }

    suspend fun getFolderTree(rootId: Long): List<SleeveFolderEntity> = withContext(Dispatchers.IO) {
        folderDao.getFolderTree(rootId)
    }

    suspend fun getFolderPath(folderId: Long): String = withContext(Dispatchers.IO) {
        val names = folderDao.getFolderPath(folderId)
        "/" + names.joinToString("/")
    }

    // ================================================================
    // File operations
    // ================================================================

    suspend fun getFileByPath(path: String): SleeveFileEntity? = withContext(Dispatchers.IO) {
        fileDao.getFileByPath(path)
    }

    suspend fun getFilesByExtension(extension: String): List<SleeveFileEntity> = withContext(Dispatchers.IO) {
        fileDao.getFilesByExtension(extension)
    }

    suspend fun getFilesInFolderByExtension(parentId: Long, extensions: List<String>): List<SleeveFileEntity> =
        withContext(Dispatchers.IO) {
            fileDao.getFilesInFolderByExtension(parentId, extensions)
        }

    // ================================================================
    // Image import with metadata
    // ================================================================

    suspend fun importImage(
        imageMetadata: ImageMetadataEntity,
        parentId: Long?
    ): Long = withContext(Dispatchers.IO) {
        // Insert metadata
        metadataDao.insertMetadata(imageMetadata)

        // Create file element
        val elementId = createElement(
            name = imageMetadata.imageName,
            type = ElementType.FILE,
            parentId = parentId,
            imageId = imageMetadata.imageId,
            filePath = imageMetadata.imagePath
        )

        // Update file entity with metadata
        fileDao.getFileByElementId(elementId)?.let { existing ->
            fileDao.updateFile(
                existing.copy(
                    fileSize = imageMetadata.fileSize,
                    fileExtension = imageMetadata.imagePath.substringAfterLast('.', ""),
                    mimeType = imageMetadata.mimeType,
                    checksum = imageMetadata.checksum,
                    width = imageMetadata.width,
                    height = imageMetadata.height
                )
            )
        }

        elementId
    }

    suspend fun importImagesBatch(
        images: List<ImageMetadataEntity>,
        parentId: Long?
    ): List<Long> = withContext(Dispatchers.IO) {
        metadataDao.insertMetadataBatch(images)
        images.map { image ->
            createElement(
                name = image.imageName,
                type = ElementType.FILE,
                parentId = parentId,
                imageId = image.imageId,
                filePath = image.imagePath
            )
        }
    }

    // ================================================================
    // Rating operations
    // ================================================================

    suspend fun setRating(imageId: Long, rating: Int): Boolean = withContext(Dispatchers.IO) {
        ratingDao.insertRating(
            RatingEntity(imageId = imageId, rating = rating.coerceIn(0, 5))
        )
        metadataDao.updateRating(imageId, rating.coerceIn(0, 5))
        true
    }

    suspend fun getRatingDistribution(): List<RatingDistributionResult> = withContext(Dispatchers.IO) {
        ratingDao.getRatingDistribution()
    }

    suspend fun getTopRated(limit: Int = 50): List<ImageMetadataEntity> = withContext(Dispatchers.IO) {
        metadataDao.getTopRated(limit)
    }

    // ================================================================
    // Collection operations
    // ================================================================

    suspend fun createCollection(name: String, description: String = ""): Long = withContext(Dispatchers.IO) {
        collectionDao.insertCollection(
            CollectionEntity(collectionName = name, description = description)
        )
    }

    suspend fun deleteCollection(collectionId: Long): Boolean = withContext(Dispatchers.IO) {
        collectionDao.deleteCollection(collectionId)
        true
    }

    suspend fun addImageToCollection(collectionId: Long, imageId: Long): Boolean = withContext(Dispatchers.IO) {
        collectionDao.addImageToCollection(
            CollectionImageEntity(collectionId = collectionId, imageId = imageId)
        )
        collectionDao.touchCollection(collectionId)
        true
    }

    suspend fun removeImageFromCollection(collectionId: Long, imageId: Long): Boolean = withContext(Dispatchers.IO) {
        collectionDao.removeImageFromCollection(collectionId, imageId)
        collectionDao.touchCollection(collectionId)
        true
    }

    suspend fun addImagesToCollection(collectionId: Long, imageIds: List<Long>): Boolean = withContext(Dispatchers.IO) {
        val mappings = imageIds.map { CollectionImageEntity(collectionId = collectionId, imageId = it) }
        collectionDao.addImagesToCollection(mappings)
        collectionDao.touchCollection(collectionId)
        true
    }

    suspend fun getCollectionsForImage(imageId: Long): List<CollectionEntity> = withContext(Dispatchers.IO) {
        val ids = collectionDao.getCollectionIdsForImage(imageId)
        collectionDao.getCollectionsByIds(ids)
    }

    suspend fun getImagesInCollection(collectionId: Long): List<Long> = withContext(Dispatchers.IO) {
        collectionDao.getImageIdsInCollection(collectionId)
    }

    suspend fun getImagesInCollectionPaginated(
        collectionId: Long,
        page: Int,
        pageSize: Int = 50
    ): PagedResult<Long> = withContext(Dispatchers.IO) {
        val offset = page * pageSize
        val imageIds = collectionDao.getImageIdsInCollectionPaginated(collectionId, pageSize, offset)
        val totalCount = collectionDao.getImageCountInCollection(collectionId)
        PagedResult(items = imageIds, totalCount = totalCount, page = page, pageSize = pageSize)
    }

    // ================================================================
    // Batch operations
    // ================================================================

    suspend fun batchDelete(elementIds: List<Long>): Int = withContext(Dispatchers.IO) {
        var count = 0
        val affectedParents = mutableSetOf<Long>()

        for (id in elementIds) {
            val element = elementDao.getElementById(id) ?: continue
            element.parentId?.let { affectedParents.add(it) }

            if (deleteElement(id)) count++
        }

        // Recalculate counts for affected parents
        for (parentId in affectedParents) {
            folderDao.recalculateCounts(parentId)
        }

        // Batch invalidate caches
        cacheManager.invalidateBatch(affectedParents.toList())

        count
    }

    suspend fun batchMove(elementIds: List<Long>, newParentId: Long?): Int = withContext(Dispatchers.IO) {
        var count = 0
        val affectedParents = mutableSetOf<Long>()

        for (id in elementIds) {
            val element = elementDao.getElementById(id) ?: continue
            element.parentId?.let { affectedParents.add(it) }
            if (moveElement(id, newParentId)) count++
        }

        newParentId?.let { affectedParents.add(it) }

        for (parentId in affectedParents) {
            folderDao.recalculateCounts(parentId)
        }

        cacheManager.invalidateBatch(affectedParents.toList())

        count
    }

    // ================================================================
    // Cache invalidation
    // ================================================================

    suspend fun invalidateCacheForParent(parentId: Long) {
        cacheManager.invalidate(parentId)
    }

    suspend fun invalidateCacheForElement(elementId: Long) {
        val element = elementDao.getElementById(elementId)
        element?.parentId?.let { cacheManager.invalidate(it) }
    }

    suspend fun invalidateExpiredCache() {
        cacheManager.invalidateExpired()
    }

    suspend fun clearAllCache() {
        cacheManager.invalidateAll()
    }

    fun getCacheStats(): DentryCacheManager.CacheStats = cacheManager.getStats()

    // ================================================================
    // Search
    // ================================================================

    suspend fun searchElementsByName(query: String): List<SleeveElementEntity> = withContext(Dispatchers.IO) {
        elementDao.searchElementsByName(query)
    }

    suspend fun ftsSearchElements(query: String): List<SleeveElementEntity> = withContext(Dispatchers.IO) {
        elementDao.ftsSearchElements(SimpleSQLiteQuery(query, null))
    }

    // ================================================================
    // Statistics
    // ================================================================

    suspend fun getStatistics(): SleeveStatistics = withContext(Dispatchers.IO) {
        SleeveStatistics(
            totalElements = elementDao.getTotalElementCount(),
            totalFiles = elementDao.getTotalFileCount(),
            totalFolders = elementDao.getTotalFolderCount(),
            totalImages = metadataDao.getMetadataCount(),
            totalCollections = collectionDao.getCollectionCount(),
            ratedImages = ratingDao.getRatedImageCount(),
            averageRating = ratingDao.getAverageRating() ?: 0f,
            distinctLabels = labelDao.getDistinctLabelCount()
        )
    }

    // ================================================================
    // ID generation
    // ================================================================

    private fun generateElementId(): Long {
        return (System.nanoTime() / 1000) + (Math.random() * 1000).toLong()
    }

    // ================================================================
    // Data classes
    // ================================================================

    data class PagedResult<T>(
        val items: List<T>,
        val totalCount: Int,
        val page: Int,
        val pageSize: Int
    ) {
        val hasMore: Boolean get() = (page + 1) * pageSize < totalCount
        val totalPages: Int get() = if (totalCount == 0) 0 else (totalCount + pageSize - 1) / pageSize
    }

    data class SleeveStatistics(
        val totalElements: Int,
        val totalFiles: Int,
        val totalFolders: Int,
        val totalImages: Int,
        val totalCollections: Int,
        val ratedImages: Int,
        val averageRating: Float,
        val distinctLabels: Int
    )
}