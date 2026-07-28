package com.alcedo.studio.domain.service

import android.graphics.Bitmap
import android.util.Log
import com.alcedo.studio.data.model.AdjustmentParams
import com.alcedo.studio.data.model.MaskRecord
import com.alcedo.studio.ndk.AlcedoNativeBridge
import com.alcedo.studio.ndk.NdkSafeCall
import com.alcedo.studio.utils.ThreadPool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
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

    /** Open an image and create its pipeline. */
    suspend fun open(uri: android.net.Uri): Boolean = withContext(ThreadPool.compute) {
        close()
        val decoded = decodeService.decode(uri) ?: run {
            _state.value = _state.value.copy(isReady = false, error = "decode_failed")
            return@withContext false
        }
        currentImage = decoded
        pipelineHandle = NdkSafeCall.handle {
            AlcedoNativeBridge.nativeCreatePipeline(decoded.handle)
        }
        if (pipelineHandle == 0L) {
            _state.value = _state.value.copy(isReady = false, error = "pipeline_create_failed")
            return@withContext false
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
        val json = """{"id":"${mask.id}","kind":"${mask.kind}","opacity":${mask.opacity}}"""
        val ok = NdkSafeCall.call(default = false) {
            AlcedoNativeBridge.nativeApplyMask(pipelineHandle, json, coverage)
        }
        if (ok) dirty = true
        return ok
    }

    /** Force a re-render of the current params and publish a new preview. */
    suspend fun render(): Boolean = withContext(ThreadPool.compute) {
        if (pipelineHandle == 0L) return@withContext false
        _state.value = _state.value.copy(isRendering = true)
        val applied = NdkSafeCall.call(default = false) {
            AlcedoNativeBridge.nativeApplyAdjustments(
                pipelineHandle,
                AlcedoNativeBridge.paramsToJson(pendingParams),
            )
        }
        if (!applied) {
            _state.value = _state.value.copy(isRendering = false, error = "apply_failed")
            return@withContext false
        }
        val bitmap = NdkSafeCall.callOrNull<Bitmap> {
            AlcedoNativeBridge.nativeGetFinalDisplayFrame(pipelineHandle)
        }
        dirty = false
        _state.value = _state.value.copy(isRendering = false, previewBitmap = bitmap, error = null)
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

    /** Close the pipeline and release the decoded image. */
    fun close() {
        if (pipelineHandle != 0L) {
            NdkSafeCall.run { AlcedoNativeBridge.nativeDestroyPipeline(pipelineHandle) }
            pipelineHandle = 0L
        }
        currentImage?.let { decodeService.release(it) }
        currentImage = null
        _state.value = PipelineState()
    }

    companion object {
        private const val TAG = "PipelineService"
    }
}
