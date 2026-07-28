package com.alcedo.studio.di

import android.content.Context
import com.alcedo.studio.data.dao.AiEmbeddingDao
import com.alcedo.studio.data.dao.EditHistoryDao
import com.alcedo.studio.data.dao.ImageDao
import com.alcedo.studio.data.dao.PipelinePresetDao
import com.alcedo.studio.data.local.DentryCacheManager
import com.alcedo.studio.data.local.SleeveDatabase
import com.alcedo.studio.data.local.ThumbnailDiskCache
import com.alcedo.studio.data.repository.EditHistoryRepositoryImpl
import com.alcedo.studio.data.repository.ImageRepositoryImpl
import com.alcedo.studio.data.repository.ProjectRepositoryImpl
import com.alcedo.studio.data.repository.SleeveRepositoryImpl
import com.alcedo.studio.domain.repository.EditHistoryRepository
import com.alcedo.studio.domain.repository.ImageRepository
import com.alcedo.studio.domain.repository.ProjectRepository
import com.alcedo.studio.domain.repository.SleeveRepository
import com.alcedo.studio.utils.MemoryGuard
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Hilt application module. Wires the data layer (Room + DAOs + repository
 * bindings), the singleton utilities that cannot use constructor injection
 * ([MemoryGuard], [ThumbnailDiskCache], [DentryCacheManager]) and the named
 * coroutine dispatchers/scope used across the app.
 *
 * All `@Singleton @Inject constructor` services (DecodeService, PipelineService,
 * AiRatingService, etc.) are constructed automatically by Hilt — they do not
 * need to be declared here.
 */
@InstallIn(SingletonComponent::class)
@Module
object AppModule {

    // ---- Database & DAOs -------------------------------------------------

    @Provides
    @Singleton
    fun provideSleeveDatabase(@ApplicationContext context: Context): SleeveDatabase =
        SleeveDatabase.build(context)

    @Provides
    @Singleton
    fun provideImageDao(db: SleeveDatabase): ImageDao = db.imageDao()

    @Provides
    @Singleton
    fun provideEditHistoryDao(db: SleeveDatabase): EditHistoryDao = db.editHistoryDao()

    @Provides
    @Singleton
    fun provideAiEmbeddingDao(db: SleeveDatabase): AiEmbeddingDao = db.aiEmbeddingDao()

    @Provides
    @Singleton
    fun providePipelinePresetDao(db: SleeveDatabase): PipelinePresetDao = db.pipelinePresetDao()

    // ---- Helpers that cannot use constructor injection --------------------

    @Provides
    @Singleton
    fun provideMemoryGuard(@ApplicationContext context: Context): MemoryGuard = MemoryGuard(context)

    @Provides
    @Singleton
    fun provideThumbnailDiskCache(@ApplicationContext context: Context): ThumbnailDiskCache =
        ThumbnailDiskCache(
            cacheDir = java.io.File(context.cacheDir, "thumbnails").apply { mkdirs() },
            maxBytes = 512L * 1024L * 1024L,
        )

    @Provides
    @Singleton
    fun provideDentryCacheManager(): DentryCacheManager = DentryCacheManager()

    // ---- Coroutine dispatchers & application scope ------------------------

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @ComputeDispatcher
    fun provideComputeDispatcher(): CoroutineDispatcher =
        com.alcedo.studio.utils.ThreadPool.compute

    @Provides
    @AiDispatcher
    fun provideAiDispatcher(): CoroutineDispatcher =
        com.alcedo.studio.utils.ThreadPool.aiInference

    @Provides
    @ThumbnailDispatcher
    fun provideThumbnailDispatcher(): CoroutineDispatcher =
        com.alcedo.studio.utils.ThreadPool.thumbnail

    @Provides
    @DatabaseDispatcher
    fun provideDatabaseDispatcher(): CoroutineDispatcher =
        com.alcedo.studio.utils.ThreadPool.database

    @Provides
    @ApplicationScope
    fun provideApplicationScope(@ComputeDispatcher dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)
}

/**
 * Interface-to-implementation bindings. Lives in its own module because
 * `@Binds` requires an abstract class (vs. `@Provides` which requires an
 * object/function).
 */
@InstallIn(SingletonComponent::class)
@Module
abstract class RepositoryBindingsModule {

    @Binds
    @Singleton
    abstract fun bindImageRepository(impl: ImageRepositoryImpl): ImageRepository

    @Binds
    @Singleton
    abstract fun bindSleeveRepository(impl: SleeveRepositoryImpl): SleeveRepository

    @Binds
    @Singleton
    abstract fun bindProjectRepository(impl: ProjectRepositoryImpl): ProjectRepository

    @Binds
    @Singleton
    abstract fun bindEditHistoryRepository(impl: EditHistoryRepositoryImpl): EditHistoryRepository
}

// ---- Dispatcher qualifiers ---------------------------------------------

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ComputeDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AiDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ThumbnailDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DatabaseDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
