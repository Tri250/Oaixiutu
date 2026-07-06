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
import com.alcedo.studio.ui.editor.GamutOverlay
import com.alcedo.studio.ui.editor.HistogramChannel
import com.alcedo.studio.ui.editor.HistogramScale
import com.alcedo.studio.ui.editor.ScopeAnalyzer
import com.alcedo.studio.ui.editor.WaveformMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive

class EditorViewModel(private val imageId: String) : ViewModel() {

    companion object {
        private const val MAX_BITMAP_PIXELS = 4096 * 4096  // 16M pixels max
        // C8 修复: 预览处理使用降采样位图,避免 4096x4096 时 256MB float 数组 OOM
        // 2048x2048 = 4M 像素,float 数组 64MB,足够预览质量且不爆内存
        private const val PREVIEW_MAX_PIXELS = 2048 * 2048
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

    // C8 修复: 预览专用降采样位图,用于 PipelineService 实时处理
    // 避免对 4096x4096 原图每次调整都分配 256MB float 数组导致 OOM
    private var previewSourceBitmap: Bitmap? = null

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

    // C9 修复: 单独跟踪"已保存"状态,避免 hasUnsavedChanges 永远返回 true
    // _savedCursor 记录上次保存时的工作版本游标位置
    private var savedCursor: Int = 0

    // C4 修复: 保存版本时,把当前 PipelineParams 物化为 JSON 写入历史版本,
    // 这样切换版本时能完整恢复所有参数
    private var lastSavedParamsJson: JsonObject? = null

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

    /**
     * C8 修复: 将原图降采样到 PREVIEW_MAX_PIXELS 以内,供实时预览 Pipeline 使用。
     * 2048x2048 足够预览质量,float 数组约 64MB,显著降低 OOM 风险。
     * 原图保留在 [_originalBitmap] 供导出使用。
     */
    private fun downscaleForPreview(source: Bitmap): Bitmap {
        val pixelCount = source.width.toLong() * source.height.toLong()
        if (pixelCount <= PREVIEW_MAX_PIXELS) return source
        val scale = kotlin.math.sqrt(PREVIEW_MAX_PIXELS.toFloat() / pixelCount.toFloat())
        val newW = (source.width * scale).toInt().coerceAtLeast(1)
        val newH = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, newW, newH, true)
    }

