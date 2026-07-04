package com.alcedo.studio.domain.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.alcedo.studio.data.local.*
import com.alcedo.studio.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.min

/**
 * Complete import service with file format detection, metadata extraction,
 * thumbnail generation, directory recursive import, duplicate detection,
 * and progress tracking.
 */
class ImportService(
    private val context: Context,
    private val metadataDao: ImageMetadataDao,
    private val sleeveService: SleeveService,
    private val thumbnailDiskCache: ThumbnailDiskCache
) {
    // ================================================================
    // Progress tracking
    // ================================================================

    private val _importProgress = MutableStateFlow(ImportProgress())
    val importProgress: Flow<ImportProgress> = _importProgress.asStateFlow()

    private val _importLog = MutableStateFlow<List<ImportLogEntry>>(emptyList())
    val importLog: Flow<List<ImportLogEntry>> = _importLog.asStateFlow()

    data class ImportProgress(
        val totalFiles: Int = 0,
        val processedFiles: Int = 0,
        val currentFile: String = "",
        val status: ImportStatus = ImportStatus.IDLE,
        val errors: List<String> = emptyList()
    )

    enum class ImportStatus { IDLE, SCANNING, IMPORTING, GENERATING_THUMBNAILS, COMPLETED, CANCELLED, ERROR }

    data class ImportLogEntry(
        val filePath: String,
        val status: ImportLogStatus,
        val message: String = "",
        val timestamp: Long = System.currentTimeMillis()
    )

    enum class ImportLogStatus { SUCCESS, SKIPPED_DUPLICATE, SKIPPED_UNSUPPORTED, ERROR }

    // ================================================================
    // Magic bytes for format detection
    // ================================================================

    private val magicBytes = mapOf(
        byteArrayOf(0xFF.toByte(), 0xD8, 0xFF.toByte()) to ImageType.JPEG,
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47) to ImageType.PNG,
        byteArrayOf(0x49, 0x49, 0x2A, 0x00) to ImageType.TIFF,   // Little-endian
        byteArrayOf(0x4D, 0x4D, 0x00, 0x2A) to ImageType.TIFF,   // Big-endian
        byteArrayOf(0x49, 0x49, 0x2A, 0x00, 0x10, 0x00, 0x00, 0x00, 0x43, 0x52) to ImageType.CR2,
        byteArrayOf(0x4D, 0x4D, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x10, 0x43, 0x52) to ImageType.CR2,
        byteArrayOf(0x49, 0x49, 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00) to ImageType.NEF,
        byteArrayOf(0x49, 0x49, 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00, 0x2F, 0x00) to ImageType.ARW,
        byteArrayOf(0x00, 0x00, 0x00, 0x0C, 0x6A, 0x50, 0x20, 0x20) to ImageType.JPEG, // JPEG2000
        byteArrayOf(0x76, 0x2F, 0x31, 0x01) to ImageType.EXR,     // EXR
        byteArrayOf(0x63, 0x69, 0x6D, 0x67) to ImageType.CR3,     // CR3 (cimg)
        byteArrayOf(0x52, 0x49, 0x46, 0x46) to ImageType.WEBP,    // RIFF/WEBP
        byteArrayOf(0x42, 0x4D) to ImageType.BMP,                  // BMP
        byteArrayOf(0x47, 0x49, 0x46, 0x38) to ImageType.GIF      // GIF
    )

    private val supportedExtensions = setOf(
        "jpg", "jpeg", "png", "tiff", "tif", "arw", "cr2", "cr3", "nef", "dng",
        "heic", "heif", "webp", "bmp", "gif", "exr"
    )

    private val rawExtensions = setOf("arw", "cr2", "cr3", "nef", "dng")

    // ================================================================
    // Single image import
    // ================================================================

    suspend fun importImage(
        uri: Uri,
        parentFolderId: Long? = null,
        generateThumbnail: Boolean = true,
        checkDuplicates: Boolean = true
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            val path = getRealPathFromUri(uri)
            val fileName = getFileNameFromUri(uri)
            val fileSize = getFileSizeFromUri(uri)

            _importProgress.value = _importProgress.value.copy(
                status = ImportStatus.IMPORTING,
                currentFile = fileName
            )

            // Detect file format
            val inputStream = context.contentResolver.openInputStream(uri)
            val imageType = inputStream?.use { detectImageType(it) } ?: detectImageTypeByExtension(fileName)
            val mimeType = getMimeType(imageType)

            if (imageType == ImageType.DEFAULT) {
                addLogEntry(path, ImportLogStatus.SKIPPED_UNSUPPORTED, "Unsupported format")
                return@withContext ImportResult.Error("Unsupported file format: $fileName")
            }

            // Compute checksum for duplicate detection
            val checksum = if (checkDuplicates) {
                computeChecksum(uri)
            } else 0L

            if (checkDuplicates && checksum != 0L) {
                val existing = metadataDao.getMetadataByChecksum(checksum)
                if (existing.isNotEmpty()) {
                    addLogEntry(path, ImportLogStatus.SKIPPED_DUPLICATE, "Duplicate of ${existing.first().imageName}")
                    return@withContext ImportResult.Duplicate(existing.first().imageId, existing.first().imageName)
                }
            }

            // Extract EXIF metadata
            val exif = try {
                val parcelFd = context.contentResolver.openFileDescriptor(uri, "r")
                parcelFd?.let { ExifInterface(it.fileDescriptor) }
            } catch (_: Exception) {
                null
            }

            val exifDisplay = exif?.let { parseExifDisplay(it) } ?: ExifDisplayMetaData()
            val imageDimensions = getImageDimensions(uri)
            val imageId = generateImageId()

            val metadata = ImageMetadataEntity(
                imageId = imageId,
                imagePath = path,
                imageName = fileName.substringBeforeLast('.'),
                imageType = imageType.ordinal,
                fileSize = fileSize,
                width = imageDimensions.first,
                height = imageDimensions.second,
                checksum = checksum,
                mimeType = mimeType,
                thumbState = ThumbState.PENDING.ordinal,
                hasExif = exif != null,
                hasExifDisplay = exifDisplay.cameraMake.isNotEmpty(),
                cameraMake = exifDisplay.cameraMake,
                cameraModel = exifDisplay.cameraModel,
                lensModel = exifDisplay.lensModel,
                focalLength = exifDisplay.focalLength.toFloatOrNull() ?: 0f,
                aperture = exifDisplay.aperture.toFloatOrNull() ?: 0f,
                shutterSpeed = parseShutterSpeedValue(exifDisplay.shutterSpeed),
                iso = exifDisplay.iso.toIntOrNull() ?: 0,
                captureDate = parseCaptureDate(exifDisplay.captureDate),
                imageSizeDisplay = exifDisplay.imageSize,
                fileSizeDisplay = formatFileSize(fileSize),
                exifJson = exif?.let { serializeExif(it) } ?: "",
                exifDisplayJson = ""
            )

            // Insert into database
            metadataDao.insertMetadata(metadata)
            val elementId = sleeveService.createElement(
                name = fileName.substringBeforeLast('.'),
                type = ElementType.FILE,
                parentId = parentFolderId,
                imageId = imageId,
                filePath = path
            )

            // Generate thumbnail
            if (generateThumbnail) {
                _importProgress.value = _importProgress.value.copy(status = ImportStatus.GENERATING_THUMBNAILS)
                generateAndCacheThumbnail(imageId, uri)
                metadataDao.updateMetadata(metadata.copy(thumbState = ThumbState.READY.ordinal, hasThumbnail = true))
            }

            addLogEntry(path, ImportLogStatus.SUCCESS)
            _importProgress.value = _importProgress.value.copy(
                processedFiles = _importProgress.value.processedFiles + 1
            )

            ImportResult.Success(imageId = imageId, elementId = elementId, metadata = metadata)
        } catch (e: Exception) {
            addLogEntry(uri.toString(), ImportLogStatus.ERROR, e.message ?: "Unknown error")
            ImportResult.Error(e.message ?: "Import failed")
        }
    }

    // ================================================================
    // Directory recursive import
    // ================================================================

    suspend fun importDirectory(
        dirUri: Uri,
        parentFolderId: Long? = null,
        recursive: Boolean = true,
        checkDuplicates: Boolean = true
    ): ImportDirectoryResult = withContext(Dispatchers.IO) {
        try {
            _importProgress.value = ImportProgress(status = ImportStatus.SCANNING)

            // Scan directory for images
            val files = scanDirectory(dirUri, recursive)
            _importProgress.value = ImportProgress(
                totalFiles = files.size,
                status = ImportStatus.IMPORTING
            )

            val results = mutableListOf<ImportResult>()
            var successCount = 0
            var duplicateCount = 0
            var errorCount = 0

            for ((index, fileUri) in files.withIndex()) {
                _importProgress.value = _importProgress.value.copy(
                    processedFiles = index,
                    currentFile = fileUri.toString()
                )

                when (val result = importImage(fileUri, parentFolderId, checkDuplicates = checkDuplicates)) {
                    is ImportResult.Success -> { successCount++; results.add(result) }
                    is ImportResult.Duplicate -> { duplicateCount++; results.add(result) }
                    is ImportResult.Error -> { errorCount++; results.add(result) }
                }
            }

            _importProgress.value = _importProgress.value.copy(
                status = ImportStatus.COMPLETED,
                processedFiles = files.size
            )

            ImportDirectoryResult(
                totalFiles = files.size,
                successCount = successCount,
                duplicateCount = duplicateCount,
                errorCount = errorCount,
                results = results
            )
        } catch (e: Exception) {
            _importProgress.value = _importProgress.value.copy(status = ImportStatus.ERROR)
            ImportDirectoryResult(
                totalFiles = 0,
                successCount = 0,
                duplicateCount = 0,
                errorCount = 0,
                results = listOf(ImportResult.Error(e.message ?: "Import failed"))
            )
        }
    }

    private suspend fun scanDirectory(dirUri: Uri, recursive: Boolean): List<Uri> {
        val files = mutableListOf<Uri>()

        try {
            val children = context.contentResolver.query(
                dirUri, null, null, null, null
            )?.use { cursor ->
                val displayNameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val documentIdIdx = cursor.getColumnIndex("document_id")

                while (cursor.moveToNext()) {
                    val name = if (displayNameIdx >= 0) cursor.getString(displayNameIdx) else null
                    val docId = if (documentIdIdx >= 0) cursor.getString(documentIdIdx) else null

                    if (name != null && docId != null) {
                        val childUri = Uri.parse("${dirUri}/$docId")
                        val ext = name.substringAfterLast('.', "").lowercase()

                        if (ext in supportedExtensions) {
                            files.add(childUri)
                        } else if (recursive && ext.isEmpty()) {
                            // Could be a directory - try to recurse
                            try {
                                files.addAll(scanDirectory(childUri, recursive))
                            } catch (_: Exception) {
                                // Skip directories we can't read
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback: try regular file listing
            val path = getRealPathFromUri(dirUri)
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        val ext = file.extension.lowercase()
                        if (ext in supportedExtensions) {
                            files.add(Uri.fromFile(file))
                        }
                    } else if (recursive && file.isDirectory) {
                        try {
                            files.addAll(scanDirectory(Uri.fromFile(file), recursive))
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        return files
    }

    // ================================================================
    // Format detection
    // ================================================================

    private fun detectImageType(inputStream: InputStream): ImageType {
        val header = ByteArray(16)
        try {
            val bytesRead = inputStream.read(header)
            if (bytesRead < 4) return ImageType.DEFAULT

            for ((magic, type) in magicBytes) {
                if (magic.size <= bytesRead && header.take(magic.size).toByteArray().contentEquals(magic)) {
                    return type
                }
            }

            // Check DNG: TIFF + DNG tag
            if (header[0] == 0x49.toByte() && header[1] == 0x49.toByte() && header[2] == 0x2A.toByte() && header[3] == 0x00.toByte()) {
                // Could be DNG - check for DNG marker
                inputStream.skip(8) // skip TIFF header
                val tagCount = ByteArray(2)
                inputStream.read(tagCount)
                // Simple heuristic: if it's TIFF but not CR2/NEF/ARW, assume DNG
                return ImageType.DNG
            }

            // HEIC/HEIF check
            if (header[4] == 0x66.toByte() && header[5] == 0x74.toByte() && header[6] == 0x79.toByte() && header[7] == 0x70.toByte()) {
                if (header[8] == 0x68.toByte() && header[9] == 0x65.toByte() && header[10] == 0x69.toByte()) {
                    return ImageType.HEIC
                }
                return ImageType.HEIF
            }

            return ImageType.DEFAULT
        } catch (_: Exception) {
            return ImageType.DEFAULT
        }
    }

    private fun detectImageTypeByExtension(fileName: String): ImageType {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> ImageType.JPEG
            "png" -> ImageType.PNG
            "tiff", "tif" -> ImageType.TIFF
            "arw" -> ImageType.ARW
            "cr2" -> ImageType.CR2
            "cr3" -> ImageType.CR3
            "nef" -> ImageType.NEF
            "dng" -> ImageType.DNG
            "heic" -> ImageType.HEIC
            "heif" -> ImageType.HEIF
            "webp" -> ImageType.WEBP
            "bmp" -> ImageType.BMP
            "gif" -> ImageType.GIF
            "exr" -> ImageType.EXR
            else -> ImageType.DEFAULT
        }
    }

    private fun getMimeType(imageType: ImageType): String = when (imageType) {
        ImageType.JPEG -> "image/jpeg"
        ImageType.PNG -> "image/png"
        ImageType.TIFF -> "image/tiff"
        ImageType.ARW -> "image/x-sony-arw"
        ImageType.CR2 -> "image/x-canon-cr2"
        ImageType.CR3 -> "image/x-canon-cr3"
        ImageType.NEF -> "image/x-nikon-nef"
        ImageType.DNG -> "image/x-adobe-dng"
        ImageType.HEIC -> "image/heic"
        ImageType.HEIF -> "image/heif"
        ImageType.WEBP -> "image/webp"
        ImageType.BMP -> "image/bmp"
        ImageType.GIF -> "image/gif"
        ImageType.EXR -> "image/x-exr"
        ImageType.DEFAULT -> "application/octet-stream"
    }

    // ================================================================
    // Checksum computation
    // ================================================================

    private fun computeChecksum(uri: Uri): Long {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val hash = digest.digest()
            // Take first 8 bytes for Long checksum
            var result = 0L
            for (i in 0 until min(8, hash.size)) {
                result = (result shl 8) or (hash[i].toLong() and 0xFF)
            }
            return result
        } catch (_: Exception) {
            return 0L
        }
    }

    // ================================================================
    // EXIF parsing
    // ================================================================

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

    private fun parseShutterSpeedValue(s: String): Float {
        if (s.isEmpty()) return 0f
        return try {
            if (s.contains("/")) {
                val parts = s.split("/")
                parts[0].toFloat() / parts[1].toFloat()
            } else {
                s.toFloat()
            }
        } catch (_: Exception) { 0f }
    }

    private fun parseCaptureDate(s: String): Long {
        if (s.isEmpty()) return 0L
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US)
            sdf.parse(s)?.time ?: 0L
        } catch (_: Exception) { 0L }
    }

    private fun serializeExif(exif: ExifInterface): String {
        val sb = StringBuilder("{")
        val tags = listOf(
            ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_IMAGE_WIDTH,
            ExifInterface.TAG_IMAGE_LENGTH, ExifInterface.TAG_ISO_SPEED,
            ExifInterface.TAG_APERTURE_VALUE, ExifInterface.TAG_SHUTTER_SPEED_VALUE,
            ExifInterface.TAG_WHITE_BALANCE, ExifInterface.TAG_FLASH,
            ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_ORIENTATION
        )
        for (tag in tags) {
            val value = exif.getAttribute(tag) ?: continue
            sb.append("\"$tag\":\"${value.replace("\"", "\\\"")}\",")
        }
        if (sb.endsWith(",")) sb.deleteCharAt(sb.length - 1)
        sb.append("}")
        return sb.toString()
    }

    // ================================================================
    // Thumbnail generation
    // ================================================================

    private suspend fun generateAndCacheThumbnail(imageId: Long, uri: Uri) {
        try {
            val bitmap = generateThumbnail(uri, 256)
            bitmap?.let { thumbnailDiskCache.put(imageId, it) }
        } catch (_: Exception) {
            // Thumbnail failure is non-fatal
        }
    }

    private fun generateThumbnail(uri: Uri, maxSize: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            val scale = maxOf(
                1,
                minOf(options.outWidth / maxSize, options.outHeight / maxSize, 8)
            )

            BitmapFactory.Options().apply {
                inSampleSize = scale
                inPreferredConfig = Bitmap.Config.RGB_565
            }.let { opts ->
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getImageDimensions(uri: Uri): Pair<Int, Int> {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            options.outWidth to options.outHeight
        } catch (_: Exception) {
            0 to 0
        }
    }

    // ================================================================
    // Utility methods
    // ================================================================

    private fun getRealPathFromUri(uri: Uri): String {
        return uri.path ?: uri.toString()
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var name = "unknown"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIdx >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(nameIdx)
            }
        }
        if (name == "unknown") {
            name = uri.lastPathSegment ?: "unknown"
        }
        return name
    }

    private fun getFileSizeFromUri(uri: Uri): Long {
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIdx >= 0 && cursor.moveToFirst()) {
                size = cursor.getLong(sizeIdx)
            }
        }
        return size
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
            else -> "${"%.2f".format(bytes.toDouble() / (1024 * 1024 * 1024))} GB"
        }
    }

    private fun addLogEntry(path: String, status: ImportLogStatus, message: String = "") {
        val entry = ImportLogEntry(filePath = path, status = status, message = message)
        _importLog.value = _importLog.value + entry
    }

    private fun generateImageId(): Long {
        return (System.nanoTime() / 1000) + (Math.random() * 1000).toLong()
    }

    // ================================================================
    // Cancel import
    // ================================================================

    fun cancelImport() {
        _importProgress.value = _importProgress.value.copy(status = ImportStatus.CANCELLED)
    }

    fun resetProgress() {
        _importProgress.value = ImportProgress()
        _importLog.value = emptyList()
    }

    // ================================================================
    // Result types
    // ================================================================

    sealed class ImportResult {
        data class Success(
            val imageId: Long,
            val elementId: Long,
            val metadata: ImageMetadataEntity
        ) : ImportResult()

        data class Duplicate(
            val existingImageId: Long,
            val existingName: String
        ) : ImportResult()

        data class Error(
            val message: String
        ) : ImportResult()
    }

    data class ImportDirectoryResult(
        val totalFiles: Int,
        val successCount: Int,
        val duplicateCount: Int,
        val errorCount: Int,
        val results: List<ImportResult>
    )
}