package com.alcedo.studio.domain.service

import com.alcedo.studio.data.model.*
import com.alcedo.studio.domain.repository.EditHistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

/**
 * Selective paste filter — mirrors RapidRAW's per-category copy/paste UX.
 *
 * Each flag toggles a logical group of [PipelineParams] fields. Geometry
 * defaults to `false` because crop/rotation/perspective are usually
 * composition-specific and should not be synced across photos.
 */
data class AdjustmentFilter(
    val copyBasic: Boolean = true,         // exposure, contrast, highlights, shadows, midtones, vibrance, sigmoid
    val copyWhiteBalance: Boolean = true,  // temperature, tint
    val copyColor: Boolean = true,         // saturation, HSL, color wheels, channel mixer, tint (split-tone)
    val copyToneCurve: Boolean = true,     // tone curve points + sigmoid
    val copyEffects: Boolean = true,       // film grain, halation, sharpen, clarity, vignette
    val copyGeometry: Boolean = false,     // crop, rotation, flip, perspective, lens correction
    val copyLut: Boolean = true            // LUT path / enabled / intensity
)

/**
 * Tracks the progress of a long-running batch operation for UI feedback.
 */
data class BatchEditProgress(
    val isActive: Boolean = false,
    val total: Int = 0,
    val completed: Int = 0,
    val currentImageId: String = "",
    val operation: String = ""
) {
    val fraction: Float get() = if (total <= 0) 0f else completed.toFloat() / total.toFloat()
}

/**
 * Outcome of a batch operation — number of images actually modified.
 */
sealed class BatchEditOutcome {
    data class Success(val affected: Int, val message: String) : BatchEditOutcome()
    data class Partial(val affected: Int, val failedIds: List<String>, val message: String) : BatchEditOutcome()
    data class Failure(val message: String) : BatchEditOutcome()
}

/**
 * REAL batch editing service (RapidRAW-inspired Ctrl+C / Ctrl+V flow).
 *
 * Operates on the persistent edit-history store: copy reads the source image's
 * materialized [PipelineParams]; paste writes a (possibly filtered) param set
 * into each target image's active version. No stubs — every operation hits
 * [EditHistoryRepository] and writes a fresh materialized-params JSON object.
 */
