package com.alcedo.studio.domain.service

import android.graphics.Bitmap
import android.util.Log
import com.alcedo.studio.data.model.AdjustmentParams
import com.alcedo.studio.data.model.MaskRecord
import com.alcedo.studio.ndk.AlcedoNativeBridge
import com.alcedo.studio.ndk.NdkSafeCall
import com.alcedo.studio.util.BitmapDecoder
import com.alcedo.studio.util.ContextProvider
import com.alcedo.studio.utils.ThreadPool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the non-destructive edit pipeline for the open image. Wraps native
 * pipeline handles and exposes a [StateFlow] of the latest rendered preview so
 * the editor viewport can recompose reactively.
 *
 * The desktop app stages Tone -> Look -> DisplayTransform -> Geometry -> RawDecode;
 * this service applies the equivalent [AdjustmentParams] through the native
 * fused pipeline (edit_pipeline_fused.comp) and taps the final display frame.
 */
@Singleton
class PipelineService @Inject constructor(
    private val decodeService: DecodeService,
) {

    data class PipelineState(
        val isReady: Boolean = false,
        val isRendering: Boolean = false,
        val previewBitmap: Bitmap? = null,
        val error: String? = null,
        val params: AdjustmentParams = AdjustmentParams.DEFAULT,
    )

    private val _state = MutableStateFlow(PipelineState())
    val state: StateFlow<PipelineState> = _state.asStateFlow()

    private var pipelineHandle: Long = 0L
    private var currentImage: DecodeService.DecodedImage? = null
    private var pendingParams: AdjustmentParams = AdjustmentParams.DEFAULT
    private var dirty = false

    /** Platform-decoded bitmap captured when the native decoder or pipeline
     *  could not be initialised in [open]; used by [render] as a last-resort
     *  preview so the editor always shows something instead of a blank frame. */
    private var fallbackBitmap: Bitmap? = null

    /** Dedicated (off-screen) pipeline handles keyed by pipeline handle, with
     *  the associated decoded image handle so it can be released on close.
     *  Used by batch export so each image renders through its own pipeline
     *  without disturbing the editor's open image. */
    private val dedicatedHandles = java.util.concurrent.ConcurrentHashMap<Long, Long>()

    private val json = Json { encodeDefaults = true }

    /** Open an image and create its pipeline. */
    suspend fun open(uri: android.net.Uri): Boolean = withContext(ThreadPool.compute) {
        close()
        val decoded = decodeService.decode(uri)
        if (decoded == null) {
            // Native decode failed: fall back to the platform decoder so we can
            // at least show the original image for viewing/basic edits.
            val platformBitmap = ContextProvider.context()?.let {
                BitmapDecoder.decodeSampled(it, uri, 2048)
            }
            if (platformBitmap == null) {
                _state.value = _state.value.copy(isReady = false, error = "decode_failed")
                return@withContext false
            }
            currentImage = DecodeService.DecodedImage(
                handle = 0L,
                width = platformBitmap.width,
                height = platformBitmap.height,
                isRaw = false,
            )
            fallbackBitmap = platformBitmap
            pipelineHandle = 0L
        } else {
            currentImage = decoded
            pipelineHandle = NdkSafeCall.handle {
                AlcedoNativeBridge.nativeCreatePipeline(decoded.handle)
            }
            // If pipeline creation failed, render() will fall back to decoding
            // the source image directly; keep isReady true so the editor can
            // still display the image instead of reporting a hard failure.
        }
        pendingParams = AdjustmentParams.DEFAULT
        dirty = true
        _state.value = _state.value.copy(isReady = true, params = pendingParams, error = null)
        render()
        true
    }

    /** Update the active [params]. Marks the pipeline dirty and schedules a render. */
    fun updateParams(params: AdjustmentParams) {
        pendingParams = params
        dirty = true
        _state.value = _state.value.copy(params = params)
    }

    /** Apply a mask to the pipeline. */
    fun applyMask(mask: MaskRecord, coverage: Bitmap? = null): Boolean {
        if (pipelineHandle == 0L) return false
        val maskJson: JsonObject = buildJsonObject {
            put("id", mask.id)
            put("kind", mask.kind.name)
            put("opacity", mask.opacity)
        }
        val jsonStr = json.encodeToString(JsonObject.serializer(), maskJson)
        val ok = NdkSafeCall.call(default = false) {
            AlcedoNativeBridge.nativeApplyMask(pipelineHandle, jsonStr, coverage)
        }
        if (ok) dirty = true
        return ok
    }

    /** Force a re-render of the current params and publish a new preview. */
    suspend fun render(): Boolean = withContext(ThreadPool.compute) {
        _state.value = _state.value.copy(isRendering = true)
        var bitmap: Bitmap? = null
        if (pipelineHandle != 0L) {
            NdkSafeCall.call(default = false) {
                AlcedoNativeBridge.nativeApplyAdjustments(
                    pipelineHandle,
                    AlcedoNativeBridge.paramsToJson(pendingParams),
                )
            }
            // Even when apply fails, the native pipeline may still hold a
            // cached display frame from a previous render, so attempt to fetch
            // it regardless of the apply result.
            bitmap = NdkSafeCall.callOrNull<Bitmap> {
                AlcedoNativeBridge.nativeGetFinalDisplayFrame(pipelineHandle)
            }
        }
        // Fallback 1: render the decoded source image directly (no adjustments).
        if (bitmap == null) {
            bitmap = currentImage?.let { decodeService.toBitmap(it) }
        }
        // Fallback 2: platform-decoded bitmap captured in open() when neither
        // the native decoder nor the pipeline could be initialised.
        if (bitmap == null) {
            bitmap = fallbackBitmap
        }
        dirty = false
        if (bitmap != null) {
            _state.value = _state.value.copy(isRendering = false, previewBitmap = bitmap, error = null)
        } else {
            _state.value = _state.value.copy(isRendering = false, error = "render_failed")
        }
        bitmap != null
    }

    /** Render to a buffer suitable for export at full resolution. */
    suspend fun renderToBuffer(): Long = withContext(ThreadPool.compute) {
        if (pipelineHandle == 0L) return@withContext 0L
        NdkSafeCall.handle { AlcedoNativeBridge.nativeRenderToBuffer(pipelineHandle) }
    }

    /** Render to a bitmap at a given max width. */
    suspend fun renderToBitmap(maxWidth: Int = 2048): Bitmap? = withContext(ThreadPool.compute) {
        if (pipelineHandle == 0L) return@withContext null
        NdkSafeCall.callOrNull { AlcedoNativeBridge.nativeRenderToBitmap(pipelineHandle, maxWidth) }
    }

    val handle: Long get() = pipelineHandle
    val isDirty: Boolean get() = dirty

    /**
     * Create a dedicated (off-screen) pipeline for [uri] without disturbing the
     * editor's currently open image. Returns a non-zero pipeline handle on
     * success, or 0L on failure. The caller MUST release it via
     * [releaseHandle] when done. Use [applyParamsToHandle] to push edit state
     * and render through [com.alcedo.studio.ndk.AlcedoNativeBridge.nativeRenderToBitmap].
     */
    suspend fun createForImage(uri: android.net.Uri): Long = withContext(ThreadPool.compute) {
        val decoded = decodeService.decode(uri) ?: return@withContext 0L
        val h = NdkSafeCall.handle { AlcedoNativeBridge.nativeCreatePipeline(decoded.handle) }
        if (h == 0L) {
            decodeService.release(decoded)
            return@withContext 0L
        }
        dedicatedHandles[h] = decoded.handle
        h
    }

    /** Apply [params] to a dedicated [handle] returned by [createForImage]. */
    fun applyParamsToHandle(handle: Long, params: AdjustmentParams) {
        if (handle == 0L) return
        NdkSafeCall.call(default = false) {
            AlcedoNativeBridge.nativeApplyAdjustments(handle, AlcedoNativeBridge.paramsToJson(params))
        }
    }

    /** Render a dedicated [handle] to a bitmap at [maxWidth] (for export). */
    suspend fun renderHandleToBitmap(handle: Long, maxWidth: Int): Bitmap? = withContext(ThreadPool.compute) {
        if (handle == 0L) return@withContext null
        NdkSafeCall.callOrNull { AlcedoNativeBridge.nativeRenderToBitmap(handle, maxWidth) }
    }

    /** Release a dedicated [handle] and its decoded image. Safe to call once. */
    fun releaseHandle(handle: Long) {
        if (handle == 0L) return
        val decodedHandle = dedicatedHandles.remove(handle)
        NdkSafeCall.run { AlcedoNativeBridge.nativeDestroyPipeline(handle) }
        decodedHandle?.let { NdkSafeCall.run { AlcedoNativeBridge.nativeReleaseImage(it) } }
    }

    /**
     * Clear all masks currently applied to the editor's pipeline so they can be
     * re-applied selectively (e.g. after a toggle/remove). Best-effort: relies
     * on the native layer resetting mask state for the active stage.
     */
    fun clearMasks(): Boolean {
        if (pipelineHandle == 0L) return false
        val ok = NdkSafeCall.call(default = false) {
            AlcedoNativeBridge.nativeClearMasks(pipelineHandle)
        }
        if (ok) dirty = true
        return ok
    }

    /** Close the pipeline and release the decoded image. */
    fun close() {
        if (pipelineHandle != 0L) {
            NdkSafeCall.run { AlcedoNativeBridge.nativeDestroyPipeline(pipelineHandle) }
            pipelineHandle = 0L
        }
        currentImage?.let { decodeService.release(it) }
        currentImage = null
        fallbackBitmap = null
        // Release any leaked dedicated handles.
        dedicatedHandles.keys.toList().forEach { releaseHandle(it) }
        _state.value = PipelineState()
    }

    /** Export the current pipeline params as JSON for sharing/preset. */
    fun exportParams(): String? {
        if (pipelineHandle == 0L) return null
        return NdkSafeCall.call(default = null as String?) {
            com.alcedo.studio.ndk.Pipeline.nativeExportParams(pipelineHandle.toInt())
        }
    }

    /** Import pipeline params from a JSON string. */
    fun importParams(json: String): Boolean {
        if (pipelineHandle == 0L) return false
        return NdkSafeCall.call(default = false) {
            com.alcedo.studio.ndk.Pipeline.nativeImportParams(pipelineHandle.toInt(), json)
            true
        }
    }

    /** Set the render region for partial rendering (performance optimization). */
    fun setRenderRegion(x: Int, y: Int, scaleX: Float, scaleY: Float, refW: Int, refH: Int) {
        if (pipelineHandle == 0L) return
        NdkSafeCall.run {
            com.alcedo.studio.ndk.Pipeline.nativeSetRenderRegion(pipelineHandle.toInt(), x, y, scaleX, scaleY, refW, refH)
        }
    }

    /** Set render resolution mode: fullRes for export, reduced for preview. */
    fun setRenderRes(fullRes: Boolean, maxSide: Int = 2048) {
        NdkSafeCall.run {
            com.alcedo.studio.ndk.Pipeline.nativeSetRenderRes(fullRes, maxSide)
        }
    }

    companion object {
        private const val TAG = "PipelineService"
    }
}
