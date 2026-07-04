package com.alcedo.studio.domain.service

import android.content.Context
import com.alcedo.studio.data.model.ExportFormat
import com.alcedo.studio.data.model.ExportSettings
import com.alcedo.studio.data.model.Project
import java.io.File

class ExportService(private val context: Context) {

    suspend fun exportImage(
        imageId: UInt,
        imagePath: String,
        settings: ExportSettings
    ): File? = null

    suspend fun exportImages(
        imageIds: List<UInt>,
        imagePaths: Map<UInt, String>,
        settings: ExportSettings
    ): List<File> = emptyList()

    suspend fun exportProject(project: Project, outputDir: File): File? = null

    fun getDefaultExportDirectory(): File = File(context.getExternalFilesDir(null), "exports")

    suspend fun cancelExport(exportId: String) {}
}
