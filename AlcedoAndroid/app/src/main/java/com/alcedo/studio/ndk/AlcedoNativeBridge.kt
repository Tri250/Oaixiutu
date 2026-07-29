package com.alcedo.studio.ndk

import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.Keep
import com.alcedo.studio.data.model.AdjustmentParams
import kotlinx.coroutines.flow.Flow

/**
 * Main JNI bridge into the native `alcedo_native` library (built from
 * core/, vulkan/ and jni/ sources). This object declares the full surface
 * of native entry points across image decoding, the edit pipeline, the sleeve
 * filesystem, RAW processing, export, thumbnails, edit history and scopes.
 *
 * All native methods are implemented in the C++ jni_*.cpp translation units.
 * Callers MUST go through [NdkSafeCall] to translate thrown JNI exceptions and
 * to validate the library loaded successfully.
 */
@Keep
object AlcedoNativeBridge {

    private const val TAG = "AlcedoNativeBridge"

    @Volatile
    private var loaded = false

    @Volatile
    private var loadError: String? = null

    /** True when [init] succeeded and native calls are safe to make. */
    val isLoaded: Boolean get() = loaded

    /** The last load error, if any. Null on success or before [init]. */
    val lastLoadError: String? get() = loadError

    /**
     * Load the native library and run one-time native initialisation
     * (Vulkan context, log bridge, colour science tables). Safe to call once.
     */
    fun init(): Boolean {
        if (loaded) return true
        return try {
            System.loadLibrary("alcedo_native")
            val ok = nativeStartup()
            loaded = ok
            if (!ok) loadError = "nativeStartup returned false"
            loaded
        } catch (t: Throwable) {
            loadError = t.message
            Log.e(TAG, "Failed to load alcedo_native", t)
            false
        }
    }

    // ---- Lifecycle ----
    private external fun nativeStartup(): Boolean
    external fun nativeShutdown()
    external fun nativeGpuAvailable(): Boolean
    external fun nativeGpuDeviceName(): String?
    external fun nativeSetCacheDir(cacheDir: String)
    external fun nativeSetTempDir(tempDir: String)
    external fun nativeOnLowMemory()
    external fun nativeVersion(): String

    // ---- Image decode ----
    external fun nativeDecodeImage(uri: String, maxDim: Int): Long // returns native image handle
    external fun nativeDecodeRaw(uri: String, maxDim: Int, demosaic: String): Long
    external fun nativeDecodeThumbnail(uri: String, targetSize: Int): Bitmap?
    external fun nativeExtractMetadata(uri: String): String // JSON metadata
    external fun nativeReleaseImage(handle: Long)
    external fun nativeImageDimensions(handle: Long): IntArray // [w, h, channels]
    external fun nativeImageToBitmap(handle: Long, maxWidth: Int): Bitmap?

    // ---- Edit pipeline ----
    external fun nativeCreatePipeline(imageHandle: Long): Long // returns pipeline handle
    external fun nativeApplyAdjustments(pipelineHandle: Long, paramsJson: String): Boolean
    external fun nativeApplyMask(pipelineHandle: Long, maskJson: String, maskBitmap: Bitmap?): Boolean
    external fun nativeClearMasks(pipelineHandle: Long): Boolean
    external fun nativeRenderToBitmap(pipelineHandle: Long, maxWidth: Int): Bitmap?
    external fun nativeRenderToBuffer(pipelineHandle: Long): Long // pixel buffer handle
    external fun nativeDestroyPipeline(pipelineHandle: Long)
    external fun nativeGetFinalDisplayFrame(pipelineHandle: Long): Bitmap?
    external fun nativeInvalidateStage(pipelineHandle: Long, stageName: String)

