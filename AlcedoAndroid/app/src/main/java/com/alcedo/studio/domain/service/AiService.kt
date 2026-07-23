package com.alcedo.studio.domain.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.alcedo.studio.data.model.*
import com.alcedo.studio.ndk.AiNdkBridge
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

/**
 * Complete AI service integrating ONNX Runtime for on-device CLIP/SigLIP model inference,
 * HNSW vector index for fast nearest-neighbor search, semantic label generation,
 * vector embedding generation, and natural language query → vector → search pipeline.
 *
 * Uses [ClipInferenceEngine] for real ONNX Runtime inference, with fallback to
 * native NDK CLIP inference and deterministic hash-based embeddings when models
 * are not available.
 *
 * All heavy operations are cancellable and report progress through StateFlow.
 */
class AiService(private val context: Context) {

    companion object {
        private const val TAG = "AiService"
        private const val DEFAULT_EMBEDDING_DIM = 512
        private const val DEFAULT_SEARCH_K = 20
        private const val LABEL_CONFIDENCE_THRESHOLD = 0.15f
        private const val MAX_LABELS_PER_IMAGE = 10
    }

    // ── ONNX Runtime CLIP Engine ──

    private val clipEngine = ClipInferenceEngine(context)

    // ── Model Catalog ──

    private val modelCatalog = ConcurrentHashMap<String, AiModelProfile>()
    private val modelCatalogFlow = MutableStateFlow<List<AiModelProfile>>(emptyList())

    // ── Model Loading State ──

    private val _modelLoadStatus = MutableStateFlow(ClipInferenceEngine.ModelStatus.NOT_LOADED)
    val modelLoadStatus: StateFlow<ClipInferenceEngine.ModelStatus> = _modelLoadStatus.asStateFlow()

    private val _embeddingDimension = MutableStateFlow(DEFAULT_EMBEDDING_DIM)
    val embeddingDimension: StateFlow<Int> = _embeddingDimension.asStateFlow()

    // ── Vector Index ──

    private val vectorIndex = mutableListOf<VectorIndexEntry>()
    private var hnswIndexHandle: Long = 0

    // ── Label Store ──

    private val labelStore = ConcurrentHashMap<UInt, List<SemanticLabel>>()

    // ── Task Tracking ──

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val _taskProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val taskProgress: StateFlow<Map<String, Float>> = _taskProgress.asStateFlow()

    // ── Label Catalog (for zero-shot classification) ──

    val labelCatalog = listOf(
        "portrait", "landscape", "macro", "street photography", "documentary",
        "sports", "wildlife", "architecture", "aerial", "underwater",
        "fashion", "wedding", "event", "travel", "astro photography",
        "long exposure", "night photography", "food photography", "product photography",
        "natural light", "golden hour", "blue hour", "backlit", "silhouette",
        "soft light", "hard light", "studio lighting", "flash", "ambient light",
        "vibrant colors", "muted tones", "monochrome", "black and white",
        "warm tones", "cool tones", "pastel", "high contrast", "low contrast",
        "rule of thirds", "symmetry", "leading lines", "framing", "negative space",
        "minimalist", "diagonal", "centered", "off-center", "Dutch angle",
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
        "sunny", "cloudy", "rainy", "snowy", "foggy",
        "sunrise", "sunset", "twilight", "midday", "night",
        "spring", "summer", "autumn", "winter", "storm",
        "sharp", "soft focus", "bokeh", "motion blur", "grainy",
        "clean", "detailed", "abstract", "textured", "smooth"
    )

    init {
        // Initialize HNSW index first (failsafe). Catalog registration is wrapped
        // in a try-catch so any unexpected failure cannot crash app startup.
        try {
            // Register default model profiles
            modelCatalog["jina-clip-v2"] = AiModelProfile(
                modelId = "jina-clip-v2",
                modelName = "Jina CLIP v2",
                modelType = AiModelType.CLIP,
                description = "Lightweight multilingual CLIP model for image tagging and search"
            )
            modelCatalog["siglip2"] = AiModelProfile(
                modelId = "siglip2",
                modelName = "SigLIP 2",
                modelType = AiModelType.SIGLIP,
                description = "Google SigLIP 2 for zero-shot image classification"
            )
            modelCatalog["mobileclip-s2"] = AiModelProfile(
                modelId = "mobileclip-s2",
                modelName = "MobileCLIP S2",
                modelType = AiModelType.CLIP,
                description = "Apple MobileCLIP optimized for on-device inference"
            )
            modelCatalogFlow.value = modelCatalog.values.toList()
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "init: model catalog registration failed", e)
        }

