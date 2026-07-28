package com.alcedo.studio.domain.repository

import com.alcedo.studio.data.model.ColorLabel
import com.alcedo.studio.data.model.FilterCombo
import com.alcedo.studio.data.model.ImageFlag
import com.alcedo.studio.data.model.ImageItem
import com.alcedo.studio.data.model.SortDescriptor
import kotlinx.coroutines.flow.Flow

/**
 * Domain contract for the image catalog. Implemented by the data layer
 * ([com.alcedo.studio.data.repository.ImageRepositoryImpl]) and consumed by
 * the album/editor ViewModels and domain services.
 */
interface ImageRepository {

    fun observeImagesInFolder(folderPath: String): Flow<List<ImageItem>>
    fun observeAllImages(): Flow<List<ImageItem>>
    fun observeImage(id: String): Flow<ImageItem?>

    suspend fun getImage(id: String): ImageItem?
    suspend fun queryImages(filter: FilterCombo, sort: SortDescriptor, limit: Int, offset: Int): List<ImageItem>
    suspend fun upsert(image: ImageItem)
    suspend fun upsertAll(images: List<ImageItem>)
    suspend fun delete(id: String)

    suspend fun setRating(id: String, rating: Int)
    suspend fun setFlag(id: String, flag: ImageFlag)
    suspend fun setColorLabel(id: String, label: ColorLabel)
    suspend fun setHidden(id: String, hidden: Boolean)
    suspend fun setCurrentVersion(id: String, versionId: String)
    suspend fun setThumbnailPath(id: String, path: String?)
    suspend fun setAiMetadata(id: String, caption: String?, tags: List<String>, score: Float?)

    suspend fun count(): Int
    suspend fun rawCount(): Int
    suspend fun pickCount(): Int
    suspend fun rejectCount(): Int
    suspend fun distinctCameras(): List<String>
    suspend fun distinctLenses(): List<String>
}
