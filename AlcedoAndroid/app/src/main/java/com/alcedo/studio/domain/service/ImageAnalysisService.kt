package com.alcedo.studio.domain.service

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

// ================================================================
// Analysis task types
// ================================================================

enum class ImageAnalysisTask {
    DESCRIBE,
    SCORE,
    ANALYZE
}

enum class ImageAnalysisStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    CANCELED,
    ERROR
}

// ================================================================
// Analysis data classes
// ================================================================

data class ImageAnalysisItem(
    val imageId: Long,
    val imagePath: String,
    val thumbnail: Bitmap? = null
)

data class ImageAnalysisOptions(
    val tasks: Set<ImageAnalysisTask> = setOf(ImageAnalysisTask.DESCRIBE, ImageAnalysisTask.SCORE),
    val modelId: String? = null,
    val language: String = "zh-CN"
)

data class ImageAnalysisResult(
    val imageId: Long,
    val description: String = "",
    val rating: Int = 0,
    val tags: List<String> = emptyList(),
    val status: ImageAnalysisStatus = ImageAnalysisStatus.PENDING,
    val error: String? = null
)

data class ImageAnalysisProgress(
    val currentIndex: Int,
    val totalCount: Int,
    val currentImageId: Long,
    val status: ImageAnalysisStatus = ImageAnalysisStatus.PROCESSING
)

// ================================================================
// In-flight gate
// ================================================================

/**
 * Ensures at most one analysis batch runs at a time.
 * Uses a [Mutex] as a non-reentrant lock so concurrent callers are
 * suspended until the current analysis finishes.
 */
class ImageAnalysisInFlightGate {
    private val mutex = Mutex()

    suspend fun <T> runWithGate(block: suspend () -> T): T {
        return mutex.withLock { block() }
    }

    fun isLocked(): Boolean = mutex.isLocked
}

// ================================================================
// ImageAnalysisService
// ================================================================

/**
 * Orchestrates on-device AI image analysis using ONNX Runtime.
 *
 * This is the Android counterpart of the desktop's `image_analysis_service.cpp`.
 * Instead of delegating to a gRPC sidecar, all inference happens on-device
 * via [AiService] (which wraps ONNX Runtime CLIP/SigLIP models).
 *
 * [ThumbnailService] is used to obtain analysis-ready renditions
 * (downscaled bitmaps) when the caller only supplies an image path.
 */
