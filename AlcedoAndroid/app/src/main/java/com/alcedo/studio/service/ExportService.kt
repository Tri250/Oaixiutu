package com.alcedo.studio.service

import android.content.Context
import com.alcedo.studio.data.model.ExportFormat
import com.alcedo.studio.data.model.ExportSettings
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

    fun getDefaultExportDirectory(): File = File(context.getExternalFilesDir(null), "exports")
}
