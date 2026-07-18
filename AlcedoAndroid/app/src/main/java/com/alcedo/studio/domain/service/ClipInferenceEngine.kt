package com.alcedo.studio.domain.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.alcedo.studio.util.BitmapDecoder
import java.io.File
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.sqrt

/**
 * Pure Kotlin ONNX Runtime CLIP inference engine for Android.
 *
 * Loads MobileCLIP2 / CLIP ONNX models (text + vision encoders) and provides:
 *   - Image encoding → 512-dim (or model-specific) normalized embedding
 *   - Text encoding → normalized embedding
 *   - Batch image/text encoding
 *   - Zero-shot classification
 *
 * Supports GPU acceleration via NNAPI delegate when available.
 * All inference operations are coroutine-safe and run on [Dispatchers.Default].
 */
class ClipInferenceEngine(private val context: Context) {

    companion object {
        private const val TAG = "ClipInferenceEngine"

        // CLIP image preprocessing constants
        private const val MEAN_R = 0.48145466f
        private const val MEAN_G = 0.4578275f
        private const val MEAN_B = 0.40821073f
        private const val STD_R = 0.26862954f
        private const val STD_G = 0.26130258f
        private const val STD_B = 0.27577711f

        // Model input names (standard CLIP ONNX export)
        private const val VISION_INPUT_NAME = "pixel_values"
        private const val TEXT_INPUT_IDS = "input_ids"
        private const val TEXT_ATTENTION_MASK = "attention_mask"
    }

    enum class ModelStatus {
        NOT_LOADED, LOADING, LOADED, FAILED
    }

    // Model configuration per model ID
    data class ModelConfig(
        val modelId: String,
        val imageSize: Int = 224,
        val embeddingDim: Int = 512,
        val visionModelFilename: String = "vision_encoder.onnx",
        val textModelFilename: String = "text_encoder.onnx",
        val useNnapi: Boolean = true
    )

    // Session state
    private var ortEnv: OrtEnvironment? = null
    private var visionSession: OrtSession? = null
    private var textSession: OrtSession? = null
    // NNAPI delegate — loaded via reflection if the ONNX Runtime Android extension is available at runtime
    private var nnapiDelegate: Any? = null
    private var tokenizer = ClipTokenizer()

    @Volatile
    private var status = ModelStatus.NOT_LOADED

    @Volatile
    private var activeModelConfig: ModelConfig? = null

    private val inferenceMutex = Mutex()

    // Public state
    val modelStatus: ModelStatus get() = status
    val isLoaded: Boolean get() = status == ModelStatus.LOADED
    val embeddingDim: Int get() = activeModelConfig?.embeddingDim ?: 512
    val imageSize: Int get() = activeModelConfig?.imageSize ?: 224
    val activeModelId: String? get() = activeModelConfig?.modelId