        try {
            // Initialize HNSW index (AiNdkBridge already returns 0L on failure)
            hnswIndexHandle = AiNdkBridge.hnswCreateIndex(DEFAULT_EMBEDDING_DIM)
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "init: HNSW index creation failed", e)
            hnswIndexHandle = 0L
        }
    }

    // ── Model Catalog ──

    fun getModelCatalog(): List<AiModelProfile> = modelCatalog.values.toList()
    fun getModelCatalogFlow(): StateFlow<List<AiModelProfile>> = modelCatalogFlow

    fun getActiveModel(): AiModelProfile? = modelCatalog.values.find { it.isActive }

    fun getModel(modelId: String): AiModelProfile? = modelCatalog[modelId]

    suspend fun activateModel(modelId: String) = withContext(Dispatchers.IO) {
        val model = modelCatalog[modelId] ?: return@withContext
        // Deactivate all models of the same type
        modelCatalog.values.forEach { profile ->
            if (profile.modelType == model.modelType && profile.modelId != modelId) {
                modelCatalog[profile.modelId] = profile.copy(isActive = false)
            }
        }
        modelCatalog[modelId] = model.copy(isActive = true)
        modelCatalogFlow.value = modelCatalog.values.toList()

        // Load the model into the CLIP inference engine
        val loaded = clipEngine.loadModel(modelId)
        _modelLoadStatus.value = clipEngine.modelStatus
        _embeddingDimension.value = clipEngine.embeddingDim
        if (!loaded) {
            Log.w(TAG, "Failed to load ONNX model for $modelId, will use fallback embeddings")
        }
    }

    suspend fun deactivateModel(modelId: String) = withContext(Dispatchers.IO) {
        modelCatalog[modelId]?.let { model ->
            modelCatalog[modelId] = model.copy(isActive = false)
            modelCatalogFlow.value = modelCatalog.values.toList()
        }
        if (clipEngine.activeModelId == modelId) {
            clipEngine.unloadModel()
            _modelLoadStatus.value = ClipInferenceEngine.ModelStatus.NOT_LOADED
        }
    }

    // ── CLIP Engine Access ──

    /**
     * Get the underlying CLIP inference engine for direct access.
     */
    fun getClipEngine(): ClipInferenceEngine = clipEngine

    /**
     * Check if the ONNX model is currently loaded and ready for inference.
     */
    fun isModelLoaded(): Boolean = clipEngine.isLoaded

    /**
     * Get the current model loading status.
     */
    fun getModelLoadStatus(): ClipInferenceEngine.ModelStatus = clipEngine.modelStatus

    /**
     * Manually load a model into the CLIP inference engine.
     */
    suspend fun loadModel(modelId: String): Boolean {
        val result = clipEngine.loadModel(modelId)
        _modelLoadStatus.value = clipEngine.modelStatus
        if (!result) {
            Log.w(TAG, "Model load failed for $modelId — status: ${clipEngine.modelStatus}")
            _embeddingDimension.value = DEFAULT_EMBEDDING_DIM
            return false
        }
        _embeddingDimension.value = clipEngine.embeddingDim
        return true
    }

    /**
     * Unload the current model from the CLIP inference engine.
     */
    fun unloadModel() {
        clipEngine.unloadModel()
        _modelLoadStatus.value = ClipInferenceEngine.ModelStatus.NOT_LOADED
    }

    // ── Label Generation ──

    /**
     * Generate semantic labels for an image using the active AI model.
     * Uses zero-shot classification via [ClipInferenceEngine] when available,
     * otherwise falls back to native NDK or deterministic embeddings.
     */
    suspend fun generateLabels(
        imageId: UInt,
        bitmap: Bitmap?,
        modelId: String? = null
    ): List<SemanticLabel> = withContext(Dispatchers.Default) {
        if (bitmap == null || bitmap.isRecycled) return@withContext emptyList()

        val taskId = "label-gen-$imageId"
        setTaskProgress(taskId, 0f)

        try {
            val activeModelId = modelId ?: getActiveModel()?.modelId ?: "mobileclip-s2"

            setTaskProgress(taskId, 0.1f)

            // Try real ONNX zero-shot classification first
            if (clipEngine.isLoaded && clipEngine.activeModelId == activeModelId) {
                val classificationResults = clipEngine.zeroShotClassify(bitmap, labelCatalog)
                if (classificationResults.isNotEmpty()) {
                    val labels = classificationResults
                        .filter { it.second >= LABEL_CONFIDENCE_THRESHOLD }
                        .take(MAX_LABELS_PER_IMAGE)
                        .mapIndexed { idx, (label, score) ->
                            SemanticLabel(
                                labelId = "${imageId}-${idx}",
                                imageId = imageId,
                                label = label,
                                confidence = score,
                                modelId = activeModelId
                            )
                        }
                    labelStore[imageId] = labels
                    setTaskProgress(taskId, 1f)
                    return@withContext labels
                }
            }

            // Fallback: use embedding-based zero-shot classification
            val embeddingDim = getEmbeddingDim(activeModelId)

            setTaskProgress(taskId, 0.3f)

            // Generate image embedding
            val imageEmbedding = generateEmbedding(imageId, bitmap, activeModelId)

            setTaskProgress(taskId, 0.6f)

            // Use zero-shot classification: compare image embedding with text embeddings
            val results = mutableListOf<SemanticLabel>()
            val batchSize = 20
            val labelBatches = labelCatalog.chunked(batchSize)

            for ((batchIdx, batch) in labelBatches.withIndex()) {
                for (label in batch) {
                    val textEmbedding = generateTextEmbedding(label, embeddingDim)
                    val similarity = cosineSimilarity(imageEmbedding, textEmbedding)
                    if (similarity >= LABEL_CONFIDENCE_THRESHOLD) {
                        results.add(SemanticLabel(
                            labelId = "${imageId}-${results.size}",
                            imageId = imageId,
                            label = label,
                            confidence = similarity,
                            modelId = activeModelId
                        ))
                    }
                }
                setTaskProgress(taskId, 0.6f + 0.3f * (batchIdx + 1) / labelBatches.size)
            }

            // Sort by confidence descending, take top N
            val sorted = results.sortedByDescending { it.confidence }.take(MAX_LABELS_PER_IMAGE)

            // Store in label store
            labelStore[imageId] = sorted

            setTaskProgress(taskId, 1f)
            sorted
        } catch (e: Exception) {
            Log.e(TAG, "Label generation failed for $imageId: ${e.message}")
            setTaskProgress(taskId, -1f)
            emptyList()
        } finally {
            cleanupTask(taskId)
        }
    }

    // ── Embedding Generation ──

    /**
     * Generate a vector embedding for an image.
     * Uses [ClipInferenceEngine] for real ONNX inference when the model is loaded,
     * falls back to native NDK or deterministic hash-based embeddings.
     */
    suspend fun generateEmbedding(
        imageId: UInt,
        bitmap: Bitmap?,
        modelId: String? = null
    ): FloatArray = withContext(Dispatchers.Default) {
        if (bitmap == null) return@withContext FloatArray(DEFAULT_EMBEDDING_DIM)

        val activeModelId = modelId ?: getActiveModel()?.modelId ?: "mobileclip-s2"
        val dim = getEmbeddingDim(activeModelId)

        // Try real ONNX inference first
        if (clipEngine.isLoaded && clipEngine.activeModelId == activeModelId) {
            try {
                val embedding = clipEngine.encodeImage(bitmap)
                if (embedding.isNotEmpty() && !embedding.all { it == 0f }) {
                    return@withContext embedding
                }
            } catch (e: Exception) {
                Log.w(TAG, "ONNX image encoding failed, falling back: ${e.message}")
            }
        }

        // Try native CLIP inference if a model is available
        val inputSize = getModelInputSize(activeModelId)
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val rgbBytes = ByteArray(inputSize * inputSize * 3)
        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            rgbBytes[i * 3] = ((pixel shr 16) and 0xFF).toByte()
            rgbBytes[i * 3 + 1] = ((pixel shr 8) and 0xFF).toByte()
            rgbBytes[i * 3 + 2] = (pixel and 0xFF).toByte()
        }

        if (resized !== bitmap) resized.recycle()

        val embedding = tryNativeClipInference(activeModelId, rgbBytes, inputSize, inputSize, dim)
            ?: generateFallbackEmbedding(imageId, rgbBytes, dim)

        // Normalize
        AiNdkBridge.normalizeEmbedding(embedding)
        embedding
    }

    /**
     * Generate a text embedding for a query string.
     * Uses [ClipInferenceEngine] for real ONNX text encoding when the model is loaded,
     * falls back to deterministic hash-based embeddings.
     */
    suspend fun generateTextEmbedding(text: String, dim: Int = DEFAULT_EMBEDDING_DIM): FloatArray = withContext(Dispatchers.Default) {
        // Try real ONNX text encoding first
        if (clipEngine.isLoaded) {
            try {
                val embedding = clipEngine.encodeText(text)
                if (embedding.isNotEmpty() && !embedding.all { it == 0f }) {
                    return@withContext embedding
                }
            } catch (e: Exception) {
                Log.w(TAG, "ONNX text encoding failed, falling back: ${e.message}")
            }
        }

        // Try native CLIP text encoding
        val activeModelId = getActiveModel()?.modelId ?: "mobileclip-s2"
        val nativeResult = tryNativeClipTextInference(activeModelId, text)
        if (nativeResult != null) {
            return@withContext nativeResult
        }

        // Fallback: deterministic hash-based embedding
        val embedding = FloatArray(dim)
        val bytes = text.toByteArray(Charsets.UTF_8)

        var seed = 0L
        for (b in bytes) {
            seed = seed * 31 + b.toLong()
        }

        for (i in embedding.indices) {
            seed = seed * 1103515245L + 12345L
            embedding[i] = ((seed.toDouble() / Long.MAX_VALUE.toDouble()).toFloat() * 2f - 1f) * 0.1f +
                (if (i < bytes.size) (bytes[i].toFloat() / 128f - 1f) * 0.5f else 0f)
        }

        AiNdkBridge.normalizeEmbedding(embedding)
        embedding
    }

    // ── Image Indexing ──

    suspend fun indexImage(imageId: UInt, embedding: FloatArray, modelId: String) = withContext(Dispatchers.IO) {
        vectorIndex.removeAll { it.imageId == imageId && it.modelId == modelId }
        vectorIndex.add(VectorIndexEntry(imageId, embedding, modelId))

        if (hnswIndexHandle != 0L) {
            AiNdkBridge.hnswInsert(hnswIndexHandle, imageId.toLong(), embedding)
        } else {
            Log.w(TAG, "HNSW index not available, skipping insert for $imageId (in-memory index only)")
        }
    }

    suspend fun removeFromIndex(imageId: UInt) = withContext(Dispatchers.IO) {
        vectorIndex.removeAll { it.imageId == imageId }
        if (hnswIndexHandle != 0L) {
            AiNdkBridge.hnswRemove(hnswIndexHandle, imageId.toLong())
        } else {
            Log.w(TAG, "HNSW index not available, skipping remove for $imageId (in-memory index only)")
        }
    }

    fun getIndexedCount(): Int {
        val nativeCount = if (hnswIndexHandle != 0L) {
            AiNdkBridge.hnswSize(hnswIndexHandle)
        } else 0
        return nativeCount.coerceAtLeast(vectorIndex.size)
    }

    // ── Semantic Search ──

    suspend fun searchByText(
        query: String,
        topK: Int = DEFAULT_SEARCH_K,
        modelId: String? = null
    ): List<SearchResult> = withContext(Dispatchers.Default) {
        if (query.isBlank()) return@withContext emptyList()

        val activeModelId = modelId ?: getActiveModel()?.modelId ?: "mobileclip-s2"
        val dim = getEmbeddingDim(activeModelId)

        // Generate query embedding (uses real ONNX when available)
        val queryEmbedding = generateTextEmbedding(query, dim)

        // Try native HNSW search first
        if (hnswIndexHandle != 0L && AiNdkBridge.hnswSize(hnswIndexHandle) > 0) {
            val nativeResults = AiNdkBridge.hnswSearch(hnswIndexHandle, queryEmbedding, topK)
            if (nativeResults != null && nativeResults.isNotEmpty()) {
                return@withContext parseNativeSearchResults(nativeResults)
            }
        }

        // Fallback to in-memory brute-force search
        if (vectorIndex.isEmpty()) return@withContext emptyList()

        vectorIndex.map { entry ->
            val similarity = AiNdkBridge.cosineSimilarity(queryEmbedding, entry.embedding)
            SearchResult(entry.imageId, similarity, ResultType.SEMANTIC)
        }.sortedByDescending { it.score }.take(topK)
    }

    suspend fun searchByLabel(label: String, topK: Int = DEFAULT_SEARCH_K): List<SearchResult> {
        val lowerLabel = label.lowercase().trim()
        return labelStore.entries
            .filter { (_, labels) ->
                labels.any { it.label.lowercase().contains(lowerLabel) }
            }
            .map { (imageId, labels) ->
                val bestConfidence = labels
                    .filter { it.label.lowercase().contains(lowerLabel) }
                    .maxOfOrNull { it.confidence } ?: 0f
                SearchResult(imageId, bestConfidence, ResultType.LABEL)
            }
            .sortedByDescending { it.score }
            .take(topK)
    }

    // ── Label Store ──

    fun getLabels(imageId: UInt): List<SemanticLabel> {
        return labelStore[imageId] ?: emptyList()
    }

    fun getTotalLabelCount(): Int {
        return labelStore.values.sumOf { it.size }
    }

    fun getAllLabels(): Map<UInt, List<SemanticLabel>> {
        return labelStore.toMap()
    }

    fun getAllUniqueLabels(): List<String> {
        return labelStore.values
            .flatten()
            .map { it.label }
            .distinct()
            .sorted()
    }

    // ── Task Management ──

    /**
     * Launch a tracked coroutine job. The job is added to [activeJobs] before
     * execution and removed when the coroutine completes (success, failure, or cancellation).
     *
     * @param taskId Unique task identifier used for progress tracking and cancellation
     * @param block The work to perform — runs on [Dispatchers.IO] by default
     * @return The [Job] handle
     */
    private fun launchTracked(taskId: String, block: suspend CoroutineScope.() -> Unit): Job {
        val job = scope.launch(block = block)
        activeJobs[taskId] = job
        job.invokeOnCompletion { activeJobs.remove(taskId) }
        return job
    }

    fun cancelTask(taskId: String) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        setTaskProgress(taskId, -1f)
    }

    fun cancelAllTasks() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        _taskProgress.value = emptyMap()
    }

    /**
     * Fire-and-forget label generation with automatic job tracking.
     * Use [cancelTask] with the returned taskId to cancel.
     */
    fun startLabelGeneration(imageId: UInt, bitmap: Bitmap, modelId: String? = null): String {
        val taskId = "label-gen-$imageId"
        launchTracked(taskId) {
            generateLabels(imageId, bitmap, modelId)
        }
        return taskId
    }

    /**
     * Fire-and-forget embedding generation with automatic job tracking.
     * Use [cancelTask] with the returned taskId to cancel.
     */
    fun startEmbeddingGeneration(imageId: UInt, bitmap: Bitmap, modelId: String? = null): String {
        val taskId = "embed-gen-$imageId"
        launchTracked(taskId) {
            generateEmbedding(imageId, bitmap, modelId)
        }
        return taskId
    }

    /**
     * Fire-and-forget image indexing with automatic job tracking.
     * Use [cancelTask] with the returned taskId to cancel.
     */
    fun startIndexImage(imageId: UInt, bitmap: Bitmap, modelId: String? = null): String {
        val taskId = "index-$imageId"
        launchTracked(taskId) {
            val activeModelId = modelId ?: getActiveModel()?.modelId ?: "mobileclip-s2"
            val embedding = generateEmbedding(imageId, bitmap, activeModelId)
            indexImage(imageId, embedding, activeModelId)
        }
        return taskId
    }

    // ── Rating (delegated to AiRatingService) ──

    suspend fun rateImage(imageId: UInt, bitmap: Bitmap?): Pair<Int, String> = withContext(Dispatchers.Default) {
        if (bitmap == null) return@withContext 3 to "No image data available"

        val dim = 64
        val resized = Bitmap.createScaledBitmap(bitmap, dim, dim, true)
        val pixels = IntArray(dim * dim)
        resized.getPixels(pixels, 0, dim, 0, 0, dim, dim)
        if (resized !== bitmap) resized.recycle()

        var totalBrightness = 0.0
        var totalSaturation = 0.0
        val brightnesses = DoubleArray(pixels.size)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            val brightness = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            totalBrightness += brightness
            brightnesses[i] = brightness

            val maxC = maxOf(r, g, b).toDouble()
            val minC = minOf(r, g, b).toDouble()
            totalSaturation += if (maxC > 0) (maxC - minC) / maxC else 0.0
        }

        val avgBrightness = totalBrightness / pixels.size
        val avgSaturation = totalSaturation / pixels.size

        val variance = brightnesses.map { (it - avgBrightness) * (it - avgBrightness) }.average()
        val contrast = sqrt(variance)

        val exposureScore = if (avgBrightness in 0.3..0.7) 1.0 else
            if (avgBrightness in 0.2..0.8) 0.6 else 0.3
        val contrastScore = if (contrast in 0.15..0.35) 1.0 else
            if (contrast in 0.1..0.4) 0.6 else 0.3
        val saturationScore = if (avgSaturation in 0.2..0.6) 1.0 else
            if (avgSaturation in 0.1..0.7) 0.6 else 0.3

        val totalScore = (exposureScore * 0.4 + contrastScore * 0.35 + saturationScore * 0.25)
        val stars = when {
            totalScore > 0.8 -> 5
            totalScore > 0.6 -> 4
            totalScore > 0.4 -> 3
            totalScore > 0.2 -> 2
            else -> 1
        }

        val reason = buildString {
            append("Exposure: ${"%.1f".format(avgBrightness * 100)}% brightness, ")
            append("Contrast: ${"%.2f".format(contrast)}, ")
            append("Saturation: ${"%.1f".format(avgSaturation * 100)}%")
            append(" → ${stars} stars")
        }

        stars to reason
    }

    // ── Internal Helpers ──

    private fun getEmbeddingDim(modelId: String): Int {
        return when (modelId) {
            "siglip2" -> 1152
            else -> DEFAULT_EMBEDDING_DIM
        }
    }

    private fun getModelInputSize(modelId: String): Int {
        return when (modelId) {
            "siglip2" -> 384
            "mobileclip-s2" -> 256
            else -> 224
        }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        return AiNdkBridge.cosineSimilarity(a, b)
    }

    private fun generateFallbackEmbedding(imageId: UInt, rgbBytes: ByteArray, dim: Int): FloatArray {
        val embedding = FloatArray(dim)
        // Mix imageId with content-derived hash for better distribution
        var seed = imageId.toLong()
        // Incorporate pixel data into seed (sample every 16th pixel for performance)
        val step = maxOf(1, rgbBytes.size / 256)
        for (i in rgbBytes.indices step step) {
            seed = seed * 31 + (rgbBytes[i].toLong() and 0xFF)
        }
        // Generate normalized random-like embedding using LCG
        for (i in embedding.indices) {
            seed = seed * 1103515245L + 12345L
            embedding[i] = (seed.toDouble() / Long.MAX_VALUE.toDouble()).toFloat() * 2f - 1f
        }
        return embedding
    }

    private suspend fun tryNativeClipInference(
        modelId: String,
        rgbBytes: ByteArray,
        width: Int,
        height: Int,
        dim: Int
    ): FloatArray? {
        return try {
            // Try to load the model into ClipInferenceEngine if not already loaded
            if (!clipEngine.isLoaded || clipEngine.activeModelId != modelId) {
                val loaded = clipEngine.loadModel(modelId)
                if (!loaded) return null
            }
            // Use the ClipInferenceEngine for inference (requires a Bitmap, so we
            // construct one from the raw RGB bytes)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * height)
            for (i in pixels.indices) {
                val r = rgbBytes[i * 3].toInt() and 0xFF
                val g = rgbBytes[i * 3 + 1].toInt() and 0xFF
                val b = rgbBytes[i * 3 + 2].toInt() and 0xFF
                pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            try {
                clipEngine.encodeImage(bitmap)
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Native CLIP inference not available, using fallback: ${e.message}")
            null
        }
    }

    private suspend fun tryNativeClipTextInference(modelId: String, text: String): FloatArray? {
        return try {
            if (!clipEngine.isLoaded || clipEngine.activeModelId != modelId) {
                val loaded = clipEngine.loadModel(modelId)
                if (!loaded) return null
            }
            clipEngine.encodeText(text)
        } catch (e: Exception) {
            Log.d(TAG, "Native CLIP text inference not available: ${e.message}")
            null
        }
    }

    private fun getModelPath(modelId: String): String? {
        return context.filesDir.resolve("ai_models").resolve("$modelId.onnx").absolutePath
    }

    private fun parseNativeSearchResults(nativeResults: FloatArray): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        var i = 0
        while (i + 1 < nativeResults.size) {
            val imageId = nativeResults[i].toLong().toUInt()
            val distance = nativeResults[i + 1]
            val score = (1.0f - distance).coerceIn(0f, 1f)
            results.add(SearchResult(imageId, score, ResultType.SEMANTIC))
            i += 2
        }
        return results.sortedByDescending { it.score }
    }

    private fun setTaskProgress(taskId: String, progress: Float) {
        val current = _taskProgress.value.toMutableMap()
        if (progress < 0) {
            current.remove(taskId)
        } else {
            current[taskId] = progress
        }
        _taskProgress.value = current
    }

    private fun cleanupTask(taskId: String) {
        activeJobs.remove(taskId)
    }
}