    // ---- Sleeve filesystem ----
    external fun nativeSleeveOpen(dbPath: String): Long // returns sleeve handle
    external fun nativeSleeveClose(handle: Long)
    external fun nativeSleeveListChildren(handle: Long, folderPath: String): String // JSON array
    external fun nativeSleeveCreateFolder(handle: Long, parentPath: String, name: String): String
    external fun nativeSleeveImportImage(handle: Long, parentPath: String, uri: String): String
    external fun nativeSleeveDeleteElement(handle: Long, path: String): Boolean
    external fun nativeSleeveMoveElement(handle: Long, srcPath: String, destPath: String): Boolean
    external fun nativeSleeveFilter(handle: Long, filterJson: String): String // JSON image ids
    external fun nativeSleeveResolvePath(handle: Long, logicalPath: String): String?

    // ---- RAW decode settings ----
    external fun nativeRawSetDemosaic(handle: Long, algorithm: String): Boolean
    external fun nativeRawSetBlackWhite(handle: Long, black: Int, white: Int): Boolean
    external fun nativeRawSetNoiseReduction(handle: Long, amount: Float): Boolean
    external fun nativeRawDetectCfaPattern(uri: String): String?
    external fun nativeRawSupportedExtensions(): StringArrayResult

    // ---- AI ----
    external fun nativeAiLoadOnnxModel(modelPath: String, deviceId: Int): Long
    external fun nativeAiRunClipText(handle: Long, text: String): FloatArray
    external fun nativeAiRunClipImage(handle: Long, imageHandle: Long): FloatArray
    external fun nativeAiRunSegmentation(handle: Long, imageHandle: Long): Bitmap?
    external fun nativeAiReleaseModel(handle: Long)

    // ---- Export ----
    external fun nativeExportImage(
        pipelineHandle: Long,
        outputPath: String,
        format: String,
        quality: Int,
        colorSpace: String,
        includeMetadata: Boolean,
    ): Boolean

    external fun nativeWriteUltraHdr(primaryPath: String, gainmapPath: String, outputPath: String): Boolean
    external fun nativeEmbedIccProfile(imagePath: String, profilePath: String): Boolean

    // ---- Thumbnails ----
    external fun nativeGenerateThumbnail(uri: String, targetSize: Int, outputPath: String): Boolean
    external fun nativeGenerateThumbnailBatch(
        uris: Array<String>,
        targetSize: Int,
        outputDir: String,
        listener: ThumbnailListener,
    ): Boolean

    // ---- Edit history ----
    external fun nativeHistoryOpen(dbPath: String): Long
    external fun nativeHistoryClose(handle: Long)
    external fun nativeHistoryCreateVersion(handle: Long, imageId: String, parentId: String?, name: String): String
    external fun nativeHistoryAddTransaction(handle: Long, versionId: String, deltaJson: String): String
    external fun nativeHistoryGetTree(handle: Long, imageId: String): String
    external fun nativeHistoryReplay(handle: Long, versionId: String): String // cumulative params JSON

    // ---- Scope analysis ----
    external fun nativeScopeHistogram(pipelineHandle: Long, bins: Int): FloatArray // r,g,b interleaved
    external fun nativeScopeWaveform(pipelineHandle: Long, width: Int, height: Int): IntArray
    external fun nativeScopeVectorscope(pipelineHandle: Long, samples: Int): FloatArray
    external fun nativeScopeRgbParade(pipelineHandle: Long, width: Int, height: Int): IntArray

    // ---- Colour science ----
    external fun nativeColorScienceApply(matrix: FloatArray, inputRgba: IntArray): IntArray
    external fun nativeColorScienceMatrix(fromSpace: String, toSpace: String): FloatArray
    external fun nativeLensCorrectionProfile(lensId: String): String? // JSON profile

    /**
     * Throw [IllegalStateException] if the native library has not loaded. Call
     * this (or go through [NdkSafeCall]) before invoking any `external fun`.
     * Direct callers of native methods MUST call this, or [requireHandle], to
     * avoid crashing the process when the native layer is unavailable.
     */
    fun requireLoaded() {
        check(loaded) {
            "Native library 'alcedo_native' not loaded" + (loadError?.let { ": $it" } ?: "")
        }
    }

