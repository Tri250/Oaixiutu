package com.alcedo.studio.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.alcedo.studio.util.BitmapDecoder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alcedo.studio.data.model.*
import com.alcedo.studio.di.AppModule
import com.alcedo.studio.domain.service.ExportService
import com.alcedo.studio.domain.service.MaskInferenceService
import com.alcedo.studio.domain.service.MaskRenderService
import com.alcedo.studio.domain.service.PipelineService
import com.alcedo.studio.i18n.StringResources
import com.alcedo.studio.ndk.AlcedoNativeBridge
import com.alcedo.studio.ui.editor.CropAspectRatio
import com.alcedo.studio.ui.editor.GamutOverlay
import com.alcedo.studio.ui.editor.HistogramChannel
import com.alcedo.studio.ui.editor.HistogramScale
import com.alcedo.studio.ui.editor.ScopeAnalyzer
import com.alcedo.studio.ui.editor.WaveformMode
import com.alcedo.studio.ui.editor.BrushState
import com.alcedo.studio.ui.editor.CompareMode
import com.alcedo.studio.utils.MemoryGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

class EditorViewModel(private val imageId: String) : ViewModel() {

    companion object {
        private const val TAG = "EditorViewModel"
        private const val MAX_BITMAP_PIXELS = 4096 * 4096  // 16M pixels max
        // C8 修复: 预览处理使用降采样位图,避免 4096x4096 时 256MB float 数组 OOM
        // 2048x2048 = 4M 像素,float 数组 64MB,足够预览质量且不爆内存
        private const val PREVIEW_MAX_PIXELS = 2048 * 2048
        // S2 修复: 预览防抖时间延长至 100ms,减少滑块快速拖动时过度重绘
        private const val PREVIEW_DEBOUNCE_MS = 100L
        // S2 修复: 示波器计算防抖时间,避免预览高速变化时全量重算
        private const val SCOPE_DEBOUNCE_MS = 300L
    }
    private val imageRepository by lazy { AppModule.imageRepository }
    private val editHistoryRepository by lazy { AppModule.editHistoryRepository }
    private val pipelineService by lazy { AppModule.pipelineService }
    private val exportService by lazy { AppModule.exportService }
    private val thumbnailService by lazy { AppModule.thumbnailService }
    val presetService by lazy { AppModule.presetService }
    private val historyMgmtService by lazy { AppModule.historyMgmtService }
    private val colorScienceBridge by lazy { AppModule.colorScienceBridge }
    private val projectService by lazy { AppModule.projectService }

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

    // ── Image load error ──
    private val _imageLoadError = MutableStateFlow(false)
    val imageLoadError: StateFlow<Boolean> = _imageLoadError.asStateFlow()

    // ── Snackbar events ──
    private val _snackbarEvent = MutableSharedFlow<String>()
    val snackbarEvent: SharedFlow<String> = _snackbarEvent.asSharedFlow()

    fun showSnackbar(message: String) {
        viewModelScope.launch { _snackbarEvent.emit(message) }
    }

    // P2-8 锐化蒙版可视化
    private val _showSharpeningMask = MutableStateFlow(false)
    val showSharpeningMask: StateFlow<Boolean> = _showSharpeningMask.asStateFlow()

    private val _sharpeningMaskBitmap = MutableStateFlow<Bitmap?>(null)
    val sharpeningMaskBitmap: StateFlow<Bitmap?> = _sharpeningMaskBitmap

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

    private val _compareMode = MutableStateFlow(CompareMode.SPLIT)
    val compareMode: StateFlow<CompareMode> = _compareMode.asStateFlow()

    private val _overlayOpacity = MutableStateFlow(0.5f)
    val overlayOpacity: StateFlow<Float> = _overlayOpacity.asStateFlow()

    // 剪裁预警开关（类似 Lightroom 的 J 键功能）
    private val _showClippingWarning = MutableStateFlow(true)
    val showClippingWarning: StateFlow<Boolean> = _showClippingWarning.asStateFlow()

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

    // P2-5 批量导出预设同步: 每张选中图片待应用的参数缓存 (key=imageId)
    // ImageModel 本身不持久化 PipelineParams，因此缓存于 ViewModel，批量导出时统一应用
    private val _batchParamsCache = MutableStateFlow<Map<Long, PipelineParams>>(emptyMap())
    val batchParamsCache: StateFlow<Map<Long, PipelineParams>> = _batchParamsCache.asStateFlow()

    // ── Masks (AI local adjustments) ──

    val maskRenderService by lazy { AppModule.maskRenderService }

    private val _maskContainers = MutableStateFlow<List<MaskContainer>>(emptyList())
    val maskContainers: StateFlow<List<MaskContainer>> = _maskContainers.asStateFlow()

    private val _maskPreviewBitmap = MutableStateFlow<Bitmap?>(null)
    val maskPreviewBitmap: StateFlow<Bitmap?> = _maskPreviewBitmap.asStateFlow()

    private val _isAnalyzingMask = MutableStateFlow(false)
    val isAnalyzingMask: StateFlow<Boolean> = _isAnalyzingMask.asStateFlow()

    /** When true the mask overlay is drawn over the preview (mask panel open). */
    private val _showMaskOverlay = MutableStateFlow(true)
    val showMaskOverlay: StateFlow<Boolean> = _showMaskOverlay.asStateFlow()

    // ── Brush overlay (画笔交互状态) ──
    // 当前选中的 Brush 类型 sub-mask 在 maskContainers 中的索引。
    // 当用户切换到 Brush 类型 sub-mask 时由 MaskPanel 设置。
    private val _activeBrushSubMaskIndex = MutableStateFlow<Pair<String, Int>?>(null)
    val activeBrushSubMaskIndex: StateFlow<Pair<String, Int>?> = _activeBrushSubMaskIndex.asStateFlow()

    private val _brushState = mutableStateOf(BrushState())
    val brushState get() = _brushState

    /**
     * 选中一个 Brush 类型 sub-mask 作为当前画笔操作目标。
     * 会把该 sub-mask 已有的笔触、画笔参数同步到 [brushState]。
     */
    fun setActiveBrushSubMask(containerId: String, subMaskIndex: Int) {
        val container = _maskContainers.value.firstOrNull { it.id == containerId } ?: return
        val sub = container.subMasks.getOrNull(subMaskIndex) ?: return
        if (sub.type != MaskType.BRUSH) return
        _activeBrushSubMaskIndex.value = containerId to subMaskIndex
        _brushState.value = BrushState(
            strokes = sub.params.brushStrokes,
            brushSize = sub.params.brushSize,
            brushHardness = sub.params.brushHardness,
            brushOpacity = sub.params.brushOpacity,
            isEraser = false,
            isDrawingMode = true
        )
    }

    /** 退出画笔编辑（取消当前激活的 brush sub-mask）。 */
    fun clearActiveBrushSubMask() {
        _activeBrushSubMaskIndex.value = null
    }

    /**
     * 接收 BrushOverlay 的状态更新。仅当存在激活的 brush sub-mask 时
     * 才把笔触写回到 maskContainers；否则只更新本地 brushState。
     */
    fun updateBrushState(state: BrushState) {
        _brushState.value = state
        val active = _activeBrushSubMaskIndex.value
        if (active != null) {
            val (containerId, subIndex) = active
            _maskContainers.value = _maskContainers.value.map { container ->
                if (container.id == containerId) {
                    val subs = container.subMasks.toMutableList()
                    if (subIndex in subs.indices && subs[subIndex].type == MaskType.BRUSH) {
                        val oldParams = subs[subIndex].params
                        subs[subIndex] = subs[subIndex].copy(
                            params = oldParams.copy(
                                brushStrokes = state.strokes,
                                brushSize = state.brushSize,
                                brushHardness = state.brushHardness,
                                brushOpacity = state.brushOpacity
                            )
                        )
                    }
                    container.copy(subMasks = subs)
                } else container
            }
            regenerateMaskPreview()
        }
    }

    fun undoLastBrushStroke() {
        val s = _brushState.value
        if (s.strokes.isEmpty()) return
        updateBrushState(s.copy(strokes = s.strokes.dropLast(1), currentStroke = null))
    }

    fun clearAllBrushStrokes() {
        val s = _brushState.value
        if (s.strokes.isEmpty()) return
        updateBrushState(s.copy(strokes = emptyList(), currentStroke = null))
    }

    fun setBrushSize(value: Float) {
        _brushState.value = _brushState.value.copy(brushSize = value)
    }

    fun setBrushHardness(value: Float) {
        _brushState.value = _brushState.value.copy(brushHardness = value)
    }

    fun setBrushOpacity(value: Float) {
        _brushState.value = _brushState.value.copy(brushOpacity = value)
    }

    fun setBrushEraser(value: Boolean) {
        _brushState.value = _brushState.value.copy(isEraser = value)
    }

