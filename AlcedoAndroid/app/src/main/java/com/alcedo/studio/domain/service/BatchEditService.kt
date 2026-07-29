package com.alcedo.studio.domain.service

import com.alcedo.studio.data.model.AdjustmentParams
import com.alcedo.studio.data.model.AdjustmentParamsDelta
import com.alcedo.studio.data.model.EditTransaction
import com.alcedo.studio.data.model.TransactionSource
import com.alcedo.studio.data.model.applyDelta
import com.alcedo.studio.domain.repository.EditHistoryRepository
import com.alcedo.studio.domain.repository.ImageRepository
import com.alcedo.studio.utils.IdGenerator
import com.alcedo.studio.utils.ThreadPool
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Batch editing service. Applies an adjustment delta (or a preset) to a set of
 * images by creating a new edit transaction per image against its active
 * version. Mirrors core/app/adjustment_transfer_service behaviour.
 */
@Singleton
class BatchEditService @Inject constructor(
    private val imageRepository: ImageRepository,
    private val editHistoryRepository: EditHistoryRepository,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Apply [delta] to [imageIds], recording one transaction per image. */
    suspend fun applyDelta(imageIds: List<String>, delta: AdjustmentParamsDelta, label: String): Int =
        withContext(ThreadPool.compute) {
            var applied = 0
            for (imageId in imageIds) {
                val active = editHistoryRepository.getActiveVersion(imageId) ?: continue
                val transaction = EditTransaction(
                    id = IdGenerator.newId("tx"),
                    versionId = active.id,
                    timestamp = System.currentTimeMillis(),
                    label = label,
                    paramDelta = delta,
                    source = TransactionSource.BATCH_EDIT,
                )
                editHistoryRepository.addTransaction(transaction)
                // Recompute cumulative params and persist.
                val updated = active.cumulativeParams.applyDelta(delta)
                editHistoryRepository.updateCumulativeParams(active.id, json.encodeToString(AdjustmentParams.serializer(), updated))
                applied++
            }
            applied
        }

    /** Copy adjustments from [sourceImageId] to [targetImageIds]. */
    suspend fun copyAdjustments(sourceImageId: String, targetImageIds: List<String>): Int =
        withContext(ThreadPool.compute) {
            val source = editHistoryRepository.getActiveVersion(sourceImageId) ?: return@withContext 0
            val delta = AdjustmentParamsDelta(
                overrides = paramsToOverrides(source.cumulativeParams),
            )
            applyDelta(targetImageIds, delta, "Copy from $sourceImageId")
        }

    /** Apply a preset's full [AdjustmentParams] to [imageIds] (replaces baseline). */
    suspend fun applyPreset(imageIds: List<String>, preset: AdjustmentParams, label: String): Int =
        withContext(ThreadPool.compute) {
            applyDelta(imageIds, AdjustmentParamsDelta(paramsToOverrides(preset)), label)
        }

    /** Clear all adjustments for [imageIds] back to defaults. */
    suspend fun reset(imageIds: List<String>): Int =
        withContext(ThreadPool.compute) {
            applyDelta(imageIds, AdjustmentParamsDelta(paramsToOverrides(AdjustmentParams.DEFAULT)), "Reset")
        }

    private fun paramsToOverrides(params: AdjustmentParams): Map<String, String> = mapOf(
        "exposure" to params.exposure.toString(),
        "contrast" to params.contrast.toString(),
        "highlights" to params.highlights.toString(),
        "shadows" to params.shadows.toString(),
        "whites" to params.whites.toString(),
        "blacks" to params.blacks.toString(),
        "temperature" to params.temperature.toString(),
        "tint" to params.tint.toString(),
        "saturation" to params.saturation.toString(),
        "vibrance" to params.vibrance.toString(),
        "clarity" to params.clarity.toString(),
        "sharpen" to params.sharpen.toString(),
        "rotation" to params.rotation.toString(),
        "perspectiveH" to params.perspectiveH.toString(),
        "perspectiveV" to params.perspectiveV.toString(),
        "filmGrainAmount" to params.filmGrainAmount.toString(),
        "halationAmount" to params.halationAmount.toString(),
        "lutIntensity" to params.lutIntensity.toString(),
    )
}
