package com.alcedo.studio.domain.service

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.alcedo.studio.data.local.ThumbnailDiskCache
import com.alcedo.studio.ndk.AlcedoNativeBridge
import com.alcedo.studio.ndk.NdkSafeCall
import com.alcedo.studio.util.BitmapDecoder
import com.alcedo.studio.util.ContextProvider
import com.alcedo.studio.utils.MemoryGuard
import com.alcedo.studio.utils.ThreadPool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thumbnail generation service. Uses the native fast thumbnail path
 * (core/decoders/thumbnail_decoder.cpp) with a disk cache
 * ([ThumbnailDiskCache]) and an in-memory LRU so the grid scrolls smoothly.
 */
@Singleton
class ThumbnailService @Inject constructor(
    private val diskCache: ThumbnailDiskCache,
    private val memoryGuard: MemoryGuard,
) {
    private val memory = com.alcedo.studio.utils.WeightedLruCache<String, Bitmap>(
        maxWeight = 64L * 1024L * 1024L,
        sizeOf = { _, bmp -> (bmp.byteCount).toLong() },
    )

    data class Pending(val total: Int, val completed: Int)
    private val _batch = MutableStateFlow<Pending?>(null)
    val batchProgress: StateFlow<Pending?> = _batch.asStateFlow()

    /** Return a cached thumbnail bitmap for [uri] at [size], generating if needed. */
    suspend fun getThumbnail(uri: Uri, size: Int = 320): Bitmap? = withContext(ThreadPool.thumbnail) {
        val key = diskCache.keyFor(uri.toString(), size)
        memory.get(key)?.let { return@withContext it }
        diskCache.read(uri.toString(), size)?.let {
            memory.put(key, it)
            return@withContext it
        }
        val bmp = generate(uri, size) ?: return@withContext null
        memory.put(key, bmp)
        diskCache.write(uri.toString(), size, bmp)
        bmp
    }

    /** Generate a thumbnail and return its on-disk path, keyed by [imageId]. */
    suspend fun generateForUri(uri: Uri, imageId: String, size: Int = 320): String? =
        withContext(ThreadPool.thumbnail) {
            val bmp = generate(uri, size) ?: return@withContext null
            diskCache.write(imageId, size, bmp)
            diskCache.fileFor(imageId).absolutePath
        }

    private suspend fun generate(uri: Uri, size: Int): Bitmap? {
        val target = size.coerceIn(64, memoryGuard.suggestedMaxDim())
        // Native fast path first.
        val native = NdkSafeCall.callOrNull<Bitmap> {
            AlcedoNativeBridge.nativeDecodeThumbnail(uri.toString(), target)
        }
        if (native != null) return native
        // Fallback to the platform decoder.
        return BitmapDecoder.decodeThumbnail(ContextProvider.requireContext(), uri, target)
    }

    /** Generate thumbnails for a batch of [uris], reporting progress. */
    suspend fun generateBatch(uris: List<Uri>, size: Int = 320): Map<String, String> =
        withContext(ThreadPool.thumbnail) {
            val results = mutableMapOf<String, String>()
            _batch.value = Pending(uris.size, 0)
            uris.forEachIndexed { index, uri ->
                runCatching {
                    val path = generateForUri(uri, uri.lastPathSegment ?: "thumb_$index", size)
                    if (path != null) results[uri.toString()] = path
                }.onFailure { Log.w(TAG, "batch thumb failed for $uri", it) }
                _batch.value = Pending(uris.size, index + 1)
            }
            _batch.value = null
            results
        }

    /** Evict the in-memory thumbnail cache (used on memory pressure). */
    fun trimMemory() {
        memory.clear()
    }

    companion object {
        private const val TAG = "ThumbnailService"
    }
}
