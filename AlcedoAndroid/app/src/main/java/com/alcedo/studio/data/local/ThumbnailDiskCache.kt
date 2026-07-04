package com.alcedo.studio.data.local

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.*
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger

/**
 * DiskLRU-style thumbnail cache with multiple resolution tiers,
 * eviction policy, statistics, and async read/write.
 *
 * Cache directory structure:
 *   cacheDir/
 *     thumb_[hash]_S   (128px)
 *     thumb_[hash]_M   (256px)
 *     thumb_[hash]_L   (512px)
 *     thumb_[hash]_XL  (1024px)
 *     journal          (cache journal for eviction tracking)
 */
class ThumbnailDiskCache(
    private val cacheDir: File,
    private val maxCacheSizeBytes: Long = 256L * 1024 * 1024, // 256MB default
    private val memoryCacheMaxEntries: Int = 128
) {
    enum class ResolutionTier(val suffix: String, val maxDimension: Int) {
        SMALL("S", 128),
        MEDIUM("M", 256),
        LARGE("L", 512),
        XLARGE("XL", 1024)
    }

    // ── In-memory LRU cache ──
    private val memoryCache = object : LruCache<String, Bitmap>(memoryCacheMaxEntries) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    // ── Journal for eviction tracking ──
    private val journal = ConcurrentHashMap<String, JournalEntry>()
    private val journalFile = File(cacheDir, "journal")
    private val journalMutex = Mutex()
    private val ioDispatcher = Dispatchers.IO

    // ── Statistics ──
    private val totalHits = AtomicLong(0)
    private val totalMisses = AtomicLong(0)
    private val totalWrites = AtomicLong(0)
    private val totalEvictions = AtomicLong(0)
    private val currentCacheSize = AtomicLong(0)

    data class JournalEntry(
        val key: String,
        val tier: ResolutionTier,
        val size: Long,
        var lastAccessTime: Long = System.currentTimeMillis(),
        var accessCount: Int = 0
    )

    data class CacheStats(
        val hits: Long,
        val misses: Long,
        val writes: Long,
        val evictions: Long,
        val hitRate: Float,
        val currentSizeBytes: Long,
        val maxSizeBytes: Long,
        val entryCount: Int,
        val memoryEntries: Int
    )

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        loadJournal()
        calculateCurrentSize()
    }

    // ============================================================
    // Read operations
    // ============================================================

    /**
     * Get a thumbnail from cache (synchronous).
     * Checks memory cache first, then disk cache.
     */
    fun get(imageId: String, tier: ResolutionTier = ResolutionTier.MEDIUM): Bitmap? {
        val key = makeKey(imageId, tier)

        // Check memory cache
        memoryCache.get(key)?.let {
            totalHits.incrementAndGet()
            touchJournal(key)
            return it
        }

        // Check disk cache
        val file = getCacheFile(imageId, tier)
        if (file.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    totalHits.incrementAndGet()
                    memoryCache.put(key, bitmap)
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
    suspend fun getAsync(imageId: String, tier: ResolutionTier = ResolutionTier.MEDIUM): Bitmap? {
        return withContext(ioDispatcher) {
            get(imageId, tier)
        }
    }

    /**
     * Check if a thumbnail exists in cache.
     */
    fun contains(imageId: String, tier: ResolutionTier = ResolutionTier.MEDIUM): Boolean {
        val key = makeKey(imageId, tier)
        if (memoryCache.get(key) != null) return true
        return getCacheFile(imageId, tier).exists()
    }

    // ============================================================
    // Write operations
    // ============================================================

    /**
     * Put a thumbnail into cache (synchronous).
     */
    fun put(
        imageId: String,
        bitmap: Bitmap,
        tier: ResolutionTier = ResolutionTier.MEDIUM,
        quality: Int = 85
    ) {
        val key = makeKey(imageId, tier)

        // Put in memory cache
        memoryCache.put(key, bitmap)

        // Write to disk asynchronously
        val file = getCacheFile(imageId, tier)
        try {
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            val fileSize = file.length()
            totalWrites.incrementAndGet()
            currentCacheSize.addAndGet(fileSize)

            // Update journal
            val entry = JournalEntry(key, tier, fileSize)
            journal[key] = entry

            // Evict if needed
            maybeEvict()
        } catch (e: Exception) {
            file.delete()
        }
    }

    /**
     * Put a thumbnail into cache (async).
     */
    suspend fun putAsync(
        imageId: String,
        bitmap: Bitmap,
        tier: ResolutionTier = ResolutionTier.MEDIUM,
        quality: Int = 85
    ) {
        withContext(ioDispatcher) {
            put(imageId, bitmap, tier, quality)
        }
    }

    /**
     * Put raw byte data into cache.
     */
    fun putBytes(
        imageId: String,
        data: ByteArray,
        tier: ResolutionTier = ResolutionTier.MEDIUM
    ) {
        val key = makeKey(imageId, tier)

        // Try to decode as bitmap for memory cache
        try {
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            if (bitmap != null) {
                memoryCache.put(key, bitmap)
            }
        } catch (_: Exception) {}

        // Write to disk
        val file = getCacheFile(imageId, tier)
        try {
            file.writeBytes(data)
            val fileSize = file.length()
            totalWrites.incrementAndGet()
            currentCacheSize.addAndGet(fileSize)

            val entry = JournalEntry(key, tier, fileSize)
            journal[key] = entry
            maybeEvict()
        } catch (e: Exception) {
            file.delete()
        }
    }

    // ============================================================
    // Eviction
    // ============================================================

    /**
     * Evict a specific thumbnail from cache.
     */
    fun evict(imageId: String, tier: ResolutionTier? = null) {
        if (tier != null) {
            val key = makeKey(imageId, tier)
            memoryCache.remove(key)
            val file = getCacheFile(imageId, tier)
            if (file.exists()) {
                val size = file.length()
                file.delete()
                currentCacheSize.addAndGet(-size)
                journal.remove(key)
            }
        } else {
            // Evict all tiers
            ResolutionTier.entries.forEach { t ->
                evict(imageId, t)
            }
        }
    }

    /**
     * Evict least recently used entries until cache is under limit.
     */
    private fun maybeEvict() {
        while (currentCacheSize.get() > maxCacheSizeBytes) {
            val oldest = journal.values
                .minByOrNull { it.lastAccessTime }
                ?: break

            val file = getCacheFileFromKey(oldest.key)
            if (file.exists()) {
                val size = file.length()
                file.delete()
                currentCacheSize.addAndGet(-size)
                totalEvictions.incrementAndGet()
            }
            journal.remove(oldest.key)
            memoryCache.remove(oldest.key)
        }
    }

    /**
     * Force eviction to free up space.
     */
    fun trimToSize(targetSizeBytes: Long) {
        while (currentCacheSize.get() > targetSizeBytes) {
            val oldest = journal.values
                .minByOrNull { it.lastAccessTime }
                ?: break

            val file = getCacheFileFromKey(oldest.key)
            if (file.exists()) {
                val size = file.length()
                file.delete()
                currentCacheSize.addAndGet(-size)
                totalEvictions.incrementAndGet()
            }
            journal.remove(oldest.key)
            memoryCache.remove(oldest.key)
        }
    }

    /**
     * Clear all caches.
     */
    fun clear() {
        memoryCache.evictAll()
        cacheDir.listFiles()?.forEach { it.delete() }
        journal.clear()
        currentCacheSize.set(0)
    }

    // ============================================================
    // Statistics
    // ============================================================

    fun getStats(): CacheStats {
        val hits = totalHits.get()
        val misses = totalMisses.get()
        val total = hits + misses
        return CacheStats(
            hits = hits,
            misses = misses,
            writes = totalWrites.get(),
            evictions = totalEvictions.get(),
            hitRate = if (total > 0) hits.toFloat() / total else 0f,
            currentSizeBytes = currentCacheSize.get(),
            maxSizeBytes = maxCacheSizeBytes,
            entryCount = journal.size,
            memoryEntries = memoryCache.size()
        )
    }

    fun resetStats() {
        totalHits.set(0)
        totalMisses.set(0)
        totalWrites.set(0)
        totalEvictions.set(0)
    }

    fun getSize(): Long = currentCacheSize.get()
    fun getCount(): Int = journal.size

    // ============================================================
    // Bulk operations
    // ============================================================

    /**
     * Prefetch thumbnails for a list of image IDs.
     * Useful for warming up the cache for visible items.
     */
    suspend fun prefetch(imageIds: List<String>, tier: ResolutionTier = ResolutionTier.MEDIUM) {
        withContext(ioDispatcher) {
            imageIds.forEach { id ->
                if (!contains(id, tier)) {
                    // Load from disk if available, otherwise skip
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

    // ============================================================
    // Helpers
    // ============================================================

    private fun makeKey(imageId: String, tier: ResolutionTier): String {
        return "${hash(imageId)}_${tier.suffix}"
    }

    private fun getCacheFile(imageId: String, tier: ResolutionTier): File {
        return File(cacheDir, "thumb_${makeKey(imageId, tier)}")
    }

    private fun getCacheFileFromKey(key: String): File {
        return File(cacheDir, "thumb_$key")
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

    // ============================================================
    // Journal persistence
    // ============================================================

    private fun loadJournal() {
        if (!journalFile.exists()) return
        try {
            journalFile.forEachLine { line ->
                val parts = line.split(",")
                if (parts.size >= 4) {
                    try {
                        val tier = ResolutionTier.valueOf(parts[1])
                        val entry = JournalEntry(
                            key = parts[0],
                            tier = tier,
                            size = parts[2].toLong(),
                            lastAccessTime = parts[3].toLong(),
                            accessCount = parts.getOrElse(4) { "0" }.toInt()
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
                    journalFile.printWriter().use { writer ->
                        journal.values.forEach { entry ->
                            writer.println(
                                "${entry.key},${entry.tier.name},${entry.size}," +
                                "${entry.lastAccessTime},${entry.accessCount}"
                            )
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun calculateCurrentSize() {
        var total = 0L
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith("thumb_")) {
                total += file.length()
            }
        }
        currentCacheSize.set(total)
    }
}