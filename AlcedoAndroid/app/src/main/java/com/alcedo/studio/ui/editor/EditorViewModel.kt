package com.alcedo.studio.ui.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alcedo.studio.data.model.AdjustmentParams
import com.alcedo.studio.data.model.AdjustmentParamsDelta
import com.alcedo.studio.data.model.ColorLabel
import com.alcedo.studio.data.model.CurvePoint
import com.alcedo.studio.data.model.EditTransaction
import com.alcedo.studio.data.model.ImageFlag
import com.alcedo.studio.data.model.ImageItem
import com.alcedo.studio.data.model.Mask
import com.alcedo.studio.data.model.MaskRecord
import com.alcedo.studio.data.model.PipelinePreset
import com.alcedo.studio.data.model.Version
import com.alcedo.studio.data.model.WatermarkConfig
import com.alcedo.studio.domain.repository.ImageRepository
import com.alcedo.studio.domain.service.ExifEditorService
import com.alcedo.studio.domain.service.HistoryMgmtService
import com.alcedo.studio.domain.service.MaskInferenceService
import com.alcedo.studio.domain.service.MaskService
import com.alcedo.studio.domain.service.PipelineService
import com.alcedo.studio.domain.service.PresetService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Editor screen ViewModel. Drives the non-destructive pipeline for the open
 * image: holds the working [AdjustmentParams], pushes live previews through
 * [PipelineService], records committed changes onto the version tree via
 * [HistoryMgmtService], and exposes presets, masks and EXIF for the editor
 * panels.
 *
 * The pipeline's rendered preview is published by [PipelineService.state] and
 * collected directly by the viewport composable; this ViewModel owns the
 * param/selection/panel state surrounding it.
 */
