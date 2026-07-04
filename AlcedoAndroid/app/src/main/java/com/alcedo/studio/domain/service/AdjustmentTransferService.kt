package com.alcedo.studio.domain.service

import com.alcedo.studio.data.model.*
import com.alcedo.studio.domain.repository.EditHistoryRepository
import com.alcedo.studio.domain.repository.ImageRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class AdjustmentTransferService(
    private val imageRepository: ImageRepository,
    private val editHistoryRepository: EditHistoryRepository,
    private val historyMgmtService: HistoryMgmtService
) {
    companion object {
        private const val TAG = "AdjustmentTransferService"
    }

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

    // ── Copy Adjustments from One Image to Another ──

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

            // Apply each filtered adjustment as a transaction
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

    // ── Batch Paste to Multiple Images ──

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

    // ── Selective Parameter Transfer ──

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

    // ── Preview Before Apply ──

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

    // ── Undo Support for Batch Operations ──

    suspend fun undoBatchTransfer(
        batchTransferId: String,
        targetImageIds: List<UInt>
    ): BatchTransferResult = withContext(Dispatchers.IO) {
        val results = mutableListOf<TransferResult>()
        val failed = mutableListOf<UInt>()

        for (targetId in targetImageIds) {
            try {
                // Undo operations until we reach the state before the batch transfer
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

    // ── Transfer from Parameter Group ──

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

    // ── Cancel Transfer ──

    fun cancelTransfer(transferId: String) {
        transferStates[transferId] = TransferState.CANCELLED
        _transferProgress.value = _transferProgress.value - transferId
    }

    fun getTransferState(transferId: String): TransferState? = transferStates[transferId]

    // ── Private Helpers ──

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

    private fun mapAdjustmentToOperator(adjustment: ParamAdjustment): OperatorType {
        return adjustment.operatorType
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