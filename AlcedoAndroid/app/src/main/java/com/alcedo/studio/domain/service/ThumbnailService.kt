package com.alcedo.studio.domain.service

import android.graphics.Bitmap
import com.alcedo.studio.data.local.ThumbnailDiskCache
import com.alcedo.studio.data.model.ImageMetadataEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ThumbnailService(
    private val diskCache: ThumbnailDiskCache,
    private val cacheDir: File
) {
    enum class ThumbnailSize(val pixels: Int) {
        MICRO(64), SMALL(128), MEDIUM(256), LARGE(512), XLARGE(1024)
    }

    sealed class ThumbnailResult {
        data class Success(val bitmap: Bitmap, val source: ThumbnailSource) : ThumbnailResult()
        data class Placeholder(val bitmap: Bitmap) : ThumbnailResult()
        data class Error(val message: String) : ThumbnailResult()
    }

    enum class ThumbnailSource { MEMORY, DISK, GENERATED }
    enum class LoadingState { NOT_LOADED, LOADING, LOADED, FAILED }
    enum class AnalysisPriority(val value: Int) { LOW(0), NORMAL(1), HIGH(2), URGENT(3) }

    data class ThumbnailGridItem(
        val imageId: Long,
        val imagePath: String,
        val imageName: String,
        val metadata: ImageMetadataEntity? = null,
        val size: ThumbnailSize = ThumbnailSize.MEDIUM
    )

    data class ThumbnailStats(
        val memoryCacheSize: Int = 0,
        val memoryHits: Long = 0,
        val diskHits: Long = 0,
        val generatedCount: Long = 0,
        val pendingLoads: Int = 0,
        val diskCacheSize: Long = 0,
        val diskCacheEntries: Int = 0,
        val diskCacheHitRate: Float = 0f,
        val diskCacheAverageEntrySize: Long = 0L,
        val analysisQueueSize: Int = 0
    )

    sealed class AnalysisRenditionResult {
        data class Ready(val bitmap: Bitmap, val fromCache: Boolean) : AnalysisRenditionResult()
        data class Error(val message: String) : AnalysisRenditionResult()
    }

    data class AnalysisRenditionRequest(
        val imageId: Long,
        val imagePath: String,
        val size: ThumbnailSize = ThumbnailSize.LARGE,
        val priority: AnalysisPriority = AnalysisPriority.NORMAL,
        val pipelineHash: Int = 0,
        val sourceLastModified: Long = 0L,
        val callback: ((AnalysisRenditionResult) -> Unit)? = null
    ) : Comparable<AnalysisRenditionRequest> {
        override fun compareTo(other: AnalysisRenditionRequest): Int =
            other.priority.value.compareTo(this.priority.value)
    }

    suspend fun loadThumbnail(imageId: Long, size: ThumbnailSize = ThumbnailSize.MEDIUM): ThumbnailResult =
        ThumbnailResult.Error("Stub")

    suspend fun loadThumbnailFromPath(
        imagePath: String, imageId: Long, size: ThumbnailSize = ThumbnailSize.MEDIUM
    ): ThumbnailResult = ThumbnailResult.Error("Stub")

    fun loadThumbnailAsync(
        imageId: Long, imagePath: String, size: ThumbnailSize = ThumbnailSize.MEDIUM,
        scope: kotlinx.coroutines.CoroutineScope, onResult: (ThumbnailResult) -> Unit
    ) {}

    fun cancelLoad(imageId: Long, size: ThumbnailSize = ThumbnailSize.MEDIUM) {}
    fun cancelAllLoads() {}
    fun isLoaded(imageId: Long, size: ThumbnailSize = ThumbnailSize.MEDIUM): Boolean = false
    fun isLoading(imageId: Long, size: ThumbnailSize = ThumbnailSize.MEDIUM): Boolean = false
    fun clearMemoryCache() {}
    suspend fun clearDiskCache() {}
    fun evict(imageId: Long) {}
    fun invalidateIfSourceModified(imageId: Long, sourceLastModified: Long) {}
    fun invalidateIfPipelineChanged(imageId: Long, pipelineHash: Int) {}
    fun invalidateSession(imageIds: List<Long>) {}
    fun getStats(): ThumbnailStats = ThumbnailStats()
    fun resetStats() {}
    fun shutdown() {}

    suspend fun requestAnalysisRendition(
        imageId: Long, imagePath: String, size: ThumbnailSize = ThumbnailSize.LARGE,
        priority: AnalysisPriority = AnalysisPriority.NORMAL, pipelineHash: Int = 0,
        sourceLastModified: Long = 0L
    ): AnalysisRenditionResult = AnalysisRenditionResult.Error("Stub")

    fun enqueueAnalysisRendition(request: AnalysisRenditionRequest) {}
    fun cancelAnalysisRendition(imageId: Long) {}
    fun cancelAllAnalysisRenditions() {}
    fun getAnalysisQueueSize(): Int = 0
    fun makeDiskCacheKeyForSize(imageId: Long, size: ThumbnailSize): Long = imageId
}
