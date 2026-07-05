package com.alcedo.studio.domain.service

import androidx.sqlite.db.SimpleSQLiteQuery
import com.alcedo.studio.data.local.*
import com.alcedo.studio.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Advanced filtering service for Sleeve images.
 * Provides EXIF-based filters, rating filters, file type filters,
 * semantic label filters, collection membership filters, and filter combinations.
 */
class SleeveFilterService(
    private val metadataDao: ImageMetadataDao,
    private val ratingDao: RatingDao,
    private val labelDao: SemanticLabelDao,
    private val collectionDao: CollectionDao,
    private val filterDao: FilterDao,
    private val filterV2Dao: FilterV2Dao,
    private val elementDao: SleeveElementDao
) {
    // ================================================================
    // EXIF-based filters
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
    // Rating filter
    // ================================================================

    suspend fun filterByRating(
        minStars: Int = 0,
        exactRating: Int? = null,
        page: Int = 0,
        pageSize: Int = 50
    ): FilterResult = withContext(Dispatchers.IO) {
        val ratings = if (exactRating != null) {
            ratingDao.getImagesByRating(exactRating)
        } else {
            ratingDao.getImagesByMinRating(minStars)
        }

        val totalCount = ratings.size
        val offset = page * pageSize
        val pagedRatings = ratings.drop(offset).take(pageSize)

        FilterResult(
            imageIds = pagedRatings.map { it.imageId },
            totalCount = totalCount,
            page = page,
            pageSize = pageSize
        )
    }

    // ================================================================
    // File type filter
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
    // Semantic label filter
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

    suspend fun filterByLabelFts(
        labelQuery: String,
        page: Int = 0,
        pageSize: Int = 50
    ): FilterResult = withContext(Dispatchers.IO) {
        val sanitized = labelQuery.replace("\"", "\"\"").replace("'", "''").trim()
        val imageIds = if (sanitized.isEmpty()) emptyList()
        else labelDao.ftsSearchImageIdsByLabel(SimpleSQLiteQuery(
            "SELECT DISTINCT image_id FROM semantic_labels WHERE label LIKE '%' || ? || '%'",
            arrayOf(sanitized)
        ))
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
    // Collection membership filter
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
    // Filter combination (AND/OR logic)
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
                    // For other filters, search by name FTS
                    elementDao.ftsSearchElements(SimpleSQLiteQuery(filter.getPredicate(), filter.getBindArgs())).map { it.elementId }.toSet()
                }
            }
        }

        val combined = when (combo.logic) {
            FilterLogic.AND -> {
                resultsPerFilter.reduce { acc, set -> acc.intersect(set) }
            }
            FilterLogic.OR -> {
                resultsPerFilter.reduce { acc, set -> acc.union(set) }
            }
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
        val exactRating: Int? = null,
        val imageTypes: List<ImageType>? = null,
        val includeLabels: List<String>? = null,
        val excludeLabels: List<String>? = null,
        val collectionId: Long? = null,
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
            val res = filterByRating(options.minRating ?: 0, options.exactRating, 0, Int.MAX_VALUE)
            resultSets.add(res.imageIds.toSet())
        }

        options.imageTypes?.let { types ->
            val res = filterByFileType(types, 0, Int.MAX_VALUE)
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

    suspend fun applyFilterPreset(presetId: Long, page: Int = 0, pageSize: Int = 50): FilterResult? =
        withContext(Dispatchers.IO) {
            val combo = loadFilterPreset(presetId) ?: return@withContext null
            filterCombined(combo, page, pageSize)
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

    suspend fun getFocalLengthRange(): FocalLengthFacet? = withContext(Dispatchers.IO) {
        metadataDao.getFocalLengthRange()?.let {
            FocalLengthFacet(
                range = "${it.minFl.toInt()}-${it.maxFl.toInt()}mm",
                min = it.minFl, max = it.maxFl, count = it.count
            )
        }
    }

    suspend fun getApertureRange(): ApertureFacet? = withContext(Dispatchers.IO) {
        metadataDao.getApertureRange()?.let {
            ApertureFacet(
                range = "f/${String.format("%.1f", it.minAp)}-f/${String.format("%.1f", it.maxAp)}",
                min = it.minAp, max = it.maxAp, count = it.count
            )
        }
    }

    suspend fun getIsoRange(): IsoFacet? = withContext(Dispatchers.IO) {
        metadataDao.getIsoRange()?.let {
            IsoFacet(range = "${it.minIso}-${it.maxIso}", min = it.minIso, max = it.maxIso, count = it.count)
        }
    }

    suspend fun getDateFacets(): List<DateFacet> = withContext(Dispatchers.IO) {
        metadataDao.getDateFacets().map {
            DateFacet(year = it.year.toIntOrNull() ?: 0, month = null, count = it.count)
        }
    }

    suspend fun getFileTypeDistribution(): List<Pair<ImageType, Int>> = withContext(Dispatchers.IO) {
        metadataDao.getFileTypeDistribution().map {
            ImageType.entries.getOrElse(it.imageType) { ImageType.DEFAULT } to it.count
        }
    }

    suspend fun getLabelFrequency(limit: Int = 50): List<LabelFrequency> = withContext(Dispatchers.IO) {
        labelDao.getLabelFrequency(limit).map {
            LabelFrequency(label = it.label, count = it.count)
        }
    }

    // ================================================================
    // FilterNode tree-based filtering
    // ================================================================

    suspend fun filterByNodeTree(
        rootNode: FilterNode,
        allImageIds: List<Long>,
        page: Int = 0,
        pageSize: Int = 50
    ): FilterResult = withContext(Dispatchers.IO) {
        val context = createEvaluationContext()
        val matchedIds = allImageIds.filter { imageId ->
            rootNode.evaluate(imageId, context)
        }.toSet()

        val totalCount = matchedIds.size
        val offset = page * pageSize
        val paged = matchedIds.toList().drop(offset).take(pageSize)

        FilterResult(
            imageIds = paged,
            totalCount = totalCount,
            page = page,
            pageSize = pageSize
        )
    }

    suspend fun filterByComboWithTree(
        combo: FilterCombo,
        page: Int = 0,
        pageSize: Int = 50
    ): FilterResult = withContext(Dispatchers.IO) {
        val rootNode = combo.rootNode
        if (rootNode != null) {
            val allIds = metadataDao.getAllMetadata().map { it.imageId }
            return@withContext filterByNodeTree(rootNode, allIds, page, pageSize)
        }
        // Fallback to flat filter logic
        filterCombined(combo, page, pageSize)
    }

    private suspend fun createEvaluationContext(): FilterEvaluationContext {
        // 预加载全部图像元数据，供叶子节点同步评估时使用
        val metadataCache = metadataDao.getAllMetadata().associateBy { it.imageId }
        return object : FilterEvaluationContext {
            private val ratingCache = mutableMapOf<Long, Int>()
            private val labelCache = mutableMapOf<Long, List<String>>()
            private val collectionCache = mutableMapOf<Long, Set<Long>>()

            override fun evaluateLeaf(imageId: Long, filter: SleeveFilter): Boolean {
                val imageMeta = metadataCache[imageId] ?: return false
                return when (filter) {
                    is RatingFilterNode -> {
                        val rating = ratingCache[imageId] ?: 0
                        rating in filter.minRating..filter.maxRating
                    }
                    is DateRangeFilterNode -> {
                        imageMeta.captureDate in filter.from..filter.to
                    }
                    is CameraFilterNode -> {
                        val hasCondition = filter.cameraMake != null || filter.cameraModel != null
                        if (!hasCondition) {
                            true
                        } else {
                            val makeMatch = filter.cameraMake?.let {
                                imageMeta.cameraMake.contains(it, ignoreCase = true)
                            } ?: false
                            val modelMatch = filter.cameraModel?.let {
                                imageMeta.cameraModel.contains(it, ignoreCase = true)
                            } ?: false
                            makeMatch || modelMatch
                        }
                    }
                    is LensFilterNode -> {
                        filter.lensModel?.let {
                            imageMeta.lensModel.contains(it, ignoreCase = true)
                        } ?: true
                    }
                    is TagFilterNode -> {
                        val labels = labelCache[imageId] ?: emptyList()
                        filter.tags.any { it in labels }
                    }
                    is CollectionFilterNode -> {
                        val collections = collectionCache[imageId] ?: emptySet()
                        filter.collectionId in collections
                    }
                    is FileTypeFilterNode -> {
                        if (filter.imageTypes.isEmpty()) {
                            true
                        } else {
                            val typeName = ImageType.entries
                                .getOrElse(imageMeta.imageType) { ImageType.DEFAULT }.name
                            filter.imageTypes.any { type ->
                                type.equals(typeName, ignoreCase = true) ||
                                    imageMeta.imageName.endsWith(".$type", ignoreCase = true)
                            }
                        }
                    }
                    else -> {
                        // Default: cannot evaluate without DB access
                        true
                    }
                }
            }
        }
    }

    // ================================================================
    // FilterCombo persistence (desktop schema - FilterEntity)
    // ================================================================

    suspend fun saveFilterCombo(name: String, combo: FilterCombo): Long =
        withContext(Dispatchers.IO) {
            filterV2Dao.insertFilter(
                FilterEntity(
                    name = name,
                    filterJson = combo.toJson()
                )
            )
        }

    suspend fun loadFilterCombo(filterId: Long): FilterCombo? = withContext(Dispatchers.IO) {
        val entity = filterV2Dao.getFilterById(filterId) ?: return@withContext null
        FilterCombo.fromEntity(entity)
    }

    suspend fun loadFilterComboByName(name: String): FilterCombo? = withContext(Dispatchers.IO) {
        val entity = filterV2Dao.getFilterByName(name) ?: return@withContext null
        FilterCombo.fromEntity(entity)
    }

    suspend fun getAllFilterCombos(): List<Pair<Long, String>> = withContext(Dispatchers.IO) {
        filterV2Dao.getAllFilters().map { it.filterId to it.name }
    }

    suspend fun deleteFilterCombo(filterId: Long) = withContext(Dispatchers.IO) {
        filterV2Dao.deleteFilterById(filterId)
    }

    suspend fun applyFilterCombo(
        filterId: Long,
        page: Int = 0,
        pageSize: Int = 50
    ): FilterResult? = withContext(Dispatchers.IO) {
        val combo = loadFilterCombo(filterId) ?: return@withContext null
        filterByComboWithTree(combo, page, pageSize)
    }

    suspend fun applyFilterComboToAlbum(
        combo: FilterCombo,
        page: Int = 0,
        pageSize: Int = 50
    ): FilterResult = withContext(Dispatchers.IO) {
        filterByComboWithTree(combo, page, pageSize)
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