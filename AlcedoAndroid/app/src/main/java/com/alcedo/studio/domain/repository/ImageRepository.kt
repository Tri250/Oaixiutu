package com.alcedo.studio.domain.repository

import android.graphics.Bitmap
import com.alcedo.studio.data.local.SleeveDatabase
import com.alcedo.studio.data.local.ThumbnailDiskCache
import com.alcedo.studio.data.model.ImageModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ImageRepository {
    suspend fun addImage(image: ImageModel)
    suspend fun getImage(id: UInt): ImageModel?
    suspend fun getAllImages(): List<ImageModel>
    suspend fun updateImage(image: ImageModel)
    suspend fun deleteImage(id: UInt)
    suspend fun getThumbnail(id: UInt): Bitmap?
    suspend fun cacheThumbnail(id: UInt, bitmap: Bitmap)
}

class ImageRepositoryImpl(
    private val db: SleeveDatabase,
    private val thumbnailCache: ThumbnailDiskCache
) : ImageRepository {

    override suspend fun addImage(image: ImageModel) = withContext(Dispatchers.IO) {
        db.insertImage(image)
    }

    override suspend fun getImage(id: UInt): ImageModel? = withContext(Dispatchers.IO) {
        db.getImageById(id)
    }

    override suspend fun getAllImages(): List<ImageModel> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ImageModel>()
        db.readableDatabase.query("images", null, null, null, null, null, "image_id DESC").use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    ImageModel(
                        imageId = cursor.getInt(0).toUInt(),
                        imagePath = cursor.getString(1),
                        imageName = cursor.getString(2),
                        imageType = com.alcedo.studio.data.model.ImageType.entries[cursor.getInt(3)],
                        thumbState = com.alcedo.studio.data.model.ThumbState.entries[cursor.getInt(4)],
                        syncState = com.alcedo.studio.data.model.ImageSyncState.entries[cursor.getInt(5)],
                        checksum = cursor.getLong(6).toULong()
                    )
                )
            }
        }
        list
    }

    override suspend fun updateImage(image: ImageModel) = withContext(Dispatchers.IO) {
        db.insertImage(image)
    }

    override suspend fun deleteImage(id: UInt) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("images", "image_id = ?", arrayOf(id.toString()))
        thumbnailCache.evict(id)
    }

    override suspend fun getThumbnail(id: UInt): Bitmap? = withContext(Dispatchers.IO) {
        thumbnailCache.get(id)
    }

    override suspend fun cacheThumbnail(id: UInt, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        thumbnailCache.put(id, bitmap)
    }
}
