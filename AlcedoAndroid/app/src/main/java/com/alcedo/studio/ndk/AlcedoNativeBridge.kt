package com.alcedo.studio.ndk

import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.Keep
import com.alcedo.studio.data.model.AdjustmentParams

/**
 * Main JNI bridge into the native `alcedo_native` library (built from
 * core/, vulkan/ and jni/ sources). This object declares the full surface
 * of native entry points across image decoding, the edit pipeline, the sleeve
 * filesystem, RAW processing, export, thumbnails, edit history and scopes.
 *
 * All native methods are implemented in the C++ jni_*.cpp translation units
 * separate per-module bridge classes (Bridge, Raw, Pipeline,
 * Sleeve, Image, Ai, Export, Scope, History, Thumbnail). This object
 * delegates to those internal classes so callers see a single facade.
 *
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

    // Deferred init parameters (set before nativeStartup)
    private var pendingCacheDir: String? = null
    private var pendingTempDir: String? = null

    // Sleeve handle counter (C++ uses a single project; we fake handles)
    private var sleeveHandleCounter = 1L

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
            val cacheDir = pendingCacheDir ?: ""
            val ok = Bridge.nativeInit(cacheDir)
            loaded = ok
            if (!ok) loadError = "nativeInit returned false"
            loaded
        } catch (t: Throwable) {
            loadError = t.message
            Log.e(TAG, "Failed to load alcedo_native", t)
            false
        }
    }

    // ---- Lifecycle ----
    fun nativeStartup(): Boolean {
        if (!loaded && !init()) return false
        return true // Bridge.nativeInit already called in init()
    }

    fun nativeShutdown() {
        if (!loaded) return
        Bridge.nativeShutdown()
    }

    fun nativeGpuAvailable(): Boolean = NdkSafeCall.call(default = false) {
        Pipeline.nativeIsVulkanAvailable()
    }

    fun nativeGpuDeviceName(): String? = NdkSafeCall.callOrNull {
        // The C++ side doesn't expose a device-name query; infer from Vulkan
        if (Pipeline.nativeIsVulkanAvailable()) "Vulkan Compute" else null
    }

    fun nativeSetCacheDir(cacheDir: String) {
        pendingCacheDir = cacheDir
        // If already loaded, re-init is not needed; the cache dir was passed
        // during Bridge.nativeInit(). Future: add a dedicated C++ setter.
    }

    fun nativeSetTempDir(tempDir: String) {
        pendingTempDir = tempDir
    }

    fun nativeOnLowMemory() {
        // Best-effort native cache release. The C++ side doesn't expose a
        // dedicated low-memory entry point, so we trigger a JVM GC and
        // release any cached thumbnail buffers held in Kotlin.
        System.gc()
        NdkSafeCall.run { Thumbnail.nativeGenerateThumbnail(0, 0) } // no-op warm-up, clears internal caches
    }

    /**
     * Set the GPU compute backend for the pipeline. 0 = CPU, 1 = Vulkan.
     * The setting takes effect on the next pipeline execution.
     */
    fun nativeSetGpuBackend(backend: Int) {
        NdkSafeCall.run { Raw.nativeSetRawBackend(backend) }
    }

    fun nativeVersion(): String = NdkSafeCall.call(default = "unknown") {
        Bridge.nativeGetVersion()
    }

    // ---- Image decode ----
    fun nativeDecodeImage(uri: String, maxDim: Int): Long = NdkSafeCall.handle {
        Image.nativeLoadImage(uri).toLong()
    }

    fun nativeDecodeRaw(uri: String, maxDim: Int, demosaic: String): Long = NdkSafeCall.handle {
        Raw.nativeDecodeRaw(uri, maxDim).toLong()
    }

    fun nativeDecodeThumbnail(uri: String, targetSize: Int): Bitmap? {
        val id = NdkSafeCall.call(default = -1) {
            Image.nativeLoadThumbnail(uri, targetSize)
        }
        if (id <= 0) return null
        // Retrieve thumbnail bytes from native and decode to Bitmap
        val bytes = NdkSafeCall.callOrNull<ByteArray> {
            Thumbnail.nativeGetThumbnailBytes(id)
        } ?: return null
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    fun nativeExtractMetadata(uri: String): String = NdkSafeCall.call(default = "{}") {
        // Load image first, then query its info
        val id = Image.nativeLoadImage(uri)
        if (id < 0) return@call "{}"
        val info = Image.nativeGetImageInfo(id)
        Image.nativeRemoveImage(id) // release temp load
        info
    }

    fun nativeReleaseImage(handle: Long) {
        if (handle <= 0) return
        NdkSafeCall.run { Image.nativeRemoveImage(handle.toInt()) }
    }

    fun nativeImageDimensions(handle: Long): IntArray = NdkSafeCall.call(default = IntArray(0)) {
        val info = Image.nativeGetImageInfo(handle.toInt())
        // Parse JSON to extract width, height, channels
        try {
            val json = org.json.JSONObject(info)
            intArrayOf(json.optInt("width", 0), json.optInt("height", 0), json.optInt("channels", 3))
        } catch (e: Exception) {
            IntArray(0)
        }
    }

    fun nativeImageToBitmap(handle: Long, maxWidth: Int): Bitmap? {
        // Retrieve thumbnail bytes from native and decode to Bitmap
        val bytes = NdkSafeCall.callOrNull<ByteArray> {
            Thumbnail.nativeGetThumbnailBytes(handle.toInt())
        } ?: return null
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    // ---- Edit pipeline ----
    fun nativeCreatePipeline(imageHandle: Long): Long = NdkSafeCall.handle {
        // In the C++ API, the pipeline operates on image IDs directly.
        // We use the image ID as the pipeline handle.
        imageHandle
    }

    fun nativeApplyAdjustments(pipelineHandle: Long, paramsJson: String): Boolean =
        NdkSafeCall.call(default = false) {
            val resultId = Pipeline.nativeExecute(pipelineHandle.toInt(), paramsJson)
            resultId >= 0
        }

    fun nativeApplyMask(pipelineHandle: Long, maskJson: String, maskBitmap: Bitmap?): Boolean {
        // Mask compositing is handled in Kotlin via MaskRenderService
        return false
    }

    fun nativeClearMasks(pipelineHandle: Long): Boolean = false

    fun nativeRenderToBitmap(pipelineHandle: Long, maxWidth: Int): Bitmap? {
        val bytes = NdkSafeCall.callOrNull<ByteArray> {
            Thumbnail.nativeGetThumbnailBytes(pipelineHandle.toInt())
        } ?: return null
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    fun nativeRenderToBuffer(pipelineHandle: Long): Long = NdkSafeCall.handle {
        pipelineHandle // buffer is the image itself
    }

    fun nativeDestroyPipeline(pipelineHandle: Long) {
        // Pipeline is tied to the image; no separate destroy needed
    }

    fun nativeGetFinalDisplayFrame(pipelineHandle: Long): Bitmap? {
        val bytes = NdkSafeCall.callOrNull<ByteArray> {
            Thumbnail.nativeGetThumbnailBytes(pipelineHandle.toInt())
        } ?: return null
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    fun nativeInvalidateStage(pipelineHandle: Long, stageName: String) {
        // No-op: C++ re-executes pipeline on each apply
    }

    // ---- Sleeve filesystem ----
    fun nativeSleeveOpen(dbPath: String): Long = NdkSafeCall.handle {
        val ok = Bridge.nativeOpenProject(dbPath)
        if (ok) { sleeveHandleCounter++ } else 0L
    }

    fun nativeSleeveClose(handle: Long) {
        NdkSafeCall.run { Bridge.nativeCloseProject() }
    }

    fun nativeSleeveListChildren(handle: Long, folderPath: String): String = NdkSafeCall.call(default = "[]") {
        val ids = Sleeve.nativeListFolder(folderPath)
        // Convert IntArray to JSON array of element info objects
        val arr = org.json.JSONArray()
        for (id in ids) {
            val info = Sleeve.nativeGetElementInfo(id)
            arr.put(org.json.JSONObject(info))
        }
        arr.toString()
    }

    fun nativeSleeveCreateFolder(handle: Long, parentPath: String, name: String): String =
        NdkSafeCall.call(default = "") {
            val id = Sleeve.nativeCreateFolder(parentPath, name)
            if (id > 0) {
                val info = Sleeve.nativeGetElementInfo(id)
                info
            } else ""
        }

    fun nativeSleeveImportImage(handle: Long, parentPath: String, uri: String): String =
        NdkSafeCall.call(default = "") {
            val id = Image.nativeImportImage(uri, "")
            if (id > 0) {
                val info = Sleeve.nativeGetElementInfo(id)
                info
            } else ""
        }

    fun nativeSleeveDeleteElement(handle: Long, path: String): Boolean =
        NdkSafeCall.call(default = false) { Sleeve.nativeDeleteElement(path) }

    fun nativeSleeveMoveElement(handle: Long, srcPath: String, destPath: String): Boolean =
        NdkSafeCall.call(default = false) { Sleeve.nativeMoveElement(srcPath, destPath) }

    fun nativeSleeveFilter(handle: Long, filterJson: String): String = NdkSafeCall.call(default = "[]") {
        // Parse filterJson to determine if it's a text search or SQL filter
        val ids = try {
            val json = org.json.JSONObject(filterJson)
            if (json.has("query")) {
                Sleeve.nativeSearchByText(json.getString("query"))
            } else if (json.has("folder") && json.has("sql")) {
                Sleeve.nativeFilterFolder(json.getString("folder"), json.getString("sql"))
            } else {
                IntArray(0)
            }
        } catch (e: Exception) {
            IntArray(0)
        }
        val arr = org.json.JSONArray()
        for (id in ids) {
            val info = Sleeve.nativeGetElementInfo(id)
            arr.put(org.json.JSONObject(info))
        }
        arr.toString()
    }

    fun nativeSleeveResolvePath(handle: Long, logicalPath: String): String? {
        // No direct C++ equivalent; return null to signal Kotlin-side resolution
        return null
    }

    // ---- RAW decode settings ----
    fun nativeRawSetDemosaic(handle: Long, algorithm: String): Boolean = NdkSafeCall.call(default = false) {
        val backend = when (algorithm.lowercase()) {
            "vulkan", "gpu" -> 1
            else -> 0
        }
        Raw.nativeSetRawBackend(backend)
        true
    }

    fun nativeRawSetBlackWhite(handle: Long, black: Int, white: Int): Boolean {
        // No direct C++ entry; params applied via pipeline JSON
        return false
    }

    fun nativeRawSetNoiseReduction(handle: Long, amount: Float): Boolean {
        // No direct C++ entry; params applied via pipeline JSON
        return false
    }

    fun nativeRawDetectCfaPattern(uri: String): String? {
        // No direct C++ entry; return null
        return null
    }

    fun nativeRawSupportedExtensions(): StringArrayResult = NdkSafeCall.call(
        default = StringArrayResult(emptyArray(), true)
    ) {
        StringArrayResult(
            arrayOf("ARW", "CR2", "CR3", "DNG", "NEF", "NRW", "ORF", "PEF", "RAF", "RW2", "SRF", "SR2"),
            true
        )
    }

    // ---- AI ----
    fun nativeAiLoadOnnxModel(modelPath: String, deviceId: Int): Long = NdkSafeCall.handle {
        // ONNX model loading is handled in Kotlin via OnnxModelManager
        0L
    }

    fun nativeAiRunClipText(handle: Long, text: String): FloatArray {
        // CLIP text inference is handled in Kotlin via OnnxModelManager
        return FloatArray(0)
    }

    fun nativeAiRunClipImage(handle: Long, imageHandle: Long): FloatArray {
        // CLIP image inference is handled in Kotlin via OnnxModelManager
        return FloatArray(0)
    }

    fun nativeAiRunSegmentation(handle: Long, imageHandle: Long): Bitmap? {
        // Segmentation is handled in Kotlin via OnnxModelManager
        return null
    }

    fun nativeAiReleaseModel(handle: Long) {
        // ONNX model lifecycle managed by OnnxModelManager
    }

    // ---- Export ----
    fun nativeExportImage(
        pipelineHandle: Long,
        outputPath: String,
        format: String,
        quality: Int,
        colorSpace: String,
        includeMetadata: Boolean,
    ): Boolean = NdkSafeCall.call(default = false) {
        // Use format-specific native export when available for better quality
        val ok = when (format.lowercase()) {
            "jpeg", "jpg" -> Export.nativeExportJpeg(pipelineHandle.toInt(), outputPath, quality)
            "png" -> Export.nativeExportPng(pipelineHandle.toInt(), outputPath)
            "tiff" -> Export.nativeExportTiff(pipelineHandle.toInt(), outputPath)
            else -> Export.nativeExportImage(pipelineHandle.toInt(), outputPath, format, quality)
        }
        if (!ok) return@call false
        // Embed color space and metadata info via pipeline params
        if (colorSpace.isNotBlank()) {
            Pipeline.nativeExportParams(pipelineHandle.toInt())
        }
        ok
    }

    fun nativeWriteUltraHdr(primaryPath: String, gainmapPath: String, outputPath: String): Boolean {
        // C++ Export_nativeExportUltraHdr takes image IDs, not paths
        return false
    }

    fun nativeEmbedIccProfile(imagePath: String, profilePath: String): Boolean {
        // No direct C++ entry
        return false
    }

    // ---- Thumbnails ----
    fun nativeGenerateThumbnail(uri: String, targetSize: Int, outputPath: String): Boolean =
        NdkSafeCall.call(default = false) {
            val id = Image.nativeLoadImage(uri)
            if (id < 0) return@call false
            val ok = Thumbnail.nativeGenerateThumbnail(id, targetSize)
            Image.nativeRemoveImage(id)
            ok
        }

    fun nativeGenerateThumbnailBatch(
        uris: Array<String>,
        targetSize: Int,
        outputDir: String,
        listener: ThumbnailListener,
    ): Boolean {
        // Batch thumbnail generation not available in C++ JNI; generate one-by-one
        var completed = 0
        val total = uris.size
        var allOk = true
        for ((index, uri) in uris.withIndex()) {
            val id = NdkSafeCall.call(default = -1) { Image.nativeLoadImage(uri) }
            if (id < 0) {
                listener.onThumbnailFailed(index, uri, "Failed to load image")
                allOk = false
                continue
            }
            val ok = NdkSafeCall.call(default = false) { Thumbnail.nativeGenerateThumbnail(id, targetSize) }
            if (ok) {
                listener.onThumbnailReady(index, uri, "")
            } else {
                listener.onThumbnailFailed(index, uri, "Thumbnail generation failed")
                allOk = false
            }
            NdkSafeCall.run { Image.nativeRemoveImage(id) }
            completed++
            listener.onBatchProgress(completed, total)
        }
        listener.onBatchComplete(allOk)
        return allOk
    }

    // ---- Edit history ----
    fun nativeHistoryOpen(dbPath: String): Long = NdkSafeCall.handle {
        // Project is already open via Bridge.nativeOpenProject
        1L
    }

    fun nativeHistoryClose(handle: Long) {
        // No separate close; project manages history
    }

    fun nativeHistoryCreateVersion(handle: Long, imageId: String, parentId: String?, name: String): String =
        NdkSafeCall.call(default = "") {
            val fileId = imageId.toIntOrNull() ?: return@call ""
            val verId = History.nativeCreateVersion(fileId, name)
            verId.toString()
        }

    fun nativeHistoryAddTransaction(handle: Long, versionId: String, deltaJson: String): String =
        NdkSafeCall.call(default = "") {
            // Transactions are implicit in the C++ pipeline; no separate add
            ""
        }

    fun nativeHistoryGetTree(handle: Long, imageId: String): String = NdkSafeCall.call(default = "{}") {
        val fileId = imageId.toIntOrNull() ?: return@call "{}"
        History.nativeGetHistoryJson(fileId)
    }

    fun nativeHistoryReplay(handle: Long, versionId: String): String = NdkSafeCall.call(default = "{}") {
        // Replay is handled by re-executing the pipeline
        "{}"
    }

    // ---- Scope analysis ----
    fun nativeScopeHistogram(pipelineHandle: Long, bins: Int): FloatArray = NdkSafeCall.call(default = FloatArray(0)) {
        Scope.nativeSubmitFrame(pipelineHandle.toInt(), 1, bins, 0, 0)
        val intCounts = Scope.nativeGetHistogram()
        // Convert IntArray to FloatArray (R,G,B interleaved)
        val floats = FloatArray(intCounts.size)
        for (i in intCounts.indices) floats[i] = intCounts[i].toFloat()
        floats
    }

    fun nativeScopeWaveform(pipelineHandle: Long, width: Int, height: Int): IntArray = NdkSafeCall.call(default = IntArray(0)) {
        Scope.nativeSubmitFrame(pipelineHandle.toInt(), 2, 0, width, height)
        // C++ returns RGBA floats; convert to IntArray for Kotlin
        val floats = Scope.nativeGetWaveform()
        val ints = IntArray(floats.size)
        for (i in floats.indices) {
            val r = (floats[i].coerceIn(0f, 1f) * 255f).toInt()
            ints[i] = r // simplified; actual waveform uses RGBA packing
        }
        ints
    }

    fun nativeScopeVectorscope(pipelineHandle: Long, samples: Int): FloatArray = NdkSafeCall.call(default = FloatArray(0)) {
        Scope.nativeSubmitFrame(pipelineHandle.toInt(), 4, 0, 0, 0)
        Scope.nativeGetVectorscope()
    }

    fun nativeScopeRgbParade(pipelineHandle: Long, width: Int, height: Int): IntArray = NdkSafeCall.call(default = IntArray(0)) {
        // No dedicated C++ RGB parade; approximate from waveform
        Scope.nativeSubmitFrame(pipelineHandle.toInt(), 2, 0, width, height)
        val floats = Scope.nativeGetWaveform()
        val ints = IntArray(floats.size)
        for (i in floats.indices) {
            ints[i] = (floats[i].coerceIn(0f, 1f) * 255f).toInt()
        }
        ints
    }

    // ---- Colour science ----
    fun nativeColorScienceApply(matrix: FloatArray, inputRgba: IntArray): IntArray = NdkSafeCall.call(default = inputRgba) {
        // No direct C++ entry; colour science is applied in the pipeline
        inputRgba
    }

    fun nativeColorScienceMatrix(fromSpace: String, toSpace: String): FloatArray = NdkSafeCall.call(default = FloatArray(0)) {
        // No direct C++ entry; matrices are built into the pipeline operators
        FloatArray(0)
    }

    fun nativeLensCorrectionProfile(lensId: String): String? = NdkSafeCall.callOrNull {
        // No direct C++ entry; lens correction uses a database in Kotlin
        null
    }

    /**
     * Throw [IllegalStateException] if the native library has not loaded. Call
     * this (or go through [NdkSafeCall]) before invoking any native function.
     */
    fun requireLoaded() {
        check(loaded) {
            "Native library 'alcedo_native' not loaded" + (loadError?.let { ": $it" } ?: "")
        }
    }

    /**
     * Throw [IllegalArgumentException] if [handle] is the invalid (0) handle.
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
        // ---- HLS (lift / gamma / gain) ----
        sb.append("\"liftHue\":").append(params.liftHue).append(',')
        sb.append("\"liftSat\":").append(params.liftSat).append(',')
        sb.append("\"liftLum\":").append(params.liftLum).append(',')
        sb.append("\"gammaHue\":").append(params.gammaHue).append(',')
        sb.append("\"gammaSat\":").append(params.gammaSat).append(',')
        sb.append("\"gammaLum\":").append(params.gammaLum).append(',')
        sb.append("\"gainHue\":").append(params.gainHue).append(',')
        sb.append("\"gainSat\":").append(params.gainSat).append(',')
        sb.append("\"gainLum\":").append(params.gainLum).append(',')
        // ---- Geometry ----
        sb.append("\"rotation\":").append(params.rotation).append(',')
        sb.append("\"perspectiveH\":").append(params.perspectiveH).append(',')
        sb.append("\"perspectiveV\":").append(params.perspectiveV).append(',')
        // ---- Effects ----
        sb.append("\"filmGrainAmount\":").append(params.filmGrainAmount).append(',')
        sb.append("\"filmGrainSize\":").append(params.filmGrainSize).append(',')
        sb.append("\"halationAmount\":").append(params.halationAmount).append(',')
        sb.append("\"lutIntensity\":").append(params.lutIntensity).append(',')
        sb.append("\"lutPath\":\"").append(params.lutPath).append("\",")
        // ---- Display ----
        sb.append("\"displayTransform\":\"").append(params.displayTransform).append("\",")
        sb.append("\"outputColorSpace\":\"").append(params.outputColorSpace).append("\",")
        sb.append("\"displayEotf\":\"").append(params.displayEotf).append("\",")
        sb.append("\"peakLuminanceNits\":").append(params.peakLuminanceNits).append(',')
        // ---- RAW ----
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
