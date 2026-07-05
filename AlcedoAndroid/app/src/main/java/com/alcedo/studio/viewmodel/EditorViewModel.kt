package com.alcedo.studio.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alcedo.studio.data.model.*
import com.alcedo.studio.di.AppModule
import com.alcedo.studio.domain.service.ExportService
import com.alcedo.studio.domain.service.PipelineService
import com.alcedo.studio.i18n.StringResources
import com.alcedo.studio.ndk.AlcedoNdkBridge
import com.alcedo.studio.ui.editor.ScopeAnalyzer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive

class EditorViewModel(private val imageId: String) : ViewModel() {

    companion object {
        private const val MAX_BITMAP_PIXELS = 4096 * 4096  // 16M pixels max
    }
    private val imageRepository by lazy { AppModule.imageRepository }
    private val editHistoryRepository by lazy { AppModule.editHistoryRepository }
    private val pipelineService by lazy { AppModule.pipelineService }
    private val exportService by lazy { AppModule.exportService }
    private val thumbnailService by lazy { AppModule.thumbnailService }

    // ── Image state ──

    private val _imageModel = MutableStateFlow<ImageModel?>(null)
    val imageModel: StateFlow<ImageModel?> = _imageModel

    private val _originalBitmap = MutableStateFlow<Bitmap?>(null)
    val originalBitmap: StateFlow<Bitmap?> = _originalBitmap

    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    // ── Pipeline parameters ──

    private val _params = mutableStateOf(PipelineParams())
    val params get() = _params

    // ── Tone curve ──

