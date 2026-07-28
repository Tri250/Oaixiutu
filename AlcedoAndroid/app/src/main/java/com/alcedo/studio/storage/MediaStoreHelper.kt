package com.alcedo.studio.storage

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.alcedo.studio.util.ContextProvider

/**
 * MediaStore access helpers. Queries the MediaStore for images (including RAW
 * on API 30+), resolves display names and file sizes, and builds URIs for
 * import. Used by [com.alcedo.studio.domain.service.ImportService].
 */
object MediaStoreHelper {

    /** Query all images in the MediaStore, newest first. */
    fun queryAllImages(context: Context = ContextProvider.requireContext(), limit: Int = 500): List<MediaStoreImage> {
        val results = mutableListOf<MediaStoreImage>()
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT $limit"
        return runCatching {
            context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val mimeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val widthCol = c.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val heightCol = c.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    results.add(
                        MediaStoreImage(
                            uri = ContentUris.withAppendedId(collection, id),
                            displayName = c.getString(nameCol) ?: "image_$id",
                            sizeBytes = c.getLong(sizeCol),
                            mimeType = c.getString(mimeCol) ?: "image/*",
                            dateTaken = c.getLong(dateCol),
                            width = if (widthCol >= 0) c.getInt(widthCol) else 0,
                            height = if (heightCol >= 0) c.getInt(heightCol) else 0,
                        ),
                    )
                }
            }
            results
        }.onFailure { Log.w(TAG, "queryAllImages failed", it) }.getOrDefault(emptyList())
    }

    /** Resolve the display name for [uri]. */
    fun displayName(context: Context = ContextProvider.requireContext(), uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment
    }

    /** Resolve the file size for [uri], or null. */
    fun fileSize(context: Context = ContextProvider.requireContext(), uri: Uri): Long? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)
                ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }
        }.getOrNull()
    }

    /** Open an output Uri for writing an exported image. */
    fun openOutput(context: Context = ContextProvider.requireContext(), uri: Uri) =
        context.contentResolver.openOutputStream(uri)

    data class MediaStoreImage(
        val uri: Uri,
        val displayName: String,
        val sizeBytes: Long,
        val mimeType: String,
        val dateTaken: Long,
        val width: Int,
        val height: Int,
    )

    private const val TAG = "MediaStoreHelper"
}
