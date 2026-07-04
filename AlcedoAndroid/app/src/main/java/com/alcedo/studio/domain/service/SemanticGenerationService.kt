package com.alcedo.studio.domain.service

import android.content.Context
import com.alcedo.studio.data.local.ImageMetadataDao
import com.alcedo.studio.data.model.*

class SemanticGenerationService(
    private val context: Context,
    private val aiService: AiService,
    private val metadataDao: ImageMetadataDao,
    private val modelDownloadService: ModelDownloadService
) {

    suspend fun generateSemanticLabels(imagePath: String): List<String> = emptyList()

    suspend fun generateDescription(imagePath: String): String = ""

    suspend fun generateTags(imagePath: String): List<String> = emptyList()

    fun isReady(): Boolean = false
}
