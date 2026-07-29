package com.alcedo.studio.ui.export

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alcedo.studio.data.model.AdjustmentParams
import com.alcedo.studio.data.model.ExportConfig
import com.alcedo.studio.permission.PermissionHelper
import com.alcedo.studio.data.model.ExportFormat
import com.alcedo.studio.data.model.ImageItem
import com.alcedo.studio.data.model.WatermarkConfig
import com.alcedo.studio.data.model.BackgroundTaskType
import com.alcedo.studio.domain.repository.ImageRepository
import com.alcedo.studio.domain.service.BackgroundTaskService
import com.alcedo.studio.domain.service.ExportService
import com.alcedo.studio.domain.service.HistoryMgmtService
import com.alcedo.studio.domain.service.PipelineService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Export ViewModel. Owns the export configuration form, drives single and batch
 * exports through [ExportService], and surfaces live progress for the export
 * sheet. The export renders through the editor's open [PipelineService] handle
 * so the exported image matches the on-screen preview exactly.
 */
@HiltViewModel
class ExportViewModel @Inject constructor(
    private val exportService: ExportService,
    private val pipelineService: PipelineService,
    private val imageRepository: ImageRepository,
    private val taskService: BackgroundTaskService,
    private val historyService: HistoryMgmtService,
) : ViewModel() {

    data class ExportUiState(
        val config: ExportConfig = ExportConfig(),
        val isExporting: Boolean = false,
        val completedCount: Int = 0,
        val totalCount: Int = 0,
        val lastOutputPath: String? = null,
        val results: List<ExportResult> = emptyList(),
        val bitDepth: Int = 8,
        val metaMode: MetadataMode = MetadataMode.KEEP_ALL,
        val maintainAspect: Boolean = true,
        val resizeWidth: String = "",
        val resizeHeight: String = "",
        val showWatermark: Boolean = false,
        val iccProfile: String = "sRGB IEC61966-2.1",
        val error: String? = null,
    )

    enum class MetadataMode(val label: String) {
        KEEP_ALL("Keep All"),
        STRIP("Strip All"),
        COPYRIGHT_ONLY("Copyright Only"),
    }

    data class ExportResult(
        val imageId: String,
        val displayName: String,
        val outputPath: String?,
        val success: Boolean,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    /** The coroutine running the current export loop, so [cancel] can interrupt it. */
    private val exportJob = MutableStateFlow<Job?>(null)

    /** Live export progress from the service. */
    val serviceProgress: StateFlow<ExportService.ExportProgress?> = exportService.progress

    // ---- Configuration mutators ------------------------------------------

    fun setFormat(format: ExportFormat) {
        _uiState.update { it.copy(config = it.config.copy(format = format)) }
    }

    fun setQuality(quality: Int) {
        _uiState.update { it.copy(config = it.config.copy(quality = quality.coerceIn(1, 100))) }
    }

    fun setMaxDimension(maxDimension: Int) {
        _uiState.update { it.copy(config = it.config.copy(maxDimension = maxDimension.coerceAtLeast(0))) }
    }

    fun setColorSpace(colorSpace: String) {
        _uiState.update { it.copy(config = it.config.copy(colorSpace = colorSpace)) }
    }

    fun setIccProfile(profile: String) {
        _uiState.update { it.copy(iccProfile = profile) }
    }

    fun setBitDepth(depth: Int) {
        _uiState.update { it.copy(bitDepth = depth) }
    }

    fun setMetaMode(mode: MetadataMode) {
        _uiState.update { it.copy(metaMode = mode) }
    }

    fun setMaintainAspect(maintain: Boolean) {
        _uiState.update { it.copy(maintainAspect = maintain) }
    }

    fun setResizeWidth(width: String) {
        _uiState.update { it.copy(resizeWidth = width.filter { c -> c.isDigit() }) }
    }

    fun setResizeHeight(height: String) {
        _uiState.update { it.copy(resizeHeight = height.filter { c -> c.isDigit() }) }
    }

    fun setShowWatermark(show: Boolean) {
        _uiState.update { it.copy(showWatermark = show, config = it.config.copy(includeWatermark = show)) }
    }

    fun setIncludeMetadata(include: Boolean) {
        _uiState.update { it.copy(config = it.config.copy(includeMetadata = include)) }
    }

    fun setUltraHdr(enabled: Boolean) {
        _uiState.update { it.copy(config = it.config.copy(ultraHdr = enabled && it.config.format == ExportFormat.JPEG)) }
    }

    fun setWatermarkEnabled(enabled: Boolean) {
        _uiState.update { it.copy(config = it.config.copy(includeWatermark = enabled)) }
    }

    fun updateWatermark(transform: (WatermarkConfig) -> WatermarkConfig) {
        _uiState.update { it.copy(config = it.config.copy(watermark = transform(it.config.watermark))) }
    }

    fun setOutputDirectory(path: String?) {
        _uiState.update { it.copy(config = it.config.copy(outputDirectory = path)) }
    }

    fun setNamingPattern(pattern: String) {
        _uiState.update { it.copy(config = it.config.copy(namingPattern = pattern.ifBlank { "{name}_edit" })) }
    }

    // ---- Export execution ------------------------------------------------

    /** Export the currently open editor image. */
    fun exportCurrent(imageId: String) {
        if (!hasExportPermissions()) {
            _uiState.update { it.copy(error = "Storage write permission required for export. Please grant the permission in Settings.") }
            return
        }
        val handle = pipelineService.handle
        if (handle == 0L) {
            _uiState.update { it.copy(error = "No active pipeline. Open an image in the editor first.") }
            return
        }
        startExport {
            val item = imageRepository.getImage(imageId) ?: run {
                _uiState.update { it.copy(error = "Image not found: $imageId") }
                return@startExport
            }
            runExport(listOf(item), dedicatedHandles = false)
        }
    }

    /**
     * Batch export a set of image ids. Each image gets its OWN dedicated
     * pipeline handle (created from that image's URI), its saved edit state is
     * loaded from history and applied, then it is rendered and exported. This
     * avoids exporting every image with the currently-open image's pipeline.
     */
    fun exportBatch(imageIds: List<String>) {
        if (imageIds.isEmpty()) return
        if (!hasExportPermissions()) {
            _uiState.update { it.copy(error = "Storage write permission required for export. Please grant the permission in Settings.") }
            return
        }
        startExport {
            val items = imageIds.mapNotNull { id -> imageRepository.getImage(id) }
            if (items.isEmpty()) {
                _uiState.update { it.copy(error = "No exportable images found.") }
                return@startExport
            }
            runExport(items, dedicatedHandles = true)
        }
    }

    private fun startExport(block: suspend () -> Unit) {
        // Cancel any in-flight export before starting a new one.
        exportJob.value?.cancel()
        val job = viewModelScope.launch { block() }
        exportJob.value = job
    }

    /**
     * @param dedicatedHandles when true, a fresh off-screen pipeline is created
     *  per image (loaded with that image's saved edit state) so batch export
     *  does not reuse the editor's open-image handle. When false, the editor's
     *  shared handle is used (single export of the open image).
     */
    private suspend fun runExport(items: List<ImageItem>, dedicatedHandles: Boolean) {
        val cfg = buildExportConfig()
        val total = items.size
        val taskId = taskService.start(
            BackgroundTaskType.EXPORT,
            if (total == 1) "Exporting ${items.first().displayName}" else "Exporting $total images",
            total,
        )
        _uiState.update {
            it.copy(isExporting = true, totalCount = total, completedCount = 0, results = emptyList(), error = null)
        }

        val results = mutableListOf<ExportResult>()
        for ((index, item) in items.withIndex()) {
            // Cooperative cancellation: abort early if the job was cancelled.
            coroutineContext.ensureActive()
            if (taskService.isCancelled(taskId)) break

            val handle = if (dedicatedHandles) {
                val h = runCatching { pipelineService.createForImage(Uri.parse(item.originalUri)) }.getOrDefault(0L)
                if (h != 0L) {
                    // Load this image's saved cumulative params and apply them.
                    val params = runCatching { historyService.getActiveVersion(item.id)?.cumulativeParams }
                        .getOrNull() ?: AdjustmentParams.DEFAULT
                    pipelineService.applyParamsToHandle(h, params)
                }
                h
            } else {
                pipelineService.handle
            }

            try {
                val path = if (handle == 0L) {
                    null
                } else {
                    val request = ExportService.ExportRequest(
                        imageId = item.id,
                        displayName = item.displayName,
                        pipelineHandle = handle,
                        config = cfg,
                    )
                    runCatching { exportService.export(request) }.getOrNull()
                }
                val success = path != null
                results += ExportResult(item.id, item.displayName, path, success, if (!success) "export_failed" else null)
            } finally {
                if (dedicatedHandles && handle != 0L) pipelineService.releaseHandle(handle)
            }

            taskService.update(taskId, index + 1, total)
            _uiState.update {
                it.copy(
                    completedCount = index + 1,
                    results = results.toList(),
                    lastOutputPath = results.lastOrNull { p -> p.success }?.outputPath ?: it.lastOutputPath,
                )
            }
        }

        val cancelled = taskService.isCancelled(taskId)
        val failures = results.count { !it.success }
        taskService.complete(taskId, if (cancelled) "cancelled" else if (failures == total) "all_failed" else null)
        _uiState.update {
            it.copy(
                isExporting = false,
                error = when {
                    cancelled -> null
                    failures > 0 -> "$failures of $total exports failed"
                    else -> null
                },
            )
        }
    }

    /** Merge the UI-state-only export fields into the [ExportConfig] sent to the service. */
    private fun buildExportConfig(): ExportConfig {
        val s = _uiState.value
        return s.config.copy(
            bitDepth = s.bitDepth,
            metaMode = s.metaMode.name,
            maintainAspect = s.maintainAspect,
            resizeWidth = s.resizeWidth.toIntOrNull() ?: 0,
            resizeHeight = s.resizeHeight.toIntOrNull() ?: 0,
            iccProfile = s.iccProfile,
        )
    }

    /** Cancel the running export loop (if any) and reset the exporting flag. */
    fun cancel() {
        exportJob.value?.cancel()
        exportJob.value = null
        _uiState.update { it.copy(isExporting = false) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun resetResults() {
        _uiState.update { it.copy(results = emptyList(), completedCount = 0, totalCount = 0, lastOutputPath = null) }
    }

    /**
     * Check if the app has the necessary write permissions for export.
     * On API 29 and below, WRITE_EXTERNAL_STORAGE is required.
     * On API 30+, scoped storage handles this automatically.
     */
    private fun hasExportPermissions(): Boolean {
        val writePerms = PermissionHelper.exportPermissions()
        if (writePerms.isEmpty()) return true // API 30+ — no write permission needed
        return PermissionHelper.areAllGranted(
            com.alcedo.studio.util.ContextProvider.requireContext(),
            writePerms,
        )
    }
}
