package com.alcedo.studio.di

import android.content.Context
import com.alcedo.studio.data.local.*
import com.alcedo.studio.domain.repository.*
import com.alcedo.studio.domain.service.*

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

    // Services
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

    // Repositories
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