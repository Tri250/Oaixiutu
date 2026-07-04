package com.alcedo.studio.domain.repository

import android.graphics.Bitmap
import com.alcedo.studio.data.local.ImageMetadataDao
import com.alcedo.studio.data.local.ThumbnailDiskCache
import com.alcedo.studio.data.model.ImageMetadataEntity
import com.alcedo.studio.data.model.ImageModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ImageRepository {
    suspend fun addImage(image: ImageModel)
    suspend fun addImageMetadata(metadata: ImageMetadataEntity)
    suspend fun getImage(id: Long): ImageModel?
    suspend fun getImageMetadata(id: Long): ImageMetadataEntity?
    suspend fun getAllImages(): List<ImageModel>
    suspend fun getAllImageMetadata(): List<ImageMetadataEntity>
    suspend fun updateImage(image: ImageModel)
    suspend fun deleteImage(id: Long)
    suspend fun getThumbnail(id: Long): Bitmap?
    suspend fun cacheThumbnail(id: Long, bitmap: Bitmap)
    suspend fun getMetadataByChecksum(checksum: Long): List<ImageMetadataEntity>
}

class ImageRepositoryImpl(
    private val metadataDao: ImageMetadataDao,
    private val thumbnailCache: ThumbnailDiskCache
) : ImageRepository {

    override suspend fun addImage(image: ImageModel) = withContext(Dispatchers.IO) {
        metadataDao.insertMetadata(image.toMetadataEntity())
        Unit
    }

    override suspend fun addImageMetadata(metadata: ImageMetadataEntity) = withContext(Dispatchers.IO) {
        metadataDao.insertMetadata(metadata)
        Unit
    }

    override suspend fun getImage(id: Long): ImageModel? = withContext(Dispatchers.IO) {
        metadataDao.getMetadataByImageId(id)?.let { ImageModel.fromMetadataEntity(it) }
    }

    override suspend fun getImageMetadata(id: Long): ImageMetadataEntity? = withContext(Dispatchers.IO) {
        metadataDao.getMetadataByImageId(id)
    }

    override suspend fun getAllImages(): List<ImageModel> = withContext(Dispatchers.IO) {
        metadataDao.getAllMetadata().map { ImageModel.fromMetadataEntity(it) }
    }

    override suspend fun getAllImageMetadata(): List<ImageMetadataEntity> = withContext(Dispatchers.IO) {
        metadataDao.getAllMetadata()
    }

    override suspend fun updateImage(image: ImageModel) = withContext(Dispatchers.IO) {
        metadataDao.updateMetadata(image.toMetadataEntity())
    }

    override suspend fun deleteImage(id: Long) = withContext(Dispatchers.IO) {
        metadataDao.deleteMetadataByImageId(id)
        thumbnailCache.evict(id.toString(), 0)
    }

    override suspend fun getThumbnail(id: Long): Bitmap? = withContext(Dispatchers.IO) {
        thumbnailCache.get(id.toString())
    }

    override suspend fun cacheThumbnail(id: Long, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        thumbnailCache.put(id.toString(), bitmap)
    }

    override suspend fun getMetadataByChecksum(checksum: Long): List<ImageMetadataEntity> = withContext(Dispatchers.IO) {
        metadataDao.getMetadataByChecksum(checksum)
    }
}