@HiltViewModel
class EditorViewModel @Inject constructor(
    private val pipelineService: PipelineService,
    private val historyService: HistoryMgmtService,
    private val presetService: PresetService,
    private val maskService: MaskService,
    private val maskInferenceService: MaskInferenceService,
    private val exifService: ExifEditorService,
    private val imageRepository: ImageRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Identifier of the image being edited, sourced from the nav route. */
    val imageId: String? = savedStateHandle[KEY_IMAGE_ID]

    data class EditorUiState(
        val image: ImageItem? = null,
        val isReady: Boolean = false,
        val isRendering: Boolean = false,
        val params: AdjustmentParams = AdjustmentParams.DEFAULT,
        val baselineParams: AdjustmentParams = AdjustmentParams.DEFAULT,
        val activePanel: EditorPanel = EditorPanel.BASIC,
        val versions: List<Version> = emptyList(),
        val activeVersionId: String? = null,
        val transactions: List<EditTransaction> = emptyList(),
        val canUndo: Boolean = false,
        val canRedo: Boolean = false,
        val presets: List<PipelinePreset> = emptyList(),
        val favoritePresets: List<PipelinePreset> = emptyList(),
        val masks: List<MaskRecord> = emptyList(),
        val exif: Map<String, String> = emptyMap(),
        val watermark: WatermarkConfig = WatermarkConfig(),
        val beforeBitmap: Bitmap? = null,
        val error: String? = null,
        val dirty: Boolean = false,
    )

    enum class EditorPanel {
        BASIC, TONE_CURVE, COLOR_WHEELS, HSL, GEOMETRY, EFFECTS, MASKS, PRESETS, HISTORY, RAW, EXIF
    }

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    /** Live pipeline state (preview bitmap + render flags) for the viewport. */
    val pipelineState: StateFlow<PipelineService.PipelineState> = pipelineService.state

    private var pendingMasks = mutableListOf<MaskRecord>()

    /** Debounce render job to prevent pipeline overflow on rapid slider changes. */
    private var renderDebounceJob: Job? = null

    init {
        // Mirror pipeline readiness/rendering into UI state and expose preview params.
        viewModelScope.launch {
            pipelineService.state.collect { ps ->
                _uiState.update {
                    it.copy(
                        isReady = ps.isReady,
                        isRendering = ps.isRendering,
                        params = ps.params,
                        error = ps.error ?: it.error,
                    )
                }
            }
        }
        imageId?.let { openImage(it) }
    }

    // ---- Open / close ----------------------------------------------------

    fun openImage(id: String) {
        viewModelScope.launch {
            val item = imageRepository.getImage(id) ?: run {
                _uiState.update { it.copy(error = "Image not found: $id") }
                return@launch
            }
            _uiState.update { it.copy(image = item) }
            // Ensure a version tree exists before editing.
            val version = runCatching { historyService.ensureHistory(id) }.getOrNull()
            val baseline = version?.cumulativeParams ?: AdjustmentParams.DEFAULT
            _uiState.update {
                it.copy(activeVersionId = version?.id, baselineParams = baseline, params = baseline)
            }
            // Open the pipeline for live preview.
            val opened = runCatching { pipelineService.open(Uri.parse(item.originalUri)) }.getOrDefault(false)
            if (opened) {
                // The pipeline resets params to DEFAULT on open, so re-push the
                // version's cumulative params so the preview matches saved state.
                pipelineService.updateParams(baseline)
                // Snapshot the unedited preview as the "before" bitmap for compare mode.
                val initial = pipelineService.state.value.previewBitmap
                _uiState.update { it.copy(beforeBitmap = initial) }
            } else {
                _uiState.update { it.copy(error = it.error ?: "Open failed") }
            }
            // Load supporting data: versions, presets, EXIF.
            observeVersions(id)
            loadPresets()
            loadExif(item)
        }
    }

    fun close() {
        commitIfDirty(label = "Auto-save")
        pipelineService.close()
        _uiState.value = EditorUiState()
    }

    // ---- Adjustment editing ----------------------------------------------

    /**
     * Update a single scalar adjustment and push it to the pipeline for a live
     * preview. The change is not recorded onto the version tree until
     * [commitChange] is called.
     */
    fun updateParam(field: String, value: Float) {
        val current = _uiState.value.params
        val updated = applyField(current, field, value)
        _uiState.update { it.copy(params = updated, dirty = updated != it.baselineParams) }
        pipelineService.updateParams(updated)
        // Debounce the re-render so rapid slider changes don't flood the pipeline.
        renderDebounceJob?.cancel()
        renderDebounceJob = viewModelScope.launch {
            delay(RENDER_DEBOUNCE_MS)
            pipelineService.render()
        }
    }

    /** Replace the entire adjustment set (e.g. when applying a preset). */
    fun setParams(params: AdjustmentParams) {
        _uiState.update { it.copy(params = params, dirty = params != it.baselineParams) }
        pipelineService.updateParams(params)
    }

    /** Reset all adjustments to defaults. */
    fun resetAdjustments() {
        setParams(AdjustmentParams.DEFAULT)
        commitChange("Reset adjustments")
    }

    /** Force a re-render of the current params. */
    fun rerender() = viewModelScope.launch {
        runCatching { pipelineService.render() }
            .onFailure { e -> _uiState.update { it.copy(error = "Render failed: ${e.message}") } }
    }

    /**
     * Record the current params onto the active version as a transaction. Builds
     * a sparse [AdjustmentParamsDelta] from the diff against the version's
     * baseline so the history panel shows meaningful, replayable changes.
     */
    fun commitChange(label: String = "Adjustment") {
        val id = imageId ?: return
        val state = _uiState.value
        if (state.params == state.baselineParams) return
        val delta = buildDelta(state.baselineParams, state.params)
        viewModelScope.launch {
            runCatching { historyService.recordChange(id, delta, label) }
                .onSuccess {
                    _uiState.update { it.copy(baselineParams = state.params, dirty = false, canRedo = false) }
                    observeTransactions(state.activeVersionId)
                }
                .onFailure { e -> _uiState.update { it.copy(error = "Commit failed: ${e.message}") } }
        }
    }

    /** Commit any uncommitted change before leaving the editor. */
    private fun commitIfDirty(label: String) {
        if (_uiState.value.dirty) commitChange(label)
    }

    // ---- Undo / versions -------------------------------------------------

    fun undo() {
        val id = imageId ?: return
        viewModelScope.launch {
            runCatching { historyService.undo(id) }
                .onSuccess {
                    reloadActiveVersionParams(id)
                    // After an undo, redo becomes available
                    _uiState.update { it.copy(canRedo = true) }
                }
                .onFailure { e -> _uiState.update { it.copy(error = "Undo failed: ${e.message}") } }
        }
    }

    fun redo() {
        val id = imageId ?: return
        viewModelScope.launch {
            runCatching { historyService.redo(id) }
                .onSuccess {
                    reloadActiveVersionParams(id)
                    // After a redo, check if more redo is available
                    // For simplicity, keep canRedo true until a new edit resets it
                }
                .onFailure { e -> _uiState.update { it.copy(error = "Redo failed: ${e.message}") } }
        }
    }

    fun switchVersion(versionId: String) {
        val id = imageId ?: return
        viewModelScope.launch {
            runCatching { historyService.switchVersion(id, versionId) }
                .onSuccess { reloadActiveVersionParams(id) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun createVirtualCopy(name: String = "Virtual Copy") {
        val id = imageId ?: return
        commitIfDirty("Auto-save before virtual copy")
        viewModelScope.launch {
            runCatching { historyService.createVirtualCopy(id, name) }
                .onSuccess { copy -> copy?.let { switchVersion(it.id) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun deleteVersion(versionId: String) {
        val id = imageId ?: return
        viewModelScope.launch {
            runCatching { historyService.deleteVersion(id, versionId) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    private fun reloadActiveVersionParams(id: String) = viewModelScope.launch {
        val history = runCatching { historyService.getHistory(id) }.getOrNull() ?: return@launch
        val active = history.current
        if (active != null) {
            _uiState.update {
                it.copy(
                    params = active.cumulativeParams,
                    baselineParams = active.cumulativeParams,
                    activeVersionId = active.id,
                    dirty = false,
                )
            }
            pipelineService.updateParams(active.cumulativeParams)
            observeTransactions(active.id)
        }
    }

    private fun observeVersions(id: String) = viewModelScope.launch {
        historyService.observeVersions(id).collect { versions ->
            _uiState.update { it.copy(versions = versions) }
            val active = versions.firstOrNull { v -> v.isActive } ?: versions.firstOrNull()
            active?.let { observeTransactions(it.id) }
        }
    }

    private fun observeTransactions(versionId: String?) = viewModelScope.launch {
        if (versionId == null) return@launch
        historyService.observeTransactions(versionId).collect { txs ->
            _uiState.update {
                it.copy(
                    transactions = txs,
                    canUndo = txs.isNotEmpty(),
                    canRedo = false, // Redo is available only after an undo; reset on new transaction
                )
            }
        }
    }

    // ---- Presets ---------------------------------------------------------

    private fun loadPresets() = viewModelScope.launch {
        runCatching { presetService.ensureBuiltIns() }
        presetService.observeAll().collect { presets ->
            _uiState.update {
                it.copy(
                    presets = presets,
                    favoritePresets = presets.filter { p -> p.isFavorite },
                )
            }
        }
    }

    fun applyPreset(preset: PipelinePreset) {
        setParams(preset.adjustments)
        commitChange("Preset: ${preset.name}")
    }

    fun saveCurrentAsPreset(name: String, category: String = "User") {
        val params = _uiState.value.params
        viewModelScope.launch {
            runCatching { presetService.save(PresetService.newPreset(name, category, params)) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun togglePresetFavorite(preset: PipelinePreset) = viewModelScope.launch {
        runCatching { presetService.setFavorite(preset.id, !preset.isFavorite) }
            .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
    }

    // ---- Masks -----------------------------------------------------------

    fun addBrushMask() = addMask(maskService.newBrushMask(activeVersionIdOrEmpty()))
    fun addRadialMask(cx: Float, cy: Float, rx: Float, ry: Float) =
        addMask(maskService.newRadialMask(activeVersionIdOrEmpty(), cx, cy, rx, ry))
    fun addLinearMask(sx: Float, sy: Float, ex: Float, ey: Float) =
        addMask(maskService.newLinearMask(activeVersionIdOrEmpty(), sx, sy, ex, ey))
    fun addLuminanceMask(min: Float, max: Float) =
        addMask(maskService.newLuminanceMask(activeVersionIdOrEmpty(), min, max))

    /** Run AI segmentation for the whole subject and add it as a mask. */
    fun addSubjectMask() = addAiSubjectMask("subject")

    /** Run AI segmentation to isolate the sky and add it as a mask. */
    fun addSkyMask() = addAiSubjectMask("sky")

    /** Run AI segmentation to isolate the background and add it as a mask. */
    fun addBackgroundMask() = addAiSubjectMask("background")

    private fun addAiSubjectMask(subjectKind: String) {
        val item = _uiState.value.image ?: return
        val versionId = activeVersionIdOrEmpty()
        viewModelScope.launch {
            runCatching {
                maskInferenceService.buildSubjectMask(Uri.parse(item.originalUri), versionId, subjectKind)
            }.onSuccess { mask ->
                if (mask != null) {
                    addMask(mask)
                } else {
                    _uiState.update { it.copy(error = "AI mask unavailable for '$subjectKind'") }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(error = "AI mask failed: ${e.message}") }
            }
        }
    }

    private fun addMask(mask: Mask) {
        val record = maskService.toRecord(mask)
        pendingMasks.add(record)
        runCatching { pipelineService.applyMask(record) }
            .onFailure { e -> _uiState.update { it.copy(error = "Mask apply failed: ${e.message}") } }
        _uiState.update { it.copy(masks = pendingMasks.toList()) }
    }

    fun toggleMask(record: MaskRecord) {
        pendingMasks = pendingMasks.map { if (it.id == record.id) it.copy(enabled = !it.enabled) else it }.toMutableList()
        _uiState.update { it.copy(masks = pendingMasks.toList()) }
        syncMasksToPipeline()
    }

    fun removeMask(id: String) {
        pendingMasks.removeAll { it.id == id }
        _uiState.update { it.copy(masks = pendingMasks.toList()) }
        syncMasksToPipeline()
    }

    /**
     * Re-sync the local mask set to the native pipeline. The native layer has no
     * per-mask remove/toggle, so we clear and re-apply only the enabled masks so
     * toggles and removals actually take effect in the rendered preview (a plain
     * [rerender] only re-applies adjustments, not masks).
     */
    private fun syncMasksToPipeline() {
        pipelineService.clearMasks()
        pendingMasks.filter { it.enabled }.forEach { record ->
            runCatching { pipelineService.applyMask(record) }
                .onFailure { e -> _uiState.update { it.copy(error = "Mask re-apply failed: ${e.message}") } }
        }
        rerender()
    }

    // ---- EXIF / metadata -------------------------------------------------

    private fun loadExif(item: ImageItem) = viewModelScope.launch {
        runCatching { exifService.read(Uri.parse(item.originalUri)) }
            .onSuccess { exif -> _uiState.update { it.copy(exif = exif) } }
            .onFailure { /* EXIF is best-effort; leave empty. */ }
    }

    /** Update a single EXIF field in the in-memory map (written at export). */
    fun setExifField(key: String, value: String) {
        _uiState.update {
            it.copy(exif = it.exif + (key to value), dirty = true)
        }
    }

    /** Replace the master tone curve control points and push to the pipeline. */
    fun setCurvePoints(points: List<CurvePoint>) {
        val current = _uiState.value.params
        val updated = current.copy(toneCurveMaster = points)
        _uiState.update { it.copy(params = updated, dirty = updated != it.baselineParams) }
        pipelineService.updateParams(updated)
    }

    /** Apply an LMT (.cube) file path to the pipeline as the active LUT. */
    fun applyLmt(path: String) {
        val current = _uiState.value.params
        val updated = current.copy(lutPath = path, lutIntensity = 1f)
        _uiState.update { it.copy(params = updated, dirty = updated != it.baselineParams) }
        pipelineService.updateParams(updated)
    }

    /** Update the watermark config used by the editor's preview/export. */
    fun setWatermarkConfig(config: WatermarkConfig) {
        _uiState.update { it.copy(watermark = config, dirty = true) }
    }

    fun setRating(rating: Int) {
        val id = imageId ?: return
        viewModelScope.launch {
            runCatching { imageRepository.setRating(id, rating.coerceIn(0, 5)) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun setFlag(flag: ImageFlag) {
        val id = imageId ?: return
        viewModelScope.launch {
            runCatching { imageRepository.setFlag(id, flag) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun setColorLabel(label: ColorLabel) {
        val id = imageId ?: return
        viewModelScope.launch {
            runCatching { imageRepository.setColorLabel(id, label) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    // ---- Panel navigation ------------------------------------------------

    fun selectPanel(panel: EditorPanel) {
        _uiState.update { it.copy(activePanel = panel) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    /** Pipeline handle for the export flow to render at full resolution. */
    val pipelineHandle: Long get() = pipelineService.handle

    // ---- Helpers ---------------------------------------------------------

    private fun activeVersionIdOrEmpty(): String = _uiState.value.activeVersionId ?: ""

    /** Apply a single named float field onto a copy of [params]. */
    private fun applyField(params: AdjustmentParams, field: String, value: Float): AdjustmentParams =
        when (field) {
            "exposure" -> params.copy(exposure = value)
            "contrast" -> params.copy(contrast = value)
            "highlights" -> params.copy(highlights = value)
            "shadows" -> params.copy(shadows = value)
            "whites" -> params.copy(whites = value)
            "blacks" -> params.copy(blacks = value)
            "temperature" -> params.copy(temperature = value)
            "tint" -> params.copy(tint = value)
            "saturation" -> params.copy(saturation = value)
            "vibrance" -> params.copy(vibrance = value)
            "clarity" -> params.copy(clarity = value)
            "sharpen" -> params.copy(sharpen = value)
            "liftHue" -> params.copy(liftHue = value)
            "liftSat" -> params.copy(liftSat = value)
            "liftLum" -> params.copy(liftLum = value)
            "gammaHue" -> params.copy(gammaHue = value)
            "gammaSat" -> params.copy(gammaSat = value)
            "gammaLum" -> params.copy(gammaLum = value)
            "gainHue" -> params.copy(gainHue = value)
            "gainSat" -> params.copy(gainSat = value)
            "gainLum" -> params.copy(gainLum = value)
            "rotation" -> params.copy(rotation = value)
            "perspectiveH" -> params.copy(perspectiveH = value)
            "perspectiveV" -> params.copy(perspectiveV = value)
            "filmGrainAmount" -> params.copy(filmGrainAmount = value)
            "filmGrainSize" -> params.copy(filmGrainSize = value)
            "halationAmount" -> params.copy(halationAmount = value)
            "lutIntensity" -> params.copy(lutIntensity = value)
            "rawNoiseReduction" -> params.copy(rawNoiseReduction = value)
            else -> params
        }

    /**
     * Build a sparse delta of the scalar fields that differ between [baseline]
     * and [current]. Only changed fields are included so the version tree stays
     * replayable and human-readable.
     */
    private fun buildDelta(baseline: AdjustmentParams, current: AdjustmentParams): AdjustmentParamsDelta {
        val overrides = LinkedHashMap<String, String>()
        // ---- Basic tone ----
        if (current.exposure != baseline.exposure) overrides["exposure"] = current.exposure.toString()
        if (current.contrast != baseline.contrast) overrides["contrast"] = current.contrast.toString()
        if (current.highlights != baseline.highlights) overrides["highlights"] = current.highlights.toString()
        if (current.shadows != baseline.shadows) overrides["shadows"] = current.shadows.toString()
        if (current.whites != baseline.whites) overrides["whites"] = current.whites.toString()
        if (current.blacks != baseline.blacks) overrides["blacks"] = current.blacks.toString()
        // ---- Color ----
        if (current.temperature != baseline.temperature) overrides["temperature"] = current.temperature.toString()
        if (current.tint != baseline.tint) overrides["tint"] = current.tint.toString()
        if (current.saturation != baseline.saturation) overrides["saturation"] = current.saturation.toString()
        if (current.vibrance != baseline.vibrance) overrides["vibrance"] = current.vibrance.toString()
        if (current.clarity != baseline.clarity) overrides["clarity"] = current.clarity.toString()
        if (current.sharpen != baseline.sharpen) overrides["sharpen"] = current.sharpen.toString()
        // ---- HLS (lift / gamma / gain) ----
        if (current.liftHue != baseline.liftHue) overrides["liftHue"] = current.liftHue.toString()
        if (current.liftSat != baseline.liftSat) overrides["liftSat"] = current.liftSat.toString()
        if (current.liftLum != baseline.liftLum) overrides["liftLum"] = current.liftLum.toString()
        if (current.gammaHue != baseline.gammaHue) overrides["gammaHue"] = current.gammaHue.toString()
        if (current.gammaSat != baseline.gammaSat) overrides["gammaSat"] = current.gammaSat.toString()
        if (current.gammaLum != baseline.gammaLum) overrides["gammaLum"] = current.gammaLum.toString()
        if (current.gainHue != baseline.gainHue) overrides["gainHue"] = current.gainHue.toString()
        if (current.gainSat != baseline.gainSat) overrides["gainSat"] = current.gainSat.toString()
        if (current.gainLum != baseline.gainLum) overrides["gainLum"] = current.gainLum.toString()
        // ---- Geometry ----
        if (current.rotation != baseline.rotation) overrides["rotation"] = current.rotation.toString()
        if (current.perspectiveH != baseline.perspectiveH) overrides["perspectiveH"] = current.perspectiveH.toString()
        if (current.perspectiveV != baseline.perspectiveV) overrides["perspectiveV"] = current.perspectiveV.toString()
        // ---- Effects ----
        if (current.filmGrainAmount != baseline.filmGrainAmount) overrides["filmGrainAmount"] = current.filmGrainAmount.toString()
        if (current.filmGrainSize != baseline.filmGrainSize) overrides["filmGrainSize"] = current.filmGrainSize.toString()
        if (current.halationAmount != baseline.halationAmount) overrides["halationAmount"] = current.halationAmount.toString()
        if (current.lutIntensity != baseline.lutIntensity) overrides["lutIntensity"] = current.lutIntensity.toString()
        // ---- RAW ----
        if (current.rawNoiseReduction != baseline.rawNoiseReduction) overrides["rawNoiseReduction"] = current.rawNoiseReduction.toString()
        // ---- Display / LUT path (string fields) ----
        if (current.displayTransform != baseline.displayTransform) overrides["displayTransform"] = current.displayTransform
        if (current.outputColorSpace != baseline.outputColorSpace) overrides["outputColorSpace"] = current.outputColorSpace
        if (current.displayEotf != baseline.displayEotf) overrides["displayEotf"] = current.displayEotf
        if (current.peakLuminanceNits != baseline.peakLuminanceNits) overrides["peakLuminanceNits"] = current.peakLuminanceNits.toString()
        if (current.rawDemosaic != baseline.rawDemosaic) overrides["rawDemosaic"] = current.rawDemosaic
        if (current.rawBlackLevel != baseline.rawBlackLevel) overrides["rawBlackLevel"] = current.rawBlackLevel.toString()
        if (current.rawWhitePoint != baseline.rawWhitePoint) overrides["rawWhitePoint"] = current.rawWhitePoint.toString()
        if (current.lutPath != baseline.lutPath) overrides["lutPath"] = current.lutPath
        return AdjustmentParamsDelta(overrides)
    }

    override fun onCleared() {
        super.onCleared()
        // Release native pipeline handles promptly on dispose.
        pipelineService.close()
    }

    companion object {
        const val KEY_IMAGE_ID = "imageId"
        private const val RENDER_DEBOUNCE_MS = 80L
    }
}
