package com.alcedo.studio.domain.service

import android.graphics.*
import android.media.ThumbnailUtils
import com.alcedo.studio.data.local.ThumbnailDiskCache
import com.alcedo.studio.data.model.ImageMetadataEntity
import com.alcedo.studio.data.model.ThumbState
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Multi-resolution thumbnail generation service with disk cache,
 * memory cache, async loading, and placeholder generation.
 */
class ThumbnailService(
    private val diskCache: ThumbnailDiskCache,
    private val cacheDir: File
) {
    // ================================================================
    // Thumbnail sizes
    // ================================================================

    enum class ThumbnailSize(val pixels: Int) {
        MICRO(64),
        SMALL(128),
        MEDIUM(256),
        LARGE(512),
        XLARGE(1024)
    }

    // ================================================================
    // Memory cache
    // ================================================================

    private val memoryCache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
            if (size > MAX_MEMORY_CACHE_SIZE) {
                eldest?.value?.recycle()
                return true
            }
            return false
        }
    }

    private val memoryCacheLock = Any()

    private val pendingLoads = ConcurrentHashMap<String, Job>()

    private val loadingStates = ConcurrentHashMap<String, LoadingState>()

    enum class LoadingState { NOT_LOADED, LOADING, LOADED, FAILED }

    companion object {
        private const val MAX_MEMORY_CACHE_SIZE = 200
        private const val THUMBNAIL_QUALITY = 85
        private const val PLACEHOLDER_COLOR = 0xFFE0E0E0.toInt()

        private val thumbnailExecutor = ThreadPoolExecutor(
            2, 4, 30, TimeUnit.SECONDS,
            LinkedBlockingQueue(100),
            ThreadPoolExecutor.DiscardOldestPolicy()
        )
    }

    // ================================================================
    // Statistics
    // ================================================================

    private val memoryHits = AtomicLong(0)
    private val diskHits = AtomicLong(0)
    private val generatedCount = AtomicLong(0)

    // ================================================================
    // Load thumbnail
    // ================================================================

    suspend fun loadThumbnail(
        imageId: Long,
        size: ThumbnailSize = ThumbnailSize.MEDIUM
    ): ThumbnailResult = withContext(Dispatchers.IO) {
        val cacheKey = makeCacheKey(imageId, size)

        // 1. Check memory cache
        synchronized(memoryCacheLock) {
            memoryCache[cacheKey]?.let {
                memoryHits.incrementAndGet()
                return@withContext ThumbnailResult.Success(it, ThumbnailSource.MEMORY)
            }
        }

        // 2. Check disk cache
        try {
            val diskBitmap = diskCache.get(makeDiskCacheKey(imageId, size))
            if (diskBitmap != null) {
                diskHits.incrementAndGet()
                synchronized(memoryCacheLock) {
                    memoryCache[cacheKey] = diskBitmap
                }
                loadingStates[cacheKey] = LoadingState.LOADED
                return@withContext ThumbnailResult.Success(diskBitmap, ThumbnailSource.DISK)
            }
        } catch (_: Exception) {
            // Disk cache miss
        }

        // 3. Generate placeholder
        return@withContext ThumbnailResult.Placeholder(generatePlaceholder(size.pixels))
    }

    suspend fun loadThumbnailFromPath(
        imagePath: String,
        imageId: Long,
        size: ThumbnailSize = ThumbnailSize.MEDIUM
    ): ThumbnailResult = withContext(Dispatchers.IO) {
        val cacheKey = makeCacheKey(imageId, size)

        // Check memory cache
        synchronized(memoryCacheLock) {
            memoryCache[cacheKey]?.let {
                memoryHits.incrementAndGet()
                return@withContext ThumbnailResult.Success(it, ThumbnailSource.MEMORY)
            }
        }

        // Check disk cache
        try {
            val diskBitmap = diskCache.get(makeDiskCacheKey(imageId, size))
            if (diskBitmap != null) {
                diskHits.incrementAndGet()
                synchronized(memoryCacheLock) { memoryCache[cacheKey] = diskBitmap }
                loadingStates[cacheKey] = LoadingState.LOADED
                return@withContext ThumbnailResult.Success(diskBitmap, ThumbnailSource.DISK)
            }
        } catch (_: Exception) {}

        // Generate from path
        val bitmap = generateThumbnailFromPath(imagePath, size.pixels)
        if (bitmap != null) {
            generatedCount.incrementAndGet()
            try {
                diskCache.put(makeDiskCacheKey(imageId, size), bitmap)
            } catch (_: Exception) {}
            synchronized(memoryCacheLock) { memoryCache[cacheKey] = bitmap }
            loadingStates[cacheKey] = LoadingState.LOADED
            return@withContext ThumbnailResult.Success(bitmap, ThumbnailSource.GENERATED)
        }

        loadingStates[cacheKey] = LoadingState.FAILED
        ThumbnailResult.Placeholder(generatePlaceholder(size.pixels))
    }

    // ================================================================
    // Async thumbnail loading
    // ================================================================

    fun loadThumbnailAsync(
        imageId: Long,
        imagePath: String,
        size: ThumbnailSize = ThumbnailSize.MEDIUM,
        scope: CoroutineScope,
        onResult: (ThumbnailResult) -> Unit
    ) {
        val cacheKey = makeCacheKey(imageId, size)

        // Check if already loading
        if (pendingLoads.containsKey(cacheKey)) return

        val job = scope.launch(Dispatchers.IO) {
            loadingStates[cacheKey] = LoadingState.LOADING
            val result = loadThumbnailFromPath(imagePath, imageId, size)
            withContext(Dispatchers.Main) {
                onResult(result)
            }
            pendingLoads.remove(cacheKey)
        }
        pendingLoads[cacheKey] = job
    }

    fun cancelLoad(imageId: Long, size: ThumbnailSize = ThumbnailSize.MEDIUM) {
        val cacheKey = makeCacheKey(imageId, size)
        pendingLoads.remove(cacheKey)?.cancel()
    }

    fun cancelAllLoads() {
        pendingLoads.values.forEach { it.cancel() }
        pendingLoads.clear()
    }

    // ================================================================
    // Prefetch thumbnails
    // ================================================================

    suspend fun prefetchThumbnails(
        imagePaths: List<Pair<Long, String>>, // (imageId, path)
        size: ThumbnailSize = ThumbnailSize.MEDIUM
    ) = withContext(Dispatchers.IO) {
        for ((imageId, path) in imagePaths) {
            val diskKey = makeDiskCacheKey(imageId, size)
            if (!diskCache.contains(diskKey)) {
                try {
                    val bitmap = generateThumbnailFromPath(path, size.pixels)
                    if (bitmap != null) {
                        diskCache.put(diskKey, bitmap)
                        generatedCount.incrementAndGet()
                    }
                } catch (_: Exception) {
                    // Skip failures during prefetch
                }
            }
        }
    }

    // ================================================================
    // Thumbnail generation
    // ================================================================

    private fun generateThumbnailFromPath(imagePath: String, maxSize: Int): Bitmap? {
        return try {
            val file = File(imagePath)
            if (!file.exists()) return null

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(imagePath, options)

            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            val scale = calculateScale(options.outWidth, options.outHeight, maxSize)

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = scale
                inPreferredConfig = Bitmap.Config.RGB_565
                inMutable = false
            }

            val rawBitmap = BitmapFactory.decodeFile(imagePath, decodeOptions) ?: return null

            // Resize to exact dimensions if needed
            val result = if (rawBitmap.width > maxSize || rawBitmap.height > maxSize) {
                val ratio = minOf(maxSize.toFloat() / rawBitmap.width, maxSize.toFloat() / rawBitmap.height)
                val newWidth = (rawBitmap.width * ratio).toInt()
                val newHeight = (rawBitmap.height * ratio).toInt()
                val scaled = Bitmap.createScaledBitmap(rawBitmap, newWidth, newHeight, true)
                rawBitmap.recycle()
                scaled
            } else {
                rawBitmap
            }

            result
        } catch (e: Exception) {
            null
        }
    }

    private fun generateThumbnailFromBitmap(source: Bitmap, maxSize: Int): Bitmap? {
        return try {
            val ratio = minOf(maxSize.toFloat() / source.width, maxSize.toFloat() / source.height)
            if (ratio >= 1f) return source
            val newWidth = (source.width * ratio).toInt()
            val newHeight = (source.height * ratio).toInt()
            Bitmap.createScaledBitmap(source, newWidth, newHeight, true)
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateScale(width: Int, height: Int, maxSize: Int): Int {
        var scale = 1
        while (width / scale > maxSize || height / scale > maxSize) {
            scale *= 2
        }
        return scale
    }

    // ================================================================
    // Placeholder generation
    // ================================================================

    private fun generatePlaceholder(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        val paint = Paint().apply {
            color = PLACEHOLDER_COLOR
            isAntiAlias = true
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        // Image icon placeholder
        val iconPaint = Paint().apply {
            color = 0xFFBDBDBD.toInt()
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val iconSize = size / 3
        val iconLeft = (size - iconSize) / 2f
        val iconTop = (size - iconSize) / 2f

        // Draw mountain shape (simplified image icon)
        canvas.drawRect(iconLeft, iconTop + iconSize / 2, iconLeft + iconSize, iconTop + iconSize, iconPaint)
        val path = Path().apply {
            moveTo(iconLeft, iconTop + iconSize / 2)
            lineTo(iconLeft + iconSize / 2, iconTop)
            lineTo(iconLeft + iconSize, iconTop + iconSize / 2)
            close()
        }
        canvas.drawPath(path, iconPaint)

        // Circle in the triangle
        canvas.drawCircle(
            iconLeft + iconSize * 0.65f,
            iconTop + iconSize * 0.25f,
            iconSize * 0.15f,
            iconPaint
        )

        return bitmap
    }

    // ================================================================
    // Cache keys
    // ================================================================

    private fun makeCacheKey(imageId: Long, size: ThumbnailSize): String {
        return "${imageId}_${size.name}"
    }

    private fun makeDiskCacheKey(imageId: Long, size: ThumbnailSize): Long {
        // Encode size into the key
        return (imageId shl 16) or size.ordinal.toLong()
    }

    fun makeDiskCacheKeyForSize(imageId: Long, size: ThumbnailSize): Long {
        return makeDiskCacheKey(imageId, size)
    }

    // ================================================================
    // Cache management
    // ================================================================

    fun clearMemoryCache() {
        synchronized(memoryCacheLock) {
            memoryCache.values.forEach { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            memoryCache.clear()
            loadingStates.clear()
        }
    }

    suspend fun clearDiskCache() = withContext(Dispatchers.IO) {
        diskCache.clear()
    }

    fun evict(imageId: Long) {
        ThumbnailSize.entries.forEach { size ->
            val cacheKey = makeCacheKey(imageId, size)
            synchronized(memoryCacheLock) {
                memoryCache.remove(cacheKey)?.recycle()
            }
            loadingStates.remove(cacheKey)
            try {
                diskCache.evict(makeDiskCacheKey(imageId, size))
            } catch (_: Exception) {}
        }
    }

    fun isLoaded(imageId: Long, size: ThumbnailSize = ThumbnailSize.MEDIUM): Boolean {
        val cacheKey = makeCacheKey(imageId, size)
        return loadingStates[cacheKey] == LoadingState.LOADED
    }

    fun isLoading(imageId: Long, size: ThumbnailSize = ThumbnailSize.MEDIUM): Boolean {
        val cacheKey = makeCacheKey(imageId, size)
        return loadingStates[cacheKey] == LoadingState.LOADING
    }

    // ================================================================
    // Statistics
    // ================================================================

    fun getStats(): ThumbnailStats {
        return ThumbnailStats(
            memoryCacheSize = synchronized(memoryCacheLock) { memoryCache.size },
            memoryHits = memoryHits.get(),
            diskHits = diskHits.get(),
            generatedCount = generatedCount.get(),
            pendingLoads = pendingLoads.size,
            diskCacheSize = try { diskCache.size() } catch (_: Exception) { 0L }
        )
    }

    fun resetStats() {
        memoryHits.set(0)
        diskHits.set(0)
        generatedCount.set(0)
    }

    data class ThumbnailStats(
        val memoryCacheSize: Int,
        val memoryHits: Long,
        val diskHits: Long,
        val generatedCount: Long,
        val pendingLoads: Int,
        val diskCacheSize: Long
    )

    // ================================================================
    // Result types
    // ================================================================

    sealed class ThumbnailResult {
        data class Success(val bitmap: Bitmap, val source: ThumbnailSource) : ThumbnailResult()
        data class Placeholder(val bitmap: Bitmap) : ThumbnailResult()
        data class Error(val message: String) : ThumbnailResult()
    }

    enum class ThumbnailSource {
        MEMORY, DISK, GENERATED
    }

    // ================================================================
    // Thumbnail Grid View Adapter support
    // ================================================================

    data class ThumbnailGridItem(
        val imageId: Long,
        val imagePath: String,
        val imageName: String,
        val metadata: ImageMetadataEntity? = null,
        val size: ThumbnailSize = ThumbnailSize.MEDIUM
    )

    suspend fun loadGridThumbnails(
        items: List<ThumbnailGridItem>,
        scope: CoroutineScope,
        onItemLoaded: (Long, ThumbnailResult) -> Unit
    ) = withContext(Dispatchers.IO) {
        for (item in items) {
            // Check if already loaded
            if (isLoaded(item.imageId, item.size)) continue

            val result = loadThumbnailFromPath(item.imagePath, item.imageId, item.size)
            withContext(Dispatchers.Main) {
                onItemLoaded(item.imageId, result)
            }
        }
    }

    fun loadGridThumbnailAsync(
        item: ThumbnailGridItem,
        scope: CoroutineScope,
        onResult: (ThumbnailResult) -> Unit
    ) {
        loadThumbnailAsync(item.imageId, item.imagePath, item.size, scope, onResult)
    }
}