    private val _toneCurveX = mutableStateOf(floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f))
    val toneCurveX get() = _toneCurveX

    private val _toneCurveY = mutableStateOf(floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f))
    val toneCurveY get() = _toneCurveY

    // ── Color wheel (CDL) ──

    private val _colorWheelLift = mutableStateOf(floatArrayOf(0f, 0f, 0f))
    val colorWheelLift get() = _colorWheelLift

    private val _colorWheelGamma = mutableStateOf(floatArrayOf(1f, 1f, 1f))
    val colorWheelGamma get() = _colorWheelGamma

    private val _colorWheelGain = mutableStateOf(floatArrayOf(1f, 1f, 1f))
    val colorWheelGain get() = _colorWheelGain

    // ── HSL ──

    private val _hslHueShift = mutableStateOf(FloatArray(8) { 0f })
    val hslHueShift get() = _hslHueShift

    private val _hslSaturationScale = mutableStateOf(FloatArray(8) { 1f })
    val hslSaturationScale get() = _hslSaturationScale

    private val _hslLuminanceScale = mutableStateOf(FloatArray(8) { 1f })
    val hslLuminanceScale get() = _hslLuminanceScale

    // ── Comparison mode ──

    private val _isCompareMode = MutableStateFlow(false)
    val isCompareMode: StateFlow<Boolean> = _isCompareMode

    // ── Scope views ──

    private val _showHistogram = MutableStateFlow(true)
    val showHistogram: StateFlow<Boolean> = _showHistogram

    private val _showWaveform = MutableStateFlow(false)
    val showWaveform: StateFlow<Boolean> = _showWaveform

    private val _showVectorscope = MutableStateFlow(false)
    val showVectorscope: StateFlow<Boolean> = _showVectorscope

    // ── History / Version ──

    private val _history = MutableStateFlow<EditHistory?>(null)
    val history: StateFlow<EditHistory?> = _history

    private val _workingVersion = mutableStateOf(WorkingVersion())
    val workingVersion get() = _workingVersion

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo

    // ── Presets ──

    private val _presets = MutableStateFlow<List<PresetEntry>>(emptyList())
    val presets: StateFlow<List<PresetEntry>> = _presets

    // ── Export ──

    val exportProgress: StateFlow<ExportService.ExportProgress> = exportService.exportProgress

    private val _lastExportResult = MutableStateFlow<ExportService.ExportResult?>(null)
    val lastExportResult: StateFlow<ExportService.ExportResult?> = _lastExportResult.asStateFlow()

    // ── Pipeline snapshot ──

    private var snapshotHandle: Long = 0

    // ── 示波器状态 (Scope analyzer state persisted across rotation) ──

    private val _selectedPanel = MutableStateFlow(EditorPanel.BASIC)
    val selectedPanel: StateFlow<EditorPanel> = _selectedPanel.asStateFlow()

    private val _showScope = MutableStateFlow(false)
    val showScope: StateFlow<Boolean> = _showScope.asStateFlow()

    private val _selectedScopeType = MutableStateFlow(ScopeType.HISTOGRAM)
    val selectedScopeType: StateFlow<ScopeType> = _selectedScopeType.asStateFlow()

    private val _histogramChannel = MutableStateFlow(HistogramChannel.RGB)
    val histogramChannel: StateFlow<HistogramChannel> = _histogramChannel.asStateFlow()

    private val _histogramScale = MutableStateFlow(HistogramScale.LINEAR)
    val histogramScale: StateFlow<HistogramScale> = _histogramScale.asStateFlow()

    private val _waveformMode = MutableStateFlow(WaveformMode.RGB_PARADE)
    val waveformMode: StateFlow<WaveformMode> = _waveformMode.asStateFlow()

    private val _gamutOverlay = MutableStateFlow(setOf(GamutOverlay.SRGB))
    val gamutOverlay: StateFlow<Set<GamutOverlay>> = _gamutOverlay.asStateFlow()

    private val _showExport = MutableStateFlow(false)
    val showExport: StateFlow<Boolean> = _showExport.asStateFlow()

    fun updateSelectedPanel(panel: EditorPanel) { _selectedPanel.value = panel }
    fun toggleShowScope() { _showScope.value = !_showScope.value }
    fun dismissShowScope() { _showScope.value = false }
    fun updateSelectedScopeType(type: ScopeType) { _selectedScopeType.value = type }
    fun updateHistogramChannel(channel: HistogramChannel) { _histogramChannel.value = channel }
    fun updateHistogramScale(scale: HistogramScale) { _histogramScale.value = scale }
    fun updateWaveformMode(mode: WaveformMode) { _waveformMode.value = mode }
    fun updateGamutOverlay(overlay: Set<GamutOverlay>) { _gamutOverlay.value = overlay }
    fun toggleShowExport() { _showExport.value = !_showExport.value }
    fun dismissExport() { _showExport.value = false }

    init {
        try {
            loadImage()
        } catch (e: Throwable) {
            // Swallow startup failures; editor will simply show empty state.
        }
    }

    // ================================================================
    // Image loading
    // ================================================================

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        while ((width / sampleSize) * (height / sampleSize) > MAX_BITMAP_PIXELS) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun loadImage() {
        viewModelScope.launch {
            val id = imageId.toLongOrNull() ?: return@launch
            val img = imageRepository.getImage(id)
            _imageModel.value = img
            img?.let {
                try {
                    // First decode bounds only
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(it.imagePath, options)

                    // Calculate sample size for large images
                    val sampleSize = calculateSampleSize(options.outWidth, options.outHeight)

                    // Load with sampling
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val bitmap = BitmapFactory.decodeFile(it.imagePath, decodeOptions)
                    _originalBitmap.value = bitmap
                    _previewBitmap.value = bitmap
                } catch (e: OutOfMemoryError) {
                    Log.e("EditorVM", "OOM loading image, attempting recovery", e)
                    System.gc()
                    _originalBitmap.value = null
                    _previewBitmap.value = null
                } catch (e: Exception) {
                    Log.e("EditorVM", "Failed to load image", e)
                    _originalBitmap.value = null
                    _previewBitmap.value = null
                }
                _history.value = editHistoryRepository.getHistory(id.toUInt())
                    ?: EditHistory(boundImageId = id.toUInt())
                _workingVersion.value = WorkingVersion(
                    boundImageId = id.toUInt(),
                    versionId = _history.value?.activeVersionId ?: ""
                )
                // Load preset list
                loadPresets()
            }
        }
    }

    // ================================================================
    // Parameter adjustments (real-time)
    // ================================================================

    fun updateExposure(value: Float) {
        _params.value = _params.value.copy(exposure = value)
        recordTransaction(OperatorType.EXPOSURE, "exposure", value)
        regeneratePreview()
    }

    fun updateContrast(value: Float) {
        _params.value = _params.value.copy(contrast = value)
        recordTransaction(OperatorType.CONTRAST, "contrast", value)
        regeneratePreview()
    }

    fun updateHighlights(value: Float) {
        _params.value = _params.value.copy(highlights = value)
        recordTransaction(OperatorType.HIGHLIGHTS, "highlights", value)
        regeneratePreview()
    }

    fun updateShadows(value: Float) {
        _params.value = _params.value.copy(shadows = value)
        recordTransaction(OperatorType.SHADOWS, "shadows", value)
        regeneratePreview()
    }

    fun updateMidtones(value: Float) {
        _params.value = _params.value.copy(midtones = value)
        recordTransaction(OperatorType.TONE_REGION, "midtones", value)
        regeneratePreview()
    }

    fun updateWhiteBalance(temperature: Float, tint: Float) {
        _params.value = _params.value.copy(whiteBalanceTemp = temperature, whiteBalanceTint = tint)
        recordTransaction(OperatorType.WHITE_BALANCE, "temperature", temperature)
        regeneratePreview()
    }

    fun updateSaturation(value: Float) {
        _params.value = _params.value.copy(saturation = value)
        recordTransaction(OperatorType.SATURATION, "saturation", value)
        regeneratePreview()
    }

    fun updateVibrance(value: Float) {
        _params.value = _params.value.copy(vibrance = value)
        recordTransaction(OperatorType.SATURATION, "vibrance", value)
        regeneratePreview()
    }

    fun updateClarity(amount: Float, radius: Float = 15f) {
        _params.value = _params.value.copy(clarityAmount = amount, clarityRadius = radius)
        recordTransaction(OperatorType.CLARITY, "clarityAmount", amount)
        regeneratePreview()
    }

    fun updateSharpen(value: Float) {
        _params.value = _params.value.copy(sharpenAmount = value)
        recordTransaction(OperatorType.SHARPEN, "sharpenAmount", value)
        regeneratePreview()
    }

    fun updateFilmGrain(value: Float) {
        _params.value = _params.value.copy(filmGrainIntensity = value)
        recordTransaction(OperatorType.FILM_GRAIN, "filmGrainIntensity", value)
        regeneratePreview()
    }

    fun updateHalation(intensity: Float, threshold: Float = 0.8f, spread: Float = 10f, redBias: Float = 0.7f) {
        _params.value = _params.value.copy(
            halationIntensity = intensity,
            halationThreshold = threshold,
            halationSpread = spread,
            halationRedBias = redBias
        )
        recordTransaction(OperatorType.HALATION, "halationIntensity", intensity)
        regeneratePreview()
    }

    fun updateSigmoidContrast(value: Float) {
        _params.value = _params.value.copy(sigmoidContrast = value)
        recordTransaction(OperatorType.CONTRAST, "sigmoidContrast", value)
        regeneratePreview()
    }

    fun updateLensCorrection(k1: Float, k2: Float, k3: Float, p1: Float, p2: Float) {
        _params.value = _params.value.copy(lensK1 = k1, lensK2 = k2, lensK3 = k3, lensP1 = p1, lensP2 = p2)
        recordTransaction(OperatorType.GEOMETRY, "lensK1", k1)
        regeneratePreview()
    }

    fun updateLut(enabled: Boolean, path: String) {
        _params.value = _params.value.copy(lutEnabled = enabled, lutPath = path)
        recordTransaction(OperatorType.LUT, "lutPath", if (enabled) 1f else 0f)
        regeneratePreview()
    }

    fun updateDisplayTransform(displayTransform: DisplayTransform) {
        _params.value = _params.value.copy(displayTransform = displayTransform)
        recordTransaction(OperatorType.DISPLAY_TRANSFORM, "colorScience", displayTransform.colorScience.ordinal.toFloat())
        regeneratePreview()
    }

    fun updateParams(newParams: PipelineParams) {
        _params.value = newParams
        regeneratePreview()
    }

    // ================================================================
    // Tone curve editing
    // ================================================================

    fun updateToneCurve(x: FloatArray, y: FloatArray) {
        _toneCurveX.value = x
        _toneCurveY.value = y
        _params.value = _params.value.copy(
            toneCurveX = x,
            toneCurveY = y,
            toneCurvePoints = x.size
        )
        recordTransaction(OperatorType.TONE_CURVE, "toneCurvePoints", x.size.toFloat())
        regeneratePreview()
    }

    fun resetToneCurve() {
        val defaultX = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        val defaultY = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        updateToneCurve(defaultX, defaultY)
    }

    // ================================================================
    // Color wheel editing (lift/gamma/gain)
    // ================================================================

    fun updateColorWheelLift(lift: FloatArray) {
        _colorWheelLift.value = lift
        _params.value = _params.value.copy(
            colorWheelLiftR = lift[0],
            colorWheelLiftG = lift[1],
            colorWheelLiftB = lift[2]
        )
        recordTransaction(OperatorType.COLOR_WHEEL, "liftR", lift[0])
        regeneratePreview()
    }

    fun updateColorWheelGamma(gamma: FloatArray) {
        _colorWheelGamma.value = gamma
        _params.value = _params.value.copy(
            colorWheelGammaR = gamma[0],
            colorWheelGammaG = gamma[1],
            colorWheelGammaB = gamma[2]
        )
        recordTransaction(OperatorType.COLOR_WHEEL, "gammaR", gamma[0])
        regeneratePreview()
    }

    fun updateColorWheelGain(gain: FloatArray) {
        _colorWheelGain.value = gain
        _params.value = _params.value.copy(
            colorWheelGainR = gain[0],
            colorWheelGainG = gain[1],
            colorWheelGainB = gain[2]
        )
        recordTransaction(OperatorType.COLOR_WHEEL, "gainR", gain[0])
        regeneratePreview()
    }

    fun resetColorWheels() {
        updateColorWheelLift(floatArrayOf(0f, 0f, 0f))
        updateColorWheelGamma(floatArrayOf(1f, 1f, 1f))
        updateColorWheelGain(floatArrayOf(1f, 1f, 1f))
    }

    // ================================================================
    // HSL editing
    // ================================================================

    fun updateHslHueShift(index: Int, value: Float) {
        val current = _hslHueShift.value.copyOf()
        current[index] = value
        _hslHueShift.value = current
        _params.value = _params.value.copy(hslHueShift = current)
        recordTransaction(OperatorType.HSL, "hslHueShift[$index]", value)
        regeneratePreview()
    }

    fun updateHslSaturationScale(index: Int, value: Float) {
        val current = _hslSaturationScale.value.copyOf()
        current[index] = value
        _hslSaturationScale.value = current
        _params.value = _params.value.copy(hslSaturationScale = current)
        recordTransaction(OperatorType.HSL, "hslSaturationScale[$index]", value)
        regeneratePreview()
    }

    fun updateHslLuminanceScale(index: Int, value: Float) {
        val current = _hslLuminanceScale.value.copyOf()
        current[index] = value
        _hslLuminanceScale.value = current
        _params.value = _params.value.copy(hslLuminanceScale = current)
        recordTransaction(OperatorType.HSL, "hslLuminanceScale[$index]", value)
        regeneratePreview()
    }

    fun resetHsl() {
        _hslHueShift.value = FloatArray(8) { 0f }
        _hslSaturationScale.value = FloatArray(8) { 1f }
        _hslLuminanceScale.value = FloatArray(8) { 1f }
        _params.value = _params.value.copy(
            hslHueShift = FloatArray(8) { 0f },
            hslSaturationScale = FloatArray(8) { 1f },
            hslLuminanceScale = FloatArray(8) { 1f }
        )
        regeneratePreview()
    }

    // ================================================================
    // Tint (split toning)
    // ================================================================

    fun updateTint(
        highlightHue: Float, highlightStrength: Float,
        shadowHue: Float, shadowStrength: Float, balance: Float
    ) {
        _params.value = _params.value.copy(
            tintHighlightHue = highlightHue,
            tintHighlightStrength = highlightStrength,
            tintShadowHue = shadowHue,
            tintShadowStrength = shadowStrength,
            tintBalance = balance
        )
        recordTransaction(OperatorType.TINT, "tintBalance", balance)
        regeneratePreview()
    }

    // ================================================================
    // Channel mixer
    // ================================================================

    fun updateChannelMixer(matrix: FloatArray, monochrome: Boolean) {
        _params.value = _params.value.copy(channelMixerMatrix = matrix, channelMixerMonochrome = monochrome)
        recordTransaction(OperatorType.COLOR_WHEEL, "channelMixerMonochrome", if (monochrome) 1f else 0f)
        regeneratePreview()
    }

    // ================================================================
    // Before/After comparison
    // ================================================================

    fun toggleCompareMode() {
        _isCompareMode.value = !_isCompareMode.value
    }

    fun getOriginalBitmap(): Bitmap? = _originalBitmap.value

    // ================================================================
    // Preview regeneration
    // ================================================================

    fun regeneratePreview() {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                val source = _originalBitmap.value
                if (source != null) {
                    _previewBitmap.value = pipelineService.applyPipeline(source, _params.value)
                }
            } catch (e: Throwable) {
                android.util.Log.e("EditorVM", "regeneratePreview failed", e)
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // ================================================================
    // Auto exposure
    // ================================================================

    fun applyAutoExposure() {
        viewModelScope.launch {
            try {
                val bitmap = _originalBitmap.value ?: return@launch
                val width = bitmap.width
                val height = bitmap.height
                val pixelCount = width * height

                val pixels = IntArray(pixelCount)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                val floatPixels = FloatArray(pixelCount * 4)
                for (i in 0 until pixelCount) {
                    val pixel = pixels[i]
                    floatPixels[i * 4]     = ((pixel shr 16) and 0xFF) / 255.0f
                    floatPixels[i * 4 + 1] = ((pixel shr 8) and 0xFF) / 255.0f
                    floatPixels[i * 4 + 2] = (pixel and 0xFF) / 255.0f
                    floatPixels[i * 4 + 3] = ((pixel shr 24) and 0xFF) / 255.0f
                }

                val ev = pipelineService.computeAutoExposure(
                    floatPixels, width, height, 4,
                    _params.value.autoExposureTargetPercentile,
                    _params.value.autoExposureTargetLuminance
                )

                _params.value = _params.value.copy(
                    autoExposureEnabled = true,
                    autoExposureValue = ev,
                    exposure = ev
                )
                recordTransaction(OperatorType.EXPOSURE, "autoExposure", ev)
                regeneratePreview()
            } catch (e: Throwable) {
                android.util.Log.e("EditorVM", "Coroutine failed", e)
            }
        }
    }

    // ================================================================
    // Auto Enhance (智能优化)
    // ================================================================

    fun autoEnhance() {
        // 智能优化 — 自动分析图像并应用最佳调整
        viewModelScope.launch {
            try {
                val currentParams = _params.value

                // 计算自动曝光补偿
                val autoExposure = analyzeExposure()
                val autoContrast = analyzeContrast()
                val autoSaturation = analyzeSaturation()

                // 应用自动调整
                _params.value = currentParams.copy(
                    exposure = autoExposure,
                    contrast = autoContrast,
                    saturation = autoSaturation
                )

                recordTransaction(OperatorType.EXPOSURE, "exposure", autoExposure)
                recordTransaction(OperatorType.CONTRAST, "contrast", autoContrast)
                recordTransaction(OperatorType.SATURATION, "saturation", autoSaturation)

                // 记录到历史
                saveVersion()
                regeneratePreview()
            } catch (e: Throwable) {
                android.util.Log.e("EditorVM", "autoEnhance failed", e)
            }
        }
    }

    private suspend fun analyzeExposure(): Float {
        // 基于预览位图分析直方图，计算最佳曝光补偿
        val previewBitmap = _previewBitmap.value ?: return 0f
        val histogram = ScopeAnalyzer.computeHistogram(previewBitmap)
        val lum = histogram.luminance
        var total = 0f
        var weighted = 0f
        for (i in lum.indices) {
            total += lum[i]
            weighted += lum[i] * (i / 255f)
        }
        val avgBrightness = if (total > 0f) weighted / total else 0.5f
        // 目标亮度: 0.5 (中灰)
        return ((0.5f - avgBrightness) * 2f).coerceIn(-2f, 2f)
    }

    private suspend fun analyzeContrast(): Float {
        val previewBitmap = _previewBitmap.value ?: return 0f
        val histogram = ScopeAnalyzer.computeHistogram(previewBitmap)
        val lum = histogram.luminance
        var total = 0f
        var weighted = 0f
        for (i in lum.indices) {
            total += lum[i]
            weighted += lum[i] * (i / 255f)
        }
        val mean = if (total > 0f) weighted / total else 0.5f
        var variance = 0f
        for (i in lum.indices) {
            val diff = (i / 255f) - mean
            variance += lum[i] * diff * diff
        }
        val stddev = if (total > 0f) kotlin.math.sqrt(variance / total) else 0f
        // 对比度调整: 标准差小于0.25时增加对比度
        return ((0.25f - stddev) * 2f).coerceIn(-0.5f, 0.5f)
    }

    private suspend fun analyzeSaturation(): Float {
        val previewBitmap = _previewBitmap.value ?: return 0f
        val histogram = ScopeAnalyzer.computeHistogram(previewBitmap)
        val r = histogram.r
        val g = histogram.g
        val b = histogram.b
        var total = 0f
        var weighted = 0f
        for (i in r.indices) {
            val maxC = maxOf(r[i], g[i], b[i])
            val minC = minOf(r[i], g[i], b[i])
            val count = (r[i] + g[i] + b[i]) / 3f
            val sat = if (maxC > 0f) (maxC - minC) / maxC else 0f
            total += count
            weighted += sat * count
        }
        val avgSat = if (total > 0f) weighted / total else 0.4f
        // 目标饱和度: 0.4
        return ((0.4f - avgSat) * 1.5f).coerceIn(-0.5f, 0.5f)
    }

    // ================================================================
    // History / Undo / Redo
    // ================================================================

    private fun recordTransaction(operatorType: OperatorType, key: String, value: Float) {
        val paramsBefore = JsonObject(mapOf(key to JsonPrimitive(_params.value.run {
            when (key) {
                "exposure" -> exposure
                "contrast" -> contrast
                "highlights" -> highlights
                "shadows" -> shadows
                "midtones" -> midtones
                "saturation" -> saturation
                "vibrance" -> vibrance
                "clarityAmount" -> clarityAmount
                "sharpenAmount" -> sharpenAmount
                "filmGrainIntensity" -> filmGrainIntensity
                "halationIntensity" -> halationIntensity
                "sigmoidContrast" -> sigmoidContrast
                else -> value
            }
        })))
        val paramsAfter = JsonObject(mapOf(key to JsonPrimitive(value)))
        val tx = EditTransaction(
            transactionId = System.currentTimeMillis().toUInt(),
            operatorType = operatorType,
            paramsBefore = paramsBefore,
            paramsAfter = paramsAfter
        )
        _workingVersion.value.appendTransaction(tx)
        updateUndoRedoState()
    }

    fun undo() {
        val didUndo = _workingVersion.value.undo()
        if (didUndo) {
            // Reconstruct params from the current transaction state
            reconstructParamsFromWorkingVersion()
        }
        updateUndoRedoState()
    }

    fun redo() {
        val didRedo = _workingVersion.value.redo()
        if (didRedo) {
            reconstructParamsFromWorkingVersion()
        }
        updateUndoRedoState()
    }

    private fun updateUndoRedoState() {
        _canUndo.value = _workingVersion.value.cursor > 0
        _canRedo.value = _workingVersion.value.cursor < _workingVersion.value.transactions.size
    }

    private fun reconstructParamsFromWorkingVersion() {
        val applied = _workingVersion.value.appliedTransactions()
        if (applied.isEmpty()) return

        // Start from default and replay all applied transactions
        var reconstructed = PipelineParams()
        for (tx in applied) {
            val after = tx.paramsAfter
            for ((key, value) in after) {
                val floatValue = value.jsonPrimitive.floatOrNull ?: continue
                reconstructed = when (key) {
                    "exposure" -> reconstructed.copy(exposure = floatValue)
                    "contrast" -> reconstructed.copy(contrast = floatValue)
                    "highlights" -> reconstructed.copy(highlights = floatValue)
                    "shadows" -> reconstructed.copy(shadows = floatValue)
                    "midtones" -> reconstructed.copy(midtones = floatValue)
                    "saturation" -> reconstructed.copy(saturation = floatValue)
                    "vibrance" -> reconstructed.copy(vibrance = floatValue)
                    "clarityAmount" -> reconstructed.copy(clarityAmount = floatValue)
                    "sharpenAmount" -> reconstructed.copy(sharpenAmount = floatValue)
                    "filmGrainIntensity" -> reconstructed.copy(filmGrainIntensity = floatValue)
                    "halationIntensity" -> reconstructed.copy(halationIntensity = floatValue)
                    "sigmoidContrast" -> reconstructed.copy(sigmoidContrast = floatValue)
                    "autoExposure" -> reconstructed.copy(exposure = floatValue)
                    else -> reconstructed
                }
            }
        }
        _params.value = reconstructed
        regeneratePreview()
    }

    // ================================================================
    // Version management
    // ================================================================

    fun switchVersion(versionId: String) {
        _history.value?.let { hist ->
            hist.setActiveVersion(versionId)
            _history.value = hist
            _workingVersion.value = WorkingVersion(
                boundImageId = hist.boundImageId,
                versionId = versionId
            )
            val version = hist.getVersion(versionId)
            version?.materializedParams?.let { jsonParams ->
                reconstructParamsFromJson(jsonParams)
            }
        }
    }

    fun createVersion(displayName: String) {
        _history.value?.let { hist ->
            val newVersionId = hist.createVersion(displayName)
            _history.value = hist
            viewModelScope.launch {
                try {
                    editHistoryRepository.saveHistory(hist)
                } catch (e: Throwable) {
                    android.util.Log.e("EditorVM", "Coroutine failed", e)
                }
            }
            // Switch to new version
            switchVersion(newVersionId)
        }
    }

    fun deleteVersion(versionId: String) {
        _history.value?.let { hist ->
            if (hist.versionStorage.size > 1) {
                hist.versionStorage.remove(versionId)
                hist.versionOrder.removeAll { it.versionId == versionId }
                if (hist.activeVersionId == versionId) {
                    hist.activeVersionId = hist.defaultVersionId
                }
                _history.value = hist
                viewModelScope.launch {
                    try {
                        editHistoryRepository.saveHistory(hist)
                    } catch (e: Throwable) {
                        android.util.Log.e("EditorVM", "Coroutine failed", e)
                    }
                }
            }
        }
    }

    fun renameVersion(versionId: String, newName: String) {
        _history.value?.let { hist ->
            hist.getVersion(versionId)?.let { version ->
                val updated = version.copy(displayName = newName)
                hist.versionStorage[versionId] = updated
                _history.value = hist
                viewModelScope.launch {
                    try {
                        editHistoryRepository.saveHistory(hist)
                    } catch (e: Throwable) {
                        android.util.Log.e("EditorVM", "Coroutine failed", e)
                    }
                }
            }
        }
    }

    fun cloneHistory() {
        _history.value?.let { hist ->
            val cloned = hist.cloneForFile(hist.boundImageId)
            _history.value = cloned
            viewModelScope.launch {
                try {
                    editHistoryRepository.saveHistory(cloned)
                } catch (e: Throwable) {
                    android.util.Log.e("EditorVM", "Coroutine failed", e)
                }
            }
        }
    }

    fun saveVersion() {
        _history.value?.let { hist ->
            val updated = hist.copy(lastModifiedTime = java.time.Instant.now())
            _history.value = updated
            viewModelScope.launch {
                try {
                    editHistoryRepository.saveHistory(updated)
                } catch (e: Throwable) {
                    android.util.Log.e("EditorVM", "Coroutine failed", e)
                }
            }
        }
    }

    // 如果有可撤销的操作说明有未保存的修改
    fun hasUnsavedChanges(): Boolean = _canUndo.value

    // 自动保存 — 每30秒检查一次
    private var autoSaveJob: Job? = null

    fun startAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            while (isActive) {
                delay(30_000) // 30秒
                if (_canUndo.value) {
                    saveVersion()
                }
            }
        }
    }

    fun stopAutoSave() {
        autoSaveJob?.cancel()
    }

    private fun reconstructParamsFromJson(json: JsonObject) {
        try {
            val exposure = json["exposure"]?.jsonPrimitive?.floatOrNull ?: 0f
            val contrast = json["contrast"]?.jsonPrimitive?.floatOrNull ?: 0f
            val highlights = json["highlights"]?.jsonPrimitive?.floatOrNull ?: 0f
            val shadows = json["shadows"]?.jsonPrimitive?.floatOrNull ?: 0f
            val saturation = json["saturation"]?.jsonPrimitive?.floatOrNull ?: 0f
            val vibrance = json["vibrance"]?.jsonPrimitive?.floatOrNull ?: 0f
            val whiteBalanceTemp = json["whiteBalanceTemp"]?.jsonPrimitive?.floatOrNull ?: 6500f
            val whiteBalanceTint = json["whiteBalanceTint"]?.jsonPrimitive?.floatOrNull ?: 0f
            val clarityAmount = json["clarityAmount"]?.jsonPrimitive?.floatOrNull ?: 0f
            val sharpenAmount = json["sharpenAmount"]?.jsonPrimitive?.floatOrNull ?: 0f
            val filmGrainIntensity = json["filmGrainIntensity"]?.jsonPrimitive?.floatOrNull ?: 0f
            val halationIntensity = json["halationIntensity"]?.jsonPrimitive?.floatOrNull ?: 0f
            val sigmoidContrast = json["sigmoidContrast"]?.jsonPrimitive?.floatOrNull ?: 0f

            _params.value = _params.value.copy(
                exposure = exposure,
                contrast = contrast,
                highlights = highlights,
                shadows = shadows,
                saturation = saturation,
                vibrance = vibrance,
                whiteBalanceTemp = whiteBalanceTemp,
                whiteBalanceTint = whiteBalanceTint,
                clarityAmount = clarityAmount,
                sharpenAmount = sharpenAmount,
                filmGrainIntensity = filmGrainIntensity,
                halationIntensity = halationIntensity,
                sigmoidContrast = sigmoidContrast
            )
            regeneratePreview()
        } catch (_: Exception) {
            // Fallback to default params
        }
    }

    // ================================================================
    // Presets
    // ================================================================

    private fun loadPresets() {
        _presets.value = builtInPresets
    }

    fun applyPreset(preset: PresetEntry) {
        _params.value = preset.params
        _toneCurveX.value = preset.params.toneCurveX
        _toneCurveY.value = preset.params.toneCurveY
        _colorWheelLift.value = floatArrayOf(
            preset.params.colorWheelLiftR,
            preset.params.colorWheelLiftG,
            preset.params.colorWheelLiftB
        )
        _colorWheelGamma.value = floatArrayOf(
            preset.params.colorWheelGammaR,
            preset.params.colorWheelGammaG,
            preset.params.colorWheelGammaB
        )
        _colorWheelGain.value = floatArrayOf(
            preset.params.colorWheelGainR,
            preset.params.colorWheelGainG,
            preset.params.colorWheelGainB
        )
        _hslHueShift.value = preset.params.hslHueShift
        _hslSaturationScale.value = preset.params.hslSaturationScale
        _hslLuminanceScale.value = preset.params.hslLuminanceScale
        regeneratePreview()
    }

    fun saveCurrentAsPreset(name: String) {
        val newPreset = PresetEntry(name = name, params = _params.value)
        _presets.value = _presets.value + newPreset
    }

    fun resetAllParams() {
        _params.value = PipelineParams()
        _toneCurveX.value = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        _toneCurveY.value = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        _colorWheelLift.value = floatArrayOf(0f, 0f, 0f)
        _colorWheelGamma.value = floatArrayOf(1f, 1f, 1f)
        _colorWheelGain.value = floatArrayOf(1f, 1f, 1f)
        _hslHueShift.value = FloatArray(8) { 0f }
        _hslSaturationScale.value = FloatArray(8) { 1f }
        _hslLuminanceScale.value = FloatArray(8) { 1f }
        regeneratePreview()
    }

    // ================================================================
    // Scope toggles
    // ================================================================

    fun toggleHistogram() {
        _showHistogram.value = !_showHistogram.value
    }

    fun toggleWaveform() {
        _showWaveform.value = !_showWaveform.value
    }

    fun toggleVectorscope() {
        _showVectorscope.value = !_showVectorscope.value
    }

    // ================================================================
    // Export
    // ================================================================

    fun export(settings: ExportSettings) {
        viewModelScope.launch {
            try {
                val img = _imageModel.value ?: return@launch
                val finalBitmap = _previewBitmap.value ?: return@launch
                val settingsWithExif = settings.copy(sourceExifPath = img.imagePath)
                val result = exportService.exportImage(img.imagePath, settingsWithExif, finalBitmap)
                _lastExportResult.value = result
            } catch (e: Throwable) {
                android.util.Log.e("EditorVM", "Coroutine failed", e)
            }
        }
    }

    fun exportBatch(items: List<ExportService.ExportBatchItem>, settings: ExportSettings) {
        viewModelScope.launch {
            try {
                exportService.exportBatch(items, settings)
            } catch (e: Throwable) {
                android.util.Log.e("EditorVM", "Coroutine failed", e)
            }
        }
    }

    fun cancelExport() {
        exportService.cancelExport()
    }

    // ================================================================
    // Pipeline Snapshot
    // ================================================================

    fun createPipelineSnapshot() {
        viewModelScope.launch {
            try {
                val bitmap = _originalBitmap.value ?: return@launch
                val width = bitmap.width
                val height = bitmap.height
                val pixelCount = width * height

                val pixels = IntArray(pixelCount)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                val floatPixels = FloatArray(pixelCount * 4)
                for (i in 0 until pixelCount) {
                    val pixel = pixels[i]
                    floatPixels[i * 4]     = ((pixel shr 16) and 0xFF) / 255.0f
                    floatPixels[i * 4 + 1] = ((pixel shr 8) and 0xFF) / 255.0f
                    floatPixels[i * 4 + 2] = (pixel and 0xFF) / 255.0f
                    floatPixels[i * 4 + 3] = ((pixel shr 24) and 0xFF) / 255.0f
                }

                if (snapshotHandle != 0L) {
                    pipelineService.releaseSnapshot(snapshotHandle)
                }

                snapshotHandle = pipelineService.createSnapshot(
                    floatPixels, width, height, 4, _params.value)
            } catch (e: Throwable) {
                android.util.Log.e("EditorVM", "Coroutine failed", e)
            }
        }
    }

    fun releasePipelineSnapshot() {
        if (snapshotHandle != 0L) {
            pipelineService.releaseSnapshot(snapshotHandle)
            snapshotHandle = 0
        }
    }

    fun clearBitmaps() {
        _originalBitmap.value?.recycle()
        _previewBitmap.value?.recycle()
        _originalBitmap.value = null
        _previewBitmap.value = null
    }

    override fun onCleared() {
        super.onCleared()
        releasePipelineSnapshot()
        _originalBitmap.value?.recycle()
        _previewBitmap.value?.recycle()
        _originalBitmap.value = null
        _previewBitmap.value = null
    }
}

