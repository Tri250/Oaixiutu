package com.alcedo.studio.service

import androidx.sqlite.db.SimpleSQLiteQuery
import com.alcedo.studio.data.local.*
import com.alcedo.studio.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Complete filter service for Sleeve images.
 * Supports: by rating, by color label, by date range, by file type (RAW/JPEG),
 * by EXIF data (camera, lens, ISO range), by text search,
 * filter combination (AND/OR), sort options, and returns filtered image IDs.
 */
class SleeveFilterService(
    private val metadataDao: ImageMetadataDao,
    private val ratingDao: RatingDao,
    private val labelDao: SemanticLabelDao,
    private val collectionDao: CollectionDao,
    private val filterDao: FilterDao,
    private val elementDao: SleeveElementDao
) {

    // ================================================================
    // Filter by rating
    // ================================================================

    suspend fun filterByRating(
        minStars: Int = 0,
        maxStars: Int = 5,
        exactRating: Int? = null,
        page: Int = 0,
        pageSize: Int = 50
    ): FilterResult = withContext(Dispatchers.IO) {
        val ratings = if (exactRating != null) {
            ratingDao.getImagesByRating(exactRating)
        } else {
            ratingDao.getImagesByMinRating(minStars)
        }

        val filtered = if (exactRating == null && maxStars < 5) {
            ratings.filter { it.rating in minStars..maxStars }
        } else {
            ratings
        }

        val totalCount = filtered.size
        val offset = page * pageSize
        val paged = filtered.drop(offset).take(pageSize)

        FilterResult(
            imageIds = paged.map { it.imageId },
            totalCount = totalCount,
            page = page,
            pageSize = pageSize
        )
    }

    // ================================================================
    // Filter by color label
    // ================================================================

    suspend fun filterByColorLabel(
        colorLabel: Int,
        page: Int = 0,
        pageSize: Int = 50
    ): FilterResult = withContext(Dispatchers.IO) {
        // Color labels are stored in image metadata; filter by label field
        val allMetadata = metadataDao.getAllMetadata()
        val matched = allMetadata.filter { it.rating == colorLabel }

        val totalCount = matched.size
        val offset = page * pageSize
        val paged = matched.drop(offset).take(pageSize)

        FilterResult(
            imageIds = paged.map { it.imageId },
            totalCount = totalCount,
            page = page,
            pageSize = pageSize
        )
    }

    // ================================================================
    // Filter by date range
    // ================================================================

    suspend fun filterByDateRange(
        startDate: Long,
        endDate: Long = Long.MAX_VALUE,
        page: Int = 0,
        pageSize: Int = 50
    ): FilterResult = withContext(Dispatchers.IO) {
        val results = metadataDao.getMetadataByExifFilter(
            cameraMake = null, cameraModel = null, lensModel = null,
            minFocalLength = null, maxFocalLength = null,
            minAperture = null, maxAperture = null,
            minIso = null, maxIso = null,
            minShutterSpeed = null, maxShutterSpeed = null,
            dateFrom = startDate, dateTo = endDate,
            limit = pageSize, offset = page * pageSize
        )

        val totalCount = metadataDao.getMetadataByExifFilterCount(
            cameraMake = null, cameraModel = null, lensModel = null,
            minFocalLength = null, maxFocalLength = null,
            minAperture = null, maxAperture = null,
            minIso = null, maxIso = null,
            minShutterSpeed = null, maxShutterSpeed = null,
            dateFrom = startDate, dateTo = endDate
        )

        FilterResult(
            imageIds = results.map { it.imageId },
            totalCount = totalCount,
            page = page,
            pageSize = pageSize
        )
    }

    // ================================================================
    // Filter by file type (RAW/JPEG)
    // ================================================================

    suspend fun filterByFileType(
        imageTypes: List<ImageType>,
        page: Int = 0,
        pageSize: Int = 50
    ): FilterResult = withContext(Dispatchers.IO) {
        val allResults = mutableListOf<ImageMetadataEntity>()
        for (type in imageTypes) {
            allResults.addAll(metadataDao.getMetadataByImageType(type.ordinal))
        }

        val totalCount = allResults.size
        val offset = page * pageSize
        val paged = allResults.drop(offset).take(pageSize)

        FilterResult(
            imageIds = paged.map { it.imageId },
            totalCount = totalCount,
            page = page,
            pageSize = pageSize
        )
    }

    // ================================================================
    // Filter by EXIF data (camera, lens, ISO range)
    // ================================================================

    suspend fun filterByExif(
        filter: ExifFilter,
        page: Int = 0,
        pageSize: Int = 50
    ): FilterResult = withContext(Dispatchers.IO) {
        val offset = page * pageSize
        val results = metadataDao.getMetadataByExifFilter(
            cameraMake = filter.cameraMake,
            cameraModel = filter.cameraModel,
            lensModel = filter.lensModel,
            minFocalLength = filter.minFocalLength,
            maxFocalLength = filter.maxFocalLength,
            minAperture = filter.minAperture,
            maxAperture = filter.maxAperture,
            minIso = filter.minIso,
            maxIso = filter.maxIso,
            minShutterSpeed = filter.minShutterSpeed,
            maxShutterSpeed = filter.maxShutterSpeed,
            dateFrom = filter.dateFrom,
            dateTo = filter.dateTo,
            limit = pageSize,
            offset = offset
        )
        val totalCount = metadataDao.getMetadataByExifFilterCount(
            cameraMake = filter.cameraMake,
            cameraModel = filter.cameraModel,
            lensModel = filter.lensModel,
            minFocalLength = filter.minFocalLength,
            maxFocalLength = filter.maxFocalLength,
            minAperture = filter.minAperture,
            maxAperture = filter.maxAperture,
            minIso = filter.minIso,
            maxIso = filter.maxIso,
            minShutterSpeed = filter.minShutterSpeed,
            maxShutterSpeed = filter.maxShutterSpeed,
            dateFrom = filter.dateFrom,
            dateTo = filter.dateTo
        )

        FilterResult(
            imageIds = results.map { it.imageId },
            totalCount = totalCount,
            page = page,
            pageSize = pageSize
        )
    }

    // ================================================================
    // Filter by text search
    // ================================================================

    suspend fun filterByTextSearch(
        query: String,
        page: Int = 0,
        pageSize: Int = 50
    ): FilterResult = withContext(Dispatchers.IO) {
        val byName = elementDao.searchElementsByName(query).map { it.elementId }
        val byLabel = labelDao.searchLabels(query).map { it.imageId }
        val combined = (byName + byLabel).distinct()

        val totalCount = combined.size
        val offset = page * pageSize
        val paged = combined.drop(offset).take(pageSize)

        FilterResult(
            imageIds = paged,
            totalCount = totalCount,
            page = page,
            pageSize = pageSize
        )
    }

    // ================================================================
    // Filter by collection membership
    // ================================================================

    suspend fun filterByCollection(
        collectionId: Long,
        page: Int = 0,
        pageSize: Int = 50
    ): FilterResult = withContext(Dispatchers.IO) {
        val imageIds = collectionDao.getImageIdsInCollectionPaginated(collectionId, pageSize, page * pageSize)
        val totalCount = collectionDao.getImageCountInCollection(collectionId)

        FilterResult(
            imageIds = imageIds,
            totalCount = totalCount,
            page = page,
            pageSize = pageSize
        )
    }

    // ================================================================
    // Filter by semantic labels
    // ================================================================

    suspend fun filterByLabels(
        includeLabels: List<String>,
        excludeLabels: List<String> = emptyList(),
        page: Int = 0,
        pageSize: Int = 50
    ): FilterResult = withContext(Dispatchers.IO) {
        val imageIds = labelDao.getImageIdsByLabelFilter(includeLabels, excludeLabels)
        val totalCount = imageIds.size
        val offset = page * pageSize
        val paged = imageIds.drop(offset).take(pageSize)

        FilterResult(
            imageIds = paged,
            totalCount = totalCount,
            page = page,
            pageSize = pageSize
        )
    }

    // ================================================================
    // Filter combination (AND/OR)
    // ================================================================

    suspend fun filterCombined(
        combo: FilterCombo,
        page: Int = 0,
        pageSize: Int = 50
    ): FilterResult = withContext(Dispatchers.IO) {
        if (combo.filters.isEmpty()) {
            return@withContext FilterResult(emptyList(), 0, page, pageSize)
        }

        val resultsPerFilter = combo.filters.map { filter ->
            when (filter) {
                is ExifFilter -> filterByExif(filter, 0, Int.MAX_VALUE).imageIds.toSet()
                is DateTimeFilter -> {
                    val metadata = metadataDao.getMetadataByExifFilter(
                        null, null, null, null, null, null, null, null, null, null, null,
                        filter.startDate, filter.endDate, Int.MAX_VALUE, 0
                    )
                    metadata.map { it.imageId }.toSet()
                }
                else -> {
                    val predicate = filter.getPredicate()
                    val bindArgs = filter.getBindArgs()
                    elementDao.ftsSearchElements(SimpleSQLiteQuery(predicate, bindArgs)).map { it.elementId }.toSet()
                }
            }
        }

        val combined = when (combo.logic) {
            FilterLogic.AND -> resultsPerFilter.reduce { acc, set -> acc.intersect(set) }
            FilterLogic.OR -> resultsPerFilter.reduce { acc, set -> acc.union(set) }
        }

        val sorted = combined.toList().sorted()
        val totalCount = sorted.size
        val offset = page * pageSize
        val paged = sorted.drop(offset).take(pageSize)

        FilterResult(
            imageIds = paged,
            totalCount = totalCount,
            page = page,
            pageSize = pageSize
        )
    }

    // ================================================================
    // Full-featured filter with all options
    // ================================================================

    data class FullFilterOptions(
        val exif: ExifFilter? = null,
        val minRating: Int? = null,
        val maxRating: Int? = null,
        val exactRating: Int? = null,
        val imageTypes: List<ImageType>? = null,
        val includeLabels: List<String>? = null,
        val excludeLabels: List<String>? = null,
        val collectionId: Long? = null,
        val startDate: Long? = null,
        val endDate: Long? = null,
        val textQuery: String? = null,
        val logic: FilterLogic = FilterLogic.AND
    )

    suspend fun filterWithAllOptions(
        options: FullFilterOptions,
        page: Int = 0,
        pageSize: Int = 50
    ): FilterResult = withContext(Dispatchers.IO) {
        val resultSets = mutableListOf<Set<Long>>()

        options.exif?.let { exif ->
            val res = filterByExif(exif, 0, Int.MAX_VALUE)
            resultSets.add(res.imageIds.toSet())
        }

        if (options.minRating != null || options.exactRating != null) {
            val res = filterByRating(
                options.minRating ?: 0,
                options.maxRating ?: 5,
                options.exactRating,
                0, Int.MAX_VALUE
            )
            resultSets.add(res.imageIds.toSet())
        }

        options.imageTypes?.let { types ->
            val res = filterByFileType(types, 0, Int.MAX_VALUE)
            resultSets.add(res.imageIds.toSet())
        }

        if (options.startDate != null || options.endDate != null) {
            val res = filterByDateRange(
                options.startDate ?: 0L,
                options.endDate ?: Long.MAX_VALUE,
                0, Int.MAX_VALUE
            )
            resultSets.add(res.imageIds.toSet())
        }

        if (options.includeLabels != null || options.excludeLabels != null) {
            val res = filterByLabels(
                options.includeLabels ?: emptyList(),
                options.excludeLabels ?: emptyList(),
                0, Int.MAX_VALUE
            )
            resultSets.add(res.imageIds.toSet())
        }

        options.collectionId?.let { colId ->
            val res = filterByCollection(colId, 0, Int.MAX_VALUE)
            resultSets.add(res.imageIds.toSet())
        }

        options.textQuery?.let { query ->
            val res = filterByTextSearch(query, 0, Int.MAX_VALUE)
            resultSets.add(res.imageIds.toSet())
        }

        if (resultSets.isEmpty()) {
            return@withContext FilterResult(emptyList(), 0, page, pageSize)
        }

        val combined = when (options.logic) {
            FilterLogic.AND -> resultSets.reduce { acc, set -> acc.intersect(set) }
            FilterLogic.OR -> resultSets.reduce { acc, set -> acc.union(set) }
        }

        val sorted = combined.toList().sorted()
        val totalCount = sorted.size
        val offset = page * pageSize
        val paged = sorted.drop(offset).take(pageSize)

        FilterResult(
            imageIds = paged,
            totalCount = totalCount,
            page = page,
            pageSize = pageSize
        )
    }

    // ================================================================
    // Sort options
    // ================================================================

    enum class SortField {
        NAME, DATE, SIZE, RATING, FILE_TYPE
    }

    enum class SortOrder {
        ASC, DESC
    }

    suspend fun sortFilteredIds(
        imageIds: List<Long>,
        sortField: SortField,
        sortOrder: SortOrder = SortOrder.DESC
    ): List<Long> = withContext(Dispatchers.IO) {
        val metadataMap = mutableMapOf<Long, ImageMetadataEntity?>()
        val ratingMap = mutableMapOf<Long, Int>()

        for (id in imageIds) {
            metadataMap[id] = metadataDao.getMetadataByImageId(id)
            ratingMap[id] = ratingDao.getRatingByImageId(id)?.rating ?: 0
        }

        val sorted = when (sortField) {
            SortField.NAME -> imageIds.sortedBy { metadataMap[it]?.imageName ?: "" }
            SortField.DATE -> imageIds.sortedBy { metadataMap[it]?.captureDate ?: 0L }
            SortField.SIZE -> imageIds.sortedBy { metadataMap[it]?.fileSize ?: 0L }
            SortField.RATING -> imageIds.sortedBy { ratingMap[it] ?: 0 }
            SortField.FILE_TYPE -> imageIds.sortedBy { metadataMap[it]?.imageType ?: 0 }
        }

        if (sortOrder == SortOrder.DESC) sorted.reversed() else sorted
    }

    // ================================================================
    // Filter presets (save/load)
    // ================================================================

    suspend fun saveFilterPreset(name: String, combo: FilterCombo, isDefault: Boolean = false): Long =
        withContext(Dispatchers.IO) {
            filterDao.insertPreset(
                FilterPresetEntity(
                    name = name,
                    filterJson = combo.toJson(),
                    isDefault = isDefault
                )
            )
        }

    suspend fun loadFilterPreset(presetId: Long): FilterCombo? = withContext(Dispatchers.IO) {
        val entity = filterDao.getPresetById(presetId) ?: return@withContext null
        FilterPreset.fromEntity(entity).filterCombo
    }

    suspend fun getAllPresets(): List<FilterPresetEntity> = withContext(Dispatchers.IO) {
        filterDao.getAllPresets()
    }

    suspend fun deleteFilterPreset(presetId: Long) = withContext(Dispatchers.IO) {
        filterDao.deletePreset(presetId)
    }

    // ================================================================
    // Facet queries (for filter UI)
    // ================================================================

    suspend fun getCameraFacets(): List<CameraFacet> = withContext(Dispatchers.IO) {
        metadataDao.getCameraFacets().map {
            CameraFacet(make = it.cameraMake, model = it.cameraModel, count = it.count)
        }
    }

    suspend fun getLensFacets(): List<LensFacet> = withContext(Dispatchers.IO) {
        metadataDao.getLensFacets().map {
            LensFacet(model = it.lensModel, count = it.count)
        }
    }

    suspend fun getDateFacets(): List<DateFacet> = withContext(Dispatchers.IO) {
        metadataDao.getDateFacets().map {
            DateFacet(year = it.year.toIntOrNull() ?: 0, month = null, count = it.count)
        }
    }

    suspend fun getLabelFrequency(limit: Int = 50): List<LabelFrequency> = withContext(Dispatchers.IO) {
        labelDao.getLabelFrequency(limit).map {
            LabelFrequency(label = it.label, count = it.count)
        }
    }

    // ================================================================
    // Filter result data class
    // ================================================================

    data class FilterResult(
        val imageIds: List<Long>,
        val totalCount: Int,
        val page: Int,
        val pageSize: Int
    ) {
        val hasMore: Boolean get() = (page + 1) * pageSize < totalCount
        val totalPages: Int get() = if (totalCount == 0) 0 else (totalCount + pageSize - 1) / pageSize
    }
}
