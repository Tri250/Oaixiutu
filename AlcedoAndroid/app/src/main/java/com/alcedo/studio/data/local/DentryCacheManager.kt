package com.alcedo.studio.data.local

import com.alcedo.studio.data.model.SleeveElementEntity
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * LRU-based directory entry cache manager.
 * Provides fast lookups for directory entries and invalidates on filesystem changes.
 */
class DentryCacheManager(
    private val maxEntries: Int = 5000
) {
    // ================================================================
    // Cache entry
    // ================================================================

    data class CacheEntry(
        val elements: List<SleeveElementEntity>,
        val timestamp: Long = System.currentTimeMillis(),
        val ttl: Long = DEFAULT_TTL_MS
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > ttl
        fun isExpiredAt(now: Long): Boolean = now - timestamp > ttl
    }

    companion object {
        const val DEFAULT_TTL_MS = 60_000L // 1 minute
        const val LONG_TTL_MS = 300_000L   // 5 minutes
    }

    // ================================================================
    // LRU Cache implementation
    // ================================================================

    private val cache = object : LinkedHashMap<Long, CacheEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, CacheEntry>?): Boolean {
            return size > maxEntries
        }
    }

    private val lock = Any()

    // Statistics
    private val hits = AtomicLong(0)
    private val misses = AtomicLong(0)
    private val evictions = AtomicLong(0)
    private val invalidations = AtomicLong(0)

    // ================================================================
    // Cache operations
    // ================================================================

    fun get(parentId: Long): List<SleeveElementEntity>? {
        synchronized(lock) {
            val entry = cache[parentId]
            if (entry != null) {
                if (entry.isExpired()) {
                    cache.remove(parentId)
                    misses.incrementAndGet()
                    return null
                }
                hits.incrementAndGet()
                return entry.elements
            }
            misses.incrementAndGet()
            return null
        }
    }

    fun put(parentId: Long, elements: List<SleeveElementEntity>, ttl: Long = DEFAULT_TTL_MS) {
        synchronized(lock) {
            if (cache.size >= maxEntries && !cache.containsKey(parentId)) {
                evictions.incrementAndGet()
            }
            cache[parentId] = CacheEntry(elements = elements, ttl = ttl)
        }
    }

    fun remove(parentId: Long) {
        synchronized(lock) {
            cache.remove(parentId)
        }
    }

    fun contains(parentId: Long): Boolean {
        synchronized(lock) {
            val entry = cache[parentId] ?: return false
            return !entry.isExpired()
        }
    }

    fun clear() {
        synchronized(lock) {
            cache.clear()
        }
    }

    // ================================================================
    // Invalidation
    // ================================================================

    /**
     * Invalidate cache for a specific parent directory
     */
    fun invalidate(parentId: Long) {
        synchronized(lock) {
            cache.remove(parentId)
            invalidations.incrementAndGet()
        }
    }

    /**
     * Invalidate cache for a specific parent and all its ancestors
     */
    suspend fun invalidateChain(parentId: Long, elementDao: SleeveElementDao) {
        // Collect all ancestor IDs first (outside the lock) to minimize lock scope
        val idsToInvalidate = mutableListOf<Long>()
        idsToInvalidate.add(parentId)

        var currentId: Long? = parentId
        while (currentId != null) {
            val element = elementDao.getElementById(currentId)
            val pid = element?.parentId
            if (pid != null) {
                idsToInvalidate.add(pid)
                currentId = pid
            } else {
                currentId = null
            }
        }

        // Remove all collected IDs in a single synchronized block to avoid race conditions
        synchronized(lock) {
            for (id in idsToInvalidate) {
                cache.remove(id)
                invalidations.incrementAndGet()
            }
        }
    }

    /**
     * Invalidate cache for multiple parents
     */
    fun invalidateBatch(parentIds: List<Long>) {
        synchronized(lock) {
            for (id in parentIds) {
                cache.remove(id)
                invalidations.incrementAndGet()
            }
        }
    }

    /**
     * Invalidate all entries that are expired
     */
    fun invalidateExpired() {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val expired = cache.entries.filter { it.value.isExpiredAt(now) }
            for (entry in expired) {
                cache.remove(entry.key)
                invalidations.incrementAndGet()
            }
        }
    }

    /**
     * Invalidate entire cache
     */
    fun invalidateAll() {
        synchronized(lock) {
            val count = cache.size
            cache.clear()
            invalidations.addAndGet(count.toLong())
        }
    }

    // ================================================================
    // Cache statistics
    // ================================================================

    fun getStats(): CacheStats {
        synchronized(lock) {
            return CacheStats(
                size = cache.size,
                maxSize = maxEntries,
                hits = hits.get(),
                misses = misses.get(),
                evictions = evictions.get(),
                invalidations = invalidations.get(),
                hitRate = calculateHitRate()
            )
        }
    }

    fun resetStats() {
        hits.set(0)
        misses.set(0)
        evictions.set(0)
        invalidations.set(0)
    }

    private fun calculateHitRate(): Float {
        val total = hits.get() + misses.get()
        return if (total > 0) hits.get().toFloat() / total.toFloat() else 0f
    }

    fun getSize(): Int = synchronized(lock) { cache.size }

    fun getHitCount(): Long = hits.get()
    fun getMissCount(): Long = misses.get()
    fun getInvalidationCount(): Long = invalidations.get()

    data class CacheStats(
        val size: Int,
        val maxSize: Int,
        val hits: Long,
        val misses: Long,
        val evictions: Long,
        val invalidations: Long,
        val hitRate: Float
    ) {
        val utilization: Float get() = if (maxSize > 0) size.toFloat() / maxSize.toFloat() else 0f
    }
}