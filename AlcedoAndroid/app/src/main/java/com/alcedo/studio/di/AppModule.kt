package com.alcedo.studio.di

import android.content.Context
import com.alcedo.studio.data.local.SleeveDatabase
import com.alcedo.studio.data.local.ThumbnailDiskCache
import com.alcedo.studio.domain.repository.*
import com.alcedo.studio.domain.service.*

object AppModule {
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    val context: Context get() = appContext

    // Database
    val database: SleeveDatabase by lazy { SleeveDatabase(appContext) }

    // Repositories
    val sleeveRepository: SleeveRepository by lazy {
        SleeveRepositoryImpl(database)
    }

    val imageRepository: ImageRepository by lazy {
        ImageRepositoryImpl(database, thumbnailDiskCache)
    }

    val projectRepository: ProjectRepository by lazy {
        ProjectRepositoryImpl(database)
    }

    val editHistoryRepository: EditHistoryRepository by lazy {
        EditHistoryRepositoryImpl(database)
    }

    // Services
    val thumbnailDiskCache: ThumbnailDiskCache by lazy {
        ThumbnailDiskCache(appContext.cacheDir.resolve("thumbnails"))
    }

    val importService: ImportService by lazy {
        ImportService(appContext, imageRepository, sleeveRepository)
    }

    val exportService: ExportService by lazy {
        ExportService(appContext)
    }

    val thumbnailService: ThumbnailService by lazy {
        ThumbnailService(imageRepository, thumbnailDiskCache)
    }

    val pipelineService: PipelineService by lazy {
        PipelineService()
    }

    val aiService: AiService by lazy {
        AiService(appContext)
    }

    val searchService: SearchService by lazy {
        SearchService(sleeveRepository, imageRepository, aiService)
    }

    val backgroundTaskService: BackgroundTaskService by lazy {
        BackgroundTaskService()
    }
}
