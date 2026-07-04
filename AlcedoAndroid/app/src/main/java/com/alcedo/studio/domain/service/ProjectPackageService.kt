package com.alcedo.studio.domain.service

import android.content.Context
import com.alcedo.studio.data.model.Project
import java.io.File

class ProjectPackageService(private val context: Context) {

    suspend fun packageProject(project: Project, outputFile: File): Boolean = false

    suspend fun unpackageProject(packageFile: File, outputDir: File): Project? = null

    suspend fun exportProject(project: Project, outputDir: File): File? = null

    suspend fun importProject(packageFile: File): Project? = null

    fun getPackageExtension(): String = ".alcd"

    fun getDefaultExportDirectory(): File = File(context.getExternalFilesDir(null), "projects")
}
