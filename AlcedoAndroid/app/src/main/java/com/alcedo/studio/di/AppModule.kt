package com.alcedo.studio.di

import android.content.Context
import com.alcedo.studio.data.dao.EditHistoryDao
import com.alcedo.studio.data.dao.PipelinePresetDao
import com.alcedo.studio.data.dao.AiEmbeddingDao
import com.alcedo.studio.data.local.*
import com.alcedo.studio.domain.repository.*
import com.alcedo.studio.domain.service.*
import com.alcedo.studio.service.RenderService
import com.alcedo.studio.service.SleeveFilterService as AppSleeveFilterService
import com.alcedo.studio.service.ExportService as AppExportService
// Old AiService in service package has been removed; domain.service.AiService is used everywhere

object AppModule {
    private var _appContext: Context? = null
    private var initialized = false

    val context: Context
        get() = _appContext ?: throw IllegalStateException("AppModule not initialized. Call initialize() first.")

    val isInitialized: Boolean get() = initialized

    fun initialize(context: Context) {
        _appContext = context.applicationContext
        initialized = true
    }

    fun getContextSafely(): Context? = _appContext

    // Database
    val database: SleeveDatabase by lazy {
        SleeveDatabase.getInstance(this.context)
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
        ThumbnailDiskCache(this.context.cacheDir.resolve("thumbnails"))
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
            context = this.context,
            metadataDao = metadataDao,
            sleeveService = sleeveService,
            thumbnailDiskCache = thumbnailDiskCache
        )
    }

    val exportService: ExportService by lazy {
        ExportService(this.context)
    }

    val thumbnailService: ThumbnailService by lazy {
        ThumbnailService(
            diskCache = thumbnailDiskCache,
            cacheDir = this.context.cacheDir.resolve("thumbnails")
        )
    }

    val pipelineService: PipelineService by lazy {
        PipelineService()
    }

    val presetService: PresetService by lazy {
        PresetService(
            presetDao = pipelinePresetDao,
            pipelineService = pipelineService,
            editHistoryRepository = editHistoryRepository
        )
    }

    val aiService: AiService by lazy {
        AiService(this.context)
    }

    val searchService: SearchService by lazy {
        SearchService(this.context, aiService, metadataDao, labelDao, elementDao)
    }

    val backgroundTaskService: BackgroundTaskService by lazy {
        BackgroundTaskService(this.context)
    }

    // AI Services
    val aiCredentialService: AiCredentialService by lazy {
        AiCredentialService(this.context)
    }

    val modelDownloadService: ModelDownloadService by lazy {
        ModelDownloadService(this.context)
    }

    val searchQueryClassifier: SearchQueryClassifier by lazy {
        SearchQueryClassifier()
    }

    val aiRatingService: AiRatingService by lazy {
        AiRatingService(this.context, aiCredentialService)
    }

    val semanticGenerationService: SemanticGenerationService by lazy {
        SemanticGenerationService(this.context, aiService, metadataDao, modelDownloadService)
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
        AppExportService(this.context)
    }

    // appAiService removed — domain.service.AiService (aiService) is the sole implementation

    // DAOs from data.dao package
    val editHistoryDao: EditHistoryDao by lazy { database.editHistoryDao() }
    val pipelinePresetDao: PipelinePresetDao by lazy { database.pipelinePresetDao() }
    val aiEmbeddingDao: AiEmbeddingDao by lazy { database.aiEmbeddingDao() }

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