    /**
     * Throw [IllegalArgumentException] if [handle] is the invalid (0) handle.
     * Native functions treat 0 as "no object" and would otherwise produce a
     * junk result or crash; validate handles before every native call that
     * dereferences one.
     */
    fun requireHandle(handle: Long) {
        require(handle != 0L) {
            "Invalid native handle: 0 (object not allocated or already released)"
        }
    }

    /** True when the library is loaded and [handle] is a usable (non-zero) handle. */
    fun isValidHandle(handle: Long): Boolean = loaded && handle != 0L

    /** Convenience: apply [params] to a pipeline via JSON. */
    fun applyParams(pipeline: Long, params: AdjustmentParams): Boolean =
        NdkSafeCall.call(default = false) {
            requireHandle(pipeline)
            nativeApplyAdjustments(pipeline, paramsToJson(params))
        }

    internal fun paramsToJson(params: AdjustmentParams): String {
        // Lightweight manual serialisation to avoid a JSON dep in the bridge.
        val sb = StringBuilder("{")
        sb.append("\"exposure\":").append(params.exposure).append(',')
        sb.append("\"contrast\":").append(params.contrast).append(',')
        sb.append("\"highlights\":").append(params.highlights).append(',')
        sb.append("\"shadows\":").append(params.shadows).append(',')
        sb.append("\"whites\":").append(params.whites).append(',')
        sb.append("\"blacks\":").append(params.blacks).append(',')
        sb.append("\"temperature\":").append(params.temperature).append(',')
        sb.append("\"tint\":").append(params.tint).append(',')
        sb.append("\"saturation\":").append(params.saturation).append(',')
        sb.append("\"vibrance\":").append(params.vibrance).append(',')
        sb.append("\"clarity\":").append(params.clarity).append(',')
        sb.append("\"sharpen\":").append(params.sharpen).append(',')
        sb.append("\"rotation\":").append(params.rotation).append(',')
        sb.append("\"perspectiveH\":").append(params.perspectiveH).append(',')
        sb.append("\"perspectiveV\":").append(params.perspectiveV).append(',')
        sb.append("\"filmGrainAmount\":").append(params.filmGrainAmount).append(',')
        sb.append("\"halationAmount\":").append(params.halationAmount).append(',')
        sb.append("\"lutIntensity\":").append(params.lutIntensity).append(',')
        sb.append("\"displayTransform\":\"").append(params.displayTransform).append("\",")
        sb.append("\"outputColorSpace\":\"").append(params.outputColorSpace).append("\",")
        sb.append("\"displayEotf\":\"").append(params.displayEotf).append("\",")
        sb.append("\"peakLuminanceNits\":").append(params.peakLuminanceNits).append(',')
        sb.append("\"rawDemosaic\":\"").append(params.rawDemosaic).append("\",")
        sb.append("\"rawBlackLevel\":").append(params.rawBlackLevel).append(',')
        sb.append("\"rawWhitePoint\":").append(params.rawWhitePoint).append(',')
        sb.append("\"rawNoiseReduction\":").append(params.rawNoiseReduction)
        sb.append('}')
        return sb.toString()
    }
}

/** Result wrapper for native calls returning a string array plus a status. */
@Keep
data class StringArrayResult(val values: Array<String>, val ok: Boolean)

/** Callback interface for batch thumbnail generation progress. */
@Keep
interface ThumbnailListener {
    fun onThumbnailReady(index: Int, uri: String, outputPath: String)
    fun onThumbnailFailed(index: Int, uri: String, error: String)
    fun onBatchProgress(completed: Int, total: Int)
    fun onBatchComplete(success: Boolean)
}

/** A flow-based progress callback for long native operations. */
fun interface NativeProgressListener {
    fun onProgress(completed: Int, total: Int): Boolean // return false to cancel
}
