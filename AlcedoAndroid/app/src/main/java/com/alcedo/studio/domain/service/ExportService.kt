package com.alcedo.studio.domain.service

import android.content.Context
import android.graphics.Bitmap
import com.alcedo.studio.data.model.ExportFormat
import com.alcedo.studio.data.model.ExportSettings
import com.alcedo.studio.data.model.Project
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

// ================================================================
// Export types
// ================================================================

enum class ExportStatus { IDLE, EXPORTING, COMPLETED, FAILED, CANCELLED }

data class ExportProgress(
    val overallProgress: Float = 0f,
    val totalItems: Int = 0,
    val completedItems: Int = 0,
    val currentItemName: String = "",
    val currentItemProgress: Float = 0f,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val status: ExportStatus = ExportStatus.IDLE
)

sealed class ExportResult {
    data class Success(val filePath: String, val message: String = "") : ExportResult()
    data class Error(val message: String) : ExportResult()
}

data class ExportBatchItem(
    val imageId: Long = 0,
    val sourcePath: String = "",
    val settings: ExportSettings = ExportSettings(),
    val processedBitmap: Bitmap? = null
)

data class BatchExportProgress(
    val totalItems: Int = 0,
    val completedItems: Int = 0,
    val currentItem: Int = 0
) {
    val progressFraction: Float
        get() = if (totalItems > 0) completedItems.toFloat() / totalItems else 0f

    val isComplete: Boolean
        get() = completedItems >= totalItems && totalItems > 0
}

// ================================================================
// Service
// ================================================================

class ExportService(private val context: Context) {

    val exportProgress = MutableStateFlow(ExportProgress())

    suspend fun exportImage(
        imageId: UInt,
        imagePath: String,
        settings: ExportSettings,
        bitmap: Bitmap? = null
    ): ExportResult = ExportResult.Error("Not implemented")

    suspend fun exportImages(
        imageIds: List<UInt>,
        imagePaths: Map<UInt, String>,
        settings: ExportSettings
    ): List<File> = emptyList()

    suspend fun exportBatch(items: List<ExportBatchItem>, settings: ExportSettings) {}

    suspend fun exportProject(project: Project, outputDir: File): File? = null

    fun getDefaultExportDirectory(): File = File(context.getExternalFilesDir(null), "exports")

    suspend fun cancelExport(exportId: String = "") {}
}
