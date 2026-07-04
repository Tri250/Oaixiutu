package com.alcedo.studio.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

// ================================================================
// MediaStore Helper for Scoped Storage compatibility
// ================================================================

object MediaStoreHelper {
    private const val TAG = "MediaStoreHelper"

    // ── Query images from MediaStore ──

    data class MediaImage(
        val id: Long,
        val displayName: String,
        val dateAdded: Long,
        val dateModified: Long,
        val size: Long,
        val mimeType: String,
        val width: Int,
        val height: Int,
        val contentUri: Uri,
        val dataPath: String?, // null on API 30+
        val isRaw: Boolean = false
    )

    fun queryImages(
        context: Context,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String = "${MediaStore.Images.Media.DATE_ADDED} DESC",
        limit: Int = 100,
        offset: Int = 0
    ): List<MediaImage> {
        val images = mutableListOf<MediaImage>()
        val resolver = context.contentResolver

        val projection = mutableListOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )

        // DATA column is deprecated but still useful on older APIs
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Images.Media.DATA)
        }

        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        resolver.query(uri, projection.toTypedArray(), selection, selectionArgs, sortOrder)?.use { cursor ->
            // Apply offset
            if (offset > 0) {
                cursor.moveToPosition(offset - 1)
            }

            var count = 0
            while (cursor.moveToNext() && count < limit) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                val displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)) ?: ""
                val dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))
                val dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED))
                val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE))
                val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)) ?: ""
                val width = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH))
                val height = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT))

                val contentUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())

                val dataPath = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                } else {
                    null
                }

                val isRaw = isRawFile(displayName, mimeType)

                images.add(MediaImage(
                    id = id,
                    displayName = displayName,
                    dateAdded = dateAdded,
                    dateModified = dateModified,
                    size = size,
                    mimeType = mimeType,
                    width = width,
                    height = height,
                    contentUri = contentUri,
                    dataPath = dataPath,
                    isRaw = isRaw
                ))
                count++
            }
        }

        return images
    }

    // ── Save image via MediaStore (Android 10+) ──

    fun saveImage(
        context: Context,
        displayName: String,
        mimeType: String,
        relativePath: String = Environment.DIRECTORY_PICTURES + "/Alcedo",
        writeAction: (OutputStream) -> Boolean
    ): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null

        try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                if (!writeAction(outputStream)) {
                    resolver.delete(uri, null, null)
                    return null
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }

            return uri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save image", e)
            resolver.delete(uri, null, null)
            return null
        }
    }

    // ── Open input stream from content URI ──

    fun openInputStream(context: Context, uri: Uri): InputStream? {
        return try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open input stream for $uri", e)
            null
        }
    }

    // ── Copy content URI to temp file (for NDK processing) ──

    fun copyToTempFile(context: Context, uri: Uri, suffix: String = ".tmp"): File? {
        return try {
            // Validate URI scheme
            if (uri.scheme != "content") {
                Log.e(TAG, "Rejecting unsafe URI scheme: ${uri.scheme}")
                return null
            }

            val tempFile = File.createTempFile("alcedo_", suffix, context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Validate that the temp file is within the app's cache directory
            val canonicalCache = context.cacheDir.canonicalPath
            val canonicalTemp = tempFile.canonicalPath
            if (!canonicalTemp.startsWith(canonicalCache)) {
                Log.e(TAG, "Temp file escape detected: $canonicalTemp")
                tempFile.delete()
                return null
            }

            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy URI to temp file", e)
            null
        }
    }

    // ── Get file info from content URI ──

    fun getFileInfo(context: Context, uri: Uri): Pair<String, Long>? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else ""
                    val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
                    Pair(name, size)
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get file info for $uri", e)
            null
        }
    }

    // ── Delete image via MediaStore ──

    fun deleteImage(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.delete(uri, null, null) > 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete image", e)
            false
        }
    }

    // ── RAW file detection ──

    private val RAW_EXTENSIONS = setOf(
        "nef", "nrw", "cr2", "cr3", "arw", "srf", "sr2",
        "dng", "raf", "orf", "pef", "rw2", "rwl", "raw",
        "3fr", "fff", "iiq", "kdc", "mef", "mos", "mrw",
        "ptx", "pxn", "r3d", "ref", "rwz", "x3f", "erf"
    )

    fun isRawFile(fileName: String, mimeType: String): Boolean {
        val ext = fileName.substringAfterLast('.', '').lowercase()
        return ext in RAW_EXTENSIONS || mimeType.startsWith("image/x-") || mimeType == "image/dng"
    }

    // ── Get app-specific export directory ──

    fun getExportDirectory(context: Context): File {
        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use app-specific external directory (no permission needed)
            File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Alcedo/Exports")
        } else {
            // Legacy: use public Pictures directory
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Alcedo/Exports")
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // ── Query RAW files specifically ──

    fun queryRawImages(context: Context, limit: Int = 100): List<MediaImage> {
        // Build selection for RAW files
        val rawSelection = RAW_EXTENSIONS.joinToString(" OR ") { ext ->
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE '%.$ext'"
        }
        return queryImages(context, selection = rawSelection, limit = limit)
    }
}
