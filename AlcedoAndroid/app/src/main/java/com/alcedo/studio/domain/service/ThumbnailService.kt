package com.alcedo.studio.domain.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.alcedo.studio.data.local.ThumbnailDiskCache
import com.alcedo.studio.domain.repository.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ThumbnailService(
    private val imageRepository: ImageRepository,
    private val diskCache: ThumbnailDiskCache
) {

    suspend fun loadThumbnail(imageId: UInt): Bitmap? = withContext(Dispatchers.IO) {
        diskCache.get(imageId) ?: run {
            val image = imageRepository.getImage(imageId) ?: return@withContext null
            val bitmap = generateThumbnail(image.imagePath)
            bitmap?.let { diskCache.put(imageId, it) }
            bitmap
        }
    }

    suspend fun prefetchThumbnails(imageIds: List<UInt>) = withContext(Dispatchers.IO) {
        imageIds.forEach { id ->
            if (!diskCache.contains(id)) {
                loadThumbnail(id)
            }
        }
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        diskCache.clear()
    }

    private fun generateThumbnail(path: String): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            val scale = maxOf(1, minOf(options.outWidth / 256, options.outHeight / 256))
            BitmapFactory.Options().apply { inSampleSize = scale }.let {
                BitmapFactory.decodeFile(path, it)
            }
        } catch (_: Exception) {
            null
        }
    }
}
