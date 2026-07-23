package com.alcedo.studio.data.local

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.*
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Enhanced disk LRU-style thumbnail cache with multiple resolution tiers,
 * multiple formats (JPEG/WebP/BMP), configurable settings, namespace support,
 * cache invalidation, and comprehensive statistics.
 *
 * Cache directory structure:
 *   cacheRoot/
 *     browse/                        (browse namespace - default)
 *       thumb_[hash]_S.jpg           (128px JPEG)
 *       thumb_[hash]_M.webp          (256px WebP)
 *       thumb_[hash]_L.bmp           (512px BMP)
 *       ...
 *     analysis/                      (analysis namespace)
 *       thumb_[hash]_XL.jpg
 *       ...
 *     project_[id]/                  (project-specific namespace)
 *       ...
 *     journal                        (cache journal for eviction tracking)
 */
class ThumbnailDiskCache(
    private val cacheDir: File,
    private val maxCacheSizeBytes: Long = 256L * 1024 * 1024,
    private val memoryCacheMaxEntries: Int = 128
) {
    // ================================================================
    // Format support
    // ================================================================

    enum class CacheFormat(val extension: String, val mimeType: String) {
        JPEG("jpg", "image/jpeg"),
        WEBP("webp", "image/webp"),
        BMP("bmp", "image/bmp")
    }

    enum class ResolutionTier(val suffix: String, val maxDimension: Int) {
        SMALL("S", 128),
        MEDIUM("M", 256),
        LARGE("L", 512),
        XLARGE("XL", 1024)
    }

    // ================================================================
    // Configurable cache settings
    // ================================================================

    data class CacheConfig(
        val enabled: Boolean = true,
        val cacheRootDir: File,
        val maxEntries: Int = 5000,
        val jpegQuality: Int = 85,
        val webpQuality: Int = 80,
        val defaultFormat: CacheFormat = CacheFormat.JPEG,
        val namespace: String = NAMESPACE_BROWSE
    ) {
        companion object {
            const val NAMESPACE_BROWSE = "browse"
            const val NAMESPACE_ANALYSIS = "analysis"
        }
    }

    private val config = AtomicReference(CacheConfig(
        enabled = true,
        cacheRootDir = cacheDir,
        maxEntries = 5000,
        jpegQuality = 85,
        webpQuality = 80,
        defaultFormat = CacheFormat.JPEG,
        namespace = CacheConfig.NAMESPACE_BROWSE
    ))

    fun updateConfig(block: CacheConfig.() -> CacheConfig) {
        config.set(config.get().block())
    }

    fun getConfig(): CacheConfig = config.get()

    // ================================================================
    // In-memory LRU cache
    // ================================================================

    private val memoryCacheLock = Any()
    private val memoryCache = object : LruCache<String, Bitmap>(memoryCacheMaxEntries) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    private fun memoryCacheGet(key: String): Bitmap? = synchronized(memoryCacheLock) { memoryCache.get(key) }
    private fun memoryCachePut(key: String, bitmap: Bitmap) = synchronized(memoryCacheLock) { memoryCache.put(key, bitmap) }
    private fun memoryCacheRemove(key: String) = synchronized(memoryCacheLock) { memoryCache.remove(key) }
    private fun memoryCacheEvictAll() = synchronized(memoryCacheLock) { memoryCache.evictAll() }
    private fun memoryCacheSize(): Int = synchronized(memoryCacheLock) { memoryCache.size() }

    // ================================================================
    // Journal for eviction tracking
    // ================================================================

    data class JournalEntry(
        val key: String,
        val tier: ResolutionTier,
        val format: CacheFormat,
        val namespace: String,
        val projectId: String?,
        val size: Long,
        var lastAccessTime: Long = System.currentTimeMillis(),
        var accessCount: Int = 0,
        var sourceLastModified: Long = 0L,
        var pipelineHash: Int = 0
    )

    // ================================================================
    // Enhanced Cache Statistics
    // ================================================================

    data class CacheStats(
        val hits: Long,
        val misses: Long,
        val hitRate: Float,
        val writes: Long,
        val evictions: Long,
        val totalEntries: Int,
        val totalSizeBytes: Long,
        val maxSizeBytes: Long,
        val averageEntrySizeBytes: Long,
        val memoryEntries: Int,
        val byNamespace: Map<String, Int>,
        val byFormat: Map<String, Int>
    )

    private val journal = ConcurrentHashMap<String, JournalEntry>()
    private val journalFile get() = File(config.get().cacheRootDir, "journal")
    private val journalMutex = Mutex()
    private val ioDispatcher = Dispatchers.IO

    // ── Statistics counters ──
    private val totalHits = AtomicLong(0)
    private val totalMisses = AtomicLong(0)
    private val totalWrites = AtomicLong(0)
    private val totalEvictions = AtomicLong(0)
    private val currentCacheSize = AtomicLong(0)
    private val currentEntryCount = AtomicInteger(0)

    init {
        try {
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            loadJournal()
            calculateCurrentSize()
        } catch (e: Throwable) {
            // Disk cache initialization must never crash the app.
            android.util.Log.e("ThumbnailDiskCache", "init failed", e)
        }
    }

    // ================================================================
    // Read operations
    // ================================================================

    /**
     * Get a thumbnail from cache (synchronous).
     * Checks memory cache first, then disk cache.
     */
    fun get(
        imageId: String,
        tier: ResolutionTier = ResolutionTier.MEDIUM,
        format: CacheFormat? = null
    ): Bitmap? {
        if (!config.get().enabled) return null

        val effectiveFormat = format ?: config.get().defaultFormat
        val key = makeKey(imageId, tier, effectiveFormat)

        // Check memory cache
        memoryCacheGet(key)?.let {
            totalHits.incrementAndGet()
            touchJournal(key)
            return it
        }

        // Check disk cache
        val file = getCacheFile(imageId, tier, effectiveFormat)
        if (file.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    totalHits.incrementAndGet()
                    memoryCachePut(key, bitmap)
                    touchJournal(key)
                    return bitmap
                }
            } catch (e: Exception) {
                file.delete()
            }
        }

        totalMisses.incrementAndGet()
        return null
    }

    /**
     * Get a thumbnail from cache (async).
     */
    suspend fun getAsync(
        imageId: String,
        tier: ResolutionTier = ResolutionTier.MEDIUM,
        format: CacheFormat? = null
    ): Bitmap? {
        val handler = CoroutineExceptionHandler { _, throwable ->
            android.util.Log.e("ThumbnailDiskCache", "getAsync failed for $imageId", throwable)
        }
        return withContext(ioDispatcher + handler) {
            try {
                get(imageId, tier, format)
            } catch (e: Exception) {
                android.util.Log.e("ThumbnailDiskCache", "getAsync exception for $imageId", e)
                null
            }
        }
    }

    /**
     * Check if a thumbnail exists in cache.
     */
    fun contains(
        imageId: String,
        tier: ResolutionTier = ResolutionTier.MEDIUM,
        format: CacheFormat? = null
    ): Boolean {
        if (!config.get().enabled) return false
        val effectiveFormat = format ?: config.get().defaultFormat
        val key = makeKey(imageId, tier, effectiveFormat)
        if (memoryCacheGet(key) != null) return true
        return getCacheFile(imageId, tier, effectiveFormat).exists()
    }

    // ================================================================
    // Long-key overloads (for backward compatibility with ThumbnailService)
    // ================================================================

    fun get(imageId: Long, tier: ResolutionTier = ResolutionTier.MEDIUM): Bitmap? {
        return get(imageId.toString(), tier)
    }

    fun put(imageId: Long, bitmap: Bitmap, tier: ResolutionTier = ResolutionTier.MEDIUM, quality: Int = 85) {
        put(imageId.toString(), bitmap, tier, quality)
    }

    fun evict(imageId: Long, tier: ResolutionTier? = null) {
        evict(imageId.toString(), tier)
    }

    fun contains(imageId: Long, tier: ResolutionTier = ResolutionTier.MEDIUM): Boolean {
        return contains(imageId.toString(), tier)
    }

    // ================================================================
    // Write operations
    // ================================================================

    /**
     * Put a thumbnail into cache (synchronous).
     */
    fun put(
        imageId: String,
        bitmap: Bitmap,
        tier: ResolutionTier = ResolutionTier.MEDIUM,
        quality: Int = 85,
        format: CacheFormat? = null,
        namespace: String? = null,
        projectId: String? = null,
        sourceLastModified: Long = 0L,
        pipelineHash: Int = 0
    ) {
        if (!config.get().enabled) return

        val effectiveFormat = format ?: config.get().defaultFormat
        val effectiveNamespace = namespace ?: config.get().namespace
        val effectiveQuality = when (effectiveFormat) {
            CacheFormat.JPEG -> if (quality > 0) quality else config.get().jpegQuality
            CacheFormat.WEBP -> if (quality > 0) quality else config.get().webpQuality
            CacheFormat.BMP -> 100 // BMP is lossless, quality is ignored
        }

        val key = makeKey(imageId, tier, effectiveFormat, effectiveNamespace)

        // Write to disk first, then update memory cache on success
        val file = getCacheFile(imageId, tier, effectiveFormat, effectiveNamespace)
        try {
            file.parentFile?.mkdirs()
            file.outputStream().use { out ->
                bitmap.compress(effectiveFormat.toBitmapCompressFormat(), effectiveQuality, out)
            }
            val fileSize = file.length()
            if (fileSize == 0L) {
                // Compress produced empty output — treat as failure
                file.delete()
                return
            }
            totalWrites.incrementAndGet()
            currentCacheSize.addAndGet(fileSize)
            currentEntryCount.incrementAndGet()

            // Update memory cache only after successful disk write
            memoryCachePut(key, bitmap)

            // Update journal
            val entry = JournalEntry(
                key = key,
                tier = tier,
                format = effectiveFormat,
                namespace = effectiveNamespace,
                projectId = projectId,
                size = fileSize,
                sourceLastModified = sourceLastModified,
                pipelineHash = pipelineHash
            )
            journal[key] = entry

            // Evict if needed
            maybeEvict()
        } catch (e: Exception) {
            // Rollback: remove partial file and any stale memory cache entry
            file.delete()
            memoryCacheRemove(key)
            journal.remove(key)
            android.util.Log.e("ThumbnailDiskCache", "put failed for $imageId, rolled back", e)
        }
    }

    /**
     * Put a thumbnail into cache (async).
     */
    suspend fun putAsync(
        imageId: String,
        bitmap: Bitmap,
        tier: ResolutionTier = ResolutionTier.MEDIUM,
        quality: Int = 85,
        format: CacheFormat? = null
    ) {
        withContext(ioDispatcher) {
            put(imageId, bitmap, tier, quality, format)
        }
    }

    /**
     * Put raw byte data into cache.
     */
    fun putBytes(
        imageId: String,
        data: ByteArray,
        tier: ResolutionTier = ResolutionTier.MEDIUM,
        format: CacheFormat? = null,
        namespace: String? = null,
        projectId: String? = null
    ) {
        if (!config.get().enabled) return

        val effectiveFormat = format ?: config.get().defaultFormat
        val effectiveNamespace = namespace ?: config.get().namespace
        val key = makeKey(imageId, tier, effectiveFormat, effectiveNamespace)

        // Try to decode as bitmap for memory cache
        try {
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            if (bitmap != null) {
                memoryCachePut(key, bitmap)
            }
        } catch (_: Exception) {}

        // Write to disk
        val file = getCacheFile(imageId, tier, effectiveFormat, effectiveNamespace)
        try {
            file.parentFile?.mkdirs()
            file.writeBytes(data)
            val fileSize = file.length()
            totalWrites.incrementAndGet()
            currentCacheSize.addAndGet(fileSize)
            currentEntryCount.incrementAndGet()

            val entry = JournalEntry(
                key = key,
                tier = tier,
                format = effectiveFormat,
                namespace = effectiveNamespace,
                projectId = projectId,
                size = fileSize
            )
            journal[key] = entry
            maybeEvict()
        } catch (e: Exception) {
            file.delete()
        }
    }

    // ================================================================
    // Eviction
    // ================================================================

    /**
     * Evict a specific thumbnail from cache.
     */
    fun evict(imageId: String, tier: ResolutionTier? = null, format: CacheFormat? = null) {
        if (tier != null && format != null) {
            val key = makeKey(imageId, tier, format)
            evictByKey(key)
        } else if (tier != null) {
            // Evict all formats for this tier
            CacheFormat.entries.forEach { fmt ->
                val key = makeKey(imageId, tier, fmt)
                evictByKey(key)
            }
        } else {
            // Evict all tiers and formats
            ResolutionTier.entries.forEach { t ->
                CacheFormat.entries.forEach { fmt ->
                    val key = makeKey(imageId, t, fmt)
                    evictByKey(key)
                }
            }
        }
    }

    private fun evictByKey(key: String) {
        memoryCacheRemove(key)
        val entry = journal[key] ?: return
        val file = getCacheFileFromKey(key)
        if (file.exists()) {
            val size = file.length()
            file.delete()
            currentCacheSize.addAndGet(-size)
            currentEntryCount.decrementAndGet()
            totalEvictions.incrementAndGet()
        }
        journal.remove(key)
    }

    /**
     * Evict least recently used entries until cache is under limit.
     */
    private fun maybeEvict() {
        val cfg = config.get()
        // Evict by size
        while (currentCacheSize.get() > maxCacheSizeBytes) {
            if (!evictOldest()) break
        }
        // Evict by entry count
        while (currentEntryCount.get() > cfg.maxEntries) {
            if (!evictOldest()) break
        }
    }

    /**
     * Evict the oldest (least recently used) entry.
     * @return true if an entry was evicted, false if no entries remain
     */
    fun evictOldest(): Boolean {
        val oldest = journal.values
            .minByOrNull { it.lastAccessTime }
            ?: return false

        evictByKey(oldest.key)
        return true
    }

    /**
     * Force eviction to free up space.
     */
    fun trimToSize(targetSizeBytes: Long) {
        while (currentCacheSize.get() > targetSizeBytes) {
            if (!evictOldest()) break
        }
    }

    // ================================================================
    // New cache management operations
    // ================================================================

    /**
     * Clear entire cache (all namespaces, all formats).
     */
    fun clearAll() {
        memoryCacheEvictAll()
        config.get().cacheRootDir.walkTopDown().forEach { file ->
            if (file.isFile && file.name.startsWith("thumb_")) {
                file.delete()
            }
        }
        journal.clear()
        currentCacheSize.set(0)
        currentEntryCount.set(0)
        calculateCurrentSize()
    }

    /**
     * Clear project-specific cache entries.
     */
    fun clearProject(projectId: String) {
        val toEvict = journal.values
            .filter { it.projectId == projectId }
            .map { it.key }
            .toList()

        toEvict.forEach { key -> evictByKey(key) }
    }

    /**
     * Refresh metadata for a cache entry without regenerating the thumbnail.
     * Updates lastAccessTime and accessCount.
     */
    fun refreshMetadata(key: String) {
        touchJournal(key)
    }

    /**
     * Refresh metadata using image ID and tier.
     */
    fun refreshMetadata(imageId: String, tier: ResolutionTier, format: CacheFormat? = null) {
        val effectiveFormat = format ?: config.get().defaultFormat
        val key = makeKey(imageId, tier, effectiveFormat)
        touchJournal(key)
    }

    // ================================================================
    // Cache invalidation
    // ================================================================

    /**
     * Invalidate cache entries when the source image has been modified.
     * Compares sourceLastModified timestamp with the stored value.
     */
    fun invalidateIfSourceModified(imageId: String, sourceLastModified: Long) {
        val keysToEvict = journal.values
            .filter { it.key.contains(hash(imageId)) && it.sourceLastModified > 0 && it.sourceLastModified < sourceLastModified }
            .map { it.key }
            .toList()

        keysToEvict.forEach { key -> evictByKey(key) }
    }

    /**
     * Invalidate cache entries when pipeline parameters have changed.
     * Compares pipelineHash with the stored value.
     */
    fun invalidateIfPipelineChanged(imageId: String, pipelineHash: Int) {
        val keysToEvict = journal.values
            .filter { it.key.contains(hash(imageId)) && it.pipelineHash != 0 && it.pipelineHash != pipelineHash }
            .map { it.key }
            .toList()

        keysToEvict.forEach { key -> evictByKey(key) }
    }

    /**
     * Invalidate all cache entries for a specific image (all tiers, all formats).
     */
    fun invalidateImage(imageId: String) {
        evict(imageId)
    }

    /**
     * Partial invalidation: invalidate only specific resolution levels.
     */
    fun invalidateTiers(imageId: String, tiers: Set<ResolutionTier>) {
        tiers.forEach { tier ->
            CacheFormat.entries.forEach { fmt ->
                val key = makeKey(imageId, tier, fmt)
                evictByKey(key)
            }
        }
    }

    /**
     * Invalidate all entries in a specific namespace.
     */
    fun invalidateNamespace(namespace: String) {
        val keysToEvict = journal.values
            .filter { it.namespace == namespace }
            .map { it.key }
            .toList()

        keysToEvict.forEach { key -> evictByKey(key) }
    }

    /**
     * Invalidate cache when an editing session ends.
     * Removes all entries that were created during the session for the given images.
     */
    fun invalidateSession(imageIds: List<String>) {
        imageIds.forEach { imageId ->
            invalidateImage(imageId)
        }
    }

    /**
     * Check if a cache entry is still valid by comparing source and pipeline metadata.
     */
    fun isValid(
        imageId: String,
        tier: ResolutionTier,
        format: CacheFormat? = null,
        sourceLastModified: Long = 0L,
        pipelineHash: Int = 0
    ): Boolean {
        val effectiveFormat = format ?: config.get().defaultFormat
        val key = makeKey(imageId, tier, effectiveFormat)
        val entry = journal[key] ?: return false

        if (sourceLastModified > 0 && entry.sourceLastModified > 0 && entry.sourceLastModified < sourceLastModified) {
            return false
        }
        if (pipelineHash != 0 && entry.pipelineHash != 0 && entry.pipelineHash != pipelineHash) {
            return false
        }
        return true
    }

    // ================================================================
    // Statistics
    // ================================================================

    fun getStats(): CacheStats {
        val hits = totalHits.get()
        val misses = totalMisses.get()
        val total = hits + misses
        val entries = journal.size
        val sizeBytes = currentCacheSize.get()

        val byNamespace = journal.values.groupBy { it.namespace }.mapValues { it.value.size }
        val byFormat = journal.values.groupBy { it.format.name }.mapValues { it.value.size }

        return CacheStats(
            hits = hits,
            misses = misses,
            hitRate = if (total > 0) hits.toFloat() / total else 0f,
            writes = totalWrites.get(),
            evictions = totalEvictions.get(),
            totalEntries = entries,
            totalSizeBytes = sizeBytes,
            maxSizeBytes = maxCacheSizeBytes,
            averageEntrySizeBytes = if (entries > 0) sizeBytes / entries else 0L,
            memoryEntries = memoryCacheSize(),
            byNamespace = byNamespace,
            byFormat = byFormat
        )
    }

    fun resetStats() {
        totalHits.set(0)
        totalMisses.set(0)
        totalWrites.set(0)
        totalEvictions.set(0)
    }

    fun getSize(): Long = currentCacheSize.get()
    fun getCount(): Int = currentEntryCount.get()

    // ================================================================
    // Bulk operations
    // ================================================================

    /**
     * Prefetch thumbnails for a list of image IDs.
     */
    suspend fun prefetch(imageIds: List<String>, tier: ResolutionTier = ResolutionTier.MEDIUM) {
        withContext(ioDispatcher) {
            imageIds.forEach { id ->
                if (!contains(id, tier)) {
                    val file = getCacheFile(id, tier)
                    if (file.exists()) {
                        get(id, tier)
                    }
                }
            }
        }
    }

    /**
     * Remove all thumbnails for a list of image IDs.
     */
    fun evictAll(imageIds: List<String>) {
        imageIds.forEach { evict(it) }
    }

    // ================================================================
    // Legacy compatibility
    // ================================================================

    /**
     * Clear all caches (legacy alias for clearAll).
     */
    fun clear() {
        clearAll()
    }

    fun size(): Long = currentCacheSize.get()

    // ================================================================
    // Helpers
    // ================================================================

    private fun makeKey(
        imageId: String,
        tier: ResolutionTier,
        format: CacheFormat = config.get().defaultFormat,
        namespace: String = config.get().namespace
    ): String {
        return "${namespace}_${hash(imageId)}_${tier.suffix}_${format.extension}"
    }

    private fun getCacheFile(
        imageId: String,
        tier: ResolutionTier,
        format: CacheFormat = config.get().defaultFormat,
        namespace: String = config.get().namespace
    ): File {
        val nsDir = File(config.get().cacheRootDir, namespace)
        return File(nsDir, "thumb_${hash(imageId)}_${tier.suffix}.${format.extension}")
    }

    private fun getCacheFileFromKey(key: String): File {
        // Key format: namespace_hash_tier_format
        // Parse namespace from key to determine subdirectory
        val namespace = key.substringBefore("_")
        val nsDir = File(config.get().cacheRootDir, namespace)
        return File(nsDir, "thumb_${key.substringAfter("_")}")
    }

    private fun hash(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun touchJournal(key: String) {
        journal[key]?.let {
            it.lastAccessTime = System.currentTimeMillis()
            it.accessCount++
        }
    }

    private fun CacheFormat.toBitmapCompressFormat(): Bitmap.CompressFormat = when (this) {
        CacheFormat.JPEG -> Bitmap.CompressFormat.JPEG
        CacheFormat.WEBP -> Bitmap.CompressFormat.WEBP
        CacheFormat.BMP -> Bitmap.CompressFormat.JPEG // Android has no BMP CompressFormat; fall back to JPEG
    }

    // ================================================================
    // Journal persistence
    // ================================================================

    private fun loadJournal() {
        if (!journalFile.exists()) return
        try {
            journalFile.forEachLine { line ->
                val parts = line.split(",")
                if (parts.size >= 5) {
                    try {
                        val tier = ResolutionTier.valueOf(parts[1])
                        val format = CacheFormat.valueOf(parts[2])
                        val entry = JournalEntry(
                            key = parts[0],
                            tier = tier,
                            format = format,
                            namespace = parts[3],
                            projectId = parts[4].takeIf { it != "null" },
                            size = parts[5].toLong(),
                            lastAccessTime = parts[6].toLong(),
                            accessCount = parts.getOrElse(7) { "0" }.toInt(),
                            sourceLastModified = parts.getOrElse(8) { "0" }.toLong(),
                            pipelineHash = parts.getOrElse(9) { "0" }.toInt()
                        )
                        journal[entry.key] = entry
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun saveJournal() {
        journalMutex.withLock {
            withContext(ioDispatcher) {
                try {
                    journalFile.parentFile?.mkdirs()
                    journalFile.printWriter().use { writer ->
                        journal.values.forEach { entry ->
                            writer.println(
                                "${entry.key},${entry.tier.name},${entry.format.name}," +
                                "${entry.namespace},${entry.projectId ?: "null"},${entry.size}," +
                                "${entry.lastAccessTime},${entry.accessCount}," +
                                "${entry.sourceLastModified},${entry.pipelineHash}"
                            )
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun calculateCurrentSize() {
        var total = 0L
        var count = 0
        config.get().cacheRootDir.walkTopDown().forEach { file ->
            if (file.isFile && file.name.startsWith("thumb_")) {
                total += file.length()
                count++
            }
        }
        currentCacheSize.set(total)
        currentEntryCount.set(count)
    }
}