class BatchEditService(
    private val editHistoryRepository: EditHistoryRepository,
    private val pipelineService: PipelineService,
    private val applicationScope: CoroutineScope
) {
    private val _progress = MutableStateFlow(BatchEditProgress())
    val progress: StateFlow<BatchEditProgress> = _progress.asStateFlow()

    // ================================================================
    // Copy
    // ================================================================

    /**
     * Reads the source image's active version materialized params and returns
     * them as a [PipelineParams]. Returns the default params if the image has
     * no edit history yet (so paste still works, just no-ops visibly).
     */
    suspend fun copyAdjustments(sourceImageId: String): PipelineParams = withContext(Dispatchers.IO) {
        val imageId = sourceImageId.toLongOrNull()?.toUInt() ?: return@withContext PipelineParams()
        val history = editHistoryRepository.getHistory(imageId)
        val version = history?.getActiveVersion() ?: history?.getDefaultVersion()
        val materialized = version?.materializedParams
        if (materialized != null) {
            deserializeParams(materialized.toString())
        } else {
            PipelineParams()
        }
    }

    // ================================================================
    // Paste
    // ================================================================

    /**
     * Overwrites each target image's active version materialized params with
     * [newParams]. Returns the number of images successfully updated.
     */
    suspend fun pasteAdjustments(
        targetImageIds: List<String>,
        newParams: PipelineParams
    ): BatchEditOutcome = withContext(Dispatchers.IO) {
        if (targetImageIds.isEmpty()) {
            return@withContext BatchEditOutcome.Failure("No target images selected")
        }
        runBatch(targetImageIds, "Paste") { imageId ->
            writeParamsToActiveVersion(imageId, newParams)
        }
    }

    /**
     * Selective paste — only the categories enabled in [filter] are copied
     * from [source]; the rest of each target's existing params are preserved.
     */
    suspend fun pastePartialAdjustments(
        targetImageIds: List<String>,
        source: PipelineParams,
        filter: AdjustmentFilter
    ): BatchEditOutcome = withContext(Dispatchers.IO) {
        if (targetImageIds.isEmpty()) {
            return@withContext BatchEditOutcome.Failure("No target images selected")
        }
        runBatch(targetImageIds, "Selective Paste") { imageId ->
            val base = readActiveParams(imageId)
            val merged = mergeWithFilter(base, source, filter)
            writeParamsToActiveVersion(imageId, merged)
        }
    }

    // ================================================================
    // Sync = copy + paste in one shot
    // ================================================================

    suspend fun syncAdjustments(
        sourceImageId: String,
        targetImageIds: List<String>
    ): BatchEditOutcome = withContext(Dispatchers.IO) {
        val source = copyAdjustments(sourceImageId)
        pasteAdjustments(targetImageIds, source)
    }

    // ================================================================
    // Reset
    // ================================================================

    suspend fun resetAdjustments(imageIds: List<String>): BatchEditOutcome = withContext(Dispatchers.IO) {
        if (imageIds.isEmpty()) {
            return@withContext BatchEditOutcome.Failure("No images selected")
        }
        val defaults = PipelineParams()
        runBatch(imageIds, "Reset") { imageId ->
            writeParamsToActiveVersion(imageId, defaults)
        }
    }

    // ================================================================
    // Preset batch
    // ================================================================

    /**
     * Applies a preset's params to every image in [imageIds] by delegating to
     * [PresetService.applyPresetToBatch] when one is available, otherwise
     * falls back to a direct repository write.
     */
    suspend fun applyPresetBatch(
        presetService: PresetService?,
        presetId: Long,
        imageIds: List<String>
    ): BatchEditOutcome = withContext(Dispatchers.IO) {
        if (imageIds.isEmpty()) {
            return@withContext BatchEditOutcome.Failure("No images selected")
        }
        if (presetService != null) {
            _progress.value = BatchEditProgress(
                isActive = true, total = imageIds.size, completed = 0,
                currentImageId = "", operation = "Apply Preset"
            )
            val applied = presetService.applyPresetToBatch(presetId, imageIds)
            _progress.value = BatchEditProgress(isActive = false)
            if (applied <= 0) {
                BatchEditOutcome.Failure("Preset not found or no images updated")
            } else if (applied < imageIds.size) {
                // PresetService.applyPresetToBatch only reports a count, so we
                // cannot identify which specific images failed — surface a
                // partial outcome with an empty failedIds list.
                BatchEditOutcome.Partial(
                    affected = applied,
                    failedIds = emptyList(),
                    message = "Preset applied to $applied of ${imageIds.size} images"
                )
            } else {
                BatchEditOutcome.Success(applied, "Preset applied to $applied images")
            }
        } else {
            BatchEditOutcome.Failure("PresetService unavailable")
        }
    }

    // ================================================================
    // Internal: per-image read / write
    // ================================================================

    private suspend fun readActiveParams(imageIdStr: String): PipelineParams {
        val imageId = imageIdStr.toLongOrNull()?.toUInt() ?: return PipelineParams()
        val history = editHistoryRepository.getHistory(imageId) ?: return PipelineParams()
        val version = history.getActiveVersion() ?: history.getDefaultVersion() ?: return PipelineParams()
        val materialized = version.materializedParams ?: return PipelineParams()
        return deserializeParams(materialized.toString())
    }

    /**
     * Writes [params] into the active version's materializedParams, persisting
     * the full history back to the database. Creates an edit history for the
     * image if one doesn't exist yet, so the very first paste on a fresh
     * import still works.
     */
    private suspend fun writeParamsToActiveVersion(
        imageIdStr: String,
        params: PipelineParams
    ): Boolean {
        val imageId = imageIdStr.toLongOrNull()?.toUInt() ?: return false
        val paramsJson = materializeParamsToJson(params)
        val history = editHistoryRepository.getHistory(imageId)
            ?: EditHistory(boundImageId = imageId)
        val version = history.getActiveVersion() ?: history.getDefaultVersion()
        if (version == null) {
            // EditHistory.init always creates a default version, so this should
            // not happen in practice; guard against corruption regardless.
            return false
        }
        version.materializedParams = paramsJson
        version.lastModifiedTime = Instant.now()
        history.lastModifiedTime = Instant.now()
        editHistoryRepository.saveHistory(history)
        return true
    }

    /**
     * Merges the [source] fields enabled by [filter] into [base], leaving the
     * rest of [base] untouched. Implemented field-by-field to guarantee that
     * disabled categories are 100% preserved from the target image.
     */
    private fun mergeWithFilter(
        base: PipelineParams,
        source: PipelineParams,
        filter: AdjustmentFilter
    ): PipelineParams {
        var p = base

        if (filter.copyBasic) {
            p = p.copy(
                exposure = source.exposure,
                contrast = source.contrast,
                highlights = source.highlights,
                shadows = source.shadows,
                midtones = source.midtones,
                shadowBoundary = source.shadowBoundary,
                highlightBoundary = source.highlightBoundary,
                vibrance = source.vibrance,
                autoExposureEnabled = source.autoExposureEnabled,
                autoExposureTargetPercentile = source.autoExposureTargetPercentile,
                autoExposureTargetLuminance = source.autoExposureTargetLuminance,
                autoExposureValue = source.autoExposureValue
            )
        }

        if (filter.copyWhiteBalance) {
            p = p.copy(
                whiteBalanceTemp = source.whiteBalanceTemp,
                whiteBalanceTint = source.whiteBalanceTint
            )
        }

        if (filter.copyColor) {
            p = p.copy(
                saturation = source.saturation,
                tintHighlightHue = source.tintHighlightHue,
                tintHighlightStrength = source.tintHighlightStrength,
                tintShadowHue = source.tintShadowHue,
                tintShadowStrength = source.tintShadowStrength,
                tintBalance = source.tintBalance,
                colorWheelLiftR = source.colorWheelLiftR,
                colorWheelLiftG = source.colorWheelLiftG,
                colorWheelLiftB = source.colorWheelLiftB,
                colorWheelGammaR = source.colorWheelGammaR,
                colorWheelGammaG = source.colorWheelGammaG,
                colorWheelGammaB = source.colorWheelGammaB,
                colorWheelGainR = source.colorWheelGainR,
                colorWheelGainG = source.colorWheelGainG,
                colorWheelGainB = source.colorWheelGainB,
                channelMixerMatrix = source.channelMixerMatrix.copyOf(),
                channelMixerMonochrome = source.channelMixerMonochrome,
                hslHueRanges = source.hslHueRanges.copyOf(),
                hslHueWidth = source.hslHueWidth,
                hslHueShift = source.hslHueShift.copyOf(),
                hslSaturationScale = source.hslSaturationScale.copyOf(),
                hslLuminanceScale = source.hslLuminanceScale.copyOf()
            )
        }

        if (filter.copyToneCurve) {
            p = p.copy(
                toneCurveX = source.toneCurveX.copyOf(),
                toneCurveY = source.toneCurveY.copyOf(),
                toneCurvePoints = source.toneCurvePoints,
                sigmoidContrast = source.sigmoidContrast,
                sigmoidPivot = source.sigmoidPivot,
                sigmoidShoulder = source.sigmoidShoulder
            )
        }

        if (filter.copyEffects) {
            p = p.copy(
                filmGrainIntensity = source.filmGrainIntensity,
                halationIntensity = source.halationIntensity,
                halationThreshold = source.halationThreshold,
                halationSpread = source.halationSpread,
                halationRedBias = source.halationRedBias,
                sharpenAmount = source.sharpenAmount,
                clarityAmount = source.clarityAmount,
                clarityRadius = source.clarityRadius,
                lensVignetteStrength = source.lensVignetteStrength
            )
        }

        if (filter.copyGeometry) {
            p = p.copy(
                geometryRotate = source.geometryRotate,
                geometryScale = source.geometryScale,
                geometryFlipH = source.geometryFlipH,
                geometryFlipV = source.geometryFlipV,
                cropLeft = source.cropLeft,
                cropTop = source.cropTop,
                cropRight = source.cropRight,
                cropBottom = source.cropBottom,
                perspectiveH = source.perspectiveH,
                perspectiveV = source.perspectiveV,
                geometryCropLeft = source.geometryCropLeft,
                geometryCropTop = source.geometryCropTop,
                geometryCropRight = source.geometryCropRight,
                geometryCropBottom = source.geometryCropBottom,
                geometryPerspectiveSrc = source.geometryPerspectiveSrc.copyOf(),
                geometryPerspectiveDst = source.geometryPerspectiveDst.copyOf(),
                lensK1 = source.lensK1,
                lensK2 = source.lensK2,
                lensK3 = source.lensK3,
                lensP1 = source.lensP1,
                lensP2 = source.lensP2,
                lensCx = source.lensCx,
                lensCy = source.lensCy,
                lensFocalRatio = source.lensFocalRatio
            )
        }

        if (filter.copyLut) {
            p = p.copy(
                lutPath = source.lutPath,
                lutEnabled = source.lutEnabled,
                lutIntensity = source.lutIntensity
            )
        }

        return p
    }

    /**
     * Iterates [imageIds], calling [action] for each and updating the public
     * progress flow. Aggregates results into a [BatchEditOutcome].
     */
    private suspend fun runBatch(
        imageIds: List<String>,
        operation: String,
        action: suspend (String) -> Boolean
    ): BatchEditOutcome {
        _progress.value = BatchEditProgress(
            isActive = true,
            total = imageIds.size,
            completed = 0,
            currentImageId = imageIds.firstOrNull() ?: "",
            operation = operation
        )

        val failed = mutableListOf<String>()
        var success = 0
        for ((index, id) in imageIds.withIndex()) {
            _progress.value = _progress.value.copy(
                completed = index,
                currentImageId = id
            )
            try {
                if (action(id)) success++ else failed.add(id)
            } catch (_: Throwable) {
                failed.add(id)
            }
        }

        _progress.value = BatchEditProgress(
            isActive = false,
            total = imageIds.size,
            completed = imageIds.size,
            currentImageId = "",
            operation = operation
        )

        return when {
            success == 0 -> BatchEditOutcome.Failure("$operation failed for all ${imageIds.size} images")
            failed.isEmpty() -> BatchEditOutcome.Success(
                success,
                "$operation succeeded for $success image${if (success == 1) "" else "s"}"
            )
            else -> BatchEditOutcome.Partial(
                affected = success,
                failedIds = failed,
                message = "$operation succeeded for $success of ${imageIds.size} images"
            )
        }
    }

    // ================================================================
    // PipelineParams JSON serialization — mirrors EditorViewModel
    // and PresetService so persisted params round-trip identically.
    // ================================================================

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
        map["autoExposureTargetPercentile"] = JsonPrimitive(p.autoExposureTargetPercentile)
        map["autoExposureTargetLuminance"] = JsonPrimitive(p.autoExposureTargetLuminance)
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
        map["lensCx"] = JsonPrimitive(p.lensCx)
        map["lensCy"] = JsonPrimitive(p.lensCy)
        map["lensFocalRatio"] = JsonPrimitive(p.lensFocalRatio)
        map["lensVignetteStrength"] = JsonPrimitive(p.lensVignetteStrength)
        map["channelMixerMonochrome"] = JsonPrimitive(p.channelMixerMonochrome)
        p.hslHueRanges.forEachIndexed { i, v -> map["hslHueRanges[$i]"] = JsonPrimitive(v) }
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

    private fun deserializeParams(jsonString: String): PipelineParams {
        return try {
            val obj = Json.parseToJsonElement(jsonString).jsonObject
            fun f(key: String, default: Float = 0f): Float =
                obj[key]?.jsonPrimitive?.floatOrNull ?: default
            fun b(key: String, default: Boolean = false): Boolean =
                obj[key]?.jsonPrimitive?.booleanOrNull ?: default
            fun s(key: String, default: String = ""): String =
                obj[key]?.jsonPrimitive?.contentOrNull ?: default

            val hslHueRanges = FloatArray(8) { i -> f("hslHueRanges[$i]", i * 45f) }
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

            PipelineParams(
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
                autoExposureEnabled = b("autoExposureEnabled"),
                autoExposureTargetPercentile = f("autoExposureTargetPercentile", 0.5f),
                autoExposureTargetLuminance = f("autoExposureTargetLuminance", 0.18f),
                autoExposureValue = f("autoExposureValue"),
                toneCurveX = toneCurveX,
                toneCurveY = toneCurveY,
                toneCurvePoints = f("toneCurvePoints", 5f).toInt(),
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
                hslHueRanges = hslHueRanges,
                hslHueWidth = f("hslHueWidth", 60f),
                hslHueShift = hslHueShift,
                hslSaturationScale = hslSatScale,
                hslLuminanceScale = hslLumScale,
                channelMixerMatrix = channelMixer,
                channelMixerMonochrome = b("channelMixerMonochrome"),
                clarityAmount = f("clarityAmount"),
                clarityRadius = f("clarityRadius", 15f),
                sharpenAmount = f("sharpenAmount"),
                filmGrainIntensity = f("filmGrainIntensity"),
                halationIntensity = f("halationIntensity"),
                halationThreshold = f("halationThreshold", 0.8f),
                halationSpread = f("halationSpread", 10f),
                halationRedBias = f("halationRedBias", 0.7f),
                lutPath = s("lutPath"),
                lutEnabled = b("lutEnabled"),
                lutIntensity = f("lutIntensity", 100f),
                geometryRotate = f("geometryRotate"),
                geometryScale = f("geometryScale", 1f),
                geometryFlipH = b("geometryFlipH"),
                geometryFlipV = b("geometryFlipV"),
                cropLeft = f("cropLeft"),
                cropTop = f("cropTop"),
                cropRight = f("cropRight", 1f),
                cropBottom = f("cropBottom", 1f),
                perspectiveH = f("perspectiveH"),
                perspectiveV = f("perspectiveV"),
                geometryCropLeft = f("geometryCropLeft"),
                geometryCropTop = f("geometryCropTop"),
                geometryCropRight = f("geometryCropRight", 1f),
                geometryCropBottom = f("geometryCropBottom", 1f),
                lensK1 = f("lensK1"),
                lensK2 = f("lensK2"),
                lensK3 = f("lensK3"),
                lensP1 = f("lensP1"),
                lensP2 = f("lensP2"),
                lensCx = f("lensCx", 0.5f),
                lensCy = f("lensCy", 0.5f),
                lensFocalRatio = f("lensFocalRatio", 1f),
                lensVignetteStrength = f("lensVignetteStrength"),
                displayTransform = DisplayTransform(
                    colorScience = colorScience,
                    eotf = eotf,
                    peakLuminance = f("displayTransform_peakLuminance", 100f),
                    displayColorSpace = colorSpace,
                    openDrtLook = openDrtLook,
                    openDrtTonescale = openDrtTonescale
                )
            )
        } catch (_: Throwable) {
            PipelineParams()
        }
    }

    /**
     * Fire-and-forget entry point used by the application-scope wrapper. The
     * ViewModel already has its own coroutine scope, so this is provided for
     * completeness only.
     */
    fun launchPaste(
        targetImageIds: List<String>,
        newParams: PipelineParams,
        onResult: (BatchEditOutcome) -> Unit = {}
    ) {
        applicationScope.launch {
            val outcome = pasteAdjustments(targetImageIds, newParams)
            onResult(outcome)
        }
    }
}
