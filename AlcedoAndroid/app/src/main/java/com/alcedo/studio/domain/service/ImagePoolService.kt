package com.alcedo.studio.domain.service

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.util.Log
import com.alcedo.studio.data.model.ImageBuffer
import com.alcedo.studio.data.model.ImageModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class ImagePoolService(private val context: Context) : ComponentCallbacks2 {
    companion object {
        private const val TAG = "ImagePoolService"
        private const val DEFAULT_POOL_SIZE = 8
        private const val MAX_POOL_SIZE = 16
        private const val MIN_POOL_SIZE = 2
        private const val LOW_MEMORY_THRESHOLD_RATIO = 0.75f
        private const val CRITICAL_MEMORY_THRESHOLD_RATIO = 0.90f
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var poolSize = DEFAULT_POOL_SIZE

    // LRU pool: imageId -> ImageBuffer
    private val imagePool = object : LinkedHashMap<UInt, PoolEntry>(DEFAULT_POOL_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<UInt, PoolEntry>?): Boolean {
            if (eldest == null) return false
            return if (size > poolSize && !eldest.value.pinned) {
                evictImage(eldest.key)
                true
            } else false
        }
    }

    private val pinnedImages = ConcurrentHashMap<UInt, Boolean>()
    private val fullDataPinned = ConcurrentHashMap<UInt, Boolean>()
    private val thumbDataPinned = ConcurrentHashMap<UInt, Boolean>()

    private val totalMemoryBytes = AtomicLong(0L)
    private val maxMemoryBytes = Runtime.getRuntime().maxMemory()

    private val _poolStats = MutableStateFlow(PoolStats())
    val poolStats: StateFlow<PoolStats> = _poolStats.asStateFlow()

    private var isLowMemory = false
    private var isCriticalMemory = false

    init {
        try {
            context.registerComponentCallbacks(this)
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "registerComponentCallbacks failed", e)
        }
        try {
            startMemoryMonitor()
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "startMemoryMonitor failed", e)
        }
    }

    data class PoolEntry(
        val imageId: UInt,
        val imageData: ImageBuffer? = null,
        val thumbnail: ImageBuffer? = null,
        var pinned: Boolean = false,
        var lastAccessTime: Long = System.currentTimeMillis(),
        val estimatedSizeBytes: Long = 0L
    )

    data class PoolStats(
        val poolSize: Int = 0,
        val pinnedCount: Int = 0,
        val totalMemoryMB: Float = 0f,
        val maxMemoryMB: Float = 0f,
        val usageRatio: Float = 0f,
        val evictionCount: Long = 0L,
        val isLowMemory: Boolean = false,
        val isCriticalMemory: Boolean = false
    )

    private var evictionCount = AtomicLong(0L)

    // ── LRU Operations ──

    @Synchronized
    fun putImage(imageId: UInt, imageData: ImageBuffer?, thumbnail: ImageBuffer? = null) {
        val existing = imagePool[imageId]
        val sizeBytes = estimateImageSize(imageData) + estimateImageSize(thumbnail)

        if (existing != null) {
            existing.imageData?.release()
            existing.thumbnail?.release()
            totalMemoryBytes.addAndGet(-existing.estimatedSizeBytes)
        }

        val entry = PoolEntry(
            imageId = imageId,
            imageData = imageData,
            thumbnail = thumbnail,
            pinned = fullDataPinned.containsKey(imageId) || thumbDataPinned.containsKey(imageId),
            lastAccessTime = System.currentTimeMillis(),
            estimatedSizeBytes = sizeBytes
        )

        imagePool[imageId] = entry
        totalMemoryBytes.addAndGet(sizeBytes)
        updateStats()
    }

    @Synchronized
    fun getImage(imageId: UInt): ImageBuffer? {
        val entry = imagePool[imageId]
        entry?.lastAccessTime = System.currentTimeMillis()
        return entry?.imageData
    }

    @Synchronized
    fun getThumbnail(imageId: UInt): ImageBuffer? {
        val entry = imagePool[imageId]
        entry?.lastAccessTime = System.currentTimeMillis()
        return entry?.thumbnail
    }

    @Synchronized
    fun contains(imageId: UInt): Boolean = imagePool.containsKey(imageId)

    @Synchronized
    fun removeImage(imageId: UInt) {
        val entry = imagePool.remove(imageId)
        if (entry != null) {
            entry.imageData?.release()
            entry.thumbnail?.release()
            totalMemoryBytes.addAndGet(-entry.estimatedSizeBytes)
            updateStats()
        }
    }

    // ── Pin / Unpin (Prevent Eviction) ──

    @Synchronized
    fun pinImage(imageId: UInt) {
        fullDataPinned[imageId] = true
        val entry = imagePool[imageId]
        if (entry != null) {
            entry.pinned = true
            imagePool[imageId] = entry
        }
        updateStats()
    }

    @Synchronized
    fun unpinImage(imageId: UInt) {
        fullDataPinned.remove(imageId)
        val entry = imagePool[imageId]
        if (entry != null) {
            entry.pinned = thumbDataPinned.containsKey(imageId)
            imagePool[imageId] = entry
        }
        updateStats()
    }

    @Synchronized
    fun pinThumbnail(imageId: UInt) {
        thumbDataPinned[imageId] = true
        val entry = imagePool[imageId]
        if (entry != null) {
            entry.pinned = true
            imagePool[imageId] = entry
        }
        updateStats()
    }

    @Synchronized
    fun unpinThumbnail(imageId: UInt) {
        thumbDataPinned.remove(imageId)
        val entry = imagePool[imageId]
        if (entry != null) {
            entry.pinned = fullDataPinned.containsKey(imageId)
            imagePool[imageId] = entry
        }
        updateStats()
    }

    @Synchronized
    fun isPinned(imageId: UInt): Boolean {
        return fullDataPinned.containsKey(imageId) || thumbDataPinned.containsKey(imageId)
    }

    // ── Memory Pressure Monitoring ──

    override fun onTrimMemory(level: Int) {
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                Log.w(TAG, "Memory pressure: moderate")
                isLowMemory = true
                evictNonPinned(0.25f)
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                Log.w(TAG, "Memory pressure: critical")
                isCriticalMemory = true
                evictAllNonPinned()
            }
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                Log.i(TAG, "UI hidden, trimming memory")
                evictNonPinned(0.5f)
            }
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                evictNonPinned(0.75f)
            }
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                evictAllNonPinned()
            }
        }
        updateStats()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {}

    override fun onLowMemory() {
        Log.w(TAG, "onLowMemory: evicting all non-pinned images")
        isLowMemory = true
        isCriticalMemory = true
        evictAllNonPinned()
        updateStats()
    }

    // ── Automatic Eviction on Low Memory ──

    @Synchronized
    fun evictNonPinned(fraction: Float) {
        val toEvict = imagePool.entries
            .filter { !it.value.pinned }
            .take((imagePool.size * fraction).toInt().coerceAtLeast(0))

        for (entry in toEvict) {
            evictImage(entry.key)
        }
    }

    @Synchronized
    fun evictAllNonPinned() {
        val toEvict = imagePool.entries
            .filter { !it.value.pinned }
            .map { it.key }
            .toList()

        for (imageId in toEvict) {
            evictImage(imageId)
        }
    }

    @Synchronized
    fun evictAll() {
        val keys = imagePool.keys.toList()
        for (imageId in keys) {
            evictImage(imageId)
        }
    }

    private fun evictImage(imageId: UInt) {
        val entry = imagePool.remove(imageId)
        if (entry != null) {
            entry.imageData?.release()
            entry.thumbnail?.release()
            totalMemoryBytes.addAndGet(-entry.estimatedSizeBytes)
            evictionCount.incrementAndGet()
            Log.d(TAG, "Evicted image $imageId (${entry.estimatedSizeBytes} bytes)")
        }
    }

    // ── Pool Statistics ──

    @Synchronized
    fun getStats(): PoolStats {
        return PoolStats(
            poolSize = imagePool.size,
            pinnedCount = fullDataPinned.size + thumbDataPinned.size,
            totalMemoryMB = totalMemoryBytes.get() / (1024f * 1024f),
            maxMemoryMB = maxMemoryBytes / (1024f * 1024f),
            usageRatio = if (maxMemoryBytes > 0) totalMemoryBytes.get().toFloat() / maxMemoryBytes else 0f,
            evictionCount = evictionCount.get(),
            isLowMemory = isLowMemory,
            isCriticalMemory = isCriticalMemory
        )
    }

    private fun updateStats() {
        _poolStats.value = getStats()
    }

    // ── Pool Size Management ──

    fun setPoolSize(size: Int) {
        poolSize = size.coerceIn(MIN_POOL_SIZE, MAX_POOL_SIZE)
        // Trigger eviction if needed
        if (imagePool.size > poolSize) {
            evictNonPinned(1f)
        }
        updateStats()
    }

    fun getPoolSize(): Int = poolSize

    fun getImageCount(): Int = imagePool.size

    // ── Memory Monitor ──

    private fun startMemoryMonitor() {
        scope.launch {
            while (isActive) {
                delay(5000) // Check every 5 seconds
                val runtime = Runtime.getRuntime()
                val usedMemory = runtime.totalMemory() - runtime.freeMemory()
                val ratio = usedMemory.toFloat() / runtime.maxMemory()

                if (ratio > CRITICAL_MEMORY_THRESHOLD_RATIO && !isCriticalMemory) {
                    isCriticalMemory = true
                    isLowMemory = true
                    evictAllNonPinned()
                    Log.w(TAG, "Critical memory: ${"%.1f".format(ratio * 100)}%")
                } else if (ratio > LOW_MEMORY_THRESHOLD_RATIO && !isLowMemory) {
                    isLowMemory = true
                    evictNonPinned(0.5f)
                    Log.w(TAG, "Low memory: ${"%.1f".format(ratio * 100)}%")
                } else if (ratio < LOW_MEMORY_THRESHOLD_RATIO) {
                    isLowMemory = false
                    isCriticalMemory = false
                }

                updateStats()
            }
        }
    }

    // ── Helpers ──

    private fun estimateImageSize(buffer: ImageBuffer?): Long {
        if (buffer == null) return 0L
        var size = 0L
        buffer.buffer?.let { size += it.size }
        buffer.cpuData?.let { size += it.allocationByteCount.toLong() }
        return size
    }

    // ── Cleanup ──

    fun shutdown() {
        scope.cancel()
        context.unregisterComponentCallbacks(this)
        evictAll()
    }
}