    /**
     * Load a CLIP model from the app's internal storage.
     *
     * @param modelId The model identifier (e.g., "mobileclip-s2")
     * @param config Optional model configuration override
     * @return true if model loaded successfully
     */
    suspend fun loadModel(
        modelId: String,
        config: ModelConfig? = null
    ): Boolean = withContext(Dispatchers.Default) {
        if (status == ModelStatus.LOADING) return@withContext false

        // Unload existing model first
        if (status == ModelStatus.LOADED) {
            unloadModel()
        }

        status = ModelStatus.LOADING

        try {
            val modelConfig = config ?: resolveModelConfig(modelId)
            activeModelConfig = modelConfig

            val modelsDir = File(context.filesDir, "ai_models")

            // Try to find vision encoder model
            val visionModelFile = findModelFile(modelsDir, modelId, modelConfig.visionModelFilename)
            val textModelFile = findModelFile(modelsDir, modelId, modelConfig.textModelFilename)

            // Single-file ONNX model (combined vision+text, or vision-only with text support)
            val singleModelFile = File(modelsDir, "$modelId.onnx")

            ortEnv = OrtEnvironment.getEnvironment()

            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            sessionOptions.setIntraOpNumThreads(4)

            // Try NNAPI delegate for GPU acceleration via reflection
            if (modelConfig.useNnapi) {
                try {
                    val nnapiClass = Class.forName("ai.onnxruntime.extensions.NnapiDelegate")
                    val constructor = nnapiClass.getConstructor()
                    val delegate = constructor.newInstance()
                    nnapiDelegate = delegate
                    // NnapiDelegate implements OrtSession.Delegate, add via sessionOptions
                    val addDelegateMethod = OrtSession.SessionOptions::class.java.getMethod(
                        "addDelegate", Class.forName("ai.onnxruntime.OrtSession\$Delegate")
                    )
                    addDelegateMethod.invoke(sessionOptions, delegate)
                    Log.i(TAG, "NNAPI delegate attached successfully via reflection")
                } catch (e: ClassNotFoundException) {
                    Log.i(TAG, "NNAPI delegate class not found, using CPU only")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to attach NNAPI delegate, using CPU: ${e.message}")
                }
            }

            // Load sessions
            val env = ortEnv ?: return@withContext false
            if (visionModelFile != null && textModelFile != null) {
                // Separate vision and text encoder files
                visionSession = env.createSession(visionModelFile.absolutePath, sessionOptions)
                textSession = env.createSession(textModelFile.absolutePath, sessionOptions)
                Log.i(TAG, "Loaded separate vision/text encoders for $modelId")
            } else if (singleModelFile.exists()) {
                // Single ONNX model file - use it for both sessions
                visionSession = env.createSession(singleModelFile.absolutePath, sessionOptions)
                textSession = visionSession // Same session for both
                Log.i(TAG, "Loaded single ONNX model for $modelId")
            } else {
                Log.e(TAG, "No ONNX model files found for $modelId in ${modelsDir.absolutePath}")
                status = ModelStatus.FAILED
                return@withContext false
            }

            // Try loading tokenizer vocab from assets
            loadTokenizerVocab(modelId)

            status = ModelStatus.LOADED
            Log.i(TAG, "Model $modelId loaded successfully (dim=${modelConfig.embeddingDim}, imageSize=${modelConfig.imageSize})")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model $modelId: ${e.message}", e)
            status = ModelStatus.FAILED
            cleanupSessions()
            false
        }
    }

    /**
     * Unload the current model and release resources.
     */
    fun unloadModel() {
        cleanupSessions()
        status = ModelStatus.NOT_LOADED
        activeModelConfig = null
    }

    /**
     * Encode a single image to a normalized embedding vector.
     *
     * @param bitmap Input image bitmap
     * @return Normalized FloatArray of dimension [embeddingDim]
     */
    suspend fun encodeImage(bitmap: Bitmap): FloatArray = withContext(Dispatchers.Default) {
        inferenceMutex.withLock {
            if (!isLoaded || visionSession == null) {
                return@withContext FloatArray(embeddingDim)
            }

            try {
                val config = activeModelConfig ?: return@withContext FloatArray(embeddingDim)
                val env = ortEnv ?: return@withContext FloatArray(embeddingDim)
                val inputTensor = preprocessImage(bitmap, config.imageSize, env)

                val session = visionSession ?: return@withContext FloatArray(embeddingDim)
                val inputName = resolveVisionInputName(session)
                val inputMap = mapOf(inputName to inputTensor)

                val output = session.run(inputMap)
                val outputTensor = output[0].value as? Array<FloatArray> ?: return@withContext FloatArray(embeddingDim)

                val embedding = outputTensor[0].copyOf()
                normalizeEmbedding(embedding)
                embedding
            } catch (e: Exception) {
                Log.e(TAG, "Image encoding failed: ${e.message}", e)
                FloatArray(embeddingDim)
            }
        }
    }

