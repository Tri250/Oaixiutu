package com.alcedo.studio.domain.service

import com.alcedo.studio.data.model.*
import com.alcedo.studio.domain.repository.EditHistoryRepository
import com.alcedo.studio.domain.repository.ImageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

class AdjustmentTransferService(
    private val imageRepository: ImageRepository,
    private val editHistoryRepository: EditHistoryRepository,
    private val historyMgmtService: HistoryMgmtService
) {
    companion object {
        const val SCHEMA_ID = "alcedo.adjustment_transfer.v1"
        const val SCHEMA_VERSION = 1
    }

    enum class TransferState { IDLE, PREVIEWING, APPLYING, COMPLETED, FAILED, CANCELLED }
    enum class AdjustmentCategory { GEOMETRY, TONE, COLOR, COLOR_TEMP, DETAIL, OUTPUT_TRANSFORM }
    enum class ApplyMode { PASTE, MERGE }

    data class TransferProgress(
        val transferId: String,
        val totalTargets: Int,
        val completedTargets: Int = 0,
        val currentTargetName: String = "",
        val isPreview: Boolean = false
    )

    @Serializable
    data class AdjustmentTransferV1(
        val schema: String = SCHEMA_ID,
        val version: Int = SCHEMA_VERSION,
        val timestamp: String = java.time.Instant.now().toString(),
        val source: SourceInfo = SourceInfo(),
        val adjustments: Map<String, JsonObject> = emptyMap()
    )

    @Serializable
    data class SourceInfo(
        val file_id: String = "",
        val file_name: String = ""
    )

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

    private val _transferProgress = MutableStateFlow<Map<String, TransferProgress>>(emptyMap())
    val transferProgress: StateFlow<Map<String, TransferProgress>> = _transferProgress.asStateFlow()

    fun captureAdjustments(pipelineParams: PipelineParams, fileId: String = "", fileName: String = ""): AdjustmentTransferV1 {
        return AdjustmentTransferV1(source = SourceInfo(file_id = fileId, file_name = fileName))
    }

    fun exportToJson(transfer: AdjustmentTransferV1): String = ""
    fun importFromJson(jsonString: String): Result<AdjustmentTransferV1> = Result.failure(IllegalStateException("Stub"))
    fun applyTransferToParams(base: PipelineParams, transfer: AdjustmentTransferV1, selectedCategories: Set<AdjustmentCategory> = AdjustmentCategory.entries.toSet()): PipelineParams = base
    fun cancelTransfer(transferId: String) {}
    fun getTransferState(transferId: String): TransferState? = null
}
