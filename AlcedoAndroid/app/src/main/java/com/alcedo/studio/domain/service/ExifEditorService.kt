package com.alcedo.studio.domain.service

import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.alcedo.studio.util.ContextProvider
import com.alcedo.studio.utils.ThreadPool
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EXIF metadata editor. Reads and writes EXIF tags on a copy of the source
 * image (non-destructive: the original is never modified in place during
 * editing; writes happen at export). Wraps [ExifInterface].
 */
@Singleton
class ExifEditorService @Inject constructor() {

    /** Read all relevant EXIF tags from [uri] as a tag->value map. */
    suspend fun read(uri: Uri): Map<String, String> = withContext(ThreadPool.io) {
        val cr = ContextProvider.requireContext().contentResolver
        val result = mutableMapOf<String, String>()
        runCatching {
            cr.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                for (tag in SUPPORTED_TAGS) {
                    exif.getAttribute(tag)?.let { result[tag] = it }
                }
            }
        }.onFailure { Log.w(TAG, "EXIF read failed for $uri", it) }
        result
    }

    /** Write [tags] to a copy of [srcUri] saved at [destPath]; returns success. */
    suspend fun write(srcUri: Uri, destPath: String, tags: Map<String, String>): Boolean =
        withContext(ThreadPool.io) {
            runCatching {
                val cr = ContextProvider.requireContext().contentResolver
                val dest = java.io.File(destPath)
                cr.openInputStream(srcUri)?.use { input ->
                    dest.outputStream().use { input.copyTo(it) }
                } ?: return@withContext false
                val exif = ExifInterface(dest.absolutePath)
                for ((tag, value) in tags) {
                    if (tag in SUPPORTED_TAGS) exif.setAttribute(tag, value)
                }
                exif.saveAttributes()
                true
            }.onFailure {
                Log.w(TAG, "EXIF write failed", it)
                false
            }.getOrDefault(false)
        }

    /** Convenience: format a capture-date string from an epoch millis value. */
    fun formatCaptureDate(epoch: Long): String =
        SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date(epoch))

    companion object {
        private const val TAG = "ExifEditorService"

        val SUPPORTED_TAGS = listOf(
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.TAG_ARTIST,
            ExifInterface.TAG_COPYRIGHT,
            ExifInterface.TAG_USER_COMMENT,
            ExifInterface.TAG_IMAGE_DESCRIPTION,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LONGITUDE,
        )
    }
}