    /**
     * Encode a text string to a normalized embedding vector.
     *
     * @param text Input text
     * @return Normalized FloatArray of dimension [embeddingDim]
     */
    suspend fun encodeText(text: String): FloatArray = withContext(Dispatchers.Default) {
        inferenceMutex.withLock {
            if (!isLoaded || textSession == null) {
                return@withContext FloatArray(embeddingDim)
            }

            try {
                val tokenIds = tokenizer.tokenize(text)

                // Create attention mask: 1 for real tokens, 0 for padding
                val attentionMask = LongArray(ClipTokenizer.MAX_SEQ_LENGTH)
                for (i in tokenIds.indices) {
                    attentionMask[i] = if (tokenIds[i] != ClipTokenizer.PAD_TOKEN.toLong()) 1L else 0L
                }

                val env = ortEnv ?: return@withContext FloatArray(embeddingDim)
                val inputIdsTensor = OnnxTensor.createTensor(env, arrayOf(tokenIds))
                val attentionMaskTensor = OnnxTensor.createTensor(env, arrayOf(attentionMask))

                val session = textSession ?: return@withContext FloatArray(embeddingDim)
                val inputNames = resolveTextInputNames(session)
                val inputMap = mutableMapOf<String, OnnxTensor>()
                inputMap[inputNames.first] = inputIdsTensor
                val attentionName = inputNames.second ?: return@withContext FloatArray(embeddingDim)
                inputMap[attentionName] = attentionMaskTensor

                val output = session.run(inputMap)
                val outputTensor = output[0].value as? Array<FloatArray> ?: return@withContext FloatArray(embeddingDim)

                val embedding = outputTensor[0].copyOf()
                normalizeEmbedding(embedding)
                embedding
            } catch (e: Exception) {
                Log.e(TAG, "Text encoding failed: ${e.message}", e)
                FloatArray(embeddingDim)
            }
        }
    }

    /**
     * Batch-encode multiple images.
     *
     * @param bitmaps List of input bitmaps
     * @return List of normalized embedding vectors
     */
    suspend fun batchEncodeImages(bitmaps: List<Bitmap>): List<FloatArray> = withContext(Dispatchers.Default) {
        if (bitmaps.isEmpty()) return@withContext emptyList()

        // Process images one by one for now (batching in ONNX requires dynamic shapes)
        bitmaps.map { bitmap ->
            encodeImage(bitmap)
        }
    }

    /**
     * Batch-encode multiple text strings.
     *
     * @param texts List of input texts
     * @return List of normalized embedding vectors
     */
    suspend fun batchEncodeTexts(texts: List<String>): List<FloatArray> = withContext(Dispatchers.Default) {
        if (texts.isEmpty()) return@withContext emptyList()

        texts.map { text ->
            encodeText(text)
        }
    }

    /**
     * Encode an image from a file path to a normalized embedding vector.
     * Convenience wrapper that loads the image from disk and delegates to [encodeImage].
     *
     * @param imagePath Absolute path to the image file
     * @return Normalized FloatArray of dimension [embeddingDim]
     */
    suspend fun getImageEmbedding(imagePath: String): FloatArray = withContext(Dispatchers.Default) {
        try {
            val file = File(imagePath)
            if (!file.exists()) {
                Log.w(TAG, "Image file not found: $imagePath")
                return@withContext FloatArray(embeddingDim)
            }
            val bitmap = BitmapDecoder.decodeSampledBitmap(context, imagePath, 224, 224)
            if (bitmap == null) {
                Log.w(TAG, "Failed to decode image: $imagePath")
                return@withContext FloatArray(embeddingDim)
            }
            val embedding = encodeImage(bitmap)
            bitmap.recycle()
            embedding
        } catch (e: Exception) {
            Log.e(TAG, "getImageEmbedding failed: ${e.message}", e)
            FloatArray(embeddingDim)
        }
    }

    /**
     * Encode a text string to a normalized embedding vector.
     * Convenience wrapper that delegates to [encodeText].
     *
     * @param text Input text
     * @return Normalized FloatArray of dimension [embeddingDim]
     */
    suspend fun getTextEmbedding(text: String): FloatArray = withContext(Dispatchers.Default) {
        encodeText(text)
    }