// ================================================================
// Preset model
// ================================================================

data class PresetEntry(
    val name: String,
    val params: PipelineParams
)

private val builtInPresets = listOf(
    PresetEntry("Default", PipelineParams()),
    PresetEntry("High Contrast", PipelineParams(contrast = 0.5f, sigmoidContrast = 0.3f)),
    PresetEntry("Soft Light", PipelineParams(contrast = -0.2f, saturation = -0.1f, halationIntensity = 0.15f)),
    PresetEntry("Film Look", PipelineParams(
        contrast = 0.15f, saturation = -0.15f, filmGrainIntensity = 0.12f,
        halationIntensity = 0.2f, halationThreshold = 0.85f, halationRedBias = 0.6f
    )),
    PresetEntry("Warm Sunset", PipelineParams(
        whiteBalanceTemp = 7200f, whiteBalanceTint = 10f,
        saturation = 0.2f, vibrance = 0.15f
    )),
    PresetEntry("Cool Tone", PipelineParams(
        whiteBalanceTemp = 5500f, whiteBalanceTint = -8f,
        saturation = -0.1f, contrast = 0.1f
    )),
    PresetEntry("B&W Film", PipelineParams(
        saturation = -1f, contrast = 0.3f, filmGrainIntensity = 0.15f,
        channelMixerMonochrome = true,
        channelMixerMatrix = floatArrayOf(0.3f, 0.59f, 0.11f, 0.3f, 0.59f, 0.11f, 0.3f, 0.59f, 0.11f)
    )),
    PresetEntry("Faded Film", PipelineParams(
        contrast = -0.1f, saturation = -0.25f, shadows = 0.15f,
        filmGrainIntensity = 0.08f, halationIntensity = 0.1f
    )),
    PresetEntry("HDR Look", PipelineParams(
        contrast = 0.4f, shadows = 0.5f, highlights = -0.3f,
        clarityAmount = 0.4f, saturation = 0.2f
    )),
    PresetEntry("Portrait Soft", PipelineParams(
        contrast = -0.1f, clarityAmount = -0.15f, saturation = 0.05f,
        sharpenAmount = 0.1f, halationIntensity = 0.08f
    ))
)

// ================================================================
// Editor panel / scope type enums (moved here so ViewModel can hold
// them as persisted state; UI imports from this package).
// ================================================================

enum class EditorPanel(val labelKey: StringResources.() -> String) {
    BASIC({ editorPanelBasic }),
    TONE_CURVE({ editorPanelCurve }),
    COLOR({ editorPanelColor }),
    HSL({ editorPanelHsl }),
    GEOMETRY({ editorPanelGeometry }),
    EFFECTS({ editorPanelEffects }),
    RAW({ editorPanelRaw }),
    HISTORY({ editorPanelHistory })
}

enum class ScopeType(val labelKey: StringResources.() -> String) {
    HISTOGRAM({ editorHistogram }),
    WAVEFORM({ editorWaveform }),
    VECTORSCOPE({ editorVectorscope }),
    CHROMATICITY({ editorChromaticity })
}
