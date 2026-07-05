package com.alcedo.studio.data.repository

import com.alcedo.studio.data.local.*
import com.alcedo.studio.data.model.*
import com.alcedo.studio.data.dao.EditHistoryDao
import com.alcedo.studio.data.dao.PipelinePresetDao
import com.alcedo.studio.data.dao.AiEmbeddingDao
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
        return sleeveElementDao.getElementById(elementId)
    }

    fun observeElementById(elementId: Long): Flow<SleeveElementEntity?> {
        return sleeveElementDao.observeElementById(elementId)
    }

    suspend fun getElementsInFolder(parentId: Long): List<SleeveElementEntity> {
        return sleeveElementDao.getChildrenByParentId(parentId)
    }

    fun observeChildrenByParentId(parentId: Long): Flow<List<SleeveElementEntity>> {
        return sleeveElementDao.observeChildrenByParentId(parentId)
    }

    suspend fun getSubFolders(parentId: Long): List<SleeveElementEntity> {
        return sleeveElementDao.getElementsByType(1)
    }

    suspend fun getFilesInFolder(parentId: Long): List<SleeveElementEntity> {
        return sleeveElementDao.getChildrenByParentId(parentId).filter { it.elementType == 0 }
    }

    suspend fun countChildren(parentId: Long): Int {
        return sleeveElementDao.getChildCount(parentId)
    }

    suspend fun createFolder(name: String, parentId: Long): Long {
        val now = System.currentTimeMillis()
        val elementId = generateFolderId()
        val element = SleeveElementEntity(
            elementId = elementId,
            elementName = name,
            elementType = 1,
            parentId = parentId,
            addedTime = now,
            lastModifiedTime = now,
            syncFlag = 1
        )
        return sleeveElementDao.insertElement(element)
    }

    suspend fun createFileElement(name: String, parentId: Long, imageId: Long): Long {
        val now = System.currentTimeMillis()
        val elementId = generateFileId()
        val element = SleeveElementEntity(
            elementId = elementId,
            elementName = name,
            elementType = 0,
            parentId = parentId,
            addedTime = now,
            lastModifiedTime = now,
            syncFlag = 1
        )
        return sleeveElementDao.insertElement(element)
    }

    suspend fun renameElement(elementId: Long, newName: String) {
        sleeveElementDao.renameElement(elementId, newName)
    }

    suspend fun moveElement(elementId: Long, newParentId: Long) {
        sleeveElementDao.moveElement(elementId, newParentId)
    }

    suspend fun deleteElement(elementId: Long) {
        // Soft delete: mark as deleted
        sleeveElementDao.setSyncFlag(elementId, 2)
    }

    suspend fun permanentlyDeleteElement(elementId: Long) {
        // Recursively delete children if it's a folder
        val element = sleeveElementDao.getElementById(elementId) ?: return
        if (element.elementType == 1) {
            val children = sleeveElementDao.getChildrenByParentId(elementId)
            for (child in children) {
                permanentlyDeleteElement(child.elementId)
            }
        }
        sleeveElementDao.deleteElementById(elementId)
    }

    suspend fun searchElements(query: String): List<SleeveElementEntity> {
        return sleeveElementDao.searchElementsByName(query)
    }

    suspend fun searchInFolder(query: String, parentId: Long): List<SleeveElementEntity> {
        return sleeveElementDao.searchElementsByName(query)
    }

    suspend fun getUnsyncedElements(): List<SleeveElementEntity> {
        return sleeveElementDao.getUnsyncedElements()
    }

    suspend fun getDeletedElements(): List<SleeveElementEntity> {
        return sleeveElementDao.getDeletedElements()
    }

    suspend fun purgeDeletedElements() {
        sleeveElementDao.purgeDeletedElements()
    }

    suspend fun markAsSynced(elementId: Long) {
        sleeveElementDao.setSyncFlag(elementId, 3)
    }

    // ==================== Image Operations ====================

    suspend fun getImageById(id: Long): ImageEntity? {
        return imageDao.getImageByFileId(id)
    }

    fun getImageByIdFlow(id: Long): Flow<ImageEntity?> {
        return imageDao.observeImageByFileId(id)
    }

    suspend fun getAllImages(): List<ImageEntity> {
        return imageDao.getImagesPaginated(Int.MAX_VALUE, 0)
    }

    fun getAllImagesFlow(): Flow<List<ImageEntity>> {
        return imageDao.observeAllImages()
    }

    suspend fun searchImages(query: String): List<ImageEntity> {
        if (query.isBlank()) return emptyList()
        return imageDao.searchImages(query)
    }

    suspend fun getImagesByDateRange(startDate: Long, endDate: Long): List<ImageEntity> {
        return imageDao.getImagesByDateRange(startDate, endDate)
    }

    suspend fun getImagesByRating(rating: Int): List<ImageEntity> {
        return imageDao.getImagesByMinRating(rating)
    }

    suspend fun getImagesByMinRating(minRating: Int): List<ImageEntity> {
        return imageDao.getImagesByMinRating(minRating)
    }

    suspend fun getRawImages(): List<ImageEntity> {
        return imageDao.getHdrImages()
    }

    fun getRawImagesFlow(): Flow<List<ImageEntity>> {
        return imageDao.observeRawImages()
    }

    suspend fun importImage(image: ImageEntity): Long {
        return imageDao.insertImage(image)
    }

    suspend fun importImages(images: List<ImageEntity>): List<Long> {
        imageDao.insertImages(images)
        return images.map { it.fileId }
    }

    suspend fun updateImageRating(imageId: Long, rating: Int) {
        imageDao.updateRating(imageId, rating)
    }

    suspend fun deleteImage(imageId: Long) {
        imageDao.deleteImageByFileId(imageId)
    }

    suspend fun imageCount(): Int {
        return imageDao.getImageCount()
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
        return editHistoryDao.insert(entity)
    }

    suspend fun switchActiveVersion(imageId: Long, versionId: String) {
        editHistoryDao.deactivateAllVersions(imageId)
        editHistoryDao.setActiveVersion(imageId, versionId)
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

    // ==================== Helper Methods ====================

    private fun generateFolderId(): Long {
        return 2000000000L + (System.currentTimeMillis() % 1000000000L)
    }

    private fun generateFileId(): Long {
        return System.currentTimeMillis()
    }
}
