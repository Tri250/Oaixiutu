package com.alcedo.studio.di

import android.content.Context
import com.alcedo.studio.data.local.*
import com.alcedo.studio.domain.repository.*
import com.alcedo.studio.domain.service.*
import com.alcedo.studio.service.RenderService
import com.alcedo.studio.service.SleeveFilterService as AppSleeveFilterService
import com.alcedo.studio.service.ExportService as AppExportService
import com.alcedo.studio.service.AiService as AppAiService

object AppModule {
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    val context: Context get() = appContext

    // Database
    val database: SleeveDatabase by lazy {
        SleeveDatabase.getInstance(appContext)
    }

    // DAOs
    val elementDao: SleeveElementDao by lazy { database.sleeveElementDao() }
    val fileDao: SleeveFileDao by lazy { database.sleeveFileDao() }
    val folderDao: SleeveFolderDao by lazy { database.sleeveFolderDao() }
    val metadataDao: ImageMetadataDao by lazy { database.imageMetadataDao() }
    val ratingDao: RatingDao by lazy { database.ratingDao() }
    val labelDao: SemanticLabelDao by lazy { database.semanticLabelDao() }
    val collectionDao: CollectionDao by lazy { database.collectionDao() }
    val filterDao: FilterDao by lazy { database.filterDao() }

    // Desktop schema DAOs
    val imageDao: ImageDao by lazy { database.imageDao() }
    val pipelineDao: PipelineDao by lazy { database.pipelineDao() }
    val historyDao: HistoryDao by lazy { database.historyDao() }
    val filterV2Dao: FilterV2Dao by lazy { database.filterV2Dao() }
    val aiDescriptionDao: AiDescriptionDao by lazy { database.aiDescriptionDao() }
    val aiRatingDao: AiRatingDao by lazy { database.aiRatingDao() }
    val semanticEmbeddingDao: SemanticEmbeddingDao by lazy { database.semanticEmbeddingDao() }
    val semanticLabelV2Dao: SemanticLabelV2Dao by lazy { database.semanticLabelV2Dao() }
    val collectionV2Dao: CollectionV2Dao by lazy { database.collectionV2Dao() }

    // Cache
    val thumbnailDiskCache: ThumbnailDiskCache by lazy {
        ThumbnailDiskCache(appContext.cacheDir.resolve("thumbnails"))
    }

    val dentryCacheManager: DentryCacheManager by lazy {
        DentryCacheManager(maxEntries = 5000)
    }

    // Path Resolver
    val pathResolver: PathResolver by lazy {
        PathResolver(elementDao, fileDao, folderDao)
    }

    // ── Domain Services ──

    val sleeveService: SleeveService by lazy {
        SleeveService(
            elementDao = elementDao,
            fileDao = fileDao,
            folderDao = folderDao,
            metadataDao = metadataDao,
            collectionDao = collectionDao,
            ratingDao = ratingDao,
            labelDao = labelDao,
            pathResolver = pathResolver,
            cacheManager = dentryCacheManager
        )
    }

    val sleeveFilterService: SleeveFilterService by lazy {
        SleeveFilterService(
            metadataDao = metadataDao,
            ratingDao = ratingDao,
            labelDao = labelDao,
            collectionDao = collectionDao,
            filterDao = filterDao,
            filterV2Dao = filterV2Dao,
            elementDao = elementDao
        )
    }

    val importService: ImportService by lazy {
        ImportService(
            context = appContext,
            metadataDao = metadataDao,
            sleeveService = sleeveService,
            thumbnailDiskCache = thumbnailDiskCache
        )
    }

    val exportService: ExportService by lazy {
        ExportService(appContext)
    }

    val thumbnailService: ThumbnailService by lazy {
        ThumbnailService(
            diskCache = thumbnailDiskCache,
            cacheDir = appContext.cacheDir.resolve("thumbnails")
        )
    }

    val pipelineService: PipelineService by lazy {
        PipelineService()
    }

    val aiService: AiService by lazy {
        AiService(appContext)
    }

    val searchService: SearchService by lazy {
        SearchService(appContext, aiService, metadataDao, labelDao, elementDao)
    }

    val backgroundTaskService: BackgroundTaskService by lazy {
        BackgroundTaskService()
    }

    // AI Services
    val aiCredentialService: AiCredentialService by lazy {
        AiCredentialService(appContext)
    }

    val modelDownloadService: ModelDownloadService by lazy {
        ModelDownloadService(appContext)
    }

    val searchQueryClassifier: SearchQueryClassifier by lazy {
        SearchQueryClassifier()
    }

    val aiRatingService: AiRatingService by lazy {
        AiRatingService(appContext, aiCredentialService)
    }

    val semanticGenerationService: SemanticGenerationService by lazy {
        SemanticGenerationService(appContext, aiService, metadataDao, modelDownloadService)
    }

    // ── App Services (com.alcedo.studio.service) ──

    val renderService: RenderService by lazy {
        RenderService()
    }

    val appSleeveFilterService: AppSleeveFilterService by lazy {
        AppSleeveFilterService(
            metadataDao = metadataDao,
            ratingDao = ratingDao,
            labelDao = labelDao,
            collectionDao = collectionDao,
            filterDao = filterDao,
            elementDao = elementDao
        )
    }

    val appExportService: AppExportService by lazy {
        AppExportService(appContext)
    }

    val appAiService: AppAiService by lazy {
        AppAiService(appContext)
    }

    // ── Repositories ──

    val sleeveRepository: SleeveRepository by lazy {
        SleeveRepository(
            sleeveService = sleeveService,
            filterService = sleeveFilterService,
            elementDao = elementDao,
            fileDao = fileDao,
            folderDao = folderDao,
            metadataDao = metadataDao,
            collectionDao = collectionDao,
            ratingDao = ratingDao,
            labelDao = labelDao,
            filterDao = filterDao,
            pathResolver = pathResolver,
            cacheManager = dentryCacheManager
        )
    }

    val imageRepository: ImageRepository by lazy {
        ImageRepositoryImpl(
            metadataDao = metadataDao,
            thumbnailCache = thumbnailDiskCache
        )
    }

    val projectRepository: ProjectRepository by lazy {
        ProjectRepositoryImpl(database)
    }

    val editHistoryRepository: EditHistoryRepository by lazy {
        EditHistoryRepositoryImpl(database)
    }
}