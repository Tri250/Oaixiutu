package com.alcedo.studio.domain.service

import com.alcedo.studio.data.model.FilterCombo
import com.alcedo.studio.data.model.ImageItem
import com.alcedo.studio.data.model.SortDescriptor
import com.alcedo.studio.data.model.SortField
import com.alcedo.studio.domain.repository.ImageRepository
import com.alcedo.studio.utils.ThreadPool
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sleeve filtering service. Translates a [FilterCombo] into a Room query against
 * the catalog and applies the in-memory predicates + sort the SQL cannot fully
 * express. The native DuckDB sleeve remains the source of truth and is
 * reconciled into Room by [SleeveService]; this service reads the projection.
 */
@Singleton
class SleeveFilterService @Inject constructor(
    private val imageRepository: ImageRepository,
) {

    /** Run [filter] + [sort] against the catalog, returning the matching images. */
    suspend fun filter(
        filter: FilterCombo,
        sort: SortDescriptor,
        limit: Int = 1000,
        offset: Int = 0,
    ): List<ImageItem> = withContext(ThreadPool.database) {
        val page = imageRepository.queryImages(filter, sort, limit, offset)
        applySort(page, sort)
    }

    /** Count matches for [filter] (used for the stats view). */
    suspend fun count(filter: FilterCombo): Int = withContext(ThreadPool.database) {
        imageRepository.queryImages(filter, SortDescriptor(SortField.DATE_ADDED), Int.MAX_VALUE, 0).size
    }

    /** Distinct values of a metadata field across the catalog, for filter chips. */
    suspend fun distinctCameras(): List<String> = imageRepository.distinctCameras()
    suspend fun distinctLenses(): List<String> = imageRepository.distinctLenses()

    private fun applySort(images: List<ImageItem>, sort: SortDescriptor): List<ImageItem> {
        val comparator: Comparator<ImageItem> = when (sort.field) {
            SortField.DATE_CAPTURED -> compareBy { it.dateCapturedEpoch }
            SortField.DATE_ADDED -> compareBy { it.dateAddedEpoch }
            SortField.NAME -> compareBy { it.displayName.lowercase() }
            SortField.RATING -> compareBy { it.rating }
            SortField.FILE_SIZE -> compareBy { it.fileSizeBytes }
            SortField.AI_SCORE -> compareBy { it.aiScore ?: -1f }
            SortField.FOCAL_LENGTH -> compareBy { it.focalLength ?: 0f }
            SortField.ISO -> compareBy { it.iso ?: 0 }
            SortField.APERTURE -> compareBy { it.aperture ?: 0f }
        }
        val ordered = images.sortedWith(comparator)
        return if (sort.ascending) ordered else ordered.asReversed()
    }
}
