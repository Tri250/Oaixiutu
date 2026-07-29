package com.alcedo.studio.ai

import android.graphics.Bitmap
import android.util.Log
import com.alcedo.studio.data.model.AiEmbedding
import com.alcedo.studio.data.model.AiModelKind
import com.alcedo.studio.domain.service.AiSidecarRuntimeService
import com.alcedo.studio.domain.service.ClipTokenizer
import com.alcedo.studio.domain.service.ModelAssetCatalog
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * High-level CLIP/SigLIP model inference facade. Wraps [OnnxModelManager] with
 * image preprocessing and L2 normalisation, returning embeddings ready for
 * [SemanticSearchEngine]. Image decoding is delegated to the caller; this class
 * operates on already-decoded bitmaps to keep GPU/decode pools separate.
 */
@Singleton
class ClipModelInference @Inject constructor(
    private val onnxModelManager: OnnxModelManager,
    private val sidecarRuntime: AiSidecarRuntimeService,
    private val tokenizer: ClipTokenizer,
) {

    private var loadedModelPath: String? = null

    /** Ensure the default CLIP/SigLIP model is loaded. */
    suspend fun ensureReady(): Boolean {
        val asset = sidecarRuntime.defaultClipAsset()
        val ok = sidecarRuntime.ensureLoaded(asset)
        if (ok) {
            loadedModelPath = sidecarRuntime.localPathFor(asset).absolutePath
            // Load the full CLIP vocab bundled next to the model so text
            // queries tokenize correctly instead of relying on the compact
            // built-in subset.
            if (!tokenizer.hasFullVocab) tokenizer.loadDefaultVocab()
        }
        return ok
    }

    /** Encode an image bitmap into an L2-normalised embedding. */
    suspend fun encodeImage(bitmap: Bitmap): FloatArray? {
        if (!ensureReady()) return null
        val asset = ModelAssetCatalog.defaultFor(AiModelKind.CLIP) ?: return null
        val handle = onnxModelManager.handleFor(asset.id).takeIf { it != 0L } ?: return null
        val resized = resizeIfNeeded(bitmap, INPUT_SIZE, INPUT_SIZE)
        val pixels = toNchwRgb(resized, INPUT_SIZE, INPUT_SIZE)
        if (resized !== bitmap) resized.recycle()
        val raw = onnxModelManager.runImageEncoder(handle, pixels, INPUT_SIZE, INPUT_SIZE)
        return if (raw.isEmpty()) null else l2Normalise(raw)
    }

    /** Encode a text query into an L2-normalised embedding. */
    suspend fun encodeText(text: String): FloatArray? {
        if (!ensureReady()) return null
        val asset = ModelAssetCatalog.defaultFor(AiModelKind.CLIP) ?: return null
        val handle = onnxModelManager.handleFor(asset.id).takeIf { it != 0L } ?: return null
        val tokens = tokenizer.encode(text)
        val raw = onnxModelManager.runTextEncoder(handle, tokens)
        return if (raw.isEmpty()) null else l2Normalise(raw)
    }

    /** Build an [AiEmbedding] for [imageId] from a bitmap. */
    suspend fun embedImage(imageId: String, bitmap: Bitmap): AiEmbedding? {
        val vector = encodeImage(bitmap) ?: return null
        return AiEmbedding(
            imageId = imageId,
            embedding = vector,
            modelId = ModelAssetCatalog.defaultFor(AiModelKind.CLIP)?.id ?: "clip",
            dimensions = vector.size,
            generatedAt = System.currentTimeMillis(),
            norm = 1f,
        )
    }

    private fun resizeIfNeeded(src: Bitmap, w: Int, h: Int): Bitmap =
        if (src.width == w && src.height == h) src
        else Bitmap.createScaledBitmap(src, w, h, true)

    /** Convert ARGB bitmap to NCHW float RGB with CLIP normalisation. */
    private fun toNchwRgb(bitmap: Bitmap, w: Int, h: Int): FloatArray {
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = FloatArray(3 * w * h)
        val mean = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
        val std = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)
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

    private fun l2Normalise(v: FloatArray): FloatArray {
        var sum = 0.0
        for (f in v) sum += (f * f).toDouble()
        val inv = if (sum > 0) (1.0 / sqrt(sum)).toFloat() else 1f
        return FloatArray(v.size) { v[it] * inv }
    }

    companion object {
        private const val TAG = "ClipModelInference"
        private const val INPUT_SIZE = 224
    }
}
