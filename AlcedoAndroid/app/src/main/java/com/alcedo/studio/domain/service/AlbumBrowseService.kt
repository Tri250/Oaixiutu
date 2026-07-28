package com.alcedo.studio.domain.service

import com.alcedo.studio.data.model.FilterCombo
import com.alcedo.studio.data.model.ImageItem
import com.alcedo.studio.data.model.SortDescriptor
import com.alcedo.studio.data.model.SortField
import com.alcedo.studio.domain.repository.ImageRepository
import com.alcedo.studio.domain.repository.SleeveRepository
import com.alcedo.studio.utils.ThreadPool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Album browsing service. Provides the album grid's data: filtered + sorted
 * image lists, folder counts and statistics. Aggregates [ImageRepository] and
 * [SleeveRepository] for the album ViewModel.
 */
@Singleton
class AlbumBrowseService @Inject constructor(
    private val imageRepository: ImageRepository,
    private val sleeveRepository: SleeveRepository,
) {

    data class AlbumStats(
        val totalImages: Int,
        val rawImages: Int,
        val picks: Int,
        val rejects: Int,
        val folders: Int,
    )

    /** Observe images in [folderPath] reactively. */
    fun observeFolder(folderPath: String): Flow<List<ImageItem>> =
        imageRepository.observeImagesInFolder(folderPath)

    /** Observe all images (used by smart collections). */
    fun observeAll(): Flow<List<ImageItem>> = imageRepository.observeAllImages()

    /** Query a filtered, sorted page of images. */
    suspend fun query(filter: FilterCombo, sort: SortDescriptor, page: Int, pageSize: Int): List<ImageItem> =
        withContext(ThreadPool.database) {
            imageRepository.queryImages(filter, sort, pageSize, page * pageSize)
        }

    /** Aggregate stats for the album header / stats view. */
    suspend fun stats(): AlbumStats = withContext(ThreadPool.database) {
        AlbumStats(
            totalImages = imageRepository.count(),
            rawImages = imageRepository.rawCount(),
            picks = imageRepository.pickCount(),
            rejects = imageRepository.rejectCount(),
            folders = sleeveRepository.countFolders(),
        )
    }

    /** Distinct cameras/lenses for the filter bar. */
    suspend fun cameras(): List<String> = imageRepository.distinctCameras()
    suspend fun lenses(): List<String> = imageRepository.distinctLenses()

    /** Default sort for the grid (capture date, descending). */
    fun defaultSort(): SortDescriptor = SortDescriptor(SortField.DATE_CAPTURED, ascending = false)
}