    private fun loadImage() {
        viewModelScope.launch {
            val id = imageId.toLongOrNull() ?: return@launch
            val img = imageRepository.getImage(id)
            _imageModel.value = img
            img?.let {
                try {
                    // 在 IO 线程解码，避免主线程 ANR；采样避免大图 OOM
                    val bitmap = withContext(Dispatchers.IO) {
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
                        BitmapFactory.decodeFile(it.imagePath, decodeOptions)
                    }
                    _originalBitmap.value = bitmap
                    // C8 修复: 为预览生成降采样位图,避免实时处理大图 OOM
                    previewSourceBitmap = downscaleForPreview(bitmap)
                    _previewBitmap.value = previewSourceBitmap
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
        // C3 修复: 复合更新需记录全部变更的子参数,否则 tint 在撤销时丢失
        recordTransaction(OperatorType.WHITE_BALANCE, "whiteBalanceTemp", temperature)
        recordTransaction(OperatorType.WHITE_BALANCE, "whiteBalanceTint", tint)
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
        // C3 修复: 同时记录 radius
        recordTransaction(OperatorType.CLARITY, "clarityAmount", amount)
        recordTransaction(OperatorType.CLARITY, "clarityRadius", radius)
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
        // C3 修复: 记录全部 halation 子参数,避免撤销时丢失 threshold/spread/redBias
        recordTransaction(OperatorType.HALATION, "halationIntensity", intensity)
        recordTransaction(OperatorType.HALATION, "halationThreshold", threshold)
        recordTransaction(OperatorType.HALATION, "halationSpread", spread)
        recordTransaction(OperatorType.HALATION, "halationRedBias", redBias)
        regeneratePreview()
    }

    fun updateSigmoidContrast(value: Float) {
        _params.value = _params.value.copy(sigmoidContrast = value)
        recordTransaction(OperatorType.CONTRAST, "sigmoidContrast", value)
        regeneratePreview()
    }

    fun updateSigmoidShoulder(value: Float) {
        _params.value = _params.value.copy(sigmoidShoulder = value)
        recordTransaction(OperatorType.CONTRAST, "sigmoidShoulder", value)
        regeneratePreview()
    }

    fun updateShadowBoundary(value: Float) {
        _params.value = _params.value.copy(shadowBoundary = value)
        recordTransaction(OperatorType.TONE_REGION, "shadowBoundary", value)
        regeneratePreview()
    }

    fun updateLensCorrection(k1: Float, k2: Float, k3: Float, p1: Float, p2: Float) {
        _params.value = _params.value.copy(lensK1 = k1, lensK2 = k2, lensK3 = k3, lensP1 = p1, lensP2 = p2)
        // C3 修复: 记录全部镜头校正参数,避免撤销只恢复 k1
        recordTransaction(OperatorType.GEOMETRY, "lensK1", k1)
        recordTransaction(OperatorType.GEOMETRY, "lensK2", k2)
        recordTransaction(OperatorType.GEOMETRY, "lensK3", k3)
        recordTransaction(OperatorType.GEOMETRY, "lensP1", p1)
        recordTransaction(OperatorType.GEOMETRY, "lensP2", p2)
        regeneratePreview()
    }

    fun updateLut(enabled: Boolean, path: String) {
        _params.value = _params.value.copy(lutEnabled = enabled, lutPath = path)
        // C3 修复: 单独记录 enabled 状态,path 字符串暂不入历史 (float 数组不支持 String)
        recordTransaction(OperatorType.LUT, "lutEnabled", if (enabled) 1f else 0f)
        regeneratePreview()
    }

    fun updateGeometryRotate(value: Float) {
        _params.value = _params.value.copy(geometryRotate = value)
        recordTransaction(OperatorType.GEOMETRY, "geometryRotate", value)
        regeneratePreview()
    }

    fun updateGeometryFlipH(flip: Boolean) {
        _params.value = _params.value.copy(geometryFlipH = flip)
        recordTransaction(OperatorType.GEOMETRY, "geometryFlipH", if (flip) 1f else 0f)
        regeneratePreview()
    }

    fun updateGeometryFlipV(flip: Boolean) {
        _params.value = _params.value.copy(geometryFlipV = flip)
        recordTransaction(OperatorType.GEOMETRY, "geometryFlipV", if (flip) 1f else 0f)
        regeneratePreview()
    }

    fun updateCrop(left: Float, top: Float, right: Float, bottom: Float) {
        _params.value = _params.value.copy(cropLeft = left, cropTop = top, cropRight = right, cropBottom = bottom)
        // C3 修复: 记录全部裁剪参数
        recordTransaction(OperatorType.GEOMETRY, "cropLeft", left)
        recordTransaction(OperatorType.GEOMETRY, "cropTop", top)
        recordTransaction(OperatorType.GEOMETRY, "cropRight", right)
        recordTransaction(OperatorType.GEOMETRY, "cropBottom", bottom)
        regeneratePreview()
    }

    fun updatePerspective(horizontal: Float, vertical: Float) {
        _params.value = _params.value.copy(perspectiveH = horizontal, perspectiveV = vertical)
        // C3 修复: 记录水平+垂直透视
        recordTransaction(OperatorType.GEOMETRY, "perspectiveH", horizontal)
        recordTransaction(OperatorType.GEOMETRY, "perspectiveV", vertical)
        regeneratePreview()
    }

    fun updateVignette(strength: Float) {
        _params.value = _params.value.copy(lensVignetteStrength = strength)
        recordTransaction(OperatorType.GEOMETRY, "lensVignetteStrength", strength)
        regeneratePreview()
    }

    fun updateLutIntensity(intensity: Float) {
        _params.value = _params.value.copy(lutIntensity = intensity)
        recordTransaction(OperatorType.LUT, "lutIntensity", intensity)
        regeneratePreview()
    }

    fun updateDisplayTransform(displayTransform: DisplayTransform) {
        _params.value = _params.value.copy(displayTransform = displayTransform)
        // C3 修复: 记录全部 DisplayTransform 子字段
        recordTransaction(OperatorType.DISPLAY_TRANSFORM, "displayTransform_colorScience", displayTransform.colorScience.ordinal.toFloat())
        recordTransaction(OperatorType.DISPLAY_TRANSFORM, "displayTransform_eotf", displayTransform.eotf.ordinal.toFloat())
        recordTransaction(OperatorType.DISPLAY_TRANSFORM, "displayTransform_peakLuminance", displayTransform.peakLuminance)
        recordTransaction(OperatorType.DISPLAY_TRANSFORM, "displayTransform_displayColorSpace", displayTransform.displayColorSpace.ordinal.toFloat())
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
        // C3 修复: 记录全部曲线点,否则撤销只恢复点数,曲线形状丢失
        recordTransaction(OperatorType.TONE_CURVE, "toneCurvePoints", x.size.toFloat())
        for (i in x.indices) {
            recordTransaction(OperatorType.TONE_CURVE, "toneCurveX[$i]", x[i])
            recordTransaction(OperatorType.TONE_CURVE, "toneCurveY[$i]", y[i])
        }
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
        // C3 修复: 记录全部 RGB 分量
        recordTransaction(OperatorType.COLOR_WHEEL, "colorWheelLiftR", lift[0])
        recordTransaction(OperatorType.COLOR_WHEEL, "colorWheelLiftG", lift[1])
        recordTransaction(OperatorType.COLOR_WHEEL, "colorWheelLiftB", lift[2])
        regeneratePreview()
    }

    fun updateColorWheelGamma(gamma: FloatArray) {
        _colorWheelGamma.value = gamma
        _params.value = _params.value.copy(
            colorWheelGammaR = gamma[0],
            colorWheelGammaG = gamma[1],
            colorWheelGammaB = gamma[2]
        )
        // C3 修复: 记录全部 RGB 分量
        recordTransaction(OperatorType.COLOR_WHEEL, "colorWheelGammaR", gamma[0])
        recordTransaction(OperatorType.COLOR_WHEEL, "colorWheelGammaG", gamma[1])
        recordTransaction(OperatorType.COLOR_WHEEL, "colorWheelGammaB", gamma[2])
        regeneratePreview()
    }

    fun updateColorWheelGain(gain: FloatArray) {
        _colorWheelGain.value = gain
        _params.value = _params.value.copy(
            colorWheelGainR = gain[0],
            colorWheelGainG = gain[1],
            colorWheelGainB = gain[2]
        )
        // C3 修复: 记录全部 RGB 分量
        recordTransaction(OperatorType.COLOR_WHEEL, "colorWheelGainR", gain[0])
        recordTransaction(OperatorType.COLOR_WHEEL, "colorWheelGainG", gain[1])
        recordTransaction(OperatorType.COLOR_WHEEL, "colorWheelGainB", gain[2])
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
        // C3 修复: 记录全部 tint 子参数,避免撤销只恢复 balance
        recordTransaction(OperatorType.TINT, "tintHighlightHue", highlightHue)
        recordTransaction(OperatorType.TINT, "tintHighlightStrength", highlightStrength)
        recordTransaction(OperatorType.TINT, "tintShadowHue", shadowHue)
        recordTransaction(OperatorType.TINT, "tintShadowStrength", shadowStrength)
        recordTransaction(OperatorType.TINT, "tintBalance", balance)
        regeneratePreview()
    }

    // ================================================================
    // Channel mixer
    // ================================================================

    fun updateChannelMixer(matrix: FloatArray, monochrome: Boolean) {
        _params.value = _params.value.copy(channelMixerMatrix = matrix, channelMixerMonochrome = monochrome)
        // C3 修复: 记录矩阵元素 + monochrome 标志,避免撤销丢失矩阵
        for (i in matrix.indices) {
            recordTransaction(OperatorType.COLOR_WHEEL, "channelMixerMatrix[$i]", matrix[i])
        }
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

    private var previewJob: Job? = null

    fun regeneratePreview() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(50) // 50ms debounce
            _isProcessing.value = true
            try {
                // C8 修复: 使用降采样位图进行预览,避免大图 OOM
                val source = previewSourceBitmap ?: _originalBitmap.value
                if (source != null) {
                    _previewBitmap.value = pipelineService.applyPipeline(source, _params.value)
                }
            } catch (e: Throwable) {
                Log.e("EditorVM", "regeneratePreview failed", e)
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
                recordTransaction(OperatorType.EXPOSURE, "autoExposureValue", ev)
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
        // C3 修复: 扩展 when 块以覆盖所有可调标量参数,
        // 否则未列出的 key 会落入 else 分支,paramsBefore == paramsAfter,撤销无效
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
                "clarityRadius" -> clarityRadius
                "sharpenAmount" -> sharpenAmount
                "filmGrainIntensity" -> filmGrainIntensity
                "halationIntensity" -> halationIntensity
                "halationThreshold" -> halationThreshold
                "halationSpread" -> halationSpread
                "halationRedBias" -> halationRedBias
                "sigmoidContrast" -> sigmoidContrast
                "sigmoidPivot" -> sigmoidPivot
                "sigmoidShoulder" -> sigmoidShoulder
                "shadowBoundary" -> shadowBoundary
                "highlightBoundary" -> highlightBoundary
                "whiteBalanceTemp" -> whiteBalanceTemp
                "whiteBalanceTint" -> whiteBalanceTint
                "autoExposureValue" -> autoExposureValue
                "geometryRotate" -> geometryRotate
                "geometryScale" -> geometryScale
                "geometryFlipH" -> if (geometryFlipH) 1f else 0f
                "geometryFlipV" -> if (geometryFlipV) 1f else 0f
                "cropLeft" -> cropLeft
                "cropTop" -> cropTop
                "cropRight" -> cropRight
                "cropBottom" -> cropBottom
                "perspectiveH" -> perspectiveH
                "perspectiveV" -> perspectiveV
                "lensK1" -> lensK1
                "lensK2" -> lensK2
                "lensK3" -> lensK3
                "lensP1" -> lensP1
                "lensP2" -> lensP2
                "lensVignetteStrength" -> lensVignetteStrength
                "lutIntensity" -> lutIntensity
                "lutEnabled" -> if (lutEnabled) 1f else 0f
                "tintHighlightHue" -> tintHighlightHue
                "tintHighlightStrength" -> tintHighlightStrength
                "tintShadowHue" -> tintShadowHue
                "tintShadowStrength" -> tintShadowStrength
                "tintBalance" -> tintBalance
                "colorWheelLiftR" -> colorWheelLiftR
                "colorWheelLiftG" -> colorWheelLiftG
                "colorWheelLiftB" -> colorWheelLiftB
                "colorWheelGammaR" -> colorWheelGammaR
                "colorWheelGammaG" -> colorWheelGammaG
                "colorWheelGammaB" -> colorWheelGammaB
                "colorWheelGainR" -> colorWheelGainR
                "colorWheelGainG" -> colorWheelGainG
                "colorWheelGainB" -> colorWheelGainB
                "channelMixerMonochrome" -> if (channelMixerMonochrome) 1f else 0f
                "toneCurvePoints" -> toneCurvePoints.toFloat()
                "displayTransform_colorScience" -> displayTransform.colorScience.ordinal.toFloat()
                "displayTransform_eotf" -> displayTransform.eotf.ordinal.toFloat()
                "displayTransform_peakLuminance" -> displayTransform.peakLuminance
                "displayTransform_displayColorSpace" -> displayTransform.displayColorSpace.ordinal.toFloat()
                // HSL / 数组元素 — 通过索引键查询
                else -> {
                    val m = Regex("""hslHueShift\[(\d+)]""").matchEntire(key)
                    if (m != null) return@run hslHueShift[m.groupValues[1].toInt()]
                    val m2 = Regex("""hslSaturationScale\[(\d+)]""").matchEntire(key)
                    if (m2 != null) return@run hslSaturationScale[m2.groupValues[1].toInt()]
                    val m3 = Regex("""hslLuminanceScale\[(\d+)]""").matchEntire(key)
                    if (m3 != null) return@run hslLuminanceScale[m3.groupValues[1].toInt()]
                    val m4 = Regex("""channelMixerMatrix\[(\d+)]""").matchEntire(key)
                    if (m4 != null) return@run channelMixerMatrix[m4.groupValues[1].toInt()]
                    val m5 = Regex("""toneCurveX\[(\d+)]""").matchEntire(key)
                    if (m5 != null) return@run toneCurveX[m5.groupValues[1].toInt()]
                    val m6 = Regex("""toneCurveY\[(\d+)]""").matchEntire(key)
                    if (m6 != null) return@run toneCurveY[m6.groupValues[1].toInt()]
                    value
                }
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

    /**
     * C9 修复: 真正判断是否有未保存修改 — 比较当前游标与上次保存时的游标。
     * 旧实现 [_canUndo.value] 在任何操作后都为 true,导致永远显示"未保存"。
     */
    fun hasUnsavedChanges(): Boolean = _workingVersion.value.cursor != savedCursor

    private fun reconstructParamsFromWorkingVersion() {
        val applied = _workingVersion.value.appliedTransactions()
        if (applied.isEmpty()) return

        // Start from default and replay all applied transactions
        var reconstructed = PipelineParams()
        // 数组类型需可变累积,避免每次事务覆盖前面的索引
        val hslHue = FloatArray(8) { 0f }
        val hslSat = FloatArray(8) { 1f }
        val hslLum = FloatArray(8) { 1f }
        val mixer = FloatArray(9) { if (it % 4 == 0) 1f else 0f }
        val tcX = FloatArray(5) { it / 4f }
        val tcY = FloatArray(5) { it / 4f }
        var toneCurvePoints = 5
        var colorScience = ColorScience.ACES20
        var eotf = EOTF.SRGB
        var peakLuminance = 100f
        var displayColorSpace = ColorSpace.SRGB

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
                    "clarityRadius" -> reconstructed.copy(clarityRadius = floatValue)
                    "sharpenAmount" -> reconstructed.copy(sharpenAmount = floatValue)
                    "filmGrainIntensity" -> reconstructed.copy(filmGrainIntensity = floatValue)
                    "halationIntensity" -> reconstructed.copy(halationIntensity = floatValue)
                    "halationThreshold" -> reconstructed.copy(halationThreshold = floatValue)
                    "halationSpread" -> reconstructed.copy(halationSpread = floatValue)
                    "halationRedBias" -> reconstructed.copy(halationRedBias = floatValue)
                    "sigmoidContrast" -> reconstructed.copy(sigmoidContrast = floatValue)
                    "sigmoidPivot" -> reconstructed.copy(sigmoidPivot = floatValue)
                    "sigmoidShoulder" -> reconstructed.copy(sigmoidShoulder = floatValue)
                    "shadowBoundary" -> reconstructed.copy(shadowBoundary = floatValue)
                    "highlightBoundary" -> reconstructed.copy(highlightBoundary = floatValue)
                    "whiteBalanceTemp" -> reconstructed.copy(whiteBalanceTemp = floatValue)
                    "whiteBalanceTint" -> reconstructed.copy(whiteBalanceTint = floatValue)
                    "autoExposureValue" -> reconstructed.copy(
                        autoExposureEnabled = true, autoExposureValue = floatValue, exposure = floatValue)
                    "geometryRotate" -> reconstructed.copy(geometryRotate = floatValue)
                    "geometryScale" -> reconstructed.copy(geometryScale = floatValue)
                    "geometryFlipH" -> reconstructed.copy(geometryFlipH = floatValue != 0f)
                    "geometryFlipV" -> reconstructed.copy(geometryFlipV = floatValue != 0f)
                    "cropLeft" -> reconstructed.copy(cropLeft = floatValue)
                    "cropTop" -> reconstructed.copy(cropTop = floatValue)
                    "cropRight" -> reconstructed.copy(cropRight = floatValue)
                    "cropBottom" -> reconstructed.copy(cropBottom = floatValue)
                    "perspectiveH" -> reconstructed.copy(perspectiveH = floatValue)
                    "perspectiveV" -> reconstructed.copy(perspectiveV = floatValue)
                    "lensK1" -> reconstructed.copy(lensK1 = floatValue)
                    "lensK2" -> reconstructed.copy(lensK2 = floatValue)
                    "lensK3" -> reconstructed.copy(lensK3 = floatValue)
                    "lensP1" -> reconstructed.copy(lensP1 = floatValue)
                    "lensP2" -> reconstructed.copy(lensP2 = floatValue)
                    "lensVignetteStrength" -> reconstructed.copy(lensVignetteStrength = floatValue)
                    "lutIntensity" -> reconstructed.copy(lutIntensity = floatValue)
                    "lutEnabled" -> reconstructed.copy(lutEnabled = floatValue != 0f)
                    "tintHighlightHue" -> reconstructed.copy(tintHighlightHue = floatValue)
                    "tintHighlightStrength" -> reconstructed.copy(tintHighlightStrength = floatValue)
                    "tintShadowHue" -> reconstructed.copy(tintShadowHue = floatValue)
                    "tintShadowStrength" -> reconstructed.copy(tintShadowStrength = floatValue)
                    "tintBalance" -> reconstructed.copy(tintBalance = floatValue)
                    "colorWheelLiftR" -> reconstructed.copy(colorWheelLiftR = floatValue)
                    "colorWheelLiftG" -> reconstructed.copy(colorWheelLiftG = floatValue)
                    "colorWheelLiftB" -> reconstructed.copy(colorWheelLiftB = floatValue)
                    "colorWheelGammaR" -> reconstructed.copy(colorWheelGammaR = floatValue)
                    "colorWheelGammaG" -> reconstructed.copy(colorWheelGammaG = floatValue)
                    "colorWheelGammaB" -> reconstructed.copy(colorWheelGammaB = floatValue)
                    "colorWheelGainR" -> reconstructed.copy(colorWheelGainR = floatValue)
                    "colorWheelGainG" -> reconstructed.copy(colorWheelGainG = floatValue)
                    "colorWheelGainB" -> reconstructed.copy(colorWheelGainB = floatValue)
                    "channelMixerMonochrome" -> reconstructed.copy(channelMixerMonochrome = floatValue != 0f)
                    "toneCurvePoints" -> { toneCurvePoints = floatValue.toInt(); reconstructed }
                    "displayTransform_colorScience" -> { colorScience = ColorScience.entries.getOrNull(floatValue.toInt()) ?: colorScience; reconstructed }
                    "displayTransform_eotf" -> { eotf = EOTF.entries.getOrNull(floatValue.toInt()) ?: eotf; reconstructed }
                    "displayTransform_peakLuminance" -> { peakLuminance = floatValue; reconstructed }
                    "displayTransform_displayColorSpace" -> { displayColorSpace = ColorSpace.entries.getOrNull(floatValue.toInt()) ?: displayColorSpace; reconstructed }
                    else -> {
                        // HSL / 数组元素 — 累积写入对应索引
                        val m = Regex("""hslHueShift\[(\d+)]""").matchEntire(key)
                        if (m != null) { hslHue[m.groupValues[1].toInt()] = floatValue; reconstructed }
                        else {
                            val m2 = Regex("""hslSaturationScale\[(\d+)]""").matchEntire(key)
                            if (m2 != null) { hslSat[m2.groupValues[1].toInt()] = floatValue; reconstructed }
                            else {
                                val m3 = Regex("""hslLuminanceScale\[(\d+)]""").matchEntire(key)
                                if (m3 != null) { hslLum[m3.groupValues[1].toInt()] = floatValue; reconstructed }
                                else {
                                    val m4 = Regex("""channelMixerMatrix\[(\d+)]""").matchEntire(key)
                                    if (m4 != null) { mixer[m4.groupValues[1].toInt()] = floatValue; reconstructed }
                                    else {
                                        val m5 = Regex("""toneCurveX\[(\d+)]""").matchEntire(key)
                                        if (m5 != null) { tcX[m5.groupValues[1].toInt()] = floatValue; reconstructed }
                                        else {
                                            val m6 = Regex("""toneCurveY\[(\d+)]""").matchEntire(key)
                                            if (m6 != null) { tcY[m6.groupValues[1].toInt()] = floatValue; reconstructed }
                                            else reconstructed
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        _params.value = reconstructed.copy(
            hslHueShift = hslHue,
            hslSaturationScale = hslSat,
            hslLuminanceScale = hslLum,
            channelMixerMatrix = mixer,
            toneCurveX = tcX,
            toneCurveY = tcY,
            toneCurvePoints = toneCurvePoints,
            displayTransform = DisplayTransform(
                colorScience = colorScience,
                eotf = eotf,
                peakLuminance = peakLuminance,
                displayColorSpace = displayColorSpace
            )
        )
        // 同步 UI 状态
        _toneCurveX.value = tcX
        _toneCurveY.value = tcY
        _colorWheelLift.value = floatArrayOf(
            reconstructed.colorWheelLiftR, reconstructed.colorWheelLiftG, reconstructed.colorWheelLiftB)
        _colorWheelGamma.value = floatArrayOf(
            reconstructed.colorWheelGammaR, reconstructed.colorWheelGammaG, reconstructed.colorWheelGammaB)
        _colorWheelGain.value = floatArrayOf(
            reconstructed.colorWheelGainR, reconstructed.colorWheelGainG, reconstructed.colorWheelGainB)
        _hslHueShift.value = hslHue
        _hslSaturationScale.value = hslSat
        _hslLuminanceScale.value = hslLum
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
            // C4 修复: 把当前 PipelineParams 物化为 JSON 写入活动版本,
            // 这样切换版本时能完整恢复所有参数(而不仅是被记录的少量字段)
            val paramsJson = materializeParamsToJson(_params.value)
            lastSavedParamsJson = paramsJson
            hist.getVersion(hist.activeVersionId)?.let { version ->
                version.materializedParams = paramsJson
            }
            val updated = hist.copy(lastModifiedTime = java.time.Instant.now())
            _history.value = updated
            // C9 修复: 记录已保存的游标位置,使 hasUnsavedChanges 返回 false
            savedCursor = _workingVersion.value.cursor
            viewModelScope.launch {
                try {
                    editHistoryRepository.saveHistory(updated)
                } catch (e: Throwable) {
                    android.util.Log.e("EditorVM", "Coroutine failed", e)
                }
            }
        }
    }

    /**
     * C4 修复: 把 PipelineParams 序列化为 JSON,作为版本的 materializedParams 持久化。
     * 覆盖所有可调参数,避免旧实现只保存少量字段导致版本切换丢失数据。
     */
    private fun materializeParamsToJson(p: PipelineParams): JsonObject {
        val map = mutableMapOf<String, JsonPrimitive>()
        map["exposure"] = JsonPrimitive(p.exposure)
        map["contrast"] = JsonPrimitive(p.contrast)
        map["saturation"] = JsonPrimitive(p.saturation)
        map["vibrance"] = JsonPrimitive(p.vibrance)
        map["highlights"] = JsonPrimitive(p.highlights)
        map["shadows"] = JsonPrimitive(p.shadows)
        map["midtones"] = JsonPrimitive(p.midtones)
        map["shadowBoundary"] = JsonPrimitive(p.shadowBoundary)
        map["highlightBoundary"] = JsonPrimitive(p.highlightBoundary)
        map["whiteBalanceTemp"] = JsonPrimitive(p.whiteBalanceTemp)
        map["whiteBalanceTint"] = JsonPrimitive(p.whiteBalanceTint)
        map["autoExposureEnabled"] = JsonPrimitive(p.autoExposureEnabled)
        map["autoExposureValue"] = JsonPrimitive(p.autoExposureValue)
        map["sigmoidContrast"] = JsonPrimitive(p.sigmoidContrast)
        map["sigmoidPivot"] = JsonPrimitive(p.sigmoidPivot)
        map["sigmoidShoulder"] = JsonPrimitive(p.sigmoidShoulder)
        map["tintHighlightHue"] = JsonPrimitive(p.tintHighlightHue)
        map["tintHighlightStrength"] = JsonPrimitive(p.tintHighlightStrength)
        map["tintShadowHue"] = JsonPrimitive(p.tintShadowHue)
        map["tintShadowStrength"] = JsonPrimitive(p.tintShadowStrength)
        map["tintBalance"] = JsonPrimitive(p.tintBalance)
        map["colorWheelLiftR"] = JsonPrimitive(p.colorWheelLiftR)
        map["colorWheelLiftG"] = JsonPrimitive(p.colorWheelLiftG)
        map["colorWheelLiftB"] = JsonPrimitive(p.colorWheelLiftB)
        map["colorWheelGammaR"] = JsonPrimitive(p.colorWheelGammaR)
        map["colorWheelGammaG"] = JsonPrimitive(p.colorWheelGammaG)
        map["colorWheelGammaB"] = JsonPrimitive(p.colorWheelGammaB)
        map["colorWheelGainR"] = JsonPrimitive(p.colorWheelGainR)
        map["colorWheelGainG"] = JsonPrimitive(p.colorWheelGainG)
        map["colorWheelGainB"] = JsonPrimitive(p.colorWheelGainB)
        map["clarityAmount"] = JsonPrimitive(p.clarityAmount)
        map["clarityRadius"] = JsonPrimitive(p.clarityRadius)
        map["sharpenAmount"] = JsonPrimitive(p.sharpenAmount)
        map["filmGrainIntensity"] = JsonPrimitive(p.filmGrainIntensity)
        map["halationIntensity"] = JsonPrimitive(p.halationIntensity)
        map["halationThreshold"] = JsonPrimitive(p.halationThreshold)
        map["halationSpread"] = JsonPrimitive(p.halationSpread)
        map["halationRedBias"] = JsonPrimitive(p.halationRedBias)
        map["lutEnabled"] = JsonPrimitive(p.lutEnabled)
        map["lutIntensity"] = JsonPrimitive(p.lutIntensity)
        map["lutPath"] = JsonPrimitive(p.lutPath)
        map["geometryRotate"] = JsonPrimitive(p.geometryRotate)
        map["geometryScale"] = JsonPrimitive(p.geometryScale)
        map["geometryFlipH"] = JsonPrimitive(p.geometryFlipH)
        map["geometryFlipV"] = JsonPrimitive(p.geometryFlipV)
        map["cropLeft"] = JsonPrimitive(p.cropLeft)
        map["cropTop"] = JsonPrimitive(p.cropTop)
        map["cropRight"] = JsonPrimitive(p.cropRight)
        map["cropBottom"] = JsonPrimitive(p.cropBottom)
        map["perspectiveH"] = JsonPrimitive(p.perspectiveH)
        map["perspectiveV"] = JsonPrimitive(p.perspectiveV)
        map["lensK1"] = JsonPrimitive(p.lensK1)
        map["lensK2"] = JsonPrimitive(p.lensK2)
        map["lensK3"] = JsonPrimitive(p.lensK3)
        map["lensP1"] = JsonPrimitive(p.lensP1)
        map["lensP2"] = JsonPrimitive(p.lensP2)
        map["lensVignetteStrength"] = JsonPrimitive(p.lensVignetteStrength)
        map["channelMixerMonochrome"] = JsonPrimitive(p.channelMixerMonochrome)
        // HSL / channelMixerMatrix / toneCurve 数组通过索引展开
        p.hslHueShift.forEachIndexed { i, v -> map["hslHueShift[$i]"] = JsonPrimitive(v) }
        p.hslSaturationScale.forEachIndexed { i, v -> map["hslSaturationScale[$i]"] = JsonPrimitive(v) }
        p.hslLuminanceScale.forEachIndexed { i, v -> map["hslLuminanceScale[$i]"] = JsonPrimitive(v) }
        p.channelMixerMatrix.forEachIndexed { i, v -> map["channelMixerMatrix[$i]"] = JsonPrimitive(v) }
        p.toneCurveX.forEachIndexed { i, v -> map["toneCurveX[$i]"] = JsonPrimitive(v) }
        p.toneCurveY.forEachIndexed { i, v -> map["toneCurveY[$i]"] = JsonPrimitive(v) }
        map["toneCurvePoints"] = JsonPrimitive(p.toneCurvePoints)
        map["displayTransform_colorScience"] = JsonPrimitive(p.displayTransform.colorScience.ordinal)
        map["displayTransform_eotf"] = JsonPrimitive(p.displayTransform.eotf.ordinal)
        map["displayTransform_peakLuminance"] = JsonPrimitive(p.displayTransform.peakLuminance)
        map["displayTransform_displayColorSpace"] = JsonPrimitive(p.displayTransform.displayColorSpace.ordinal)
        map["displayTransform_openDrtLook"] = JsonPrimitive(p.displayTransform.openDrtLook.ordinal)
        map["displayTransform_openDrtTonescale"] = JsonPrimitive(p.displayTransform.openDrtTonescale.ordinal)
        return JsonObject(map)
    }

    // 自动保存 — 每30秒检查一次
    private var autoSaveJob: Job? = null

    fun startAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            while (isActive) {
                delay(30_000) // 30秒
                // C9 修复: 使用真实的未保存判断,而非 _canUndo (永远为 true)
                if (hasUnsavedChanges()) {
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
            // C4 修复: 完整恢复所有参数,避免版本切换丢失 HSL/色轮/几何/显示变换等
            fun f(key: String, default: Float = 0f): Float =
                json[key]?.jsonPrimitive?.floatOrNull ?: default

            val hslHueShift = FloatArray(8) { i -> f("hslHueShift[$i]") }
            val hslSatScale = FloatArray(8) { i -> f("hslSaturationScale[$i]", 1f) }
            val hslLumScale = FloatArray(8) { i -> f("hslLuminanceScale[$i]", 1f) }
            val channelMixer = FloatArray(9) { i ->
                f("channelMixerMatrix[$i]", if (i % 4 == 0) 1f else 0f)
            }
            val toneCurveX = (0 until 5).map { f("toneCurveX[$it]", it / 4f) }.toFloatArray()
            val toneCurveY = (0 until 5).map { f("toneCurveY[$it]", it / 4f) }.toFloatArray()

            val colorScience = ColorScience.entries.getOrNull(
                f("displayTransform_colorScience").toInt()) ?: ColorScience.ACES20
            val eotf = EOTF.entries.getOrNull(
                f("displayTransform_eotf").toInt()) ?: EOTF.SRGB
            val colorSpace = ColorSpace.entries.getOrNull(
                f("displayTransform_displayColorSpace").toInt()) ?: ColorSpace.SRGB
            val openDrtLook = OpenDrtLook.entries.getOrNull(
                f("displayTransform_openDrtLook").toInt()) ?: OpenDrtLook.STANDARD
            val openDrtTonescale = OpenDrtTonescale.entries.getOrNull(
                f("displayTransform_openDrtTonescale").toInt()) ?: OpenDrtTonescale.STANDARD

            _params.value = _params.value.copy(
                exposure = f("exposure"),
                contrast = f("contrast"),
                saturation = f("saturation"),
                vibrance = f("vibrance"),
                highlights = f("highlights"),
                shadows = f("shadows"),
                midtones = f("midtones"),
                shadowBoundary = f("shadowBoundary", 0.25f),
                highlightBoundary = f("highlightBoundary", 0.75f),
                whiteBalanceTemp = f("whiteBalanceTemp", 6500f),
                whiteBalanceTint = f("whiteBalanceTint"),
                autoExposureEnabled = json["autoExposureEnabled"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                autoExposureValue = f("autoExposureValue"),
                sigmoidContrast = f("sigmoidContrast"),
                sigmoidPivot = f("sigmoidPivot", 0.18f),
                sigmoidShoulder = f("sigmoidShoulder", 0.5f),
                tintHighlightHue = f("tintHighlightHue"),
                tintHighlightStrength = f("tintHighlightStrength"),
                tintShadowHue = f("tintShadowHue"),
                tintShadowStrength = f("tintShadowStrength"),
                tintBalance = f("tintBalance"),
                colorWheelLiftR = f("colorWheelLiftR"),
                colorWheelLiftG = f("colorWheelLiftG"),
                colorWheelLiftB = f("colorWheelLiftB"),
                colorWheelGammaR = f("colorWheelGammaR", 1f),
                colorWheelGammaG = f("colorWheelGammaG", 1f),
                colorWheelGammaB = f("colorWheelGammaB", 1f),
                colorWheelGainR = f("colorWheelGainR", 1f),
                colorWheelGainG = f("colorWheelGainG", 1f),
                colorWheelGainB = f("colorWheelGainB", 1f),
                clarityAmount = f("clarityAmount"),
                clarityRadius = f("clarityRadius", 15f),
                sharpenAmount = f("sharpenAmount"),
                filmGrainIntensity = f("filmGrainIntensity"),
                halationIntensity = f("halationIntensity"),
                halationThreshold = f("halationThreshold", 0.8f),
                halationSpread = f("halationSpread", 10f),
                halationRedBias = f("halationRedBias", 0.7f),
                lutEnabled = json["lutEnabled"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                lutIntensity = f("lutIntensity", 100f),
                lutPath = json["lutPath"]?.jsonPrimitive?.content ?: "",
                geometryRotate = f("geometryRotate"),
                geometryScale = f("geometryScale", 1f),
                geometryFlipH = json["geometryFlipH"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                geometryFlipV = json["geometryFlipV"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                cropLeft = f("cropLeft"),
                cropTop = f("cropTop"),
                cropRight = f("cropRight", 1f),
                cropBottom = f("cropBottom", 1f),
                perspectiveH = f("perspectiveH"),
                perspectiveV = f("perspectiveV"),
                lensK1 = f("lensK1"),
                lensK2 = f("lensK2"),
                lensK3 = f("lensK3"),
                lensP1 = f("lensP1"),
                lensP2 = f("lensP2"),
                lensVignetteStrength = f("lensVignetteStrength"),
                channelMixerMatrix = channelMixer,
                channelMixerMonochrome = json["channelMixerMonochrome"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                hslHueShift = hslHueShift,
                hslSaturationScale = hslSatScale,
                hslLuminanceScale = hslLumScale,
                toneCurveX = toneCurveX,
                toneCurveY = toneCurveY,
                toneCurvePoints = f("toneCurvePoints", 5f).toInt(),
                displayTransform = DisplayTransform(
                    colorScience = colorScience,
                    eotf = eotf,
                    peakLuminance = f("displayTransform_peakLuminance", 100f),
                    displayColorSpace = colorSpace,
                    openDrtLook = openDrtLook,
                    openDrtTonescale = openDrtTonescale
                )
            )

            // 同步 UI 状态
            _toneCurveX.value = toneCurveX
            _toneCurveY.value = toneCurveY
            _colorWheelLift.value = floatArrayOf(
                f("colorWheelLiftR"), f("colorWheelLiftG"), f("colorWheelLiftB"))
            _colorWheelGamma.value = floatArrayOf(
                f("colorWheelGammaR", 1f), f("colorWheelGammaG", 1f), f("colorWheelGammaB", 1f))
            _colorWheelGain.value = floatArrayOf(
                f("colorWheelGainR", 1f), f("colorWheelGainG", 1f), f("colorWheelGainB", 1f))
            _hslHueShift.value = hslHueShift
            _hslSaturationScale.value = hslSatScale
            _hslLuminanceScale.value = hslLumScale

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
        // 不主动 recycle 暴露给 UI 的 Bitmap — Compose 可能仍在引用
        // 仅置空引用，由 GC 回收，避免配置变更期间 use-after-recycle 崩溃
        _originalBitmap.value = null
        _previewBitmap.value = null
        // C8 修复: 同步清空预览源位图引用
        previewSourceBitmap = null
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoSave()
        releasePipelineSnapshot()
        // 不 recycle — 避免配置变更期间 use-after-recycle 崩溃
        clearBitmaps()
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
    HISTORY({ editorPanelHistory }),
    DISPLAY_TRANSFORM({ editorPanelDisplayTransform }),
    LMT({ editorPanelLmt }),
    INSPECTOR({ editorPanelInspector })
}

enum class ScopeType(val labelKey: StringResources.() -> String) {
    HISTOGRAM({ editorHistogram }),
    WAVEFORM({ editorWaveform }),
    VECTORSCOPE({ editorVectorscope }),
    CHROMATICITY({ editorChromaticity })
}