    /**
     * Zero-shot classification of an image against candidate labels.
     *
     * Uses the prompt template "a photo of a {label}" for each label,
     * computes image-text similarity, and returns results sorted by score.
     *
     * @param bitmap Input image
     * @param labels Candidate label strings
     * @return List of (label, score) pairs sorted by score descending
     */
    suspend fun zeroShotClassify(
        bitmap: Bitmap,
        labels: List<String>
    ): List<Pair<String, Float>> = withContext(Dispatchers.Default) {
        if (!isLoaded || labels.isEmpty()) return@withContext emptyList()

        val imageEmbedding = encodeImage(bitmap)
        if (imageEmbedding.all { it == 0f }) return@withContext emptyList()

        // Use template "a photo of a {label}" for zero-shot classification
        val prompts = labels.map { "a photo of a $it" }
        val textEmbeddings = batchEncodeTexts(prompts)

        val results = labels.indices.mapNotNull { i ->
            if (i >= textEmbeddings.size) return@mapNotNull null
            val textEmb = textEmbeddings[i]
            val score = cosineSimilarity(imageEmbedding, textEmb)
            labels[i] to score
        }

        results.sortedByDescending { it.second }
    }

    // ── Image Preprocessing ──

    /**
     * Preprocess a Bitmap for CLIP vision encoder input.
     * 1. Center-crop to square
     * 2. Resize to model input size
     * 3. Normalize with CLIP mean/std
     * 4. Convert to NCHW FloatBuffer tensor [1, 3, imageSize, imageSize]
     */
    private fun preprocessImage(bitmap: Bitmap, imageSize: Int, env: OrtEnvironment): OnnxTensor {
        // Center-crop to square
        val size = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2

        val cropped = Bitmap.createBitmap(bitmap, x, y, size, size)
        val resized = Bitmap.createScaledBitmap(cropped, imageSize, imageSize, true)

        val pixels = IntArray(imageSize * imageSize)
        resized.getPixels(pixels, 0, imageSize, 0, 0, imageSize, imageSize)

        if (cropped !== bitmap) cropped.recycle()
        if (resized !== cropped) resized.recycle()

        // Convert to NCHW float tensor with normalization
        val tensorSize = 3 * imageSize * imageSize
        val floatBuffer = FloatBuffer.allocate(tensorSize)
        val totalPixels = imageSize * imageSize

        // R channel
        for (i in 0 until totalPixels) {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            floatBuffer.put((r - MEAN_R) / STD_R)
        }
        // G channel
        for (i in 0 until totalPixels) {
            val pixel = pixels[i]
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            floatBuffer.put((g - MEAN_G) / STD_G)
        }
        // B channel
        for (i in 0 until totalPixels) {
            val pixel = pixels[i]
            val b = (pixel and 0xFF) / 255.0f
            floatBuffer.put((b - MEAN_B) / STD_B)
        }

        floatBuffer.rewind()

        val shape = longArrayOf(1, 3, imageSize.toLong(), imageSize.toLong())
        return OnnxTensor.createTensor(env, floatBuffer, shape)
    }