    fun setBrushDrawingMode(value: Boolean) {
        _brushState.value = _brushState.value.copy(isDrawingMode = value, currentStroke = null)
    }

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
     *
     * S2 修复: 根据设备内存自适应调整预览最大像素,低内存设备使用更小的预览尺寸。
     */
    private fun downscaleForPreview(source: Bitmap): Bitmap {
        val pixelCount = source.width.toLong() * source.height.toLong()
        // S2 修复: 根据设备内存自适应预览尺寸
        val adaptiveMaxPixels = try {
            MemoryGuard.suggestedMaxPixels(AppModule.context)
        } catch (e: Exception) {
            PREVIEW_MAX_PIXELS
        }
        val effectiveMaxPixels = adaptiveMaxPixels.coerceIn(1024 * 1024, PREVIEW_MAX_PIXELS)
        if (pixelCount <= effectiveMaxPixels) return source
        val scale = kotlin.math.sqrt(effectiveMaxPixels.toFloat() / pixelCount.toFloat())
        val newW = (source.width * scale).toInt().coerceAtLeast(1)
        val newH = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, newW, newH, true)
    }

    private fun loadImage() {
        viewModelScope.launch {
            _imageLoadError.value = false
            val id = imageId.toLongOrNull()
            if (id == null) {
                Log.e(TAG, "Invalid imageId: $imageId")
                _imageLoadError.value = true
                return@launch
            }

            // 方案 B-1: 包裹数据库查询的 try-catch
            // Room/SQLCipher 在 WAL 模式异常、journal 损坏时可能直接抛异常
            val img: ImageModel?
            try {
                img = imageRepository.getImage(id)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to query image from database", e)
                _imageLoadError.value = true
                return@launch
            }

            _imageModel.value = img

            // 方案 B-2: img 为 null 时设置错误状态（原代码用 ?.let 静默跳过）
            if (img == null) {
                Log.e(TAG, "No image found for id=$id")
                _imageLoadError.value = true
                return@launch
            }

            // 方案 B-3: imagePath 无效时设置错误状态
            if (img.imagePath.isBlank()) {
                Log.e(TAG, "Image path is empty for imageId=$id")
                _imageLoadError.value = true
                return@launch
            }

            try {
                // 在 IO 线程解码，避免主线程 ANR；采样避免大图 OOM
                val bitmap = withContext(Dispatchers.IO) {
                    val (outWidth, outHeight) = BitmapDecoder.decodeJustBounds(AppModule.context, img.imagePath)
                    if (outWidth <= 0 || outHeight <= 0) {
                        null
                    } else {
                        val rawSampleSize = calculateSampleSize(outWidth, outHeight)
                        val estimatedBytes = MemoryGuard.estimateBitmapBytes(
                            outWidth / rawSampleSize, outHeight / rawSampleSize
                        )
                        val finalSampleSize = if (!MemoryGuard.canAllocateBitmap(estimatedBytes)) {
                            MemoryGuard.calculateSafeSampleSize(
                                outWidth, outHeight,
                                MemoryGuard.availableHeapBytes() - 32L * 1024 * 1024
                            )
                        } else {
                            rawSampleSize
                        }
                        val decodeOptions = BitmapFactory.Options().apply {
                            inSampleSize = finalSampleSize
                            // S2 修复: 低内存设备使用 RGB_565 解码原图,节省 50% 内存
                            // RGB_565 色彩精度足够预览使用,导出时管线会重新处理
                            inPreferredConfig = if (MemoryGuard.availableHeapBytes() < 128L * 1024 * 1024) {
                                Bitmap.Config.RGB_565
                            } else {
                                Bitmap.Config.ARGB_8888
                            }
                        }
                        BitmapDecoder.decodeBitmap(AppModule.context, img.imagePath, decodeOptions)
                    }
                }
                _originalBitmap.value = bitmap
                // C8 修复: 为预览生成降采样位图,避免实时处理大图 OOM
                previewSourceBitmap = bitmap?.let { downscaleForPreview(it) }
                _previewBitmap.value = previewSourceBitmap

                // 方案 B-4: bitmap 解码为 null（宽高无效等）时设置错误状态
                if (bitmap == null) {
                    _imageLoadError.value = true
                }
            } catch (e: OutOfMemoryError) {
                Log.e("EditorVM", "OOM loading image, attempting recovery", e)
                System.gc()
                _originalBitmap.value = null
                _previewBitmap.value = null
                _imageLoadError.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("EditorVM", "Failed to load image", e)
                _originalBitmap.value = null
                _previewBitmap.value = null
                _imageLoadError.value = true
            }

            // P3-9 修复: 历史加载需要异常保护,数据库损坏/SQLCipher 异常时不应崩溃协程
            try {
                _history.value = editHistoryRepository.getHistory(id.toUInt())
                    ?: EditHistory(boundImageId = id.toUInt())
                _workingVersion.value = WorkingVersion(
                    boundImageId = id.toUInt(),
                    versionId = _history.value?.activeVersionId ?: ""
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to load edit history", e)
                _history.value = EditHistory(boundImageId = id.toUInt())
                _workingVersion.value = WorkingVersion(
                    boundImageId = id.toUInt(),
                    versionId = ""
                )
            }
            // Load preset list
            loadPresets()
        }
    }

    /** Retry loading the image after a failure. */
    fun retryLoadImage() {
        loadImage()
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

    // ── P2-8 锐化蒙版可视化 ─────────────────────────────────────

    /** 切换锐化蒙版显示状态。开启时自动生成蒙版预览。 */
    fun setShowSharpeningMask(show: Boolean) {
        _showSharpeningMask.value = show
        if (show) generateSharpeningMaskPreview()
        else _sharpeningMaskBitmap.value = null
    }

    /**
     * 使用边缘检测生成锐化蒙版预览。
     * 将当前预览位图转为 float 数组后调用原生边缘检测，
     * 结果转回 Bitmap 供 UI 叠加层渲染。
     */
    fun generateSharpeningMaskPreview() {
        viewModelScope.launch {
            val currentBitmap = _previewBitmap.value ?: return@launch
            val params = _params.value
            val width = currentBitmap.width
            val height = currentBitmap.height
            val pixelCount = width * height
            val pixels = IntArray(pixelCount)
            currentBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            // 转为 RGBA Float 数组
            val floatArray = FloatArray(pixelCount * 4) { idx ->
                val pixel = pixels[idx / 4]
                when (idx % 4) {
                    0 -> ((pixel shr 16) and 0xFF) / 255.0f   // R
                    1 -> ((pixel shr 8) and 0xFF) / 255.0f    // G
                    2 -> (pixel and 0xFF) / 255.0f             // B
                    else -> ((pixel shr 24) and 0xFF) / 255.0f // A
                }
            }

            val edgeData = AlcedoNativeBridge.generateEdgeMask(
                floatArray, width, height,
                params.sharpenAmount.coerceAtLeast(1f),  // radius 映射自 sharpenAmount
                0.3f                                        // threshold
            )

            if (edgeData != null && edgeData.size == pixelCount) {
                // 单通道边缘数据 → 白色高亮叠加（Alpha 通道表示强度）
                val maskPixels = IntArray(pixelCount) { i ->
                    val intensity = (edgeData[i] * 255).toInt().coerceIn(0, 255)
                    0xFF shl 24 or (intensity shl 16) or (intensity shl 8) or intensity
                }
                try {
                    val maskBitmap = Bitmap.createBitmap(maskPixels, width, height, Bitmap.Config.ARGB_8888)
                    _sharpeningMaskBitmap.value = maskBitmap
                } catch (_: OutOfMemoryError) {
                    _sharpeningMaskBitmap.value = null
                }
            } else {
                _sharpeningMaskBitmap.value = null
            }
        }
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

    /**
     * H5 修复: sigmoidPivot 调整需记录事务,否则 PARAMETRIC 曲线模式的
     * pivot 滑块无法撤销/重做
     */
    fun updateSigmoidPivot(value: Float) {
        _params.value = _params.value.copy(sigmoidPivot = value)
        recordTransaction(OperatorType.CONTRAST, "sigmoidPivot", value)
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

    fun updateGeometryScale(value: Float) {
        _params.value = _params.value.copy(geometryScale = value)
        recordTransaction(OperatorType.GEOMETRY, "geometryScale", value)
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
        recordTransaction(OperatorType.VIGNETTE, "lensVignetteStrength", strength)
        regeneratePreview()
    }

    // ── Denoise (luminance + chroma) ──

    fun updateLuminanceDenoise(strength: Float, detail: Float = 0.5f) {
        _params.value = _params.value.copy(luminanceDenoiseStrength = strength, luminanceDenoiseDetail = detail)
        recordTransaction(OperatorType.DENOISE, "luminanceDenoiseStrength", strength)
        recordTransaction(OperatorType.DENOISE, "luminanceDenoiseDetail", detail)
        regeneratePreview()
    }

    fun updateChromaDenoise(strength: Float, threshold: Float = 0.5f) {
        _params.value = _params.value.copy(chromaDenoiseStrength = strength, chromaDenoiseThreshold = threshold)
        recordTransaction(OperatorType.DENOISE, "chromaDenoiseStrength", strength)
        recordTransaction(OperatorType.DENOISE, "chromaDenoiseThreshold", threshold)
        regeneratePreview()
    }

    // ── Enhanced perspective transform ──

    fun updatePerspectiveDistortion(value: Float) {
        _params.value = _params.value.copy(perspectiveDistortion = value)
        recordTransaction(OperatorType.PERSPECTIVE, "perspectiveDistortion", value)
        regeneratePreview()
    }

    fun updatePerspectiveVertical(value: Float) {
        _params.value = _params.value.copy(perspectiveVertical = value)
        recordTransaction(OperatorType.PERSPECTIVE, "perspectiveVertical", value)
        regeneratePreview()
    }

    fun updatePerspectiveHorizontal(value: Float) {
        _params.value = _params.value.copy(perspectiveHorizontal = value)
        recordTransaction(OperatorType.PERSPECTIVE, "perspectiveHorizontal", value)
        regeneratePreview()
    }

    fun updatePerspectiveRotation(value: Float) {
        _params.value = _params.value.copy(perspectiveRotation = value)
        recordTransaction(OperatorType.PERSPECTIVE, "perspectiveRotation", value)
        regeneratePreview()
    }

    fun updatePerspectiveAspect(value: Float) {
        _params.value = _params.value.copy(perspectiveAspect = value)
        recordTransaction(OperatorType.PERSPECTIVE, "perspectiveAspect", value)
        regeneratePreview()
    }

    fun updatePerspectiveScale(value: Float) {
        _params.value = _params.value.copy(perspectiveScale = value)
        recordTransaction(OperatorType.PERSPECTIVE, "perspectiveScale", value)
        regeneratePreview()
    }

    fun updatePerspectiveXOffset(value: Float) {
        _params.value = _params.value.copy(perspectiveXOffset = value)
        recordTransaction(OperatorType.PERSPECTIVE, "perspectiveXOffset", value)
        regeneratePreview()
    }

    fun updatePerspectiveYOffset(value: Float) {
        _params.value = _params.value.copy(perspectiveYOffset = value)
        recordTransaction(OperatorType.PERSPECTIVE, "perspectiveYOffset", value)
        regeneratePreview()
    }

    fun resetPerspective() {
        _params.value = _params.value.copy(
            perspectiveDistortion = 0f,
            perspectiveVertical = 0f,
            perspectiveHorizontal = 0f,
            perspectiveRotation = 0f,
            perspectiveAspect = 0f,
            perspectiveScale = 0f,
            perspectiveXOffset = 0f,
            perspectiveYOffset = 0f
        )
        recordTransaction(OperatorType.PERSPECTIVE, "perspectiveDistortion", 0f)
        recordTransaction(OperatorType.PERSPECTIVE, "perspectiveVertical", 0f)
        recordTransaction(OperatorType.PERSPECTIVE, "perspectiveHorizontal", 0f)
        recordTransaction(OperatorType.PERSPECTIVE, "perspectiveRotation", 0f)
        recordTransaction(OperatorType.PERSPECTIVE, "perspectiveAspect", 0f)
        recordTransaction(OperatorType.PERSPECTIVE, "perspectiveScale", 0f)
        recordTransaction(OperatorType.PERSPECTIVE, "perspectiveXOffset", 0f)
        recordTransaction(OperatorType.PERSPECTIVE, "perspectiveYOffset", 0f)
        regeneratePreview()
    }

    // ── Lens correction toggles and amounts ──

    fun updateLensAutoDetect(enabled: Boolean) {
        _params.value = _params.value.copy(lensAutoDetect = enabled)
        recordTransaction(OperatorType.LENS_CORRECTION, "lensAutoDetect", if (enabled) 1f else 0f)
        regeneratePreview()
    }

    fun updateLensCorrectDistortion(enabled: Boolean) {
        _params.value = _params.value.copy(lensCorrectDistortion = enabled)
        recordTransaction(OperatorType.LENS_CORRECTION, "lensCorrectDistortion", if (enabled) 1f else 0f)
        regeneratePreview()
    }

    fun updateLensCorrectVignette(enabled: Boolean) {
        _params.value = _params.value.copy(lensCorrectVignette = enabled)
        recordTransaction(OperatorType.LENS_CORRECTION, "lensCorrectVignette", if (enabled) 1f else 0f)
        regeneratePreview()
    }

    fun updateLensCorrectTca(enabled: Boolean) {
        _params.value = _params.value.copy(lensCorrectTca = enabled)
        recordTransaction(OperatorType.LENS_CORRECTION, "lensCorrectTca", if (enabled) 1f else 0f)
        regeneratePreview()
    }

    fun updateLensDistortionAmount(value: Float) {
        _params.value = _params.value.copy(lensDistortionAmount = value)
        recordTransaction(OperatorType.LENS_CORRECTION, "lensDistortionAmount", value)
        regeneratePreview()
    }

    fun updateLensVignetteAmount(value: Float) {
        _params.value = _params.value.copy(lensVignetteAmount = value)
        recordTransaction(OperatorType.LENS_CORRECTION, "lensVignetteAmount", value)
        regeneratePreview()
    }

    fun updateLensTcaAmount(value: Float) {
        _params.value = _params.value.copy(lensTcaAmount = value)
        recordTransaction(OperatorType.LENS_CORRECTION, "lensTcaAmount", value)
        regeneratePreview()
    }

    fun updateLensMaker(maker: String) {
        _params.value = _params.value.copy(lensMaker = maker)
        // lensMaker 是文本字段，暂不入历史记录（history 仅支持 Float 值）
        regeneratePreview()
    }

    fun updateLensModel(model: String) {
        _params.value = _params.value.copy(lensModel = model)
        // lensModel 是文本字段，暂不入历史记录（history 仅支持 Float 值）
        regeneratePreview()
    }

    fun resetLensCorrection() {
        _params.value = _params.value.copy(
            lensK1 = 0f,
            lensK2 = 0f,
            lensK3 = 0f,
            lensP1 = 0f,
            lensP2 = 0f,
            lensAutoDetect = false,
            lensMaker = "",
            lensModel = "",
            lensCorrectDistortion = false,
            lensCorrectVignette = false,
            lensCorrectTca = false,
            lensDistortionAmount = 0f,
            lensVignetteAmount = 0f,
            lensTcaAmount = 0f
        )
        recordTransaction(OperatorType.LENS_CORRECTION, "lensK1", 0f)
        recordTransaction(OperatorType.LENS_CORRECTION, "lensK2", 0f)
        recordTransaction(OperatorType.LENS_CORRECTION, "lensK3", 0f)
        recordTransaction(OperatorType.LENS_CORRECTION, "lensP1", 0f)
        recordTransaction(OperatorType.LENS_CORRECTION, "lensP2", 0f)
        recordTransaction(OperatorType.LENS_CORRECTION, "lensAutoDetect", 0f)
        recordTransaction(OperatorType.LENS_CORRECTION, "lensCorrectDistortion", 0f)
        recordTransaction(OperatorType.LENS_CORRECTION, "lensCorrectVignette", 0f)
        recordTransaction(OperatorType.LENS_CORRECTION, "lensCorrectTca", 0f)
        recordTransaction(OperatorType.LENS_CORRECTION, "lensDistortionAmount", 0f)
        recordTransaction(OperatorType.LENS_CORRECTION, "lensVignetteAmount", 0f)
        recordTransaction(OperatorType.LENS_CORRECTION, "lensTcaAmount", 0f)
        regeneratePreview()
    }

    // ================================================================
    // Crop / Geometry (P1-7 裁剪工具交互)
    // ================================================================

    fun updateCropAspectRatio(ratio: CropAspectRatio) {
        val currentParams = _params.value
        // 根据比例更新 geometryCropLeft/Top/Right/Bottom
        val cropWidth = currentParams.geometryCropRight - currentParams.geometryCropLeft
        val cropHeight = currentParams.geometryCropBottom - currentParams.geometryCropTop

        when (ratio.ratio) {
            null -> return  // 自由裁剪，不调整
            else -> {
                val targetRatio = ratio.ratio
                val currentRatio = cropWidth / cropHeight.coerceAtLeast(0.001f)
                if (currentRatio > targetRatio) {
                    // 需要裁掉左右
                    val newWidth = cropHeight * targetRatio
                    val excess = cropWidth - newWidth
                    _params.value = currentParams.copy(
                        geometryCropLeft = currentParams.geometryCropLeft + excess / 2f,
                        geometryCropRight = currentParams.geometryCropRight - excess / 2f
                    )
                } else {
                    // 需要裁掉上下
                    val newHeight = cropWidth / targetRatio
                    val excess = cropHeight - newHeight
                    _params.value = currentParams.copy(
                        geometryCropTop = currentParams.geometryCropTop + excess / 2f,
                        geometryCropBottom = currentParams.geometryCropBottom - excess / 2f
                    )
                }
            }
        }
        // C3 修复: 记录裁剪参数变更以使撤销/重做可追踪
        val newParams = _params.value
        recordTransaction(OperatorType.CROP, "geometryCropLeft", newParams.geometryCropLeft)
        recordTransaction(OperatorType.CROP, "geometryCropTop", newParams.geometryCropTop)
        recordTransaction(OperatorType.CROP, "geometryCropRight", newParams.geometryCropRight)
        recordTransaction(OperatorType.CROP, "geometryCropBottom", newParams.geometryCropBottom)
        regeneratePreview()
    }

    fun updateCropRotation(degrees: Int) {
        _params.value = _params.value.copy(cropRotation = degrees)
        recordTransaction(OperatorType.CROP, "cropRotation", degrees.toFloat())
        regeneratePreview()
    }

    fun updateCropFlip(flipH: Boolean, flipV: Boolean) {
        _params.value = _params.value.copy(cropFlipHorizontal = flipH, cropFlipVertical = flipV)
        recordTransaction(OperatorType.CROP, "cropFlipHorizontal", if (flipH) 1f else 0f)
        recordTransaction(OperatorType.CROP, "cropFlipVertical", if (flipV) 1f else 0f)
        regeneratePreview()
    }

    fun resetCrop() {
        val params = _params.value
        _params.value = params.copy(
            geometryCropLeft = 0f, geometryCropTop = 0f, geometryCropRight = 1f, geometryCropBottom = 1f,
            cropRotation = 0, cropFlipHorizontal = false, cropFlipVertical = false
        )
        recordTransaction(OperatorType.CROP, "geometryCropLeft", 0f)
        recordTransaction(OperatorType.CROP, "geometryCropTop", 0f)
        recordTransaction(OperatorType.CROP, "geometryCropRight", 1f)
        recordTransaction(OperatorType.CROP, "geometryCropBottom", 1f)
        recordTransaction(OperatorType.CROP, "cropRotation", 0f)
        recordTransaction(OperatorType.CROP, "cropFlipHorizontal", 0f)
        recordTransaction(OperatorType.CROP, "cropFlipVertical", 0f)
        regeneratePreview()
    }

    fun updateGeometryCrop(left: Float, top: Float, right: Float, bottom: Float) {
        _params.value = _params.value.copy(
            geometryCropLeft = left, geometryCropTop = top,
            geometryCropRight = right, geometryCropBottom = bottom
        )
        recordTransaction(OperatorType.CROP, "geometryCropLeft", left)
        recordTransaction(OperatorType.CROP, "geometryCropTop", top)
        recordTransaction(OperatorType.CROP, "geometryCropRight", right)
        recordTransaction(OperatorType.CROP, "geometryCropBottom", bottom)
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

    /**
     * H1-H4 修复: 带历史记录的批量参数更新。
     *
     * 用于 HlsProfilePanel / DisplayTransformPanel / LmtPanel / RawDecodePanel 等面板,
     * 这些面板通过 onParamsChanged 回调直接传入完整 PipelineParams,绕过了 recordTransaction,
     * 导致撤销/重做失效。此方法对比新旧 params 的标量与数组字段,为每个变更记录事务。
     *
     * 注意: recordTransaction 读取 _params.value 当前值作为 paramsBefore,
     * 因此必须先记录所有事务(此时 _params 仍为旧值),最后再更新 _params。
     */
    fun updateParamsWithHistory(newParams: PipelineParams, operatorType: OperatorType) {
        val old = _params.value
        // ── 标量字段对比 ──
        if (old.exposure != newParams.exposure) recordTransaction(operatorType, "exposure", newParams.exposure)
        if (old.contrast != newParams.contrast) recordTransaction(operatorType, "contrast", newParams.contrast)
        if (old.saturation != newParams.saturation) recordTransaction(operatorType, "saturation", newParams.saturation)
        if (old.vibrance != newParams.vibrance) recordTransaction(operatorType, "vibrance", newParams.vibrance)
        if (old.highlights != newParams.highlights) recordTransaction(operatorType, "highlights", newParams.highlights)
        if (old.shadows != newParams.shadows) recordTransaction(operatorType, "shadows", newParams.shadows)
        if (old.midtones != newParams.midtones) recordTransaction(operatorType, "midtones", newParams.midtones)
        if (old.sigmoidContrast != newParams.sigmoidContrast) recordTransaction(operatorType, "sigmoidContrast", newParams.sigmoidContrast)
        if (old.sigmoidPivot != newParams.sigmoidPivot) recordTransaction(operatorType, "sigmoidPivot", newParams.sigmoidPivot)
        if (old.sigmoidShoulder != newParams.sigmoidShoulder) recordTransaction(operatorType, "sigmoidShoulder", newParams.sigmoidShoulder)
        if (old.whiteBalanceTemp != newParams.whiteBalanceTemp) recordTransaction(operatorType, "whiteBalanceTemp", newParams.whiteBalanceTemp)
        if (old.whiteBalanceTint != newParams.whiteBalanceTint) recordTransaction(operatorType, "whiteBalanceTint", newParams.whiteBalanceTint)
        if (old.clarityAmount != newParams.clarityAmount) recordTransaction(operatorType, "clarityAmount", newParams.clarityAmount)
        if (old.sharpenAmount != newParams.sharpenAmount) recordTransaction(operatorType, "sharpenAmount", newParams.sharpenAmount)
        if (old.filmGrainIntensity != newParams.filmGrainIntensity) recordTransaction(operatorType, "filmGrainIntensity", newParams.filmGrainIntensity)
        if (old.halationIntensity != newParams.halationIntensity) recordTransaction(operatorType, "halationIntensity", newParams.halationIntensity)
        if (old.halationThreshold != newParams.halationThreshold) recordTransaction(operatorType, "halationThreshold", newParams.halationThreshold)
        if (old.halationSpread != newParams.halationSpread) recordTransaction(operatorType, "halationSpread", newParams.halationSpread)
        if (old.halationRedBias != newParams.halationRedBias) recordTransaction(operatorType, "halationRedBias", newParams.halationRedBias)
        if (old.lensVignetteStrength != newParams.lensVignetteStrength) recordTransaction(operatorType, "lensVignetteStrength", newParams.lensVignetteStrength)
        if (old.lutIntensity != newParams.lutIntensity) recordTransaction(operatorType, "lutIntensity", newParams.lutIntensity)
        if (old.lutEnabled != newParams.lutEnabled) recordTransaction(operatorType, "lutEnabled", if (newParams.lutEnabled) 1f else 0f)

        // ── Denoise 字段对比 ──
        if (old.luminanceDenoiseStrength != newParams.luminanceDenoiseStrength) recordTransaction(operatorType, "luminanceDenoiseStrength", newParams.luminanceDenoiseStrength)
        if (old.luminanceDenoiseDetail != newParams.luminanceDenoiseDetail) recordTransaction(operatorType, "luminanceDenoiseDetail", newParams.luminanceDenoiseDetail)
        if (old.chromaDenoiseStrength != newParams.chromaDenoiseStrength) recordTransaction(operatorType, "chromaDenoiseStrength", newParams.chromaDenoiseStrength)
        if (old.chromaDenoiseThreshold != newParams.chromaDenoiseThreshold) recordTransaction(operatorType, "chromaDenoiseThreshold", newParams.chromaDenoiseThreshold)

        // ── Perspective 字段对比 ──
        if (old.perspectiveDistortion != newParams.perspectiveDistortion) recordTransaction(operatorType, "perspectiveDistortion", newParams.perspectiveDistortion)
        if (old.perspectiveVertical != newParams.perspectiveVertical) recordTransaction(operatorType, "perspectiveVertical", newParams.perspectiveVertical)
        if (old.perspectiveHorizontal != newParams.perspectiveHorizontal) recordTransaction(operatorType, "perspectiveHorizontal", newParams.perspectiveHorizontal)
        if (old.perspectiveRotation != newParams.perspectiveRotation) recordTransaction(operatorType, "perspectiveRotation", newParams.perspectiveRotation)
        if (old.perspectiveAspect != newParams.perspectiveAspect) recordTransaction(operatorType, "perspectiveAspect", newParams.perspectiveAspect)
        if (old.perspectiveScale != newParams.perspectiveScale) recordTransaction(operatorType, "perspectiveScale", newParams.perspectiveScale)
        if (old.perspectiveXOffset != newParams.perspectiveXOffset) recordTransaction(operatorType, "perspectiveXOffset", newParams.perspectiveXOffset)
        if (old.perspectiveYOffset != newParams.perspectiveYOffset) recordTransaction(operatorType, "perspectiveYOffset", newParams.perspectiveYOffset)

        // ── Lens correction 字段对比 ──
        if (old.lensAutoDetect != newParams.lensAutoDetect) recordTransaction(operatorType, "lensAutoDetect", if (newParams.lensAutoDetect) 1f else 0f)
        if (old.lensCorrectDistortion != newParams.lensCorrectDistortion) recordTransaction(operatorType, "lensCorrectDistortion", if (newParams.lensCorrectDistortion) 1f else 0f)
        if (old.lensCorrectVignette != newParams.lensCorrectVignette) recordTransaction(operatorType, "lensCorrectVignette", if (newParams.lensCorrectVignette) 1f else 0f)
        if (old.lensCorrectTca != newParams.lensCorrectTca) recordTransaction(operatorType, "lensCorrectTca", if (newParams.lensCorrectTca) 1f else 0f)
        if (old.lensDistortionAmount != newParams.lensDistortionAmount) recordTransaction(operatorType, "lensDistortionAmount", newParams.lensDistortionAmount)
        if (old.lensVignetteAmount != newParams.lensVignetteAmount) recordTransaction(operatorType, "lensVignetteAmount", newParams.lensVignetteAmount)
        if (old.lensTcaAmount != newParams.lensTcaAmount) recordTransaction(operatorType, "lensTcaAmount", newParams.lensTcaAmount)

        // ── DisplayTransform 子字段对比 (H2) ──
        if (old.displayTransform.colorScience != newParams.displayTransform.colorScience)
            recordTransaction(OperatorType.DISPLAY_TRANSFORM, "displayTransform_colorScience", newParams.displayTransform.colorScience.ordinal.toFloat())
        if (old.displayTransform.eotf != newParams.displayTransform.eotf)
            recordTransaction(OperatorType.DISPLAY_TRANSFORM, "displayTransform_eotf", newParams.displayTransform.eotf.ordinal.toFloat())
        if (old.displayTransform.peakLuminance != newParams.displayTransform.peakLuminance)
            recordTransaction(OperatorType.DISPLAY_TRANSFORM, "displayTransform_peakLuminance", newParams.displayTransform.peakLuminance)
        if (old.displayTransform.displayColorSpace != newParams.displayTransform.displayColorSpace)
            recordTransaction(OperatorType.DISPLAY_TRANSFORM, "displayTransform_displayColorSpace", newParams.displayTransform.displayColorSpace.ordinal.toFloat())

        // ── HSL 数组对比 (H1) ──
        for (i in 0 until 8) {
            if (old.hslHueShift[i] != newParams.hslHueShift[i])
                recordTransaction(OperatorType.HSL, "hslHueShift[$i]", newParams.hslHueShift[i])
            if (old.hslSaturationScale[i] != newParams.hslSaturationScale[i])
                recordTransaction(OperatorType.HSL, "hslSaturationScale[$i]", newParams.hslSaturationScale[i])
            if (old.hslLuminanceScale[i] != newParams.hslLuminanceScale[i])
                recordTransaction(OperatorType.HSL, "hslLuminanceScale[$i]", newParams.hslLuminanceScale[i])
        }

        // ── Tone curve 数组对比 (H5 补充) ──
        for (i in newParams.toneCurveX.indices) {
            if (i < old.toneCurveX.size && old.toneCurveX[i] != newParams.toneCurveX[i])
                recordTransaction(OperatorType.TONE_CURVE, "toneCurveX[$i]", newParams.toneCurveX[i])
            if (i < old.toneCurveY.size && old.toneCurveY[i] != newParams.toneCurveY[i])
                recordTransaction(OperatorType.TONE_CURVE, "toneCurveY[$i]", newParams.toneCurveY[i])
        }

        // ── Channel mixer 数组对比 ──
        for (i in newParams.channelMixerMatrix.indices) {
            if (i < old.channelMixerMatrix.size && old.channelMixerMatrix[i] != newParams.channelMixerMatrix[i])
                recordTransaction(OperatorType.COLOR_WHEEL, "channelMixerMatrix[$i]", newParams.channelMixerMatrix[i])
        }

        // ── RawDecodeParams 子字段对比 (H4) ──
        if (old.rawDecodeParams.demosaicAlgorithm != newParams.rawDecodeParams.demosaicAlgorithm)
            recordTransaction(OperatorType.RAW_DECODE, "rawDecode_demosaicAlgorithm", newParams.rawDecodeParams.demosaicAlgorithm.ordinal.toFloat())
        if (old.rawDecodeParams.highlightReconstruction != newParams.rawDecodeParams.highlightReconstruction)
            recordTransaction(OperatorType.RAW_DECODE, "rawDecode_highlightReconstruction", if (newParams.rawDecodeParams.highlightReconstruction) 1f else 0f)
        if (old.rawDecodeParams.autoBrightness != newParams.rawDecodeParams.autoBrightness)
            recordTransaction(OperatorType.RAW_DECODE, "rawDecode_autoBrightness", if (newParams.rawDecodeParams.autoBrightness) 1f else 0f)
        if (old.rawDecodeParams.useCameraMatrix != newParams.rawDecodeParams.useCameraMatrix)
            recordTransaction(OperatorType.RAW_DECODE, "rawDecode_useCameraMatrix", if (newParams.rawDecodeParams.useCameraMatrix) 1f else 0f)

        // 最后更新 _params 并刷新预览
        _params.value = newParams
        regeneratePreview()
    }

    // ================================================================
    // Tone curve editing
    // ================================================================

    fun updateToneCurve(x: FloatArray, y: FloatArray) {
        _toneCurveX.value = x
        _toneCurveY.value = y
        // BUG FIX: Count actual non-zero control points, not padded array size.
        // The native pipeline uses toneCurvePoints to know how many valid
        // entries to read from toneCurveX/toneCurveY. Using x.size (which
        // may be 16 after padding) would cause the curve to include (0,0)
        // padding points, producing a completely wrong curve shape.
        val actualPoints = if (x.size <= 5) x.size else {
            // Count trailing zeros to find the real point count
            var count = x.size
            while (count > 0 && x[count - 1] == 0f && y[count - 1] == 0f) count--
            count.coerceAtLeast(2) // At least 2 endpoints
        }
        _params.value = _params.value.copy(
            toneCurveX = x,
            toneCurveY = y,
            toneCurvePoints = actualPoints
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

    fun updateCompareMode(mode: CompareMode) {
        _compareMode.value = mode
    }

    fun updateOverlayOpacity(opacity: Float) {
        _overlayOpacity.value = opacity.coerceIn(0f, 1f)
    }

    fun toggleClippingWarning() {
        _showClippingWarning.value = !_showClippingWarning.value
    }

    fun getOriginalBitmap(): Bitmap? = _originalBitmap.value

    // ================================================================
    // Preview regeneration
    // ================================================================

    private var previewJob: Job? = null

    // S2 修复: 示波器计算协程引用,用于取消和防抖
    private var scopeJob: Job? = null

    fun regeneratePreview() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(PREVIEW_DEBOUNCE_MS) // S2 修复: 100ms 防抖,减少快速拖动滑块时的过度重绘
            _isProcessing.value = true
            try {
                // C8 修复: 使用降采样位图进行预览,避免大图 OOM
                val source = previewSourceBitmap ?: _originalBitmap.value
                if (source != null && !source.isRecycled) {
                    val oldPreview = _previewBitmap.value
                    val newPreview = pipelineService.applyPipeline(source, _params.value)
                    _previewBitmap.value = newPreview
                    // P3-2 修复: 回收旧预览时必须排除 previewSourceBitmap 和 _originalBitmap,
                    // 因为 downscaleForPreview 在小图时直接返回 source (同一对象),
                    // 回收它们会导致 source 被 destroy,后续预览和导出全部崩溃。
                    if (oldPreview != null && !oldPreview.isRecycled
                        && oldPreview !== previewSourceBitmap
                        && oldPreview !== _originalBitmap.value
                        && oldPreview !== newPreview
                    ) {
                        oldPreview.recycle()
                    }
                }
            } catch (e: CancellationException) {
                throw e
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
                // S2 修复: 使用预览降采样位图进行自动曝光分析,避免大图 OOM
                // 原图可能是 4096x4096,IntArray+FloatArray 需要 80MB,极易 OOM
                val bitmap = previewSourceBitmap ?: _originalBitmap.value ?: return@launch
                val width = bitmap.width
                val height = bitmap.height
                val pixelCount = width * height

                // S2 修复: 超大图额外安全检查
                if (pixelCount > MAX_BITMAP_PIXELS / 4) {
                    // 超过 4M 像素,跳过自动曝光分析以避免 OOM
                    android.util.Log.w(TAG, "applyAutoExposure skipped: image too large (${pixelCount}px)")
                    return@launch
                }

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
                recordTransaction(OperatorType.EXPOSURE, "exposure", ev)
                regeneratePreview()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                android.util.Log.e("EditorVM", "Coroutine failed", e)
            }
        }
    }

    // ================================================================
    // Auto Enhance (智能优化)
    // ================================================================

    /** Scene classification result used to modulate auto-enhance adjustments. */
    private enum class SceneType {
        NORMAL, LOW_LIGHT, HIGH_KEY, HIGH_CONTRAST,
        LOW_SATURATION, HIGH_SATURATION, BACKLIT
    }

    /** Computed image statistics used by scene classification and analyze functions. */
    private data class ImageStats(
        val avgBrightness: Float,
        val stddev: Float,
        val avgSaturation: Float,
        val highlightRatio: Float,   // fraction of pixels above 230
        val shadowRatio: Float,      // fraction of pixels below 25
        val midtoneRatio: Float       // fraction of pixels in [64, 192]
    )

    fun autoEnhance() {
        // 智能优化 — 自动分析图像并应用最佳调整
        viewModelScope.launch {
            try {
                val currentParams = _params.value
                val previewBitmap = previewSourceBitmap ?: _previewBitmap.value ?: return@launch

                // Emit loading state
                _isProcessing.value = true

                // Parallel analysis
                val results = coroutineScope {
                    val statsDeferred = async(Dispatchers.Default) { computeImageStats(previewBitmap) }
                    val exposureDeferred = async(Dispatchers.Default) { analyzeExposure(previewBitmap) }
                    val wbDeferred = async(Dispatchers.Default) { analyzeWhiteBalance(previewBitmap) }
                    val contrastDeferred = async(Dispatchers.Default) { analyzeContrast(previewBitmap) }
                    val hsDeferred = async(Dispatchers.Default) { analyzeHighlightsShadows(previewBitmap) }
                    val satDeferred = async(Dispatchers.Default) { analyzeSaturation(previewBitmap) }
                    val vibDeferred = async(Dispatchers.Default) { analyzeVibrance(previewBitmap) }
                    val clarityDeferred = async(Dispatchers.Default) { analyzeClarity(previewBitmap) }

                    Octuple(
                        statsDeferred.await(),
                        exposureDeferred.await(),
                        wbDeferred.await(),
                        contrastDeferred.await(),
                        hsDeferred.await(),
                        satDeferred.await(),
                        vibDeferred.await(),
                        clarityDeferred.await()
                    )
                }
                val stats = results.a
                val autoExposure = results.b
                val autoWB = results.c
                val autoContrast = results.d
                val autoHS = results.e
                val autoSaturation = results.f
                val autoVibrance = results.g
                val autoClarity = results.h

                // Scene-aware parameter modulation
                val scene = classifyScene(stats)
                var exposure = autoExposure
                var contrast = autoContrast
                var highlights = autoHS.first
                var shadows = autoHS.second
                var saturation = autoSaturation
                var vibrance = autoVibrance
                var clarity = autoClarity

                when (scene) {
                    SceneType.LOW_LIGHT -> {
                        exposure = (exposure + 0.3f).coerceIn(-2f, 2f)
                        contrast = (contrast - 0.1f).coerceIn(-0.5f, 0.5f)
                        clarity = (clarity + 0.15f).coerceIn(-1f, 1f)
                    }
                    SceneType.HIGH_KEY -> {
                        exposure = (exposure - 0.15f).coerceIn(-2f, 2f)
                        contrast = (contrast + 0.12f).coerceIn(-0.5f, 0.5f)
                    }
                    SceneType.HIGH_CONTRAST -> {
                        shadows = (shadows + 0.2f).coerceIn(-1f, 1f)
                        highlights = (highlights - 0.15f).coerceIn(-1f, 1f)
                    }
                    SceneType.LOW_SATURATION -> {
                        saturation = (saturation + 0.1f).coerceIn(-0.5f, 0.5f)
                        vibrance = (vibrance + 0.1f).coerceIn(-0.5f, 0.5f)
                    }
                    SceneType.HIGH_SATURATION -> {
                        saturation = (saturation - 0.08f).coerceIn(-0.5f, 0.5f)
                        // Only boost vibrance to protect skin tones
                        vibrance = (vibrance + 0.05f).coerceIn(-0.5f, 0.5f)
                    }
                    SceneType.BACKLIT -> {
                        shadows = (shadows + 0.3f).coerceIn(-1f, 1f)
                        highlights = (highlights - 0.2f).coerceIn(-1f, 1f)
                        contrast = (contrast + 0.1f).coerceIn(-0.5f, 0.5f)
                    }
                    SceneType.NORMAL -> { /* no modulation */ }
                }

                // Final clamp — ensure every value is within PipelineParams valid ranges
                exposure = exposure.coerceIn(-2f, 2f)
                contrast = contrast.coerceIn(-0.5f, 0.5f)
                highlights = highlights.coerceIn(-1f, 1f)
                shadows = shadows.coerceIn(-1f, 1f)
                saturation = saturation.coerceIn(-0.5f, 0.5f)
                vibrance = vibrance.coerceIn(-0.5f, 0.5f)
                clarity = clarity.coerceIn(-1f, 1f)
                val wbTemp = autoWB.first.coerceIn(2000f, 15000f)
                val wbTint = autoWB.second.coerceIn(-50f, 50f)

                _params.value = currentParams.copy(
                    exposure = exposure,
                    contrast = contrast,
                    whiteBalanceTemp = wbTemp,
                    whiteBalanceTint = wbTint,
                    highlights = highlights,
                    shadows = shadows,
                    saturation = saturation,
                    vibrance = vibrance,
                    clarityAmount = clarity
                )

                // 记录到历史
                recordTransaction(OperatorType.EXPOSURE, "exposure", exposure)
                recordTransaction(OperatorType.CONTRAST, "contrast", contrast)
                recordTransaction(OperatorType.WHITE_BALANCE, "whiteBalanceTemp", wbTemp)
                recordTransaction(OperatorType.WHITE_BALANCE, "whiteBalanceTint", wbTint)
                recordTransaction(OperatorType.HIGHLIGHTS, "highlights", highlights)
                recordTransaction(OperatorType.SHADOWS, "shadows", shadows)
                recordTransaction(OperatorType.SATURATION, "saturation", saturation)
                recordTransaction(OperatorType.SATURATION, "vibrance", vibrance)
                recordTransaction(OperatorType.CLARITY, "clarityAmount", clarity)
                saveVersion()
                regeneratePreview()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                android.util.Log.e("EditorVM", "autoEnhance failed", e)
            } finally {
                _isProcessing.value = false
            }
        }
    }

    /** Simple 8-element tuple for parallel analysis results. */
    private class Octuple<A, B, C, D, E, F, G, H>(
        val a: A, val b: B, val c: C, val d: D,
        val e: E, val f: F, val g: G, val h: H
    )

    // ── Scene classification ──────────────────────────────────────────────

    private fun classifyScene(stats: ImageStats): SceneType {
        // Backlit: very high highlight ratio + high shadow ratio (bimodal)
        if (stats.highlightRatio > 0.15f && stats.shadowRatio > 0.12f) {
            return SceneType.BACKLIT
        }
        // Low-light: overall dark
        if (stats.avgBrightness < 0.25f) {
            return SceneType.LOW_LIGHT
        }
        // High-key: overall bright with compressed tonal range
        if (stats.avgBrightness > 0.7f && stats.stddev < 0.18f) {
            return SceneType.HIGH_KEY
        }
        // High-contrast: wide tonal range
        if (stats.stddev > 0.32f) {
            return SceneType.HIGH_CONTRAST
        }
        // Low-saturation
        if (stats.avgSaturation < 0.2f) {
            return SceneType.LOW_SATURATION
        }
        // High-saturation
        if (stats.avgSaturation > 0.55f) {
            return SceneType.HIGH_SATURATION
        }
        return SceneType.NORMAL
    }

    // ── Image statistics computation ──────────────────────────────────────

    private fun computeImageStats(bitmap: Bitmap): ImageStats {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var sumLum = 0.0
        var sumSat = 0.0
        var highlightCount = 0
        var shadowCount = 0
        var midtoneCount = 0
        val step = maxOf(1, pixels.size / 50000) // sample ~50k pixels max

        var sampled = 0
        var i = 0
        while (i < pixels.size) {
            val pixel = pixels[i]
            val rv = ((pixel shr 16) and 0xFF) / 255.0
            val gv = ((pixel shr 8) and 0xFF) / 255.0
            val bv = (pixel and 0xFF) / 255.0
            val lum = 0.299 * rv + 0.587 * gv + 0.114 * bv

            sumLum += lum

            // Per-pixel saturation (HSV-style)
            val maxC = maxOf(rv, gv, bv)
            val minC = minOf(rv, gv, bv)
            val sat = if (maxC > 0.001) (maxC - minC) / maxC else 0.0
            sumSat += sat

            // Bucket
            val lumByte = (lum * 255.0).coerceIn(0.0, 255.0).toInt()
            if (lumByte > 230) highlightCount++
            if (lumByte < 25) shadowCount++
            if (lumByte in 64..192) midtoneCount++

            sampled++
            i += step
        }

        if (sampled == 0) return ImageStats(0.5f, 0.2f, 0.3f, 0f, 0f, 0.5f)

        val avgBrightness = (sumLum / sampled).toFloat()
        val avgSaturation = (sumSat / sampled).toFloat()
        val highlightRatio = highlightCount.toFloat() / sampled
        val shadowRatio = shadowCount.toFloat() / sampled
        val midtoneRatio = midtoneCount.toFloat() / sampled

        // Compute stddev from a second pass (still using sampled pixels)
        var variance = 0.0
        i = 0
        while (i < pixels.size) {
            val pixel = pixels[i]
            val rv = ((pixel shr 16) and 0xFF) / 255.0
            val gv = ((pixel shr 8) and 0xFF) / 255.0
            val bv = (pixel and 0xFF) / 255.0
            val lum = 0.299 * rv + 0.587 * gv + 0.114 * bv
            val diff = lum - (sumLum / sampled)
            variance += diff * diff
            i += step
        }
        val stddev = kotlin.math.sqrt(variance / sampled).toFloat()

        return ImageStats(avgBrightness, stddev, avgSaturation, highlightRatio, shadowRatio, midtoneRatio)
    }

    // ── Analyze functions ─────────────────────────────────────────────────

    private suspend fun analyzeWhiteBalance(bitmap: Bitmap): Pair<Float, Float> {
        return withContext(Dispatchers.Default) {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

            var avgR = 0.0
            var avgG = 0.0
            var avgB = 0.0
            var count = 0

            for (i in pixels.indices step 4) {  // 采样每4个像素
                val pixel = pixels[i]
                avgR += ((pixel shr 16) and 0xFF) / 255.0
                avgG += ((pixel shr 8) and 0xFF) / 255.0
                avgB += (pixel and 0xFF) / 255.0
                count++
            }

            if (count == 0) return@withContext Pair(6500f, 0f)

            avgR /= count
            avgG /= count
            avgB /= count

            // 灰世界假设: avgR ≈ avgG ≈ avgB
            // 计算所需的色温偏移
            val ratioR = avgG / avgR.coerceAtLeast(0.001)
            val ratioB = avgG / avgB.coerceAtLeast(0.001)

            // 简化映射: R/G ratio → 色温偏移, B/G ratio → tint偏移
            val tempOffset = ((ratioR - 1.0) * 3000f).toFloat().coerceIn(-2000f, 2000f)
            val tintOffset = ((ratioB - 1.0) * 200f).toFloat().coerceIn(-50f, 50f)

            Pair((6500f + tempOffset).coerceIn(2000f, 15000f), tintOffset)
        }
    }

    private suspend fun analyzeHighlightsShadows(bitmap: Bitmap): Pair<Float, Float> {
        return withContext(Dispatchers.Default) {
            val histogram = ScopeAnalyzer.computeHistogram(bitmap)
            val lum = histogram.luminance

            var total = 0f
            for (v in lum) total += v
            if (total <= 0f) return@withContext Pair(0f, 0f)

            // ── Highlights: nuanced, not just clipping ──
            // Accumulate weighted mass in the upper tonal range (200..255)
            // Stronger weight the closer to 255
            var highlightWeighted = 0f
            for (i in 200..255) {
                val weight = (i - 200) / 55f  // 0..1 ramp
                highlightWeighted += lum[i] * weight
            }
            val highlightMass = highlightWeighted / total
            // Soft S-curve: small mass → small recovery, large mass → strong recovery
            val autoHighlights = if (highlightMass > 0.02f) {
                -((-1f + 1f / (1f + highlightMass * 6f)) * 2f).coerceIn(-1f, 0f)
            } else 0f

            // ── Shadows: nuanced ──
            // Accumulate weighted mass in the lower tonal range (0..55)
            var shadowWeighted = 0f
            for (i in 0..55) {
                val weight = (55 - i) / 55f  // 1..0 ramp
                shadowWeighted += lum[i] * weight
            }
            val shadowMass = shadowWeighted / total
            val autoShadows = if (shadowMass > 0.02f) {
                ((1f / (1f + shadowMass * 4f) - 1f / (1f + 0.02f * 4f)) * 3f)
                    .coerceIn(0f, 1f)
            } else 0f

            Pair(autoHighlights, autoShadows)
        }
    }

    private suspend fun analyzeVibrance(bitmap: Bitmap): Float {
        return withContext(Dispatchers.Default) {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

            // Compute per-pixel saturation to find low-saturation regions
            var sumSat = 0.0
            var lowSatCount = 0
            var sampled = 0
            val step = maxOf(1, pixels.size / 30000)

            var i = 0
            while (i < pixels.size) {
                val pixel = pixels[i]
                val rv = ((pixel shr 16) and 0xFF) / 255.0
                val gv = ((pixel shr 8) and 0xFF) / 255.0
                val bv = (pixel and 0xFF) / 255.0

                val maxC = maxOf(rv, gv, bv)
                val minC = minOf(rv, gv, bv)
                val sat = if (maxC > 0.001) (maxC - minC) / maxC else 0.0
                sumSat += sat
                if (sat < 0.25) lowSatCount++
                sampled++
                i += step
            }

            if (sampled == 0) return@withContext 0f

            val avgSat = sumSat / sampled
            val lowSatFraction = lowSatCount.toFloat() / sampled

            // Vibrance boosts low-saturation pixels preferentially.
            // If a large fraction of pixels are desaturated, boost vibrance.
            if (lowSatFraction > 0.3f) {
                (0.1f + lowSatFraction * 0.15f).coerceIn(0f, 0.5f)
            } else if (avgSat < 0.3) {
                (0.08f + (0.3 - avgSat).toFloat() * 0.3f).coerceIn(0f, 0.5f)
            } else {
                0f
            }
        }
    }

    private suspend fun analyzeClarity(bitmap: Bitmap): Float {
        return withContext(Dispatchers.Default) {
            // Estimate sharpness/detail level using pixel variance in luminance channel.
            // High local variance = detailed/sharp image → less clarity needed.
            // Low local variance = soft/flat image → more clarity needed.
            val w = bitmap.width
            val h = bitmap.height
            if (w < 4 || h < 4) return@withContext 0f

            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

            // Compute luminance array
            val lum = FloatArray(w * h)
            for (idx in pixels.indices) {
                val pixel = pixels[idx]
                val rv = ((pixel shr 16) and 0xFF) / 255f
                val gv = ((pixel shr 8) and 0xFF) / 255f
                val bv = (pixel and 0xFF) / 255f
                lum[idx] = 0.299f * rv + 0.587f * gv + 0.114f * bv
            }

            // Compute average local variance using 3×3 blocks
            // Sample a grid of blocks to keep it fast
            val blockStep = maxOf(2, minOf(w, h) / 100)
            var totalVariance = 0f
            var blockCount = 0

            var y = 1
            while (y < h - 1) {
                var x = 1
                while (x < w - 1) {
                    val center = lum[y * w + x]
                    var localVar = 0f
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            val diff = lum[(y + dy) * w + (x + dx)] - center
                            localVar += diff * diff
                        }
                    }
                    localVar /= 9f
                    totalVariance += localVar
                    blockCount++
                    x += blockStep
                }
                y += blockStep
            }

            if (blockCount == 0) return@withContext 0f

            val avgLocalVariance = totalVariance / blockCount

            // Typical range: 0.0 (flat) to ~0.05 (very detailed)
            // Low variance → soft image → needs clarity boost
            // High variance → already sharp → minimal clarity
            val varianceTarget = 0.008f  // "pleasant" local detail level
            val clarity = if (avgLocalVariance < varianceTarget) {
                // Boost clarity proportional to how far below target
                ((varianceTarget - avgLocalVariance) / varianceTarget * 0.4f).coerceIn(0f, 0.6f)
            } else {
                // Already sharp; apply a small touch or none
                ((varianceTarget - avgLocalVariance) / 0.03f * 0.05f).coerceIn(-0.1f, 0.05f)
            }

            clarity
        }
    }

    private suspend fun analyzeExposure(bitmap: Bitmap): Float {
        return withContext(Dispatchers.Default) {
            // 基于预览位图分析直方图，计算最佳曝光补偿
            val histogram = ScopeAnalyzer.computeHistogram(bitmap)
            val lum = histogram.luminance
            var total = 0f
            var weighted = 0f
            for (i in lum.indices) {
                total += lum[i]
                weighted += lum[i] * (i / 255f)
            }
            val avgBrightness = if (total > 0f) weighted / total else 0.5f
            // 目标亮度: 0.5 (中灰)
            ((0.5f - avgBrightness) * 2f).coerceIn(-2f, 2f)
        }
    }

    private suspend fun analyzeContrast(bitmap: Bitmap): Float {
        return withContext(Dispatchers.Default) {
            val histogram = ScopeAnalyzer.computeHistogram(bitmap)
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
            ((0.25f - stddev) * 2f).coerceIn(-0.5f, 0.5f)
        }
    }

    private suspend fun analyzeSaturation(bitmap: Bitmap): Float {
        return withContext(Dispatchers.Default) {
            val histogram = ScopeAnalyzer.computeHistogram(bitmap)
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
            ((0.4f - avgSat) * 1.5f).coerceIn(-0.5f, 0.5f)
        }
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
                // Denoise params
                "luminanceDenoiseStrength" -> luminanceDenoiseStrength
                "luminanceDenoiseDetail" -> luminanceDenoiseDetail
                "chromaDenoiseStrength" -> chromaDenoiseStrength
                "chromaDenoiseThreshold" -> chromaDenoiseThreshold
                // Perspective transform params
                "perspectiveDistortion" -> perspectiveDistortion
                "perspectiveVertical" -> perspectiveVertical
                "perspectiveHorizontal" -> perspectiveHorizontal
                "perspectiveRotation" -> perspectiveRotation
                "perspectiveAspect" -> perspectiveAspect
                "perspectiveScale" -> perspectiveScale
                "perspectiveXOffset" -> perspectiveXOffset
                "perspectiveYOffset" -> perspectiveYOffset
                // Crop params
                "cropRotation" -> cropRotation.toFloat()
                "cropFlipHorizontal" -> if (cropFlipHorizontal) 1f else 0f
                "cropFlipVertical" -> if (cropFlipVertical) 1f else 0f
                "geometryCropLeft" -> geometryCropLeft
                "geometryCropTop" -> geometryCropTop
                "geometryCropRight" -> geometryCropRight
                "geometryCropBottom" -> geometryCropBottom
                // Lens correction toggles and amounts
                "lensAutoDetect" -> if (lensAutoDetect) 1f else 0f
                "lensCorrectDistortion" -> if (lensCorrectDistortion) 1f else 0f
                "lensCorrectVignette" -> if (lensCorrectVignette) 1f else 0f
                "lensCorrectTca" -> if (lensCorrectTca) 1f else 0f
                "lensDistortionAmount" -> lensDistortionAmount
                "lensVignetteAmount" -> lensVignetteAmount
                "lensTcaAmount" -> lensTcaAmount
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
                // RawDecodeParams 子字段 (H4)
                "rawDecode_demosaicAlgorithm" -> rawDecodeParams.demosaicAlgorithm.ordinal.toFloat()
                "rawDecode_highlightReconstruction" -> if (rawDecodeParams.highlightReconstruction) 1f else 0f
                "rawDecode_autoBrightness" -> if (rawDecodeParams.autoBrightness) 1f else 0f
                "rawDecode_useCameraMatrix" -> if (rawDecodeParams.useCameraMatrix) 1f else 0f
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

    /**
     * P2-6 撤销/重做可视化: 直接跳转到历史记录中的指定步骤。
     *
     * [stepCursor] 为目标游标位置（即应被应用的事务数量，范围 0..transactions.size）。
     * 0 表示回到初始状态（无任何操作），transactions.size 表示应用全部操作。
     * 跳转后重建参数并刷新预览。
     */
    fun jumpToHistoryStep(stepCursor: Int) {
        val wv = _workingVersion.value
        if (stepCursor in 0..wv.transactions.size) {
            wv.cursor = stepCursor
            reconstructParamsFromWorkingVersion()
            updateUndoRedoState()
        }
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
        // RawDecodeParams 可变累积 (H4)
        var rawDemosaic = DemosaicAlgorithm.RCD
        var rawHighlightRecon = true
        var rawAutoBrightness = false
        var rawUseCameraMatrix = true

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
                    "luminanceDenoiseStrength" -> reconstructed.copy(luminanceDenoiseStrength = floatValue)
                    "luminanceDenoiseDetail" -> reconstructed.copy(luminanceDenoiseDetail = floatValue)
                    "chromaDenoiseStrength" -> reconstructed.copy(chromaDenoiseStrength = floatValue)
                    "chromaDenoiseThreshold" -> reconstructed.copy(chromaDenoiseThreshold = floatValue)
                    "perspectiveDistortion" -> reconstructed.copy(perspectiveDistortion = floatValue)
                    "perspectiveVertical" -> reconstructed.copy(perspectiveVertical = floatValue)
                    "perspectiveHorizontal" -> reconstructed.copy(perspectiveHorizontal = floatValue)
                    "perspectiveRotation" -> reconstructed.copy(perspectiveRotation = floatValue)
                    "perspectiveAspect" -> reconstructed.copy(perspectiveAspect = floatValue)
                    "perspectiveScale" -> reconstructed.copy(perspectiveScale = floatValue)
                    "perspectiveXOffset" -> reconstructed.copy(perspectiveXOffset = floatValue)
                    "perspectiveYOffset" -> reconstructed.copy(perspectiveYOffset = floatValue)
                    "cropRotation" -> reconstructed.copy(cropRotation = floatValue.toInt())
                    "cropFlipHorizontal" -> reconstructed.copy(cropFlipHorizontal = floatValue != 0f)
                    "cropFlipVertical" -> reconstructed.copy(cropFlipVertical = floatValue != 0f)
                    "geometryCropLeft" -> reconstructed.copy(geometryCropLeft = floatValue)
                    "geometryCropTop" -> reconstructed.copy(geometryCropTop = floatValue)
                    "geometryCropRight" -> reconstructed.copy(geometryCropRight = floatValue)
                    "geometryCropBottom" -> reconstructed.copy(geometryCropBottom = floatValue)
                    "lensAutoDetect" -> reconstructed.copy(lensAutoDetect = floatValue != 0f)
                    "lensCorrectDistortion" -> reconstructed.copy(lensCorrectDistortion = floatValue != 0f)
                    "lensCorrectVignette" -> reconstructed.copy(lensCorrectVignette = floatValue != 0f)
                    "lensCorrectTca" -> reconstructed.copy(lensCorrectTca = floatValue != 0f)
                    "lensDistortionAmount" -> reconstructed.copy(lensDistortionAmount = floatValue)
                    "lensVignetteAmount" -> reconstructed.copy(lensVignetteAmount = floatValue)
                    "lensTcaAmount" -> reconstructed.copy(lensTcaAmount = floatValue)
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
                    // RawDecodeParams 子字段恢复 (H4)
                    "rawDecode_demosaicAlgorithm" -> { rawDemosaic = DemosaicAlgorithm.entries.getOrNull(floatValue.toInt()) ?: rawDemosaic; reconstructed }
                    "rawDecode_highlightReconstruction" -> { rawHighlightRecon = floatValue != 0f; reconstructed }
                    "rawDecode_autoBrightness" -> { rawAutoBrightness = floatValue != 0f; reconstructed }
                    "rawDecode_useCameraMatrix" -> { rawUseCameraMatrix = floatValue != 0f; reconstructed }
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
            ),
            rawDecodeParams = RawDecodeParams(
                demosaicAlgorithm = rawDemosaic,
                highlightReconstruction = rawHighlightRecon,
                autoBrightness = rawAutoBrightness,
                useCameraMatrix = rawUseCameraMatrix
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
                    // 通过 HistoryMgmtService 同步版本管理状态
                    historyMgmtService.loadHistory(hist.boundImageId)
                } catch (e: CancellationException) {
                    throw e
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
                    } catch (e: CancellationException) {
                        throw e
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
                    } catch (e: CancellationException) {
                        throw e
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
                } catch (e: CancellationException) {
                    throw e
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
                    // 通过 ProjectService 同步项目状态（预留项目工程功能）
                    try {
                        if (projectService.isProjectOpen()) {
                            projectService.markDirty()
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) { /* 项目服务不可用时静默跳过 */ }
                } catch (e: CancellationException) {
                    throw e
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
        map["luminanceDenoiseStrength"] = JsonPrimitive(p.luminanceDenoiseStrength)
        map["luminanceDenoiseDetail"] = JsonPrimitive(p.luminanceDenoiseDetail)
        map["chromaDenoiseStrength"] = JsonPrimitive(p.chromaDenoiseStrength)
        map["chromaDenoiseThreshold"] = JsonPrimitive(p.chromaDenoiseThreshold)
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
        // Enhanced perspective transform (RapidRAW-style)
        map["perspectiveDistortion"] = JsonPrimitive(p.perspectiveDistortion)
        map["perspectiveVertical"] = JsonPrimitive(p.perspectiveVertical)
        map["perspectiveHorizontal"] = JsonPrimitive(p.perspectiveHorizontal)
        map["perspectiveRotation"] = JsonPrimitive(p.perspectiveRotation)
        map["perspectiveAspect"] = JsonPrimitive(p.perspectiveAspect)
        map["perspectiveScale"] = JsonPrimitive(p.perspectiveScale)
        map["perspectiveXOffset"] = JsonPrimitive(p.perspectiveXOffset)
        map["perspectiveYOffset"] = JsonPrimitive(p.perspectiveYOffset)
        // Lens profile + correction toggles/amounts
        map["lensAutoDetect"] = JsonPrimitive(p.lensAutoDetect)
        map["lensMaker"] = JsonPrimitive(p.lensMaker)
        map["lensModel"] = JsonPrimitive(p.lensModel)
        map["lensCorrectDistortion"] = JsonPrimitive(p.lensCorrectDistortion)
        map["lensCorrectVignette"] = JsonPrimitive(p.lensCorrectVignette)
        map["lensCorrectTca"] = JsonPrimitive(p.lensCorrectTca)
        map["lensDistortionAmount"] = JsonPrimitive(p.lensDistortionAmount)
        map["lensVignetteAmount"] = JsonPrimitive(p.lensVignetteAmount)
        map["lensTcaAmount"] = JsonPrimitive(p.lensTcaAmount)
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
                perspectiveDistortion = f("perspectiveDistortion"),
                perspectiveVertical = f("perspectiveVertical"),
                perspectiveHorizontal = f("perspectiveHorizontal"),
                perspectiveRotation = f("perspectiveRotation"),
                perspectiveAspect = f("perspectiveAspect"),
                perspectiveScale = f("perspectiveScale"),
                perspectiveXOffset = f("perspectiveXOffset"),
                perspectiveYOffset = f("perspectiveYOffset"),
                lensAutoDetect = json["lensAutoDetect"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                lensMaker = json["lensMaker"]?.jsonPrimitive?.content ?: "",
                lensModel = json["lensModel"]?.jsonPrimitive?.content ?: "",
                lensCorrectDistortion = json["lensCorrectDistortion"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                lensCorrectVignette = json["lensCorrectVignette"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                lensCorrectTca = json["lensCorrectTca"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                lensDistortionAmount = f("lensDistortionAmount"),
                lensVignetteAmount = f("lensVignetteAmount"),
                lensTcaAmount = f("lensTcaAmount"),
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
        // Seed the database-backed preset system (RapidRAW-style) on first launch.
        viewModelScope.launch {
            try {
                presetService.ensureBuiltInPresetsInitialized()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Non-fatal: preset panel will simply show an empty state.
            }
        }
    }

    /**
     * Apply a [PresetWithThumbnail] from the database-backed PresetService.
     * Replaces all pipeline parameters and syncs derived UI state (tone curve,
     * color wheels, HSL) so editor panels reflect the change immediately.
     */
    fun applyPreset(preset: com.alcedo.studio.domain.service.PresetWithThumbnail) {
        applyPresetParams(preset.params)
        recordTransaction(OperatorType.PRESET, "applyPreset:${preset.name}", 0f)
        showSnackbar("预设已应用")
    }

    fun applyPreset(preset: PresetEntry) {
        applyPresetParams(preset.params)
        recordTransaction(OperatorType.PRESET, "applyPreset:${preset.name}", 0f)
        showSnackbar("预设已应用")
    }

    /**
     * Applies a [PipelineParams] (e.g. loaded from a database preset) and syncs
     * all derived UI state (tone curve / color wheels / HSL) so the editor
     * panels reflect the preset immediately.
     */
    fun applyPresetParams(params: PipelineParams) {
        _params.value = params
        _toneCurveX.value = params.toneCurveX
        _toneCurveY.value = params.toneCurveY
        _colorWheelLift.value = floatArrayOf(
            params.colorWheelLiftR,
            params.colorWheelLiftG,
            params.colorWheelLiftB
        )
        _colorWheelGamma.value = floatArrayOf(
            params.colorWheelGammaR,
            params.colorWheelGammaG,
            params.colorWheelGammaB
        )
        _colorWheelGain.value = floatArrayOf(
            params.colorWheelGainR,
            params.colorWheelGainG,
            params.colorWheelGainB
        )
        _hslHueShift.value = params.hslHueShift
        _hslSaturationScale.value = params.hslSaturationScale
        _hslLuminanceScale.value = params.hslLuminanceScale
        regeneratePreview()
    }

    /**
     * Saves the current pipeline state as a named database-backed preset.
     * The optional [description] is stored on the entity for display in the
     * panel.  This method persists via Room; it does NOT modify the legacy
     * in-memory [_presets] list.
     */
    fun saveCurrentAsPreset(name: String, category: String = "Custom", description: String = "") {
        viewModelScope.launch {
            try {
                val previewBitmap = _previewBitmap.value?.let { bmp ->
                    // Downsample for thumbnail to keep cache small.
                    if (bmp.width > 256 || bmp.height > 256) {
                        Bitmap.createScaledBitmap(bmp, 160, 160, true)
                    } else {
                        bmp
                    }
                }
                presetService.createPreset(name, category, _params.value, previewBitmap, description)
                Log.d(TAG, "Preset saved: $name")
                showSnackbar("预设已保存")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                Log.e(TAG, "Failed to save preset", null)
            }
        }
    }

    /** Legacy convenience overload that only takes a name. */
    fun saveCurrentAsPreset(name: String) {
        saveCurrentAsPreset(name, "Custom")
    }

    /** Deletes a database-backed preset by its row id. */
    fun deletePreset(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                presetService.deletePreset(id)
                showSnackbar("预设已删除")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {}
        }
    }

    /**
     * Imports a preset file via SAF [Uri]. Supports:
     *   - `.json` – native JSON export format
     *   - `.xmp`  – Adobe Lightroom XMP presets
     *   - `.cube` – LUT files
     *
     * Returns the new preset id (> 0 on success), or -1 on failure.
     */
    suspend fun importPreset(uri: Uri): Long = withContext(Dispatchers.IO) {
        try {
            val context = AppModule.context
            var result = -1L
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val ext = uri.toString()
                    .substringAfterLast(".", "").lowercase()
                when (ext) {
                    "xmp" -> {
                        val tempFile = File.createTempFile("import_xmp_", ".xmp", context.cacheDir)
                        tempFile.outputStream().use { inputStream.copyTo(it) }
                        result = presetService.importXmpPreset(tempFile)
                        tempFile.delete()
                    }
                    "cube" -> {
                        val lutDir = File(context.filesDir, "luts")
                        val tempFile = File.createTempFile("import_cube_", ".cube", context.cacheDir)
                        tempFile.outputStream().use { inputStream.copyTo(it) }
                        result = presetService.importCubePreset(tempFile, lutDir)
                        tempFile.delete()
                    }
                    else -> {
                        val tempFile = File.createTempFile("import_preset_", ".json", context.cacheDir)
                        tempFile.outputStream().use { inputStream.copyTo(it) }
                        result = presetService.importPreset(tempFile)
                        tempFile.delete()
                    }
                }
            }
            Log.d(TAG, "Imported preset from URI, id=$result")
            result
        } catch (e: Throwable) {
            Log.e(TAG, "Import preset failed", e)
            -1L
        }
    }

    /**
     * Exports a single database-backed preset to a destination [Uri] via SAF.
     * Writes JSON to a temporary file first, then copies into the output URI.
     * Returns true on success.
     */
    suspend fun exportPreset(presetId: Long, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val context = AppModule.context
            val tempFile = File.createTempFile("preset_export_", ".json", context.cacheDir)
            val ok = presetService.exportPreset(presetId, tempFile)
            if (ok) {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    tempFile.inputStream().use { it.copyTo(outputStream) }
                }
            } else {
                Log.w(TAG, "Export service returned false for preset $presetId")
            }
            tempFile.delete()
            Log.d(TAG, "Exported preset $presetId to $uri")
            ok
        } catch (e: Throwable) {
            Log.e(TAG, "Export preset failed for preset $presetId", e)
            false
        }
    }

    // ================================================================
    // Masks (AI local adjustments)
    // ================================================================

    private fun newId(): String = java.util.UUID.randomUUID().toString()

    /** Create a new mask container with an initial whole-image sub-mask. */
    fun addMaskContainer(initialType: MaskType = MaskType.WHOLE_IMAGE) {
        val subMask = SubMask(
            id = newId(),
            type = initialType,
            combineMode = MaskCombineMode.ADDITIVE,
            name = maskTypeName(initialType),
            params = MaskParams.defaultFor(initialType)
        )
        val container = MaskContainer(
            id = newId(),
            name = "Mask ${_maskContainers.value.size + 1}",
            subMasks = listOf(subMask)
        )
        _maskContainers.value = _maskContainers.value + container
        regenerateMaskPreview()
    }

    fun removeMaskContainer(containerId: String) {
        _maskContainers.value = _maskContainers.value.filterNot { it.id == containerId }
        maskRenderService.invalidate()
        regenerateMaskPreview()
    }

    fun updateMaskContainer(updated: MaskContainer) {
        _maskContainers.value = _maskContainers.value.map {
            if (it.id == updated.id) updated else it
        }
        regenerateMaskPreview()
    }

    /** Add a sub-mask of [type] to [containerId], combined with [combineMode]. */
    fun addSubMask(
        containerId: String,
        type: MaskType,
        combineMode: MaskCombineMode = MaskCombineMode.ADDITIVE
    ) {
        _maskContainers.value = _maskContainers.value.map { container ->
            if (container.id == containerId) {
                container.copy(
                    subMasks = container.subMasks + SubMask(
                        id = newId(),
                        type = type,
                        combineMode = combineMode,
                        name = maskTypeName(type),
                        params = MaskParams.defaultFor(type)
                    )
                )
            } else container
        }
        regenerateMaskPreview()
    }

    fun removeSubMask(containerId: String, subMaskId: String) {
        _maskContainers.value = _maskContainers.value.map { container ->
            if (container.id == containerId) {
                container.copy(subMasks = container.subMasks.filterNot { it.id == subMaskId })
            } else container
        }
        maskRenderService.invalidate(subMaskId)
        regenerateMaskPreview()
    }

    /** Reorder sub-masks within a container (simple move [fromIndex] → [toIndex]). */
    fun moveSubMask(containerId: String, fromIndex: Int, toIndex: Int) {
        _maskContainers.value = _maskContainers.value.map { container ->
            if (container.id == containerId) {
                val subs = container.subMasks.toMutableList()
                if (fromIndex in subs.indices && toIndex in 0..subs.size) {
                    val moved = subs.removeAt(fromIndex)
                    val target = if (toIndex > fromIndex) toIndex - 1 else toIndex
                    subs.add(target.coerceIn(0, subs.size), moved)
                }
                container.copy(subMasks = subs)
            } else container
        }
        regenerateMaskPreview()
    }

    fun setShowMaskOverlay(show: Boolean) {
        _showMaskOverlay.value = show
    }

    /**
     * Re-render the red mask overlay over the current preview bitmap. Runs AI
     * inference for any SUBJECT/SKY/FOREGROUND sub-masks; debounced so rapid
     * edits don't queue up work.
     */
    private var maskPreviewJob: Job? = null
    fun regenerateMaskPreview() {
        maskPreviewJob?.cancel()
        maskPreviewJob = viewModelScope.launch {
            delay(80) // debounce
            val source = previewSourceBitmap ?: _originalBitmap.value ?: return@launch
            val containers = _maskContainers.value
            if (containers.isEmpty() || containers.all { !it.visible }) {
                _maskPreviewBitmap.value = null
                return@launch
            }
            _isAnalyzingMask.value = true
            try {
                _maskPreviewBitmap.value =
                    maskRenderService.renderMaskOverlay(containers, source)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e("EditorVM", "regenerateMaskPreview failed", e)
            } finally {
                _isAnalyzingMask.value = false
            }
        }
    }

    /** Apply all mask adjustments to the current preview bitmap and return it. */
    suspend fun applyMasksToPreview(): Bitmap? {
        val source = previewSourceBitmap ?: _originalBitmap.value ?: return null
        val containers = _maskContainers.value
        if (containers.isEmpty()) return source
        return try {
            maskRenderService.applyMasks(containers, source)
        } catch (e: Throwable) {
            Log.e("EditorVM", "applyMasksToPreview failed", e)
            source
        }
    }

    private fun maskTypeName(type: MaskType): String = when (type) {
        MaskType.SUBJECT -> "Subject"
        MaskType.SKY -> "Sky"
        MaskType.FOREGROUND -> "Foreground"
        MaskType.LINEAR -> "Linear"
        MaskType.RADIAL -> "Radial"
        MaskType.BRUSH -> "Brush"
        MaskType.COLOR_RANGE -> "Color Range"
        MaskType.LUMINANCE_RANGE -> "Luminance"
        MaskType.WHOLE_IMAGE -> "Whole Image"
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
                val img = _imageModel.value
                if (img == null) {
                    showSnackbar("导出失败：图片信息丢失")
                    return@launch
                }
                // Use _originalBitmap for full-quality export, not the downscaled preview.
                // Apply the pipeline to the original to get the full-resolution result.
                val originalBitmap = _originalBitmap.value
                if (originalBitmap == null || originalBitmap.isRecycled) {
                    showSnackbar("导出失败：无法加载原始图片")
                    return@launch
                }

                // S2 修复: 导出前检查内存,超大图导出可能 OOM
                val pixelCount = originalBitmap.width.toLong() * originalBitmap.height.toLong()
                if (pixelCount > MAX_BITMAP_PIXELS) {
                    Log.w(TAG, "Export: image ${pixelCount}px exceeds safe limit, may OOM")
                }
                if (!MemoryGuard.canAllocateBitmap(pixelCount * 4 * 5)) {
                    // 需要约 5x 像素字节的内存 (原图 + float数组 + 结果)
                    Log.w(TAG, "Export: low memory, triggering GC")
                    MemoryGuard.emergencyGC()
                }

                var pipelineBitmap = pipelineService.applyPipeline(originalBitmap, _params.value, forceFullResolution = true)
                // C1 修复: 导出时必须应用蒙版/局部调整,否则用户所有蒙版调整
                // (主体/天空/画笔等)不会出现在导出图像中
                val containers = _maskContainers.value
                val finalBitmap = if (containers.isNotEmpty()) {
                    try {
                        val masked = maskRenderService.applyMasks(containers, pipelineBitmap)
                        // 蒙版生成了新 bitmap，回收旧的管线结果
                        if (masked !== pipelineBitmap) {
                            pipelineBitmap.recycle()
                        }
                        masked
                    } catch (e: Throwable) {
                        Log.e("EditorVM", "applyMasks during export failed, exporting pipeline-only result", e)
                        pipelineBitmap
                    }
                } else {
                    pipelineBitmap
                }
                val settingsWithExif = settings.copy(sourceExifPath = img.imagePath)
                // 通过 ColorScienceBridge 获取显示变换函数（用于导出时色彩空间转换）
                val colorScienceAvailable = try {
                    colorScienceBridge.getDisplayTransform(
                        mode = ColorScience.ACES20,
                        peakLuminance = 100f
                    )
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    false
                }
                val result = exportService.exportImage(img.imagePath, settingsWithExif, finalBitmap)
                _lastExportResult.value = result

                // P3 修复: 导出完成后回收传给 ExportService 的 bitmap
                // ExportService 内部不回收外部传入的 processedBitmap
                if (!finalBitmap.isRecycled) {
                    finalBitmap.recycle()
                }

                // P3 修复: 导出结果反馈给用户
                when (result) {
                    is ExportService.ExportResult.Success -> {
                        showSnackbar("导出成功：${result.filePath}")
                    }
                    is ExportService.ExportResult.Error -> {
                        showSnackbar("导出失败：${result.message}")
                    }
                }
                if (colorScienceAvailable) {
                    Log.d(TAG, "Color science bridge available for export")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                android.util.Log.e("EditorVM", "Export failed", e)
                showSnackbar("导出失败：${e.message ?: "未知错误"}")
            }
        }
    }

    // ================================================================
    // P2-5 Batch export preset sync (批量导出预设同步)
    // ================================================================

    /**
     * 将 [params] 应用到所有 [imageIds] 指定的图片。EditorViewModel 基于单图，
     * [com.alcedo.studio.data.model.ImageModel] 不含 PipelineParams 字段，
     * 因此参数缓存到 [_batchParamsCache]，由 [batchExport] 在导出时统一应用。
     */
    fun applyParamsToBatch(params: PipelineParams, imageIds: List<Long>) {
        viewModelScope.launch {
            val updated = _batchParamsCache.value.toMutableMap()
            imageIds.forEach { id -> updated[id] = params }
            _batchParamsCache.value = updated
        }
    }

    /**
     * 批量导出：对每张选中图片解码、应用管线（使用 [_batchParamsCache] 中的参数，
     * 缺省回退到当前编辑参数 [_params]），然后导出。
     */
    fun batchExport(imageIds: List<Long>, settings: ExportSettings) {
        viewModelScope.launch {
            imageIds.forEach { id ->
                val image = imageRepository.getImage(id) ?: return@forEach
                val params = _batchParamsCache.value[id] ?: _params.value
                withContext(Dispatchers.IO) {
                    val bitmap = BitmapDecoder.decodeBitmap(AppModule.context, image.imagePath)
                    val processed = if (bitmap != null) {
                        val out = pipelineService.applyPipeline(bitmap, params)
                        bitmap.recycle()
                        out
                    } else {
                        null
                    }
                    exportService.exportImage(image.imagePath, settings, processed)
                }
            }
        }
    }

    fun exportBatch(items: List<ExportService.ExportBatchItem>, settings: ExportSettings) {
        viewModelScope.launch {
            try {
                exportService.exportBatch(items, settings)
            } catch (e: CancellationException) {
                throw e
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
            } catch (e: CancellationException) {
                throw e
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
        // S2 修复: 取消所有协程任务,避免 ViewModel 销毁后泄漏
        previewJob?.cancel()
        previewJob = null
        scopeJob?.cancel()
        scopeJob = null
        stopAutoSave()
        releasePipelineSnapshot()
        // 不 recycle — 避免配置变更期间 use-after-recycle 崩溃
        clearBitmaps()
    }

    /**
     * S2 修复: 系统内存压力回调 — 低内存时主动释放非关键资源。
     * 由 Activity/Fragment 在 onTrimMemory/onLowMemory 中调用。
     */
    fun onTrimMemory(level: Int) {
        when (level) {
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                // 中等压力: 释放预览缓存,保留原图
                Log.w(TAG, "Trim memory level $level: clearing preview cache")
                _previewBitmap.value?.let { bmp ->
                    if (!bmp.isRecycled && bmp !== previewSourceBitmap && bmp !== _originalBitmap.value) {
                        bmp.recycle()
                    }
                }
                _previewBitmap.value = null
                System.gc()
            }
            android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                // 高压力: 释放所有位图,仅保留参数
                Log.w(TAG, "Trim memory level $level: releasing all bitmaps")
                releasePipelineSnapshot()
                _originalBitmap.value?.let { bmp ->
                    if (!bmp.isRecycled) bmp.recycle()
                }
                _originalBitmap.value = null
                _previewBitmap.value?.let { bmp ->
                    if (!bmp.isRecycled) bmp.recycle()
                }
                _previewBitmap.value = null
                previewSourceBitmap = null
                MemoryGuard.emergencyGC()
            }
        }
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
    INSPECTOR({ editorPanelInspector }),
    LENS_CORRECTION({ editorPanelLensCorrection }),
    MASKS({ editorPanelMasks }),
    PRESETS({ presetTitle })
}

enum class ScopeType(val labelKey: StringResources.() -> String) {
    HISTOGRAM({ editorHistogram }),
    WAVEFORM({ editorWaveform }),
    VECTORSCOPE({ editorVectorscope }),
    CHROMATICITY({ editorChromaticity })
}
