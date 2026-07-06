package com.alcedo.studio.domain.service

import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Holds the editable EXIF metadata of an image. Nullable fields mean "not present / cleared".
 * Read-only camera capture fields (make/model/focal length/etc.) are also exposed so the
 * editor panel can display them, but only the editable text fields are written back.
 */
data class ExifData(
    val author: String?,          // TAG_ARTIST
    val copyright: String?,       // TAG_COPYRIGHT
    val title: String?,           // TAG_IMAGE_DESCRIPTION
    val comment: String?,         // TAG_USER_COMMENT
    val dateTime: String?,        // TAG_DATETIME
    val dateTimeOriginal: String?,// TAG_DATETIME_ORIGINAL
    val make: String?,            // TAG_MAKE
    val model: String?,           // TAG_MODEL
    val lensModel: String?,       // TAG_LENS_MODEL
    val focalLength: String?,     // TAG_FOCAL_LENGTH
    val fNumber: String?,         // TAG_F_NUMBER
    val iso: String?,             // TAG_PHOTOGRAPHIC_SENSITIVITY
    val exposureTime: String?,    // TAG_EXPOSURE_TIME
    val gpsLatitude: String?,     // TAG_GPS_LATITUDE
    val gpsLongitude: String?,    // TAG_GPS_LONGITUDE
    val software: String?         // TAG_SOFTWARE
)

/**
 * Real EXIF metadata editor backed by `androidx.exifinterface:exifinterface`.
 *
 * `ExifInterface` operates on a file path (or stream). When writing, the library rewrites
 * the file in place for JPEG/HEIF, and falls back to a temp-file copy + replace for formats
 * that cannot be rewritten in place.
 */
class ExifEditorService {

    /**
     * Read all editable EXIF fields from a file. Returns an [ExifData] populated with
     * whatever fields are present (missing fields are null).
     */
    suspend fun readExif(filePath: String): ExifData = withContext(Dispatchers.IO) {
        val exif = openExif(filePath)
        ExifData(
            author = exif.getAttribute(ExifInterface.TAG_ARTIST)?.takeIf { it.isNotBlank() },
            copyright = exif.getAttribute(ExifInterface.TAG_COPYRIGHT)?.takeIf { it.isNotBlank() },
            title = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION)?.takeIf { it.isNotBlank() },
            comment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT)?.takeIf { it.isNotBlank() },
            dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME)?.takeIf { it.isNotBlank() },
            dateTimeOriginal = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.takeIf { it.isNotBlank() },
            make = exif.getAttribute(ExifInterface.TAG_MAKE)?.takeIf { it.isNotBlank() },
            model = exif.getAttribute(ExifInterface.TAG_MODEL)?.takeIf { it.isNotBlank() },
            lensModel = exif.getAttribute(ExifInterface.TAG_LENS_MODEL)?.takeIf { it.isNotBlank() },
            focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.takeIf { it.isNotBlank() },
            fNumber = exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.takeIf { it.isNotBlank() },
            iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.takeIf { it.isNotBlank() },
            exposureTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.takeIf { it.isNotBlank() },
            gpsLatitude = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)?.takeIf { it.isNotBlank() },
            gpsLongitude = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)?.takeIf { it.isNotBlank() },
            software = exif.getAttribute(ExifInterface.TAG_SOFTWARE)?.takeIf { it.isNotBlank() }
        )
    }

    /**
     * Write the editable EXIF fields to a file. Only the editable text fields
     * (author, copyright, title, comment, dateTime, software) are written; capture
     * fields like make/model/focal length are preserved untouched.
     *
     * Returns true on success.
     */
    suspend fun writeExif(filePath: String, data: ExifData): Boolean = withContext(Dispatchers.IO) {
        try {
            val exif = openExif(filePath)
            // Editable text fields
            setOrClear(exif, ExifInterface.TAG_ARTIST, data.author)
            setOrClear(exif, ExifInterface.TAG_COPYRIGHT, data.copyright)
            setOrClear(exif, ExifInterface.TAG_IMAGE_DESCRIPTION, data.title)
            setOrClear(exif, ExifInterface.TAG_USER_COMMENT, data.comment)
            setOrClear(exif, ExifInterface.TAG_DATETIME, data.dateTime)
            setOrClear(exif, ExifInterface.TAG_SOFTWARE, data.software)
            exif.saveAttributes()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Clear specific EXIF fields by tag name. [fields] should be ExifInterface tag
     * constant strings (e.g. ExifInterface.TAG_GPS_LATITUDE).
     * Returns true on success.
     */
    suspend fun clearExifFields(filePath: String, fields: List<String>): Boolean = withContext(Dispatchers.IO) {
        try {
            val exif = openExif(filePath)
            fields.forEach { tag -> exif.setAttribute(tag, null) }
            exif.saveAttributes()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun openExif(filePath: String): ExifInterface {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            throw IllegalArgumentException("EXIF source file not found: $filePath")
        }
        return ExifInterface(filePath)
    }

    private fun setOrClear(exif: ExifInterface, tag: String, value: String?) {
        // Blank/empty values are treated as "clear" so the field is removed.
        if (value.isNullOrBlank()) {
            exif.setAttribute(tag, null)
        } else {
            exif.setAttribute(tag, value)
        }
    }
}
