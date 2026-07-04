package com.alcedo.studio.domain.service

import android.graphics.Bitmap
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ImageAnalysisTask { DESCRIBE, SCORE, ANALYZE }
enum class ImageAnalysisStatus { PENDING, PROCESSING, COMPLETED, CANCELED, ERROR }

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

class ImageAnalysisInFlightGate {
    private val mutex = Mutex()
    suspend fun <T> runWithGate(block: suspend () -> T): T = mutex.withLock { block() }
    fun isLocked(): Boolean = mutex.isLocked
}

class ImageAnalysisService(
    private val aiService: AiService,
    private val thumbnailService: ThumbnailService
) {
    fun startAnalysis(
        items: List<ImageAnalysisItem>,
        options: ImageAnalysisOptions,
        onProgress: (ImageAnalysisProgress) -> Unit = {},
        onFinished: (List<ImageAnalysisResult>) -> Unit = {}
    ): Job = GlobalScope.launch { }

    fun registerCredentials(providerId: String, apiKey: String) {}
    fun shutdown() {}
}
