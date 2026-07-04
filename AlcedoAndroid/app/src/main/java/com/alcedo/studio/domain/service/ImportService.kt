package com.alcedo.studio.domain.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.alcedo.studio.data.model.*
import com.alcedo.studio.domain.repository.ImageRepository
import com.alcedo.studio.domain.repository.SleeveRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ImportService(
    private val context: Context,
    private val imageRepository: ImageRepository,
    private val sleeveRepository: SleeveRepository
) {
    private var nextImageId: UInt = 1u

    suspend fun importImage(uri: Uri, parentFolderId: UInt? = null): SleeveFile = withContext(Dispatchers.IO) {
        val path = getRealPathFromUri(uri)
        val file = File(path)
        val imageId = nextImageId++
        val imageName = file.nameWithoutExtension
        val extension = file.extension.uppercase()

        val imageType = when (extension) {
            "JPG", "JPEG" -> ImageType.JPEG
            "PNG" -> ImageType.PNG
            "TIFF", "TIF" -> ImageType.TIFF
            "ARW" -> ImageType.ARW
            "CR2" -> ImageType.CR2
            "CR3" -> ImageType.CR3
            "NEF" -> ImageType.NEF
            "DNG" -> ImageType.DNG
            else -> ImageType.DEFAULT
        }

        val exif = try { ExifInterface(path) } catch (_: Exception) { null }
        val exifDisplay = exif?.let { parseExifDisplay(it) } ?: ExifDisplayMetaData()

        val thumbnail = generateThumbnail(path)

        val image = ImageModel(
            imageId = imageId,
            imagePath = path,
            imageName = imageName,
            exifData = exif,
            exifDisplay = exifDisplay,
            imageType = imageType,
            thumbState = if (thumbnail != null) ThumbState.READY else ThumbState.FAILED,
            hasThumb = thumbnail != null,
            hasExif = exif != null,
            hasExifDisplay = exifDisplay.cameraMake.isNotEmpty()
        )

        imageRepository.addImage(image)
        thumbnail?.let { imageRepository.cacheThumbnail(imageId, it) }

        val sleeveFile = sleeveRepository.createFile(imageName, imageId, parentFolderId)
        sleeveFile
    }

    suspend fun importDirectory(dirPath: String, parentFolderId: UInt? = null): SleeveFolder = withContext(Dispatchers.IO) {
        val dir = File(dirPath)
        val folder = sleeveRepository.createFolder(dir.name, parentFolderId)

        dir.listFiles()?.filter { it.isFile }?.forEach { file ->
            val ext = file.extension.uppercase()
            if (ext in listOf("JPG", "JPEG", "PNG", "TIFF", "TIF", "ARW", "CR2", "CR3", "NEF", "DNG")) {
                try {
                    importImage(Uri.fromFile(file), folder.elementId)
                } catch (_: Exception) {
                    // Log error
                }
            }
        }
        folder
    }

    private fun getRealPathFromUri(uri: Uri): String {
        return uri.path ?: ""
    }

    private fun parseExifDisplay(exif: ExifInterface): ExifDisplayMetaData {
        return ExifDisplayMetaData(
            cameraMake = exif.getAttribute(ExifInterface.TAG_MAKE) ?: "",
            cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL) ?: "",
            lensModel = exif.getAttribute(ExifInterface.TAG_LENS_MODEL) ?: "",
            focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH) ?: "",
            aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER) ?: "",
            shutterSpeed = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME) ?: "",
            iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY) ?: "",
            captureDate = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: "",
            imageSize = "${exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH) ?: ""} x ${exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH) ?: ""}"
        )
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
