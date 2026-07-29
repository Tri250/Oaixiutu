package com.alcedo.studio.domain.service

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.alcedo.studio.data.model.AiModelKind
import com.alcedo.studio.data.model.AiSubjectMask
import com.alcedo.studio.data.model.Mask
import com.alcedo.studio.ndk.AiNdkBridge
import com.alcedo.studio.ndk.AlcedoNativeBridge
import com.alcedo.studio.ndk.NdkSafeCall
import com.alcedo.studio.util.BitmapDecoder
import com.alcedo.studio.util.ContextProvider
import com.alcedo.studio.utils.ThreadPool
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mask inference service. Runs the on-device segmentation model (SAM-tiny) to
 * produce AI subject/background/sky masks, returning a coverage bitmap that
 * [MaskRenderService] can composite.
 */
@Singleton
class MaskInferenceService @Inject constructor(
    private val sidecarRuntime: AiSidecarRuntimeService,
    private val onnxModelManager: com.alcedo.studio.ai.OnnxModelManager,
    private val decodeService: DecodeService,
) {

    /** Run segmentation for [uri]; returns a coverage bitmap (white = masked). */
    suspend fun segment(uri: Uri, subjectKind: String): Bitmap? = withContext(ThreadPool.aiInference) {
        val asset = ModelAssetCatalog.defaultFor(AiModelKind.MASK_SEGMENT) ?: return@withContext null
        if (!sidecarRuntime.ensureLoaded(asset)) return@withContext null

        // Native path: decode then run segmentation through AiNdkBridge.
        val decoded = decodeService.decode(uri) ?: return@withContext null
        try {
            val handle = onnxModelManager.handleFor(asset.id) ?: return@withContext null
            val nativeMask = NdkSafeCall.callOrNull<Bitmap> {
                AlcedoNativeBridge.nativeAiRunSegmentation(handle, decoded.handle)
            }
            if (nativeMask != null) return@withContext refineByKind(nativeMask, subjectKind)
        } finally {
            decodeService.release(decoded)
        }
        // Fallback: derive a content-aware coverage mask from pixel luminance so
        // the heuristic at least tracks the image instead of a fixed shape.
        Log.w(TAG, "nativeAiRunSegmentation unavailable; using luminance heuristic for '$subjectKind'")
        fallbackCoverage(uri, subjectKind)
    }

    /** Build an [AiSubjectMask] tied to [versionId] from a segmentation result. */
    suspend fun buildSubjectMask(uri: Uri, versionId: String, subjectKind: String): AiSubjectMask? {
        val coverage = segment(uri, subjectKind) ?: return null
        val kind = when (subjectKind.lowercase()) {
            "background" -> com.alcedo.studio.data.model.AiSubjectKind.BACKGROUND
            "sky" -> com.alcedo.studio.data.model.AiSubjectKind.SKY
            "object" -> com.alcedo.studio.data.model.AiSubjectKind.OBJECT
            else -> com.alcedo.studio.data.model.AiSubjectKind.SUBJECT
        }
        // Persist the coverage bitmap to a temp file before recycling so the
        // mask data survives; set coveragePath to the temp file path.
        val coveragePath = runCatching {
            val tmp = java.io.File(
                com.alcedo.studio.util.ContextProvider.requireContext().cacheDir,
                "mask_${com.alcedo.studio.utils.IdGenerator.newId("cov")}.png",
            )
            tmp.outputStream().use { out -> coverage.compress(Bitmap.CompressFormat.PNG, 100, out) }
            tmp.absolutePath
        }.getOrNull()
        coverage.recycle()
        return com.alcedo.studio.data.model.AiSubjectMask(
            id = com.alcedo.studio.utils.IdGenerator.newId("mask"),
            versionId = versionId,
            name = subjectKind.replaceFirstChar { it.uppercase() },
            subjectKind = kind,
            coveragePath = coveragePath,
        )
    }

    private fun refineByKind(mask: Bitmap, subjectKind: String): Bitmap {
        // The native model returns a generic subject map; for sky/background we
        // invert or take the upper region. This is a lightweight refinement.
        return when (subjectKind.lowercase()) {
            "background" -> invertBitmap(mask)
            else -> mask
        }
    }

    private fun invertBitmap(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(src.width * src.height)
        src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
        for (i in pixels.indices) {
            val a = pixels[i] ushr 24
            val inv = 255 - a
            pixels[i] = (inv shl 24) or 0x00FFFFFF
        }
        out.setPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
        return out
    }

    /**
     * Content-aware fallback coverage mask. Instead of a fixed radial gradient,
     * the coverage is derived from the image's per-pixel luminance and the
     * requested [subjectKind] so the heuristic at least tracks the actual image
     * content when the segmentation model is unavailable.
     */
    private suspend fun fallbackCoverage(uri: Uri, subjectKind: String): Bitmap? {
        val bmp = BitmapDecoder.decodeThumbnail(ContextProvider.requireContext(), uri, 256) ?: return null
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        // Per-pixel luminance (Rec. 709) and image mean.
        val lum = FloatArray(w * h)
        var sum = 0.0
        for (i in pixels.indices) {
            val px = pixels[i]
            val r = ((px shr 16) and 0xFF) / 255f
            val g = ((px shr 8) and 0xFF) / 255f
            val b = (px and 0xFF) / 255f
            val l = 0.2126f * r + 0.7152f * g + 0.0722f * b
            lum[i] = l
            sum += l
        }
        val mean = (sum / pixels.size.coerceAtLeast(1)).toFloat()

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val outPx = IntArray(w * h)
        val kind = subjectKind.lowercase()
        for (y in 0 until h) {
            val ny = if (h > 1) y / (h - 1f) else 0f
            val cy = kotlin.math.abs(ny - 0.5f) * 2f
            for (x in 0 until w) {
                val i = y * w + x
                val l = lum[i]
                val nx = if (w > 1) x / (w - 1f) else 0f
                val cx = kotlin.math.abs(nx - 0.5f) * 2f
                val centreDist = kotlin.math.sqrt(cx * cx + cy * cy)
                val a: Float = when (kind) {
                    "sky" -> {
                        // Sky is typically bright and in the upper portion of the frame.
                        val brightness = ((l - mean) / (1f - mean + 1e-3f)).coerceIn(0f, 1f)
                        val topBias = 1f - ny
                        brightness * 0.6f + topBias * 0.4f
                    }
                    "background" -> {
                        // Background tends to be away from the centre and low contrast.
                        val edge = centreDist.coerceIn(0f, 1f)
                        val flatness = 1f - (kotlin.math.abs(l - mean) / (mean + 1e-3f)).coerceIn(0f, 1f)
                        edge * 0.6f + flatness * 0.4f
                    }
                    else -> {
                        // subject/object: central region with strong deviation from the mean.
                        val centrality = (1f - centreDist).coerceIn(0f, 1f)
                        val contrast = (kotlin.math.abs(l - mean) / (mean + 1e-3f)).coerceIn(0f, 1f)
                        centrality * 0.6f + contrast * 0.4f
                    }
                }
                outPx[i] = ((a.coerceIn(0f, 1f) * 255f).toInt() shl 24)
            }
        }
        out.setPixels(outPx, 0, w, 0, 0, w, h)
        bmp.recycle()
        return out
    }

    companion object {
        private const val TAG = "MaskInferenceService"
    }
}