    private fun calculateInSampleSize(outWidth: Int, outHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        if (reqWidth <= 0 || reqHeight <= 0) return 1
        var inSampleSize = 1
        if (outHeight > reqHeight || outWidth > reqWidth) {
            val halfHeight = outHeight / 2
            val halfWidth = outWidth / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    // ── Normalization ──

    private fun normalizeEmbedding(embedding: FloatArray) {
        var normSq = 0.0
        for (v in embedding) {
            normSq += v.toDouble() * v.toDouble()
        }
        val norm = sqrt(normSq).toFloat()
        if (norm > 1e-8f) {
            for (i in embedding.indices) {
                embedding[i] /= norm
            }
        }
    }

    // ── Similarity ──

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i].toDouble() * b[i].toDouble()
            normA += a[i].toDouble() * a[i].toDouble()
            normB += b[i].toDouble() * b[i].toDouble()
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 1e-12) (dot / denom).toFloat() else 0f
    }

    // ── Model Config Resolution ──

    private fun resolveModelConfig(modelId: String): ModelConfig {
        return when (modelId) {
            "mobileclip-s2" -> ModelConfig(
                modelId = modelId,
                imageSize = 256,
                embeddingDim = 512
            )
            "jina-clip-v2" -> ModelConfig(
                modelId = modelId,
                imageSize = 224,
                embeddingDim = 512
            )
            "siglip2" -> ModelConfig(
                modelId = modelId,
                imageSize = 384,
                embeddingDim = 1152
            )
            else -> ModelConfig(
                modelId = modelId,
                imageSize = 224,
                embeddingDim = 512
            )
        }
    }

    // ── Model File Discovery ──

    private fun findModelFile(modelsDir: File, modelId: String, filename: String): File? {
        // Try model-specific subdirectory first
        val subDir = File(modelsDir, modelId)
        val fileInSubDir = File(subDir, filename)
        if (fileInSubDir.exists()) return fileInSubDir

        // Try top-level with modelId prefix
        val prefixed = File(modelsDir, "${modelId}_$filename")
        if (prefixed.exists()) return prefixed

        return null
    }

    // ── Session Input Name Resolution ──

    private fun resolveVisionInputName(session: OrtSession): String {
        val inputNames = session.inputNames.iterator()
        while (inputNames.hasNext()) {
            val name = inputNames.next()
            if (name.contains("pixel", ignoreCase = true) || name.contains("image", ignoreCase = true)) {
                return name
            }
        }
        // Fall back to first input
        return session.inputNames.iterator().next() ?: VISION_INPUT_NAME
    }

    private fun resolveTextInputNames(session: OrtSession): Pair<String, String?> {
        val names = session.inputNames.toList()
        var inputIdsName = TEXT_INPUT_IDS
        var attentionMaskName: String? = TEXT_ATTENTION_MASK

        for (name in names) {
            when {
                name.contains("input_ids", ignoreCase = true) -> inputIdsName = name
                name.contains("attention_mask", ignoreCase = true) -> attentionMaskName = name
            }
        }

        // If only one input, attention mask might not be needed
        if (names.size == 1) {
            attentionMaskName = null
            inputIdsName = names[0]
        }

        return inputIdsName to attentionMaskName
    }

    // ── Tokenizer Vocab Loading ──

    private fun loadTokenizerVocab(modelId: String) {
        try {
            // Try to load from assets
            val mergesAsset = "ai_models/${modelId}_merges.txt"
            val vocabAsset = "ai_models/${modelId}_vocab.txt"

            val merges = try {
                context.assets.open(mergesAsset).bufferedReader().readText()
            } catch (_: Exception) { null }

            val vocab = try {
                context.assets.open(vocabAsset).bufferedReader().readText()
            } catch (_: Exception) { null }

            // Also try from files directory
            val mergesFile = File(context.filesDir, "ai_models/${modelId}_merges.txt")
            val vocabFile = File(context.filesDir, "ai_models/${modelId}_vocab.txt")

            val mergesContent = merges ?: mergesFile.takeIf { it.exists() }?.readText()
            val vocabContent = vocab ?: vocabFile.takeIf { it.exists() }?.readText()

            if (mergesContent != null && vocabContent != null) {
                tokenizer.loadVocab(mergesContent, vocabContent)
                Log.i(TAG, "Loaded BPE tokenizer vocab for $modelId")
            } else {
                Log.i(TAG, "Using fallback tokenizer for $modelId (no vocab files found)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load tokenizer vocab, using fallback: ${e.message}")
        }
    }

    // ── Cleanup ──

    private fun cleanupSessions() {
        // Close OrtSession via reflection if close() is available at runtime
        closeOrtSession(visionSession)
        closeOrtSession(textSession)
        visionSession = null
        textSession = null

        // Close NNAPI delegate via reflection if possible
        closeNnapiDelegate()
        nnapiDelegate = null
    }

    private fun closeOrtSession(session: OrtSession?) {
        if (session == null) return
        try {
            val closeMethod = OrtSession::class.java.getMethod("close")
            closeMethod.invoke(session)
            Log.d(TAG, "OrtSession closed via reflection")
        } catch (e: NoSuchMethodException) {
            Log.d(TAG, "OrtSession.close() not available, relying on GC")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to close OrtSession: ${e.message}")
        }
    }

    private fun closeNnapiDelegate() {
        val delegate = nnapiDelegate ?: return
        try {
            val closeMethod = delegate.javaClass.getMethod("close")
            closeMethod.invoke(delegate)
            Log.d(TAG, "NNAPI delegate closed via reflection")
        } catch (e: NoSuchMethodException) {
            Log.d(TAG, "NNAPI delegate close() not available, relying on GC")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to close NNAPI delegate: ${e.message}")
        }
    }
}
