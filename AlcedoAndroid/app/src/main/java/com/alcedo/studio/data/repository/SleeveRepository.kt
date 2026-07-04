package com.alcedo.studio.data.repository

import com.alcedo.studio.data.dao.*
import com.alcedo.studio.data.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Repository that mediates between DAOs and ViewModel layer.
 * Provides high-level operations combining multiple DAOs with business logic.
 */
class SleeveRepository(
    private val sleeveElementDao: SleeveElementDao,
    private val imageDao: ImageDao,
    private val editHistoryDao: EditHistoryDao,
    private val pipelinePresetDao: PipelinePresetDao,
    private val aiEmbeddingDao: AiEmbeddingDao
) {

    // ==================== Sleeve Element Operations ====================

    suspend fun getElementById(elementId: Long): SleeveElementEntity? {
        return sleeveElementDao.getById(elementId)
    }

    fun getElementByIdFlow(elementId: Long): Flow<SleeveElementEntity?> {
        return sleeveElementDao.getByIdFlow(elementId)
    }

    suspend fun getElementsInFolder(parentId: Long): List<SleeveElementEntity> {
        return sleeveElementDao.getByParent(parentId)
    }

    fun getElementsInFolderFlow(parentId: Long): Flow<List<SleeveElementEntity>> {
        return sleeveElementDao.getByParentFlow(parentId)
    }

    suspend fun getSubFolders(parentId: Long): List<SleeveElementEntity> {
        return sleeveElementDao.getSubFolders(parentId)
    }

    suspend fun getFilesInFolder(parentId: Long): List<SleeveElementEntity> {
        return sleeveElementDao.getFilesInFolder(parentId)
    }

    suspend fun countChildren(parentId: Long): Int {
        return sleeveElementDao.countChildren(parentId)
    }

    suspend fun createFolder(name: String, parentId: Long): Long {
        val now = System.currentTimeMillis()
        val elementId = generateFolderId()
        val element = SleeveElementEntity(
            elementId = elementId,
            elementName = name,
            elementType = 2,
            parentId = parentId,
            addedTime = now,
            lastModifiedTime = now,
            syncFlag = 1
        )
        return sleeveElementDao.insert(element)
    }

    suspend fun createFileElement(name: String, parentId: Long, imageId: Long): Long {
        val now = System.currentTimeMillis()
        val elementId = generateFileId()
        val element = SleeveElementEntity(
            elementId = elementId,
            elementName = name,
            elementType = 1,
            parentId = parentId,
            addedTime = now,
            lastModifiedTime = now,
            syncFlag = 1,
            imageId = imageId
        )
        return sleeveElementDao.insert(element)
    }

    suspend fun renameElement(elementId: Long, newName: String) {
        val element = sleeveElementDao.getById(elementId) ?: return
        sleeveElementDao.update(element.copy(
            elementName = newName,
            lastModifiedTime = System.currentTimeMillis(),
            syncFlag = 1
        ))
    }

    suspend fun moveElement(elementId: Long, newParentId: Long) {
        val element = sleeveElementDao.getById(elementId) ?: return
        sleeveElementDao.update(element.copy(
            parentId = newParentId,
            lastModifiedTime = System.currentTimeMillis(),
            syncFlag = 1
        ))
    }

    suspend fun deleteElement(elementId: Long) {
        // Soft delete: mark as deleted
        sleeveElementDao.updateSyncFlag(elementId, 2)
    }

    suspend fun permanentlyDeleteElement(elementId: Long) {
        // Recursively delete children if it's a folder
        val element = sleeveElementDao.getById(elementId) ?: return
        if (element.elementType == 2) {
            val children = sleeveElementDao.getByParent(elementId)
            for (child in children) {
                permanentlyDeleteElement(child.elementId)
            }
        }
        // Delete associated image and history
        if (element.imageId > 0) {
            editHistoryDao.deleteByImageId(element.imageId)
            aiEmbeddingDao.deleteByImageId(element.imageId)
            imageDao.deleteById(element.imageId)
        }
        sleeveElementDao.deleteById(elementId)
    }

    suspend fun searchElements(query: String): List<SleeveElementEntity> {
        return sleeveElementDao.search(query)
    }

    suspend fun searchInFolder(query: String, parentId: Long): List<SleeveElementEntity> {
        return sleeveElementDao.searchInFolder(query, parentId)
    }

    suspend fun getUnsyncedElements(): List<SleeveElementEntity> {
        return sleeveElementDao.getBySyncFlag(1)
    }

    suspend fun getDeletedElements(): List<SleeveElementEntity> {
        return sleeveElementDao.getBySyncFlag(2)
    }

    suspend fun purgeDeletedElements() {
        val deleted = sleeveElementDao.getBySyncFlag(2)
        for (element in deleted) {
            permanentlyDeleteElement(element.elementId)
        }
    }

    suspend fun markAsSynced(elementId: Long) {
        sleeveElementDao.updateSyncFlag(elementId, 3)
    }

    // ==================== Image Operations ====================

    suspend fun getImageById(id: Long): ImageEntity? {
        return imageDao.getById(id)
    }

    fun getImageByIdFlow(id: Long): Flow<ImageEntity?> {
        return imageDao.getByIdFlow(id)
    }

    suspend fun getAllImages(): List<ImageEntity> {
        return imageDao.getAll()
    }

    fun getAllImagesFlow(): Flow<List<ImageEntity>> {
        return imageDao.getAllFlow()
    }

    suspend fun getImagesPaged(limit: Int, offset: Int): List<ImageEntity> {
        return imageDao.getAllPaged(limit, offset)
    }

    suspend fun searchImages(query: String): List<ImageEntity> {
        return imageDao.searchByName(query)
    }

    suspend fun getImagesByDateRange(startDate: Long, endDate: Long): List<ImageEntity> {
        return imageDao.getByDateRange(startDate, endDate)
    }

    suspend fun getImagesByRating(rating: Int): List<ImageEntity> {
        return imageDao.getByRating(rating)
    }

    suspend fun getImagesByMinRating(minRating: Int): List<ImageEntity> {
        return imageDao.getByMinRating(minRating)
    }

    suspend fun getImagesByColorLabel(colorLabel: Int): List<ImageEntity> {
        return imageDao.getByColorLabel(colorLabel)
    }

    suspend fun getRawImages(): List<ImageEntity> {
        return imageDao.getRawImages()
    }

    fun getRawImagesFlow(): Flow<List<ImageEntity>> {
        return imageDao.getRawImagesFlow()
    }

    suspend fun getImagesByCameraMake(make: String): List<ImageEntity> {
        return imageDao.getByCameraMake(make)
    }

    suspend fun getImagesByCameraModel(model: String): List<ImageEntity> {
        return imageDao.getByCameraModel(model)
    }

    suspend fun importImage(image: ImageEntity): Long {
        val existing = imageDao.getByFilePath(image.filePath)
        if (existing != null) {
            imageDao.update(image.copy(id = existing.id))
            return existing.id
        }
        return imageDao.insert(image)
    }

    suspend fun importImages(images: List<ImageEntity>): List<Long> {
        return imageDao.insertAll(images)
    }

    suspend fun updateImageRating(imageId: Long, rating: Int) {
        val image = imageDao.getById(imageId) ?: return
        imageDao.updateRating(imageId, rating)
    }

    suspend fun updateImageColorLabel(imageId: Long, colorLabel: Int) {
        val image = imageDao.getById(imageId) ?: return
        imageDao.updateColorLabel(imageId, colorLabel)
    }

    suspend fun updateImageThumbnail(imageId: Long, thumbnailPath: String) {
        val image = imageDao.getById(imageId) ?: return
        imageDao.updateThumbnail(imageId, thumbnailPath)
    }

    suspend fun deleteImage(imageId: Long) {
        // Also delete associated data
        editHistoryDao.deleteByImageId(imageId)
        aiEmbeddingDao.deleteByImageId(imageId)
        imageDao.deleteById(imageId)
    }

    suspend fun imageCount(): Int {
        return imageDao.count()
    }

    // ==================== Edit History Operations ====================

    suspend fun getEditHistoryForImage(imageId: Long): List<EditHistoryEntity> {
        return editHistoryDao.getVersionsByImageId(imageId)
    }

    fun getEditHistoryForImageFlow(imageId: Long): Flow<List<EditHistoryEntity>> {
        return editHistoryDao.getVersionsByImageIdFlow(imageId)
    }

    suspend fun getActiveVersion(imageId: Long): EditHistoryEntity? {
        return editHistoryDao.getActiveVersion(imageId)
    }

    suspend fun getVersion(imageId: Long, versionId: String): EditHistoryEntity? {
        return editHistoryDao.getByVersionId(imageId, versionId)
    }

    suspend fun createVersion(
        imageId: Long,
        versionId: String,
        parentId: String = "",
        name: String = "",
        paramsJson: String = "{}"
    ): Long {
        // Deactivate current active version
        editHistoryDao.deactivateAllVersions(imageId)
        val entity = EditHistoryEntity(
            imageId = imageId,
            versionId = versionId,
            parentId = parentId,
            createdTime = System.currentTimeMillis(),
            name = name,
            isActive = true,
            paramsJson = paramsJson
        )
        val id = editHistoryDao.insert(entity)
        // Update current version on sleeve element
        val element = findElementByImageId(imageId)
        if (element != null) {
            sleeveElementDao.updateCurrentVersion(element.elementId, versionId)
        }
        return id
    }

    suspend fun switchActiveVersion(imageId: Long, versionId: String) {
        editHistoryDao.deactivateAllVersions(imageId)
        editHistoryDao.setActiveVersion(imageId, versionId)
        val element = findElementByImageId(imageId)
        if (element != null) {
            sleeveElementDao.updateCurrentVersion(element.elementId, versionId)
        }
    }

    suspend fun updateVersionParams(imageId: Long, versionId: String, paramsJson: String) {
        editHistoryDao.updateParams(imageId, versionId, paramsJson)
    }

    suspend fun updateVersionName(imageId: Long, versionId: String, name: String) {
        editHistoryDao.updateVersionName(imageId, versionId, name)
    }

    suspend fun deleteVersion(imageId: Long, versionId: String) {
        editHistoryDao.deleteByVersionId(imageId, versionId)
    }

    suspend fun initDefaultHistory(imageId: Long): Long {
        return createVersion(
            imageId = imageId,
            versionId = "v0",
            name = "Original"
        )
    }

    // ==================== Pipeline Preset Operations ====================

    suspend fun getPresetById(id: Long): PipelinePresetEntity? {
        return pipelinePresetDao.getById(id)
    }

    suspend fun getAllPresets(): List<PipelinePresetEntity> {
        return pipelinePresetDao.getAll()
    }

    fun getAllPresetsFlow(): Flow<List<PipelinePresetEntity>> {
        return pipelinePresetDao.getAllFlow()
    }

    suspend fun getPresetsByCategory(category: String): List<PipelinePresetEntity> {
        return pipelinePresetDao.getByCategory(category)
    }

    fun getPresetsByCategoryFlow(category: String): Flow<List<PipelinePresetEntity>> {
        return pipelinePresetDao.getByCategoryFlow(category)
    }

    suspend fun getBuiltInPresets(): List<PipelinePresetEntity> {
        return pipelinePresetDao.getBuiltInPresets()
    }

    fun getBuiltInPresetsFlow(): Flow<List<PipelinePresetEntity>> {
        return pipelinePresetDao.getBuiltInPresetsFlow()
    }

    suspend fun getUserPresets(): List<PipelinePresetEntity> {
        return pipelinePresetDao.getUserPresets()
    }

    suspend fun searchPresets(query: String): List<PipelinePresetEntity> {
        return pipelinePresetDao.searchByName(query)
    }

    suspend fun searchPresetsInCategory(query: String, category: String): List<PipelinePresetEntity> {
        return pipelinePresetDao.searchByNameInCategory(query, category)
    }

    suspend fun getAllCategories(): List<String> {
        return pipelinePresetDao.getAllCategories()
    }

    suspend fun createPreset(
        name: String,
        category: String,
        paramsJson: String,
        isBuiltIn: Boolean = false
    ): Long {
        val preset = PipelinePresetEntity(
            name = name,
            category = category,
            paramsJson = paramsJson,
            createdTime = System.currentTimeMillis(),
            isBuiltIn = isBuiltIn
        )
        return pipelinePresetDao.insert(preset)
    }

    suspend fun updatePreset(preset: PipelinePresetEntity) {
        pipelinePresetDao.update(preset)
    }

    suspend fun deletePreset(id: Long) {
        pipelinePresetDao.deleteById(id)
    }

    suspend fun deleteUserPresets() {
        pipelinePresetDao.deleteAllUserPresets()
    }

    // ==================== AI Embedding Operations ====================

    suspend fun getEmbeddingById(id: Long): AiEmbeddingEntity? {
        return aiEmbeddingDao.getById(id)
    }

    suspend fun getEmbeddingByImageId(imageId: Long): AiEmbeddingEntity? {
        return aiEmbeddingDao.getByImageId(imageId)
    }

    fun getEmbeddingByImageIdFlow(imageId: Long): Flow<AiEmbeddingEntity?> {
        return aiEmbeddingDao.getByImageIdFlow(imageId)
    }

    suspend fun getAllEmbeddings(): List<AiEmbeddingEntity> {
        return aiEmbeddingDao.getAll()
    }

    fun getAllEmbeddingsFlow(): Flow<List<AiEmbeddingEntity>> {
        return aiEmbeddingDao.getAllFlow()
    }

    suspend fun getEmbeddingsPaged(limit: Int, offset: Int): List<AiEmbeddingEntity> {
        return aiEmbeddingDao.getAllPaged(limit, offset)
    }

    suspend fun getEmbeddingsByModel(modelVersion: String): List<AiEmbeddingEntity> {
        return aiEmbeddingDao.getByModelVersion(modelVersion)
    }

    suspend fun getEmbeddingsByModelPaged(modelVersion: String, limit: Int, offset: Int): List<AiEmbeddingEntity> {
        return aiEmbeddingDao.getByModelVersionPaged(modelVersion, limit, offset)
    }

    suspend fun getAllEmbeddedImageIds(): List<Long> {
        return aiEmbeddingDao.getAllEmbeddedImageIds()
    }

    suspend fun hasEmbedding(imageId: Long): Boolean {
        return aiEmbeddingDao.countByImageId(imageId) > 0
    }

    suspend fun insertEmbedding(embedding: AiEmbeddingEntity): Long {
        return aiEmbeddingDao.insert(embedding)
    }

    suspend fun insertEmbeddings(embeddings: List<AiEmbeddingEntity>): List<Long> {
        return aiEmbeddingDao.insertAll(embeddings)
    }

    suspend fun deleteEmbeddingByImageId(imageId: Long) {
        aiEmbeddingDao.deleteByImageId(imageId)
    }

    suspend fun deleteEmbeddingsByModel(modelVersion: String) {
        aiEmbeddingDao.deleteByModelVersion(modelVersion)
    }

    suspend fun embeddingCount(): Int {
        return aiEmbeddingDao.count()
    }

    // ==================== Combined Operations ====================

    /**
     * Get image with its full edit history.
     */
    suspend fun getImageWithHistory(imageId: Long): Pair<ImageEntity, List<EditHistoryEntity>>? {
        val image = imageDao.getById(imageId) ?: return null
        val history = editHistoryDao.getVersionsByImageId(imageId)
        return Pair(image, history)
    }

    /**
     * Full import: create element + image + default history version.
     */
    suspend fun importImageWithElement(
        fileName: String,
        filePath: String,
        fileSize: Long,
        width: Int,
        height: Int,
        mimeType: String,
        parentId: Long,
        isRaw: Boolean = false,
        rawMake: String = "",
        rawModel: String = "",
        iso: Int = 0,
        exposureTime: Double = 0.0,
        fNumber: Double = 0.0,
        focalLength: Double = 0.0
    ): Long {
        val now = System.currentTimeMillis()

        // Insert image
        val image = ImageEntity(
            filePath = filePath,
            fileName = fileName,
            fileSize = fileSize,
            width = width,
            height = height,
            mimeType = mimeType,
            dateAdded = now,
            dateModified = now,
            isRaw = isRaw,
            rawMake = rawMake,
            rawModel = rawModel,
            iso = iso,
            exposureTime = exposureTime,
            fNumber = fNumber,
            focalLength = focalLength
        )
        val imageId = imageDao.insert(image)

        // Create sleeve element
        val elementId = createFileElement(fileName, parentId, imageId)

        // Init default edit history
        initDefaultHistory(imageId)

        // Update element with version
        sleeveElementDao.updateCurrentVersion(elementId, "v0")

        return imageId
    }

    /**
     * Full delete: remove element, image, history, and embeddings.
     */
    suspend fun deleteImageCompletely(imageId: Long) {
        val element = findElementByImageId(imageId)
        if (element != null) {
            sleeveElementDao.deleteById(element.elementId)
        }
        editHistoryDao.deleteByImageId(imageId)
        aiEmbeddingDao.deleteByImageId(imageId)
        imageDao.deleteById(imageId)
    }

    /**
     * Batch import images into a folder.
     */
    suspend fun batchImportImages(
        parentId: Long,
        imageDataList: List<ImageImportData>
    ): List<Long> {
        val imageIds = mutableListOf<Long>()
        for (data in imageDataList) {
            val id = importImageWithElement(
                fileName = data.fileName,
                filePath = data.filePath,
                fileSize = data.fileSize,
                width = data.width,
                height = data.height,
                mimeType = data.mimeType,
                parentId = parentId,
                isRaw = data.isRaw,
                rawMake = data.rawMake,
                rawModel = data.rawModel,
                iso = data.iso,
                exposureTime = data.exposureTime,
                fNumber = data.fNumber,
                focalLength = data.focalLength
            )
            imageIds.add(id)
        }
        return imageIds
    }

    // ==================== Helper Methods ====================

    private suspend fun findElementByImageId(imageId: Long): SleeveElementEntity? {
        if (imageId <= 0) return null
        val allElements = sleeveElementDao.getAll()
        return allElements.find { it.imageId == imageId }
    }

    private fun generateFolderId(): Long {
        return 2000000000L + (System.currentTimeMillis() % 1000000000L)
    }

    private fun generateFileId(): Long {
        return System.currentTimeMillis()
    }
}

/**
 * Data class for batch image import parameters.
 */
data class ImageImportData(
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val isRaw: Boolean = false,
    val rawMake: String = "",
    val rawModel: String = "",
    val iso: Int = 0,
    val exposureTime: Double = 0.0,
    val fNumber: Double = 0.0,
    val focalLength: Double = 0.0
)
