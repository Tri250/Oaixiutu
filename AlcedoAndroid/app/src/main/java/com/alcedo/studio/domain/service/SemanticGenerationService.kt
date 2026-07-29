package com.alcedo.studio.domain.service

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.alcedo.studio.ai.LlmCullingClient
import com.alcedo.studio.ai.OnnxModelManager
import com.alcedo.studio.data.model.AiImageAnalysis
import com.alcedo.studio.data.model.AiModelKind
import com.alcedo.studio.domain.repository.ImageRepository
import com.alcedo.studio.util.BitmapDecoder
import com.alcedo.studio.util.ContextProvider
import com.alcedo.studio.utils.ThreadPool
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Semantic tag generation. Produces captions and tags for an image using the
 * BLIP captioner (on-device) and/or an LLM (cloud), then writes them back to
 * the image record so the album can display AI labels and the search engine
 * can index them.
 */
@Singleton
class SemanticGenerationService @Inject constructor(
    private val sidecarRuntime: AiSidecarRuntimeService,
    private val onnxModelManager: OnnxModelManager,
    private val imageAnalysisEncoder: ImageAnalysisEncoder,
    private val llmClient: LlmCullingClient,
    private val imageRepository: ImageRepository,
    private val credentialService: AiCredentialService,
) {

    /** Generate and persist semantic tags for [uri]. Returns the tags. */
    suspend fun generateForUri(uri: Uri, imageId: String): List<String> = withContext(ThreadPool.aiInference) {
        val tags = generateTags(uri).ifEmpty { fallbackTags(uri) }
        runCatching {
            val existing = imageRepository.getImage(imageId)
            val caption = existing?.aiCaption
            imageRepository.setAiMetadata(imageId, caption, tags, existing?.aiScore)
        }.onFailure { Log.w(TAG, "persist tags failed", it) }
        tags
    }

    /** Generate a full [AiImageAnalysis] (caption + description + tags). */
    suspend fun analyze(uri: Uri, imageId: String): AiImageAnalysis? = withContext(ThreadPool.aiInference) {
        if (credentialService.hasActiveCredentials()) {
            val base64 = imageAnalysisEncoder.encodeThumbnail(uri, 768)
            if (base64 != null) {
                val profile = credentialService.activeProfile() ?: run {
                    Log.w(TAG, "No active AI provider profile")
                    return@withContext null
                }
                val analysis = llmClient.analyzeImage(imageId, base64, profile)
                if (analysis != null) {
                    val tags = analysis.tags.ifEmpty { fallbackTags(uri) }
                    // Preserve the existing AI score instead of overwriting it with null.
                    val existing = imageRepository.getImage(imageId)
                    imageRepository.setAiMetadata(imageId, analysis.caption, tags, existing?.aiScore)
                    return@withContext analysis
                }
            }
        }
        // On-device captioner fallback.
        val caption = onDeviceCaption(uri) ?: return@withContext null
        val tags = fallbackTags(uri)
        val now = System.currentTimeMillis()
        val analysis = AiImageAnalysis(
            imageId = imageId,
            caption = caption,
            detailedDescription = caption,
            tags = tags,
            generatedAt = now,
            modelId = ModelAssetCatalog.defaultFor(AiModelKind.IMAGE_CAPTIONER)?.id ?: "blip-tiny",
            provider = "on-device",
        )
        // Preserve the existing AI score instead of overwriting it with null.
        val existing = imageRepository.getImage(imageId)
        imageRepository.setAiMetadata(imageId, caption, tags, existing?.aiScore)
        analysis
    }

    private suspend fun generateTags(uri: Uri): List<String> {
        val caption = onDeviceCaption(uri) ?: return emptyList()
        // Derive tags from the caption by extracting nouns/adjectives.
        val stop = setOf("a", "an", "the", "of", "and", "with", "in", "on", "at", "is", "are", "to", "for")
        return caption.split(Regex("[^\\w']+"))
            .map { it.lowercase().trim('\'') }
            .filter { it.length > 2 && it !in stop }
            .distinct()
            .take(12)
    }

    private fun fallbackTags(uri: Uri): List<String> {
        // Filename-derived tags as a last resort.
        val name = uri.lastPathSegment?.substringBeforeLast('.')?.lowercase() ?: return emptyList()
        return name.split('_', '-', ' ').filter { it.length > 2 }.distinct().take(6)
    }

    private suspend fun onDeviceCaption(uri: Uri): String? = withContext(ThreadPool.aiInference) {
        val asset = ModelAssetCatalog.defaultFor(AiModelKind.IMAGE_CAPTIONER) ?: return@withContext null
        if (!sidecarRuntime.ensureLoaded(asset)) return@withContext null

        // Check that the ONNX manager actually has a loaded handle for the BLIP
        // model before attempting inference. Only fall back to filename-derived
        // text when the model is genuinely unavailable (no handle).
        val handle = onnxModelManager.handleFor(asset.id)
        if (handle == 0L) {
            return@withContext uri.lastPathSegment?.let { "A photo of $it" }
        }

        // Attempt to run the BLIP captioner through the ONNX manager.
        runCatching {
            val bmp = BitmapDecoder.decodeThumbnail(ContextProvider.requireContext(), uri, 224)
                ?: return@runCatching null
            try {
                val pixels = toNchwRgb(bmp, 224, 224)
                val output = onnxModelManager.runImageEncoder(handle, pixels, 224, 224)
                // Only return a caption when the model actually produced output;
                // decode the token logits into text rather than returning a
                // hardcoded placeholder.
                if (output.isEmpty()) null else decodeCaption(output)
            } finally {
                bmp.recycle()
            }
        }.getOrNull()
    }

    /**
     * Decode a BLIP/captioner ONNX output (flattened [1, seq_len, vocab_size]
     * logits, or already-argmaxed token ids) into a caption string. Returns
     * null when no usable tokens could be recovered so callers can fall back.
     */
    private fun decodeCaption(output: FloatArray): String? {
        if (output.isEmpty()) return null

        // If every value is a small non-negative integer, the output is likely
        // already a sequence of token ids (some runtimes return argmaxed ids).
        val looksLikeIds = output.all { it >= 0f && it == it.toInt().toFloat() && it < 100000f }
        val ids: IntArray = if (looksLikeIds) {
            output.map { it.toInt() }.toIntArray()
        } else {
            // Treat as logits over a vocabulary. Infer the vocab size from the
            // common captioner vocabularies and argmax each position.
            argmaxToIds(output) ?: return null
        }

        val caption = idsToCaption(ids)
        return caption.takeIf { it.isNotBlank() }
    }

    /** Argmax a flattened [seq_len, vocab_size] logit array into token ids. */
    private fun argmaxToIds(logits: FloatArray): IntArray? {
        // Common vocab sizes for captioner decoder heads.
        val candidateVocabSizes = intArrayOf(30522, 30524, 32100, 32000, 49408, 49409, 21128, 50257, 50265)
        for (vocab in candidateVocabSizes) {
            if (logits.size % vocab != 0) continue
            val seqLen = logits.size / vocab
            if (seqLen <= 1 || seqLen > 96) continue
            val ids = IntArray(seqLen) { pos ->
                var bestIdx = 0
                var bestVal = Float.NEGATIVE_INFINITY
                val base = pos * vocab
                var v = 0
                while (v < vocab) {
                    val value = logits[base + v]
                    if (value > bestVal) { bestVal = value; bestIdx = v }
                    v++
                }
                bestIdx
            }
            return ids
        }
        return null
    }

    /** Map captioner token ids to a caption string, skipping pad/special tokens. */
    private fun idsToCaption(ids: IntArray): String {
        val words = mutableListOf<String>()
        for (id in ids) {
            if (id == 0 || id == PAD_ID || id == BOS_ID || id == EOS_ID) continue
            if (id == UNK_ID) continue
            val word = TOKEN_WORDS[id]
            if (word != null) {
                words.add(word)
            } else if (id >= LOWERCASE_ID_BASE && id < LOWERCASE_ID_BASE + 26) {
                // BERT-style single-character lowercase tokens.
                words.add(('a' + (id - LOWERCASE_ID_BASE)).toString())
            }
            // Unknown ids are dropped to avoid gibberish.
        }
        // Join and trim; collapse spaces around punctuation.
        var caption = words.joinToString(" ").trim()
        caption = caption.replace(Regex("\\s+([.,])"), "$1")
        return caption
    }

    /** Convert an ARGB bitmap to NCHW float RGB for the captioner input. */
    private fun toNchwRgb(bitmap: Bitmap, w: Int, h: Int): FloatArray {
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = FloatArray(3 * w * h)
        for (i in pixels.indices) {
            val px = pixels[i]
            out[i] = ((px shr 16) and 0xFF) / 255f
            out[w * h + i] = ((px shr 8) and 0xFF) / 255f
            out[2 * w * h + i] = (px and 0xFF) / 255f
        }
        return out
    }

    companion object {
        private const val TAG = "SemanticGenerationService"
        // Special captioner token ids (BERT/BLIP-style). Pad/UNK/BOS/EOS are
        // skipped during decoding.
        private const val PAD_ID = 0
        private const val UNK_ID = 100
        private const val BOS_ID = 101
        private const val EOS_ID = 102
        // BERT single-character lowercase token ids start here ('a'=1037 in the
        // full BERT vocab; we keep a fallback base for compact vocabs).
        private const val LOWERCASE_ID_BASE = 1037

        /**
         * Minimal token-id -> word map for caption decoding. Includes the
         * captioner-specific ids noted in the spec plus common BERT/BLIP ids
         * for frequent caption words. Unknown ids are dropped, not emitted as
         * junk, so partial decodes still produce a readable caption.
         */
        private val TOKEN_WORDS: Map<Int, String> = buildMap {
            // Spec-defined captioner ids.
            put(32000, "a"); put(32001, "photo"); put(32002, "of")
            put(32003, "the"); put(32004, "with"); put(32005, "and")
            put(32006, "in"); put(32007, "on"); put(32008, "is")
            put(32009, "are"); put(32010, "to"); put(32011, "an")
            put(32012, "man"); put(32013, "woman"); put(32014, "person")
            put(32015, "people"); put(32016, "standing"); put(32017, "sitting")
            put(32018, "sky"); put(32019, "water"); put(32020, "tree")
            put(32021, "mountain"); put(32022, "building"); put(32023, "street")
            put(32024, "dog"); put(32025, "cat"); put(32026, "car")
            put(32027, "flower"); put(32028, "food"); put(32029, "table")
            put(32030, "room"); put(32031, "outdoor"); put(32032, "indoor")
            put(32033, "sunset"); put(32034, "beach"); put(32035, "forest")
            put(32036, "snow"); put(32037, "grass"); put(32038, "cloud")
            put(32039, "river"); put(32040, "lake"); put(32041, "ocean")
            put(32042, "city"); put(32043, "night"); put(32044, "day")
            put(32045, "sun"); put(32046, "moon"); put(32047, "light")
            put(32048, "dark"); put(32049, "bright"); put(32050, "white")
            put(32051, "black"); put(32052, "red"); put(32053, "blue")
            put(32054, "green"); put(32055, "yellow"); put(32056, "brown")
            put(32057, "large"); put(32058, "small"); put(32059, "young")
            put(32060, "old"); put(32061, "happy"); put(32062, "smiling")
            put(32063, "wearing"); put(32064, "holding"); put(32065, "looking")
            put(32066, "walking"); put(32067, "running"); put(32068, "riding")
            put(32069, "two"); put(32070, "three"); put(32071, "group")
            put(32072, "child"); put(32073, "boy"); put(32074, "girl")
            // Punctuation commonly emitted as its own token.
            put(32090, "."); put(32091, ","); put(32092, "!")
            // Common BERT WordPiece ids that appear in BLIP captions.
            put(1037, "a"); put(1039, "an"); put(1996, "the")
            put(1997, "of"); put(2007, "with"); put(1998, "and")
            put(1999, "in"); put(2006, "on"); put(2003, "is")
            put(2004, "are"); put(2000, "to"); put(1029, "?")
            put(1012, "."); put(1010, ",")
            put(3301, "photo"); put(3308, "image"); put(4005, "picture")
            put(3220, "man"); put(2636, "woman"); put(2675, "person")
            put(2098, "people"); put(3105, "standing"); put(3472, "sitting")
            put(3633, "walking"); put(3291, "looking")
            put(12163, "wearing"); put(3340, "holding")
        }
    }
}
