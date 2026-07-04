package com.alcedo.studio.domain.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.alcedo.studio.data.local.ImageMetadataDao
import com.alcedo.studio.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class SemanticGenerationService(
    private val context: Context,
    private val aiService: AiService,
    private val metadataDao: ImageMetadataDao,
    private val modelDownloadService: ModelDownloadService
) {

    companion object {
        private const val TAG = "SemanticGenService"
        private const val BATCH_SIZE = 4
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var generationJob: Job? = null

    private val _progress = MutableStateFlow(SemanticGenerationProgress())
    val progress: StateFlow<SemanticGenerationProgress> = _progress.asStateFlow()

    val labelCatalog = listOf(
        "portrait", "landscape", "macro", "street photography", "documentary",
        "sports", "wildlife", "architecture", "aerial", "underwater",
        "fashion", "wedding", "event", "travel", "astro photography",
        "long exposure", "night photography", "food photography", "product photography",
        "natural light", "golden hour", "blue hour", "backlit", "silhouette",
        "soft light", "hard light", "studio lighting", "flash", "ambient light",
        "side lighting", "front lighting", "rim lighting", "low key", "high key",
        "vibrant colors", "muted tones", "monochrome", "black and white",
        "warm tones", "cool tones", "pastel", "high contrast", "low contrast",
        "sepia", "selective color", "complementary colors",
        "rule of thirds", "symmetry", "leading lines", "framing", "negative space",
        "minimalist", "diagonal", "centered", "off-center", "Dutch angle",
        "bird's eye view", "worm's eye view", "wide angle", "telephoto compression",
        "person", "group of people", "child", "elderly", "athlete",
        "animal", "bird", "insect", "pet", "wildlife in habitat",
        "flower", "tree", "forest", "mountain", "beach",
        "ocean", "river", "lake", "waterfall", "desert",
        "city skyline", "building", "bridge", "street", "interior",
        "vehicle", "car", "bicycle", "airplane", "boat",
        "food", "drink", "fruit", "vegetable", "dessert",
        "technology", "gadget", "phone", "computer", "camera",
        "happy", "sad", "serene", "dramatic", "mysterious",
        "romantic", "energetic", "calm", "nostalgic", "melancholic",
        "joyful", "peaceful", "tense", "whimsical", "elegant",
        "sunny", "cloudy", "rainy", "snowy", "foggy",
        "sunrise", "sunset", "twilight", "midday", "night",
        "spring", "summer", "autumn", "winter", "storm",
        "sharp", "soft focus", "bokeh", "motion blur", "grainy",
        "clean", "detailed", "abstract", "textured", "smooth"
    )

    suspend fun generateAllSemanticLabels(
        onImageProgress: ((Int, Int) -> Unit)? = null
    ): Boolean {
        val activeModel = modelDownloadService.getActiveModel()
        if (activeModel == null) {
            Log.w(TAG, "No active AI model. Cannot generate labels.")
            return false
        }

        val allMetadata = metadataDao.getAllMetadata()
        if (allMetadata.isEmpty()) return false

        _progress.value = SemanticGenerationProgress(
            totalImages = allMetadata.size,
            status = SemanticGenerationStatus.RUNNING
        )

        generationJob?.cancel()
        generationJob = scope.launch {
            var processed = 0
            val startTime = System.currentTimeMillis()

            for (metadata in allMetadata) {
                if (!isActive) {
                    _progress.value = _progress.value.copy(status = SemanticGenerationStatus.PAUSED)
                    break
                }

                _progress.value = _progress.value.copy(
                    currentImageId = metadata.imageId,
                    currentImageName = metadata.imageName
                )

                try {
                    val bitmap = loadBitmap(metadata.imagePath)
                    if (bitmap != null) {
                        val labels = aiService.generateLabels(metadata.imageId, bitmap)
                        val embedding = aiService.generateEmbedding(metadata.imageId, bitmap)
                        aiService.indexImage(metadata.imageId, embedding, activeModel.modelId)
                        bitmap.recycle()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process image ${metadata.imageId}: ${e.message}")
                }

                processed++
                _progress.value = _progress.value.copy(
                    processedImages = processed,
                    elapsedMs = System.currentTimeMillis() - startTime
                )

                withContext(Dispatchers.Main) {
                    onImageProgress?.invoke(processed, allMetadata.size)
                }
            }

            if (isActive) {
                _progress.value = _progress.value.copy(
                    status = SemanticGenerationStatus.COMPLETED,
                    processedImages = processed
                )
            }
        }

        generationJob?.join()
        return _progress.value.status == SemanticGenerationStatus.COMPLETED
    }

    suspend fun generateLabelsForImage(imageId: Long, bitmap: Bitmap): List<SemanticLabel> {
        return aiService.generateLabels(imageId, bitmap)
    }

    suspend fun generateEmbeddingForImage(imageId: Long, bitmap: Bitmap): FloatArray? {
        return try {
            aiService.generateEmbedding(imageId, bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate embedding: ${e.message}")
            null
        }
    }

    fun pauseGeneration() {
        generationJob?.cancel()
        _progress.value = _progress.value.copy(status = SemanticGenerationStatus.PAUSED)
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        _progress.value = _progress.value.copy(
            status = SemanticGenerationStatus.IDLE,
            processedImages = 0,
            currentImageId = 0L,
            currentImageName = ""
        )
    }

    fun reset() {
        cancelGeneration()
        _progress.value = SemanticGenerationProgress()
    }

    private fun loadBitmap(imagePath: String): Bitmap? {
        return try {
            val file = File(imagePath)
            if (!file.exists()) return null
            BitmapFactory.decodeFile(imagePath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap: ${e.message}")
            null
        }
    }

    suspend fun getExistingLabels(imageId: Long): List<SemanticLabel> {
        return aiService.getLabels(imageId)
    }

    suspend fun getLabelCount(): Int {
        return aiService.getTotalLabelCount()
    }

    suspend fun getIndexedImageCount(): Int {
        return aiService.getIndexedCount()
    }
}