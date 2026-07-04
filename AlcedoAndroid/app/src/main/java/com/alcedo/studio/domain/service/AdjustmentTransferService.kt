package com.alcedo.studio.domain.service

import com.alcedo.studio.data.model.*
import com.alcedo.studio.domain.repository.EditHistoryRepository
import com.alcedo.studio.domain.repository.ImageRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

class AdjustmentTransferService(
    private val imageRepository: ImageRepository,
    private val editHistoryRepository: EditHistoryRepository,
    private val historyMgmtService: HistoryMgmtService
) {
    companion object {
        private const val TAG = "AdjustmentTransferService"
        const val SCHEMA_ID = "alcedo.adjustment_transfer.v1"
        const val SCHEMA_VERSION = 1
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val transferStates = ConcurrentHashMap<String, TransferState>()

    private val _transferProgress = MutableStateFlow<Map<String, TransferProgress>>(emptyMap())
    val transferProgress: StateFlow<Map<String, TransferProgress>> = _transferProgress.asStateFlow()

    data class TransferProgress(
        val transferId: String,
        val totalTargets: Int,
        val completedTargets: Int = 0,
        val currentTargetName: String = "",
        val isPreview: Boolean = false
    )

    enum class TransferState {
        IDLE, PREVIEWING, APPLYING, COMPLETED, FAILED, CANCELLED
    }

    // ================================================================
    // v1 Schema Data Classes
    // ================================================================

    enum class AdjustmentCategory {
        GEOMETRY, TONE, COLOR, COLOR_TEMP, DETAIL, OUTPUT_TRANSFORM
    }

    @Serializable
    data class AdjustmentTransferV1(
        val schema: String = SCHEMA_ID,
        val version: Int = SCHEMA_VERSION,
        val timestamp: String = Instant.now().toString(),
        val source: SourceInfo = SourceInfo(),
        val adjustments: Map<String, JsonObject> = emptyMap()
    )

    @Serializable
    data class SourceInfo(
        val file_id: String = "",
        val file_name: String = ""
    )

    // ================================================================
    // Apply Mode
    // ================================================================

    enum class ApplyMode {
        PASTE, MERGE
    }

    // ================================================================
    // Capture: Extract adjustments by category from PipelineParams
    // ================================================================

    fun captureAdjustments(
        pipelineParams: PipelineParams,
        fileId: String = "",
        fileName: String = ""
    ): AdjustmentTransferV1 {
        val adjustments = mutableMapOf<String, JsonObject>()

        adjustments[AdjustmentCategory.GEOMETRY.name.lowercase()] = buildJsonObject {
            put("crop", buildJsonObject {
                put("left", pipelineParams.geometryCropLeft)
                put("top", pipelineParams.geometryCropTop)
                put("right", pipelineParams.geometryCropRight)
                put("bottom", pipelineParams.geometryCropBottom)
            })
            put("rotation", pipelineParams.geometryRotate)
            put("lens_correction", buildJsonObject {
                put("k1", pipelineParams.lensK1)
                put("k2", pipelineParams.lensK2)
                put("k3", pipelineParams.lensK3)
                put("p1", pipelineParams.lensP1)
                put("p2", pipelineParams.lensP2)
                put("cx", pipelineParams.lensCx)
                put("cy", pipelineParams.lensCy)
                put("focal_ratio", pipelineParams.lensFocalRatio)
                put("vignette_strength", pipelineParams.lensVignetteStrength)
            })
            put("geometry", buildJsonObject {
                put("scale", pipelineParams.geometryScale)
                put("perspective_src", pipelineParams.geometryPerspectiveSrc.toList().jsonArray)
                put("perspective_dst", pipelineParams.geometryPerspectiveDst.toList().jsonArray)
            })
        }

        adjustments[AdjustmentCategory.TONE.name.lowercase()] = buildJsonObject {
            put("exposure", pipelineParams.exposure)
            put("contrast", pipelineParams.contrast)
            put("highlights", pipelineParams.highlights)
            put("shadows", pipelineParams.shadows)
            put("whites", pipelineParams.midtones)
            put("blacks", 0f)
            put("tone_curve", buildJsonObject {
                put("x", pipelineParams.toneCurveX.toList().jsonArray)
                put("y", pipelineParams.toneCurveY.toList().jsonArray)
                put("points", pipelineParams.toneCurvePoints)
                put("sigmoid_contrast", pipelineParams.sigmoidContrast)
                put("sigmoid_pivot", pipelineParams.sigmoidPivot)
                put("sigmoid_shoulder", pipelineParams.sigmoidShoulder)
            })
            put("vibrance", pipelineParams.vibrance)
        }

        adjustments[AdjustmentCategory.COLOR.name.lowercase()] = buildJsonObject {
            put("saturation", pipelineParams.saturation)
            put("hsl", buildJsonObject {
                put("hue_ranges", pipelineParams.hslHueRanges.toList().jsonArray)
                put("hue_width", pipelineParams.hslHueWidth)
                put("hue_shift", pipelineParams.hslHueShift.toList().jsonArray)
                put("saturation_scale", pipelineParams.hslSaturationScale.toList().jsonArray)
                put("luminance_scale", pipelineParams.hslLuminanceScale.toList().jsonArray)
            })
            put("color_wheel", buildJsonObject {
                put("lift", buildJsonObject {
                    put("r", pipelineParams.colorWheelLiftR)
                    put("g", pipelineParams.colorWheelLiftG)
                    put("b", pipelineParams.colorWheelLiftB)
                })
                put("gamma", buildJsonObject {
                    put("r", pipelineParams.colorWheelGammaR)
                    put("g", pipelineParams.colorWheelGammaG)
                    put("b", pipelineParams.colorWheelGammaB)
                })
                put("gain", buildJsonObject {
                    put("r", pipelineParams.colorWheelGainR)
                    put("g", pipelineParams.colorWheelGainG)
                    put("b", pipelineParams.colorWheelGainB)
                })
            })
            put("channel_mixer", buildJsonObject {
                put("matrix", pipelineParams.channelMixerMatrix.toList().jsonArray)
                put("monochrome", pipelineParams.channelMixerMonochrome)
            })
            put("tint", buildJsonObject {
                put("highlight_hue", pipelineParams.tintHighlightHue)
                put("highlight_strength", pipelineParams.tintHighlightStrength)
                put("shadow_hue", pipelineParams.tintShadowHue)
                put("shadow_strength", pipelineParams.tintShadowStrength)
                put("balance", pipelineParams.tintBalance)
            })
        }

        adjustments[AdjustmentCategory.COLOR_TEMP.name.lowercase()] = buildJsonObject {
            put("white_balance", buildJsonObject {
                put("temperature", pipelineParams.whiteBalanceTemp)
                put("tint", pipelineParams.whiteBalanceTint)
            })
        }

        adjustments[AdjustmentCategory.DETAIL.name.lowercase()] = buildJsonObject {
            put("clarity", buildJsonObject {
                put("amount", pipelineParams.clarityAmount)
                put("radius", pipelineParams.clarityRadius)
            })
            put("sharpen", buildJsonObject {
                put("amount", pipelineParams.sharpenAmount)
            })
        }

        adjustments[AdjustmentCategory.OUTPUT_TRANSFORM.name.lowercase()] = buildJsonObject {
            put("cst", pipelineParams.displayTransform.colorScience.name)
            put("lmt", buildJsonObject {
                put("lut_path", pipelineParams.lutPath)
                put("lut_enabled", pipelineParams.lutEnabled)
            })
            put("odt", buildJsonObject {
                put("eotf", pipelineParams.displayTransform.eotf.name)
                put("peak_luminance", pipelineParams.displayTransform.peakLuminance)
                put("display_color_space", pipelineParams.displayTransform.displayColorSpace.name)
            })
            put("film_grain", buildJsonObject {
                put("intensity", pipelineParams.filmGrainIntensity)
            })
            put("halation", buildJsonObject {
                put("intensity", pipelineParams.halationIntensity)
                put("threshold", pipelineParams.halationThreshold)
                put("spread", pipelineParams.halationSpread)
                put("red_bias", pipelineParams.halationRedBias)
            })
        }

        return AdjustmentTransferV1(
            source = SourceInfo(file_id = fileId, file_name = fileName),
            adjustments = adjustments
        )
    }

    // ================================================================
    // Export to JSON
    // ================================================================

    fun exportToJson(transfer: AdjustmentTransferV1): String {
        return json.encodeToString(
            AdjustmentTransferV1.serializer(),
            transfer
        )
    }

    // ================================================================
    // Import from JSON
    // ================================================================

    fun importFromJson(jsonString: String): Result<AdjustmentTransferV1> {
        return try {
            val transfer = json.decodeFromString<AdjustmentTransferV1>(jsonString)
            if (transfer.schema != SCHEMA_ID) {
                return Result.failure(IllegalArgumentException("Unsupported schema: ${transfer.schema}"))
            }
            if (transfer.version > SCHEMA_VERSION) {
                return Result.failure(IllegalArgumentException("Unsupported version: ${transfer.version}"))
            }
            Result.success(transfer)
        } catch (e: SerializationException) {
            Result.failure(e)
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        }
    }

    // ================================================================
    // Parse adjustments back into PipelineParams
    // ================================================================

    fun applyTransferToParams(
        base: PipelineParams,
        transfer: AdjustmentTransferV1,
        selectedCategories: Set<AdjustmentCategory> = AdjustmentCategory.entries.toSet()
    ): PipelineParams {
        var params = base

        if (AdjustmentCategory.GEOMETRY in selectedCategories) {
            transfer.adjustments[AdjustmentCategory.GEOMETRY.name.lowercase()]?.let { geo ->
                params = params.copy(
                    geometryCropLeft = geo["crop"]?.jsonObject?.get("left")?.jsonPrimitive?.floatOrNull
                        ?: params.geometryCropLeft,
                    geometryCropTop = geo["crop"]?.jsonObject?.get("top")?.jsonPrimitive?.floatOrNull
                        ?: params.geometryCropTop,
                    geometryCropRight = geo["crop"]?.jsonObject?.get("right")?.jsonPrimitive?.floatOrNull
                        ?: params.geometryCropRight,
                    geometryCropBottom = geo["crop"]?.jsonObject?.get("bottom")?.jsonPrimitive?.floatOrNull
                        ?: params.geometryCropBottom,
                    geometryRotate = geo["rotation"]?.jsonPrimitive?.floatOrNull ?: params.geometryRotate,
                    lensK1 = geo["lens_correction"]?.jsonObject?.get("k1")?.jsonPrimitive?.floatOrNull
                        ?: params.lensK1,
                    lensK2 = geo["lens_correction"]?.jsonObject?.get("k2")?.jsonPrimitive?.floatOrNull
                        ?: params.lensK2,
                    lensK3 = geo["lens_correction"]?.jsonObject?.get("k3")?.jsonPrimitive?.floatOrNull
                        ?: params.lensK3,
                    lensP1 = geo["lens_correction"]?.jsonObject?.get("p1")?.jsonPrimitive?.floatOrNull
                        ?: params.lensP1,
                    lensP2 = geo["lens_correction"]?.jsonObject?.get("p2")?.jsonPrimitive?.floatOrNull
                        ?: params.lensP2,
                    lensCx = geo["lens_correction"]?.jsonObject?.get("cx")?.jsonPrimitive?.floatOrNull
                        ?: params.lensCx,
                    lensCy = geo["lens_correction"]?.jsonObject?.get("cy")?.jsonPrimitive?.floatOrNull
                        ?: params.lensCy,
                    lensFocalRatio = geo["lens_correction"]?.jsonObject?.get("focal_ratio")?.jsonPrimitive?.floatOrNull
                        ?: params.lensFocalRatio,
                    lensVignetteStrength = geo["lens_correction"]?.jsonObject?.get("vignette_strength")?.jsonPrimitive?.floatOrNull
                        ?: params.lensVignetteStrength,
                    geometryScale = geo["geometry"]?.jsonObject?.get("scale")?.jsonPrimitive?.floatOrNull
                        ?: params.geometryScale
                )
            }
        }

        if (AdjustmentCategory.TONE in selectedCategories) {
            transfer.adjustments[AdjustmentCategory.TONE.name.lowercase()]?.let { tone ->
                params = params.copy(
                    exposure = tone["exposure"]?.jsonPrimitive?.floatOrNull ?: params.exposure,
                    contrast = tone["contrast"]?.jsonPrimitive?.floatOrNull ?: params.contrast,
                    highlights = tone["highlights"]?.jsonPrimitive?.floatOrNull ?: params.highlights,
                    shadows = tone["shadows"]?.jsonPrimitive?.floatOrNull ?: params.shadows,
                    midtones = tone["whites"]?.jsonPrimitive?.floatOrNull ?: params.midtones,
                    vibrance = tone["vibrance"]?.jsonPrimitive?.floatOrNull ?: params.vibrance
                )
                tone["tone_curve"]?.jsonObject?.let { tc ->
                    params = params.copy(
                        toneCurveX = tc["x"]?.jsonArray?.toFloatArray() ?: params.toneCurveX,
                        toneCurveY = tc["y"]?.jsonArray?.toFloatArray() ?: params.toneCurveY,
                        toneCurvePoints = tc["points"]?.jsonPrimitive?.intOrNull ?: params.toneCurvePoints,
                        sigmoidContrast = tc["sigmoid_contrast"]?.jsonPrimitive?.floatOrNull
                            ?: params.sigmoidContrast,
                        sigmoidPivot = tc["sigmoid_pivot"]?.jsonPrimitive?.floatOrNull
                            ?: params.sigmoidPivot,
                        sigmoidShoulder = tc["sigmoid_shoulder"]?.jsonPrimitive?.floatOrNull
                            ?: params.sigmoidShoulder
                    )
                }
            }
        }

        if (AdjustmentCategory.COLOR in selectedCategories) {
            transfer.adjustments[AdjustmentCategory.COLOR.name.lowercase()]?.let { color ->
                params = params.copy(
                    saturation = color["saturation"]?.jsonPrimitive?.floatOrNull ?: params.saturation
                )
                color["hsl"]?.jsonObject?.let { hsl ->
                    params = params.copy(
                        hslHueRanges = hsl["hue_ranges"]?.jsonArray?.toFloatArray() ?: params.hslHueRanges,
                        hslHueWidth = hsl["hue_width"]?.jsonPrimitive?.floatOrNull ?: params.hslHueWidth,
                        hslHueShift = hsl["hue_shift"]?.jsonArray?.toFloatArray() ?: params.hslHueShift,
                        hslSaturationScale = hsl["saturation_scale"]?.jsonArray?.toFloatArray()
                            ?: params.hslSaturationScale,
                        hslLuminanceScale = hsl["luminance_scale"]?.jsonArray?.toFloatArray()
                            ?: params.hslLuminanceScale
                    )
                }
                color["color_wheel"]?.jsonObject?.let { cw ->
                    params = params.copy(
                        colorWheelLiftR = cw["lift"]?.jsonObject?.get("r")?.jsonPrimitive?.floatOrNull
                            ?: params.colorWheelLiftR,
                        colorWheelLiftG = cw["lift"]?.jsonObject?.get("g")?.jsonPrimitive?.floatOrNull
                            ?: params.colorWheelLiftG,
                        colorWheelLiftB = cw["lift"]?.jsonObject?.get("b")?.jsonPrimitive?.floatOrNull
                            ?: params.colorWheelLiftB,
                        colorWheelGammaR = cw["gamma"]?.jsonObject?.get("r")?.jsonPrimitive?.floatOrNull
                            ?: params.colorWheelGammaR,
                        colorWheelGammaG = cw["gamma"]?.jsonObject?.get("g")?.jsonPrimitive?.floatOrNull
                            ?: params.colorWheelGammaG,
                        colorWheelGammaB = cw["gamma"]?.jsonObject?.get("b")?.jsonPrimitive?.floatOrNull
                            ?: params.colorWheelGammaB,
                        colorWheelGainR = cw["gain"]?.jsonObject?.get("r")?.jsonPrimitive?.floatOrNull
                            ?: params.colorWheelGainR,
                        colorWheelGainG = cw["gain"]?.jsonObject?.get("g")?.jsonPrimitive?.floatOrNull
                            ?: params.colorWheelGainG,
                        colorWheelGainB = cw["gain"]?.jsonObject?.get("b")?.jsonPrimitive?.floatOrNull
                            ?: params.colorWheelGainB
                    )
                }
                color["channel_mixer"]?.jsonObject?.let { cm ->
                    params = params.copy(
                        channelMixerMatrix = cm["matrix"]?.jsonArray?.toFloatArray() ?: params.channelMixerMatrix,
                        channelMixerMonochrome = cm["monochrome"]?.jsonPrimitive?.booleanOrNull
                            ?: params.channelMixerMonochrome
                    )
                }
                color["tint"]?.jsonObject?.let { tint ->
                    params = params.copy(
                        tintHighlightHue = tint["highlight_hue"]?.jsonPrimitive?.floatOrNull
                            ?: params.tintHighlightHue,
                        tintHighlightStrength = tint["highlight_strength"]?.jsonPrimitive?.floatOrNull
                            ?: params.tintHighlightStrength,
                        tintShadowHue = tint["shadow_hue"]?.jsonPrimitive?.floatOrNull
                            ?: params.tintShadowHue,
                        tintShadowStrength = tint["shadow_strength"]?.jsonPrimitive?.floatOrNull
                            ?: params.tintShadowStrength,
                        tintBalance = tint["balance"]?.jsonPrimitive?.floatOrNull ?: params.tintBalance
                    )
                }
            }
        }

        if (AdjustmentCategory.COLOR_TEMP in selectedCategories) {
            transfer.adjustments[AdjustmentCategory.COLOR_TEMP.name.lowercase()]?.let { ct ->
                ct["white_balance"]?.jsonObject?.let { wb ->
                    params = params.copy(
                        whiteBalanceTemp = wb["temperature"]?.jsonPrimitive?.floatOrNull
                            ?: params.whiteBalanceTemp,
                        whiteBalanceTint = wb["tint"]?.jsonPrimitive?.floatOrNull
                            ?: params.whiteBalanceTint
                    )
                }
            }
        }

        if (AdjustmentCategory.DETAIL in selectedCategories) {
            transfer.adjustments[AdjustmentCategory.DETAIL.name.lowercase()]?.let { detail ->
                detail["clarity"]?.jsonObject?.let { cl ->
                    params = params.copy(
                        clarityAmount = cl["amount"]?.jsonPrimitive?.floatOrNull ?: params.clarityAmount,
                        clarityRadius = cl["radius"]?.jsonPrimitive?.floatOrNull ?: params.clarityRadius
                    )
                }
                detail["sharpen"]?.jsonObject?.let { sh ->
                    params = params.copy(
                        sharpenAmount = sh["amount"]?.jsonPrimitive?.floatOrNull ?: params.sharpenAmount
                    )
                }
            }
        }

        if (AdjustmentCategory.OUTPUT_TRANSFORM in selectedCategories) {
            transfer.adjustments[AdjustmentCategory.OUTPUT_TRANSFORM.name.lowercase()]?.let { ot ->
                val cst = ot["cst"]?.jsonPrimitive?.contentOrNull
                val eotf = ot["odt"]?.jsonObject?.get("eotf")?.jsonPrimitive?.contentOrNull
                val peakLum = ot["odt"]?.jsonObject?.get("peak_luminance")?.jsonPrimitive?.floatOrNull
                val dispCs = ot["odt"]?.jsonObject?.get("display_color_space")?.jsonPrimitive?.contentOrNull

                val displayTransform = params.displayTransform.let { dt ->
                    dt.copy(
                        colorScience = cst?.let { runCatching { ColorScience.valueOf(it) }.getOrDefault(dt.colorScience) }
                            ?: dt.colorScience,
                        eotf = eotf?.let { runCatching { EOTF.valueOf(it) }.getOrDefault(dt.eotf) } ?: dt.eotf,
                        peakLuminance = peakLum ?: dt.peakLuminance,
                        displayColorSpace = dispCs?.let {
                            runCatching { ColorSpace.valueOf(it) }.getOrDefault(dt.displayColorSpace)
                        } ?: dt.displayColorSpace
                    )
                }
                params = params.copy(displayTransform = displayTransform)

                ot["lmt"]?.jsonObject?.let { lmt ->
                    params = params.copy(
                        lutPath = lmt["lut_path"]?.jsonPrimitive?.contentOrNull ?: params.lutPath,
                        lutEnabled = lmt["lut_enabled"]?.jsonPrimitive?.booleanOrNull ?: params.lutEnabled
                    )
                }
                ot["film_grain"]?.jsonObject?.let { fg ->
                    params = params.copy(
                        filmGrainIntensity = fg["intensity"]?.jsonPrimitive?.floatOrNull
                            ?: params.filmGrainIntensity
                    )
                }
                ot["halation"]?.jsonObject?.let { ha ->
                    params = params.copy(
                        halationIntensity = ha["intensity"]?.jsonPrimitive?.floatOrNull
                            ?: params.halationIntensity,
                        halationThreshold = ha["threshold"]?.jsonPrimitive?.floatOrNull
                            ?: params.halationThreshold,
                        halationSpread = ha["spread"]?.jsonPrimitive?.floatOrNull
                            ?: params.halationSpread,
                        halationRedBias = ha["red_bias"]?.jsonPrimitive?.floatOrNull
                            ?: params.halationRedBias
                    )
                }
            }
        }

        return params
    }

    // ================================================================
    // Apply with mode selection
    // ================================================================

    suspend fun applyTransfer(
        targetImageId: UInt,
        transfer: AdjustmentTransferV1,
        mode: ApplyMode = ApplyMode.PASTE,
        selectedCategories: Set<AdjustmentCategory> = AdjustmentCategory.entries.toSet()
    ): TransferResult = withContext(Dispatchers.IO) {
        val transferId = "apply_${targetImageId}_${Instant.now().toEpochMilli()}"
        transferStates[transferId] = TransferState.APPLYING

        try {
            when (mode) {
                ApplyMode.PASTE -> {
                    // PASTE mode: Apply each selected category as an individual transaction
                    for (category in selectedCategories) {
                        val operatorType = mapCategoryToOperator(category)
                        historyMgmtService.recordTransaction(
                            imageId = targetImageId,
                            operatorType = operatorType,
                            paramsBefore = JsonObject(emptyMap()),
                            paramsAfter = transfer.adjustments[category.name.lowercase()]
                                ?: JsonObject(emptyMap())
                        )
                    }
                }
                ApplyMode.MERGE -> {
                    // MERGE mode: Merge all adjustments into a single version without transactions
                    val mergedParams = buildJsonObject {
                        for ((key, value) in transfer.adjustments) {
                            if (key.uppercase() in selectedCategories.map { it.name }) {
                                put(key, value)
                            }
                        }
                    }
                    historyMgmtService.recordTransaction(
                        imageId = targetImageId,
                        operatorType = OperatorType.DISPLAY_TRANSFORM,
                        paramsBefore = JsonObject(emptyMap()),
                        paramsAfter = mergedParams
                    )
                }
            }

            transferStates[transferId] = TransferState.COMPLETED
            TransferResult(
                transferId = transferId,
                success = true,
                message = "Applied ${selectedCategories.size} categories in ${mode.name} mode"
            )
        } catch (e: Exception) {
            transferStates[transferId] = TransferState.FAILED
            TransferResult(transferId, false, "Transfer failed: ${e.message}")
        }
    }

    // ================================================================
    // Copy Adjustments from One Image to Another (legacy + v1)
    // ================================================================

    suspend fun copyAdjustments(
        sourceImageId: UInt,
        targetImageId: UInt,
        config: TransferConfig = TransferConfig()
    ): TransferResult = withContext(Dispatchers.IO) {
        val transferId = "transfer_${sourceImageId}_${targetImageId}_${Instant.now().toEpochMilli()}"
        transferStates[transferId] = TransferState.APPLYING

        try {
            val sourceHistory = editHistoryRepository.getHistory(sourceImageId)
                ?: return@withContext TransferResult(transferId, false, "Source has no edit history")

            val sourceVersion = sourceHistory.getActiveVersion()
                ?: return@withContext TransferResult(transferId, false, "Source has no active version")

            val adjustments = extractAdjustments(sourceVersion, sourceHistory)
            val filtered = filterAdjustments(adjustments, config)

            val targetHistory = editHistoryRepository.getHistory(targetImageId)
                ?: EditHistory(boundImageId = targetImageId)

            val targetVersion = targetHistory.getActiveVersion()
                ?: targetHistory.getDefaultVersion()
                ?: return@withContext TransferResult(transferId, false, "Target has no version")

            for (adjustment in filtered) {
                historyMgmtService.recordTransaction(
                    imageId = targetImageId,
                    operatorType = mapAdjustmentToOperator(adjustment),
                    paramsBefore = JsonObject(emptyMap()),
                    paramsAfter = JsonObject(emptyMap())
                )
            }

            transferStates[transferId] = TransferState.COMPLETED
            TransferResult(
                transferId = transferId,
                success = true,
                message = "Applied ${filtered.size} adjustments to target image"
            )
        } catch (e: Exception) {
            transferStates[transferId] = TransferState.FAILED
            TransferResult(transferId, false, "Transfer failed: ${e.message}")
        }
    }

    // ================================================================
    // Batch Paste to Multiple Images
    // ================================================================

    suspend fun batchTransfer(
        sourceImageId: UInt,
        targetImageIds: List<UInt>,
        config: TransferConfig = TransferConfig()
    ): BatchTransferResult = withContext(Dispatchers.IO) {
        val transferId = "batch_${sourceImageId}_${Instant.now().toEpochMilli()}"
        transferStates[transferId] = TransferState.APPLYING

        _transferProgress.value = _transferProgress.value + (transferId to TransferProgress(
            transferId = transferId,
            totalTargets = targetImageIds.size
        ))

        val results = mutableListOf<TransferResult>()
        val failed = mutableListOf<UInt>()

        for ((index, targetId) in targetImageIds.withIndex()) {
            _transferProgress.value = _transferProgress.value + (transferId to TransferProgress(
                transferId = transferId,
                totalTargets = targetImageIds.size,
                completedTargets = index,
                currentTargetName = targetId.toString()
            ))

            val result = copyAdjustments(sourceImageId, targetId, config)
            results.add(result)
            if (!result.success) failed.add(targetId)
        }

        _transferProgress.value = _transferProgress.value + (transferId to TransferProgress(
            transferId = transferId,
            totalTargets = targetImageIds.size,
            completedTargets = targetImageIds.size
        ))

        transferStates[transferId] = TransferState.COMPLETED
        BatchTransferResult(
            transferId = transferId,
            totalTargets = targetImageIds.size,
            successCount = targetImageIds.size - failed.size,
            failedTargets = failed,
            results = results
        )
    }

    // ================================================================
    // Batch Apply v1 Transfer
    // ================================================================

    suspend fun batchApplyTransfer(
        transfer: AdjustmentTransferV1,
        targetImageIds: List<UInt>,
        mode: ApplyMode = ApplyMode.PASTE,
        selectedCategories: Set<AdjustmentCategory> = AdjustmentCategory.entries.toSet()
    ): BatchTransferResult = withContext(Dispatchers.IO) {
        val transferId = "batch_apply_${Instant.now().toEpochMilli()}"
        transferStates[transferId] = TransferState.APPLYING

        _transferProgress.value = _transferProgress.value + (transferId to TransferProgress(
            transferId = transferId,
            totalTargets = targetImageIds.size
        ))

        val results = mutableListOf<TransferResult>()
        val failed = mutableListOf<UInt>()

        for ((index, targetId) in targetImageIds.withIndex()) {
            _transferProgress.value = _transferProgress.value + (transferId to TransferProgress(
                transferId = transferId,
                totalTargets = targetImageIds.size,
                completedTargets = index,
                currentTargetName = targetId.toString()
            ))

            val result = applyTransfer(targetId, transfer, mode, selectedCategories)
            results.add(result)
            if (!result.success) failed.add(targetId)
        }

        _transferProgress.value = _transferProgress.value + (transferId to TransferProgress(
            transferId = transferId,
            totalTargets = targetImageIds.size,
            completedTargets = targetImageIds.size
        ))

        transferStates[transferId] = TransferState.COMPLETED
        BatchTransferResult(
            transferId = transferId,
            totalTargets = targetImageIds.size,
            successCount = targetImageIds.size - failed.size,
            failedTargets = failed,
            results = results
        )
    }

    // ================================================================
    // Selective Parameter Transfer
    // ================================================================

    suspend fun selectiveTransfer(
        sourceImageId: UInt,
        targetImageIds: List<UInt>,
        selectedParams: Map<OperatorType, Boolean>
    ): BatchTransferResult = withContext(Dispatchers.IO) {
        val transferId = "selective_${sourceImageId}_${Instant.now().toEpochMilli()}"
        transferStates[transferId] = TransferState.APPLYING

        _transferProgress.value = _transferProgress.value + (transferId to TransferProgress(
            transferId = transferId,
            totalTargets = targetImageIds.size
        ))

        val sourceHistory = editHistoryRepository.getHistory(sourceImageId)
        if (sourceHistory == null) {
            return@withContext BatchTransferResult(transferId, targetImageIds.size, 0, targetImageIds, emptyList())
        }

        val sourceVersion = sourceHistory.getActiveVersion()
        if (sourceVersion == null) {
            return@withContext BatchTransferResult(transferId, targetImageIds.size, 0, targetImageIds, emptyList())
        }

        val results = mutableListOf<TransferResult>()
        val failed = mutableListOf<UInt>()

        for ((index, targetId) in targetImageIds.withIndex()) {
            _transferProgress.value = _transferProgress.value + (transferId to TransferProgress(
                transferId = transferId,
                totalTargets = targetImageIds.size,
                completedTargets = index,
                currentTargetName = targetId.toString()
            ))

            try {
                for (tx in sourceVersion.appliedTransactions()) {
                    if (selectedParams[tx.operatorType] == true) {
                        historyMgmtService.recordTransaction(
                            imageId = targetId,
                            operatorType = tx.operatorType,
                            paramsBefore = tx.paramsBefore,
                            paramsAfter = tx.paramsAfter
                        )
                    }
                }
                results.add(TransferResult(transferId, true, "Applied selected params"))
            } catch (e: Exception) {
                failed.add(targetId)
                results.add(TransferResult(transferId, false, e.message ?: "Unknown error"))
            }
        }

        _transferProgress.value = _transferProgress.value + (transferId to TransferProgress(
            transferId = transferId,
            totalTargets = targetImageIds.size,
            completedTargets = targetImageIds.size
        ))

        transferStates[transferId] = TransferState.COMPLETED
        BatchTransferResult(
            transferId = transferId,
            totalTargets = targetImageIds.size,
            successCount = targetImageIds.size - failed.size,
            failedTargets = failed,
            results = results
        )
    }

    // ================================================================
    // Preview Before Apply
    // ================================================================

    suspend fun previewTransfer(
        sourceImageId: UInt,
        targetImageId: UInt,
        config: TransferConfig = TransferConfig()
    ): TransferPreview = withContext(Dispatchers.IO) {
        val sourceHistory = editHistoryRepository.getHistory(sourceImageId)
            ?: return@withContext TransferPreview(sourceImageId, targetImageId)

        val sourceVersion = sourceHistory.getActiveVersion()
            ?: return@withContext TransferPreview(sourceImageId, targetImageId)

        val adjustments = extractAdjustments(sourceVersion, sourceHistory)
        val filtered = filterAdjustments(adjustments, config)

        val paramsDiff = filtered.associate { adj ->
            adj.paramName to (adj.sourceValue to adj.targetValue)
        }

        TransferPreview(
            sourceImageId = sourceImageId,
            targetImageId = targetImageId,
            paramsDiff = paramsDiff
        )
    }

    suspend fun batchPreviewTransfer(
        sourceImageId: UInt,
        targetImageIds: List<UInt>,
        config: TransferConfig = TransferConfig()
    ): List<TransferPreview> = withContext(Dispatchers.IO) {
        targetImageIds.map { targetId ->
            previewTransfer(sourceImageId, targetId, config)
        }
    }

    // ================================================================
    // Undo Support for Batch Operations
    // ================================================================

    suspend fun undoBatchTransfer(
        batchTransferId: String,
        targetImageIds: List<UInt>
    ): BatchTransferResult = withContext(Dispatchers.IO) {
        val results = mutableListOf<TransferResult>()
        val failed = mutableListOf<UInt>()

        for (targetId in targetImageIds) {
            try {
                var undidCount = 0
                while (historyMgmtService.canUndo(targetId)) {
                    historyMgmtService.undo(targetId)
                    undidCount++
                }
                results.add(TransferResult(batchTransferId, true, "Undid $undidCount operations"))
            } catch (e: Exception) {
                failed.add(targetId)
                results.add(TransferResult(batchTransferId, false, e.message ?: "Undo failed"))
            }
        }

        BatchTransferResult(
            transferId = batchTransferId,
            totalTargets = targetImageIds.size,
            successCount = targetImageIds.size - failed.size,
            failedTargets = failed,
            results = results
        )
    }

    // ================================================================
    // Transfer from Parameter Group
    // ================================================================

    suspend fun transferByGroup(
        sourceImageId: UInt,
        targetImageIds: List<UInt>,
        groups: Set<TransferParamGroup>
    ): BatchTransferResult = withContext(Dispatchers.IO) {
        val config = TransferConfig(
            enabledGroups = groups,
            copyGeometry = TransferParamGroup.GEOMETRY in groups || TransferParamGroup.ALL in groups,
            copyDisplayTransform = TransferParamGroup.DISPLAY in groups || TransferParamGroup.ALL in groups,
            copyRawDecodeParams = TransferParamGroup.RAW_DECODE in groups || TransferParamGroup.ALL in groups
        )
        batchTransfer(sourceImageId, targetImageIds, config)
    }

    // ================================================================
    // Transfer by v1 Category
    // ================================================================

    suspend fun transferByCategory(
        sourceImageId: UInt,
        targetImageIds: List<UInt>,
        categories: Set<AdjustmentCategory>,
        mode: ApplyMode = ApplyMode.PASTE
    ): BatchTransferResult = withContext(Dispatchers.IO) {
        val sourceHistory = editHistoryRepository.getHistory(sourceImageId)
            ?: return@withContext BatchTransferResult(
                "cat_${Instant.now().toEpochMilli()}", targetImageIds.size, 0, targetImageIds, emptyList()
            )

        val sourceVersion = sourceHistory.getActiveVersion()
            ?: return@withContext BatchTransferResult(
                "cat_${Instant.now().toEpochMilli()}", targetImageIds.size, 0, targetImageIds, emptyList()
            )

        // Build a simplified transfer from the source version's transactions
        val transfer = AdjustmentTransferV1(
            source = SourceInfo(file_id = sourceImageId.toString()),
            adjustments = buildCategoryAdjustments(sourceVersion, categories)
        )

        batchApplyTransfer(transfer, targetImageIds, mode, categories)
    }

    // ================================================================
    // Cancel Transfer
    // ================================================================

    fun cancelTransfer(transferId: String) {
        transferStates[transferId] = TransferState.CANCELLED
        _transferProgress.value = _transferProgress.value - transferId
    }

    fun getTransferState(transferId: String): TransferState? = transferStates[transferId]

    // ================================================================
    // Private Helpers
    // ================================================================

    private fun extractAdjustments(
        version: Version,
        history: EditHistory
    ): List<ParamAdjustment> {
        val adjustments = mutableListOf<ParamAdjustment>()
        for (tx in version.appliedTransactions()) {
            adjustments.add(ParamAdjustment(
                operatorType = tx.operatorType,
                paramName = tx.operatorType.name,
                sourceValue = 0f,
                targetValue = 0f
            ))
        }
        return adjustments
    }

    private fun filterAdjustments(
        adjustments: List<ParamAdjustment>,
        config: TransferConfig
    ): List<ParamAdjustment> {
        if (TransferParamGroup.ALL in config.enabledGroups) {
            return adjustments
        }
        return adjustments.filter { adj ->
            val group = mapOperatorToGroup(adj.operatorType)
            group in config.enabledGroups
        }
    }

    private fun mapOperatorToGroup(operatorType: OperatorType): TransferParamGroup {
        return when (operatorType) {
            OperatorType.EXPOSURE -> TransferParamGroup.EXPOSURE
            OperatorType.CONTRAST -> TransferParamGroup.EXPOSURE
            OperatorType.SATURATION -> TransferParamGroup.COLOR
            OperatorType.VIBRANCE -> TransferParamGroup.COLOR
            OperatorType.HIGHLIGHTS -> TransferParamGroup.EXPOSURE
            OperatorType.SHADOWS -> TransferParamGroup.EXPOSURE
            OperatorType.WHITE_BALANCE -> TransferParamGroup.WHITE_BALANCE
            OperatorType.TONE_CURVE -> TransferParamGroup.TONE_CURVE
            OperatorType.HSL -> TransferParamGroup.HSL
            OperatorType.COLOR_WHEEL -> TransferParamGroup.COLOR_WHEEL
            OperatorType.GEOMETRY -> TransferParamGroup.GEOMETRY
            OperatorType.CROP -> TransferParamGroup.GEOMETRY
            OperatorType.FILM_GRAIN -> TransferParamGroup.FILM_GRAIN
            OperatorType.HALATION -> TransferParamGroup.HALATION
            OperatorType.SHARPEN -> TransferParamGroup.SHARPEN
            OperatorType.CLARITY -> TransferParamGroup.CLARITY
            OperatorType.TINT -> TransferParamGroup.TINT
            OperatorType.LUT -> TransferParamGroup.COLOR
            OperatorType.DISPLAY_TRANSFORM -> TransferParamGroup.DISPLAY
            OperatorType.RAW_DECODE -> TransferParamGroup.RAW_DECODE
            OperatorType.TONE_REGION -> TransferParamGroup.TONE_CURVE
        }
    }

    private fun mapCategoryToOperator(category: AdjustmentCategory): OperatorType {
        return when (category) {
            AdjustmentCategory.GEOMETRY -> OperatorType.GEOMETRY
            AdjustmentCategory.TONE -> OperatorType.EXPOSURE
            AdjustmentCategory.COLOR -> OperatorType.SATURATION
            AdjustmentCategory.COLOR_TEMP -> OperatorType.WHITE_BALANCE
            AdjustmentCategory.DETAIL -> OperatorType.CLARITY
            AdjustmentCategory.OUTPUT_TRANSFORM -> OperatorType.DISPLAY_TRANSFORM
        }
    }

    private fun mapAdjustmentToOperator(adjustment: ParamAdjustment): OperatorType {
        return adjustment.operatorType
    }

    private fun buildCategoryAdjustments(
        version: Version,
        categories: Set<AdjustmentCategory>
    ): Map<String, JsonObject> {
        val result = mutableMapOf<String, JsonObject>()
        for (category in categories) {
            val operatorTypes = mapCategoryToOperatorTypes(category)
            val relatedTxs = version.appliedTransactions().filter { it.operatorType in operatorTypes }
            if (relatedTxs.isNotEmpty()) {
                result[category.name.lowercase()] = buildJsonObject {
                    for (tx in relatedTxs) {
                        put(tx.operatorType.name, tx.paramsAfter)
                    }
                }
            }
        }
        return result
    }

    private fun mapCategoryToOperatorTypes(category: AdjustmentCategory): Set<OperatorType> {
        return when (category) {
            AdjustmentCategory.GEOMETRY -> setOf(
                OperatorType.GEOMETRY, OperatorType.CROP
            )
            AdjustmentCategory.TONE -> setOf(
                OperatorType.EXPOSURE, OperatorType.CONTRAST,
                OperatorType.HIGHLIGHTS, OperatorType.SHADOWS,
                OperatorType.TONE_CURVE, OperatorType.TONE_REGION,
                OperatorType.VIBRANCE
            )
            AdjustmentCategory.COLOR -> setOf(
                OperatorType.SATURATION, OperatorType.HSL,
                OperatorType.COLOR_WHEEL, OperatorType.TINT, OperatorType.LUT
            )
            AdjustmentCategory.COLOR_TEMP -> setOf(
                OperatorType.WHITE_BALANCE
            )
            AdjustmentCategory.DETAIL -> setOf(
                OperatorType.CLARITY, OperatorType.SHARPEN
            )
            AdjustmentCategory.OUTPUT_TRANSFORM -> setOf(
                OperatorType.DISPLAY_TRANSFORM, OperatorType.FILM_GRAIN,
                OperatorType.HALATION
            )
        }
    }

    data class ParamAdjustment(
        val operatorType: OperatorType,
        val paramName: String,
        val sourceValue: Float,
        val targetValue: Float
    )

    data class TransferResult(
        val transferId: String,
        val success: Boolean,
        val message: String
    )

    data class BatchTransferResult(
        val transferId: String,
        val totalTargets: Int,
        val successCount: Int,
        val failedTargets: List<UInt>,
        val results: List<TransferResult>
    )
}

// ================================================================
// JSON extension helpers
// ================================================================

private val List<Float>.jsonArray: JsonArray
    get() = JsonArray(map { JsonPrimitive(it) })

private fun JsonArray.toFloatArray(): FloatArray {
    return map { it.jsonPrimitive.floatOrNull ?: 0f }.toFloatArray()
}
