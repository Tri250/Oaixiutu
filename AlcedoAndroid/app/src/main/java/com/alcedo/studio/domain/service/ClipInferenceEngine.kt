package com.alcedo.studio.domain.service

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.alcedo.studio.ai.OnnxModelManager
import com.alcedo.studio.data.dao.AiEmbeddingDao
import com.alcedo.studio.data.local.AiEmbeddingEntity
import com.alcedo.studio.data.model.AiEmbedding
import com.alcedo.studio.data.model.AiModelAsset
import com.alcedo.studio.data.model.AiModelKind
import com.alcedo.studio.ndk.AiNdkBridge
import com.alcedo.studio.util.BitmapDecoder
import com.alcedo.studio.util.ContextProvider
import com.alcedo.studio.utils.IdGenerator
import com.alcedo.studio.utils.ThreadPool
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * CLIP/SigLIP inference engine. Encodes images and text into a shared
 * embedding space for semantic search, persisting image embeddings in Room.
 * Prefers the native ONNX path via [AiNdkBridge]; falls back to the Kotlin
 * [OnnxModelManager] when the native text encoder is unavailable.
 */
@Singleton
class ClipInferenceEngine @Inject constructor(
    private val onnxModelManager: OnnxModelManager,
    private val sidecarRuntime: AiSidecarRuntimeService,
    private val embeddingDao: AiEmbeddingDao,
    private val tokenizer: ClipTokenizer,
) {

    private var clipAsset: AiModelAsset? = null

    /** Ensure the CLIP model is downloaded and loaded. */
    suspend fun ensureReady(): Boolean {
        val asset = sidecarRuntime.defaultClipAsset()
        val ok = sidecarRuntime.ensureLoaded(asset)
        if (ok) {
            clipAsset = asset
            // Replace the compact fallback vocab with the full CLIP vocab
            // bundled next to the model, so queries tokenize accurately.
            if (!tokenizer.hasFullVocab) tokenizer.loadDefaultVocab()
        }
        return ok
    }

    /** Encode an image at [uri] to an L2-normalised embedding. */
    suspend fun encodeImage(uri: Uri, imageId: String): AiEmbedding? = withContext(ThreadPool.aiInference) {
        if (!ensureReady()) return@withContext null
        val asset = clipAsset ?: return@withContext null
        val modelHandle = onnxModelManager.handleFor(asset.id) ?: return@withContext null

        // Native path expects a decoded native image handle; here we decode a
        // bitmap and route through the ONNX image encoder in Kotlin.
        val bitmap = BitmapDecoder.decodeThumbnail(ContextProvider.requireContext(), uri, 224) ?: return@withContext null
        val vector = encodeImageBitmap(modelHandle, bitmap)
        bitmap.recycle()
        if (vector.isEmpty()) return@withContext null
        val normalised = l2Normalise(vector)
        val now = System.currentTimeMillis()
        val embedding = AiEmbedding(
            imageId = imageId,
            embedding = normalised,
            modelId = asset.id,
            dimensions = normalised.size,
            generatedAt = now,
            norm = 1f,
        )
        persist(embedding)
        embedding
    }

    /** Encode a text query to an L2-normalised embedding. */
    suspend fun encodeText(text: String): FloatArray? = withContext(ThreadPool.aiInference) {
        if (!ensureReady()) return@withContext null
        val asset = clipAsset ?: return@withContext null
        val modelHandle = onnxModelManager.handleFor(asset.id) ?: return@withContext null
        val tokens = tokenizer.encode(text)
        val vector = onnxModelManager.runTextEncoder(modelHandle, tokens)
        if (vector.isEmpty()) null else l2Normalise(vector)
    }

    /** Encode an already-decoded bitmap. */
    private fun encodeImageBitmap(modelHandle: Long, bitmap: Bitmap): FloatArray {
        val resized = resizeIfNeeded(bitmap, 224, 224)
        val pixels = toNchwRgb(resized, 224, 224)
        if (resized !== bitmap) resized.recycle()
        return onnxModelManager.runImageEncoder(modelHandle, pixels, 224, 224)
    }

    /** Search persisted embeddings for [queryVector], returning scored results. */
    suspend fun search(
        queryVector: FloatArray,
        modelId: String,
        limit: Int = 50,
    ): List<Pair<String, Float>> = withContext(ThreadPool.aiInference) {
        val entities = embeddingDao.getAllForModel(modelId)
        entities.mapNotNull { entity ->
            val vec = AiEmbeddingDao.unpack(entity.embeddingBlob)
            if (vec.size != queryVector.size) return@mapNotNull null
            val score = AiEmbedding.cosineSimilarity(queryVector, vec)
            entity.imageId to score
        }.sortedByDescending { it.second }.take(limit)
    }

    private suspend fun persist(embedding: AiEmbedding) {
        embeddingDao.upsertEmbedding(
            AiEmbeddingEntity(
                id = IdGenerator.newId("emb"),
                imageId = embedding.imageId,
                modelId = embedding.modelId,
                dimensions = embedding.dimensions,
                generatedAt = embedding.generatedAt,
                norm = embedding.norm,
                embeddingBlob = AiEmbeddingDao.pack(embedding.embedding),
            ),
        )
    }

    private fun l2Normalise(v: FloatArray): FloatArray {
        var sum = 0.0
        for (f in v) sum += (f * f).toDouble()
        val inv = if (sum > 0) (1.0 / sqrt(sum)).toFloat() else 1f
        return FloatArray(v.size) { v[it] * inv }
    }

    private fun resizeIfNeeded(src: Bitmap, w: Int, h: Int): Bitmap =
        if (src.width == w && src.height == h) src
        else Bitmap.createScaledBitmap(src, w, h, true)

    /** Convert an ARGB bitmap to NCHW float RGB in CLIP normalisation range. */
    private fun toNchwRgb(bitmap: Bitmap, w: Int, h: Int): FloatArray {
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = FloatArray(3 * w * h)
        val mean = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
        val std = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)
        // NCHW: [C][H][W]
        for (i in pixels.indices) {
            val px = pixels[i]
            val r = ((px shr 16) and 0xFF) / 255f
            val g = ((px shr 8) and 0xFF) / 255f
            val b = (px and 0xFF) / 255f
            out[i] = (r - mean[0]) / std[0]
            out[w * h + i] = (g - mean[1]) / std[1]
            out[2 * w * h + i] = (b - mean[2]) / std[2]
        }
        return out
    }

    companion object {
        private const val TAG = "ClipInferenceEngine"
    }
}