class ImageAnalysisService(
    private val aiService: AiService,
    private val thumbnailService: ThumbnailService,
    private val imageAnalysisEncoder: ImageAnalysisEncoder? = null
) {
    companion object {
        private const val TAG = "ImageAnalysisService"
        private const val MIN_VALID_RATING = 1
        private const val MAX_VALID_RATING = 5
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightGate = ImageAnalysisInFlightGate()

    /**
     * Start a sequential analysis batch.
     *
     * Items are processed one by one on [Dispatchers.IO].
     * Progress is reported via [onProgress] after each item.
     * The returned [Job] can be cancelled to abort the remaining items.
     *
     * @param items images to analyse
     * @param options what analysis tasks to run and which model to use
     * @param onProgress called on the main thread after each item
     * @param onFinished called once when the batch completes or fails
     * @return a cancellable [Job]
     */
    fun startAnalysis(
        items: List<ImageAnalysisItem>,
        options: ImageAnalysisOptions,
        onProgress: (ImageAnalysisProgress) -> Unit = {},
        onFinished: (List<ImageAnalysisResult>) -> Unit = {}
    ): Job {
        return serviceScope.launch {
            inFlightGate.runWithGate {
                val results = mutableListOf<ImageAnalysisResult>()

                for ((index, item) in items.withIndex()) {
                    ensureActive()

                    val progress = ImageAnalysisProgress(
                        currentIndex = index,
                        totalCount = items.size,
                        currentImageId = item.imageId
                    )
                    withContext(Dispatchers.Main) { onProgress(progress) }

                    val result = analyseSingleItem(item, options)
                    results.add(result)
                }

                withContext(Dispatchers.Main) { onFinished(results) }
            }
        }
    }

    // ── Single-item analysis ──

    private suspend fun analyseSingleItem(
        item: ImageAnalysisItem,
        options: ImageAnalysisOptions
    ): ImageAnalysisResult = withContext(Dispatchers.IO) {
        try {
            // Obtain a bitmap for analysis
            val bitmap = item.thumbnail ?: loadAnalysisBitmap(item)
            if (bitmap == null) {
                return@withContext ImageAnalysisResult(
                    imageId = item.imageId,
                    status = ImageAnalysisStatus.ERROR,
                    error = "Could not load bitmap for image ${item.imageId}"
                )
            }

            // 使用 ImageAnalysisEncoder 进行图像编码预分析（如果可用）
            val embeddingResult = imageAnalysisEncoder?.let { encoder ->
                item.imagePath?.let { path ->
                    runCatching { encoder.encodeImage(path) }.getOrNull()
                }
            }

            var description = ""
            var rating = 0
            val tags = mutableListOf<String>()

            // ---- DESCRIBE task ----
            if (options.tasks.contains(ImageAnalysisTask.DESCRIBE)) {
                val labels = aiService.generateLabels(
                    imageId = item.imageId.toUInt(),
                    bitmap = bitmap,
                    modelId = options.modelId
                )
                description = labels.joinToString(", ") { it.label }
                tags.addAll(labels.map { it.label })
            }

            // ---- SCORE task ----
            if (options.tasks.contains(ImageAnalysisTask.SCORE)) {
                val (stars, reason) = aiService.rateImage(
                    imageId = item.imageId.toUInt(),
                    bitmap = bitmap
                )
                rating = stars
                if (description.isEmpty()) {
                    description = reason
                }
            }

            // ---- ANALYZE task (combined describe + score) ----
            if (options.tasks.contains(ImageAnalysisTask.ANALYZE)) {
                if (description.isEmpty()) {
                    val labels = aiService.generateLabels(
                        imageId = item.imageId.toUInt(),
                        bitmap = bitmap,
                        modelId = options.modelId
                    )
                    description = labels.joinToString(", ") { it.label }
                    tags.addAll(labels.map { it.label })
                }
                if (rating == 0) {
                    val (stars, _) = aiService.rateImage(
                        imageId = item.imageId.toUInt(),
                        bitmap = bitmap
                    )
                    rating = stars
                }
            }

            // Validate
            rating = rating.coerceIn(MIN_VALID_RATING, MAX_VALID_RATING)
            if (description.isBlank()) {
                description = "No description available"
            }

            ImageAnalysisResult(
                imageId = item.imageId,
                description = description,
                rating = rating,
                tags = tags.distinct(),
                status = ImageAnalysisStatus.COMPLETED
            )
        } catch (e: CancellationException) {
            Log.d(TAG, "Analysis cancelled for image ${item.imageId}")
            ImageAnalysisResult(
                imageId = item.imageId,
                status = ImageAnalysisStatus.CANCELED
            )
        } catch (e: Exception) {
            Log.e(TAG, "Analysis failed for image ${item.imageId}: ${e.message}")
            ImageAnalysisResult(
                imageId = item.imageId,
                status = ImageAnalysisStatus.ERROR,
                error = e.message
            )
        }
    }

    // ── Thumbnail loading ──

    private suspend fun loadAnalysisBitmap(item: ImageAnalysisItem): Bitmap? {
        if (item.imagePath.isBlank()) return null

        val result = thumbnailService.requestAnalysisRendition(
            imageId = item.imageId,
            imagePath = item.imagePath,
            size = ThumbnailService.ThumbnailSize.LARGE,
            priority = ThumbnailService.AnalysisPriority.NORMAL
        )

        return when (result) {
            is ThumbnailService.AnalysisRenditionResult.Ready -> result.bitmap
            is ThumbnailService.AnalysisRenditionResult.Error -> {
                Log.w(TAG, "Failed to load analysis bitmap for ${item.imageId}: ${result.message}")
                null
            }
        }
    }

    // ── Credential registration (no-op on Android, ONNX is local) ──

    /**
     * On the desktop this registers API credentials with the gRPC sidecar.
     * On Android all inference is on-device via ONNX Runtime, so this is a no-op.
     */
    fun registerCredentials(providerId: String, apiKey: String) {
        Log.d(TAG, "registerCredentials: no-op on Android (ONNX is local)")
    }

    // ── Cleanup ──

    fun shutdown() {
        serviceScope.cancel()
    }
}
