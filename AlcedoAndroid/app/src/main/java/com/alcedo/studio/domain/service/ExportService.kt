package com.alcedo.studio.domain.service

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.alcedo.studio.data.model.ExportConfig
import com.alcedo.studio.data.model.ExportFormat
import com.alcedo.studio.ndk.AlcedoNativeBridge
import com.alcedo.studio.ndk.NdkSafeCall
import com.alcedo.studio.util.ContextProvider
import com.alcedo.studio.utils.ThreadPool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Export service. Renders the active pipeline to disk in JPEG/PNG/TIFF/WebP
 * (optionally UltraHDR JPEG_R), applying resizing, watermark and metadata
 * options. Reports per-export progress and ETA for the export UI.
 */
@Singleton
class ExportService @Inject constructor(
    private val pipelineService: PipelineService,
    private val watermarkService: WatermarkService,
    private val ultraHdrWriter: UltraHdrWriter,
) {

    data class ExportProgress(
        val id: String,
        val completed: Int,
        val total: Int,
        val outputPath: String? = null,
        val error: String? = null,
        val etaMs: Long? = null,
        val done: Boolean = false,
    )

    /** Sub-progress for the single image currently being exported (0..1). */
    private val _progress = MutableStateFlow<ExportProgress?>(null)
    val progress: StateFlow<ExportProgress?> = _progress.asStateFlow()

    /** Overall batch progress (completed/total across the whole batch). Kept
     *  separate from single-export [progress] so batch progress is not
     *  overwritten by the per-image sub-progress of the current image. */
    private val _batchProgress = MutableStateFlow<ExportProgress?>(null)
    val batchProgress: StateFlow<ExportProgress?> = _batchProgress.asStateFlow()

    data class ExportRequest(
        val imageId: String,
        val displayName: String,
        val pipelineHandle: Long,
        val config: ExportConfig,
    )

    /** Export a single image. Returns the output file path on success. */
    suspend fun export(request: ExportRequest): String? = withContext(ThreadPool.compute) {
        val start = System.currentTimeMillis()
        val cfg = request.config
        val outDir = cfg.outputDirectory?.let { File(it) }
            ?: File(ContextProvider.requireContext().cacheDir, "exports").apply { mkdirs() }
        val name = resolveName(request.displayName, cfg.namingPattern)
        // TIFF is not natively encodable on Android; it degrades to PNG, so the
        // output file must use the .png extension (not .tif) to stay consistent
        // with the bytes actually written.
        val effectiveExt = effectiveExtension(cfg.format)
        val outFile = File(outDir, "$name.$effectiveExt")
        val degradedCfg = if (effectiveExt != cfg.format.extension) {
            cfg.copy(fallbackExtension = effectiveExt)
        } else cfg
        _progress.value = ExportProgress(request.imageId, 0, 1)

        // Render through the pipeline.
        val rawBitmap = if (request.pipelineHandle != 0L) {
            NdkSafeCall.callOrNull<Bitmap> {
                AlcedoNativeBridge.nativeRenderToBitmap(request.pipelineHandle, cfg.maxDimension.coerceAtLeast(0))
            }
        } else null
        val bitmap = rawBitmap ?: pipelineService.renderToBitmap(cfg.maxDimension.coerceAtLeast(1)) ?: run {
            _progress.value = ExportProgress(request.imageId, 1, 1, error = "render_failed", done = true)
            return@withContext null
        }
        _progress.value = ExportProgress(request.imageId, 1, 2)

        // Optional watermark.
        val finalBitmap = if (cfg.includeWatermark) watermarkService.apply(bitmap, cfg.watermark) else bitmap

        // Encode.
        val encoded = encode(finalBitmap, outFile, degradedCfg)
        if (!encoded) {
            // Eagerly recycle the large bitmaps to avoid OOM during batch export.
            if (bitmap !== finalBitmap) bitmap.recycle()
            finalBitmap.recycle()
            _progress.value = ExportProgress(request.imageId, 2, 2, error = "encode_failed", done = true)
            return@withContext null
        }

        // Native export path (metadata/ICC) when a pipeline handle is available.
        if (request.pipelineHandle != 0L && cfg.includeMetadata) {
            val nativeOk = NdkSafeCall.call(default = false) {
                AlcedoNativeBridge.nativeExportImage(
                    request.pipelineHandle, outFile.absolutePath,
                    cfg.format.name, cfg.quality, cfg.colorSpace, cfg.includeMetadata,
                )
            }
            if (!nativeOk) {
                Log.w(TAG, "nativeExportImage failed for ${request.imageId}; metadata/ICC not embedded")
            }
        }

        // Optional UltraHDR — must use finalBitmap BEFORE recycling.
        if (cfg.ultraHdr && cfg.format == ExportFormat.JPEG) {
            val gainmapFile = File(outDir, "$name.gainmap.jpg")
            val hdrBitmap = pipelineService.renderToBitmap(cfg.maxDimension.coerceAtLeast(1))
            if (hdrBitmap != null) {
                val gainBytes = ultraHdrWriter.buildGainMapBytes(finalBitmap, hdrBitmap)
                hdrBitmap.recycle()
                if (gainBytes != null) {
                    val gainWritten = runCatching {
                        FileOutputStream(gainmapFile).use { it.write(gainBytes) }
                        true
                    }.onFailure { Log.w(TAG, "failed to write gainmap", it) }.getOrDefault(false)
                    if (gainWritten) {
                        val ultraFile = File(outDir, "$name.ultrahdr.jpg")
                        val ultraOk = ultraHdrWriter.write(outFile.absolutePath, gainmapFile.absolutePath, ultraFile.absolutePath)
                        if (ultraOk && ultraFile.exists() && ultraFile.length() > 0L) {
                            // Recycle bitmaps after successful UltraHDR write.
                            if (bitmap !== finalBitmap) bitmap.recycle()
                            finalBitmap.recycle()
                            outFile.delete()
                            return@withContext finish(request, ultraFile, start)
                        } else {
                            Log.w(TAG, "UltraHDR write failed; falling back to SDR JPEG")
                        }
                    }
                }
            }
        }

        // Eagerly recycle the large bitmaps to avoid OOM during batch export.
        // Bitmap.recycle() is idempotent; safe even if bitmap == finalBitmap.
        if (bitmap !== finalBitmap) bitmap.recycle()
        finalBitmap.recycle()
        finish(request, outFile, start)
    }

    private fun finish(request: ExportRequest, outFile: File, start: Long): String {
        val eta = System.currentTimeMillis() - start
        _progress.value = ExportProgress(
            request.imageId, 2, 2, outputPath = outFile.absolutePath, etaMs = eta, done = true,
        )
        return outFile.absolutePath
    }

    /** Export many images sequentially, returning the paths of successful exports. */
    suspend fun exportBatch(requests: List<ExportRequest>): List<String> = withContext(ThreadPool.compute) {
        _batchProgress.value = ExportProgress(id = "batch", completed = 0, total = requests.size)
        val results = mutableListOf<String>()
        requests.forEachIndexed { index, req ->
            export(req)?.let { results.add(it) }
            // Overall batch progress is tracked separately from the per-image
            // sub-progress so consumers can show both without one clobbering the other.
            _batchProgress.value = ExportProgress(
                id = "batch",
                completed = index + 1,
                total = requests.size,
                done = index + 1 >= requests.size,
            )
        }
        results
    }

    /** The file extension actually written for [format] (TIFF degrades to PNG). */
    private fun effectiveExtension(format: ExportFormat): String = when (format) {
        ExportFormat.TIFF -> {
            Log.w(TAG, "TIFF is not natively encodable on Android; writing PNG bytes with .png extension")
            ExportFormat.PNG.extension
        }
        else -> format.extension
    }

    private fun encode(bitmap: Bitmap, outFile: File, cfg: ExportConfig): Boolean = runCatching {
        val format = when (cfg.format) {
            ExportFormat.JPEG -> Bitmap.CompressFormat.JPEG
            ExportFormat.PNG -> Bitmap.CompressFormat.PNG
            ExportFormat.WEBP -> Bitmap.CompressFormat.WEBP
            ExportFormat.TIFF -> {
                Log.w(TAG, "TIFF encoding not supported on Android; degrading to PNG. Output: ${outFile.name}")
                Bitmap.CompressFormat.PNG
            }
        }
        FileOutputStream(outFile).use { out ->
            bitmap.compress(format, cfg.quality, out)
        }
    }.onFailure { Log.w(TAG, "encode failed", it) }.getOrDefault(false)

    private fun resolveName(displayName: String, pattern: String): String {
        val base = displayName.substringBeforeLast('.')
        return pattern.replace("{name}", base)
            .replace("{date}", System.currentTimeMillis().toString())
            .ifBlank { base }
    }

    companion object {
        private const val TAG = "ExportService"
    }
}
