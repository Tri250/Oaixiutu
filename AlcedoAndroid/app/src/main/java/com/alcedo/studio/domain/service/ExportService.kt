package com.alcedo.studio.domain.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.alcedo.studio.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportService(private val context: Context) {

    suspend fun exportImage(
        sourcePath: String,
        settings: ExportSettings,
        processedBitmap: Bitmap? = null
    ): Uri? = withContext(Dispatchers.IO) {
        val bitmap = processedBitmap ?: BitmapFactory.decodeFile(sourcePath)
        ?: return@withContext null

        val outputDir = File(settings.outputPath).takeIf { it.exists() }
            ?: File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "AlcedoStudio")

        if (!outputDir.exists()) outputDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val ext = when (settings.format) {
            ExportFormat.JPEG -> "jpg"
            ExportFormat.PNG -> "png"
            ExportFormat.TIFF -> "tiff"
            ExportFormat.EXR -> "exr"
            ExportFormat.ULTRA_HDR -> "jpg"
        }
        val outputFile = File(outputDir, "alcedo_export_${timestamp}.$ext")

        FileOutputStream(outputFile).use { out ->
            when (settings.format) {
                ExportFormat.JPEG, ExportFormat.ULTRA_HDR -> {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, settings.quality.coerceIn(1, 100), out)
                }
                ExportFormat.PNG -> {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                ExportFormat.TIFF -> {
                    // TIFF requires additional library; fallback to PNG for now
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                ExportFormat.EXR -> {
                    // EXR requires OpenImageIO; fallback to PNG for now
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
        }

        MediaScannerConnection.scanFile(context, arrayOf(outputFile.absolutePath), null, null)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outputFile)
    }

    suspend fun exportBatch(
        items: List<Pair<String, Bitmap?>>,
        settings: ExportSettings
    ): List<Uri?> = withContext(Dispatchers.IO) {
        items.map { (path, bitmap) ->
            exportImage(path, settings, bitmap)
        }
    }
}
