package com.alcedo.studio.data.repository

import com.alcedo.studio.data.dao.ImageDao
import com.alcedo.studio.data.local.ImageEntity
import com.alcedo.studio.data.model.ColorLabel
import com.alcedo.studio.data.model.FilterCombo
import com.alcedo.studio.data.model.ImageFlag
import com.alcedo.studio.data.model.ImageItem
import com.alcedo.studio.data.model.SortDescriptor
import com.alcedo.studio.data.model.SortField
import com.alcedo.studio.domain.repository.ImageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [ImageRepository]. Translates between the
 * [ImageEntity] persistence layer and the domain [ImageItem] model, and maps
 * [FilterCombo]/[SortDescriptor] onto SQL queries.
 */
@Singleton
class ImageRepositoryImpl @Inject constructor(
    private val imageDao: ImageDao,
) : ImageRepository {

    override fun observeImagesInFolder(folderPath: String): Flow<List<ImageItem>> =
        imageDao.observeByFolder(folderPath).map { list -> list.map { it.toDomain() } }

    override fun observeAllImages(): Flow<List<ImageItem>> =
        imageDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeImage(id: String): Flow<ImageItem?> =
        imageDao.observeById(id).map { it?.toDomain() }

    override suspend fun getImage(id: String): ImageItem? = imageDao.getById(id)?.toDomain()

    override suspend fun queryImages(
        filter: FilterCombo,
        sort: SortDescriptor,
        limit: Int,
        offset: Int,
    ): List<ImageItem> {
        val results = imageDao.queryFiltered(
            folderPath = filter.folderPath,
            includeHidden = if (filter.includeHidden) 1 else 0,
            ratingMin = filter.ratingMin.takeIf { it > 0 },
            ratingMax = filter.ratingMax.takeIf { it < 5 },
            searchText = filter.searchText?.takeIf { it.isNotBlank() },
            sortField = sort.field.name,
            limit = limit,
            offset = offset,
        )
        val mapped = results.map { it.toDomain() }
        // Apply the remaining in-memory predicates the SQL cannot express.
        return mapped.filter { item ->
            (filter.flags.isEmpty() || item.flag in filter.flags) &&
                (filter.colorLabels.isEmpty() || item.colorLabel in filter.colorLabels) &&
                (filter.dateFrom == null || item.dateCapturedEpoch >= filter.dateFrom) &&
                (filter.dateTo == null || item.dateCapturedEpoch <= filter.dateTo) &&
                (filter.fileExtensions.isEmpty() ||
                    item.fileExtension.lowercase() in filter.fileExtensions) &&
                (filter.semanticTags.isEmpty() ||
                    filter.semanticTags.any { tag -> item.aiTags.contains(tag) })
        }
    }

    override suspend fun upsert(image: ImageItem) = imageDao.upsert(image.toEntity())

    override suspend fun upsertAll(images: List<ImageItem>) =
        imageDao.upsertAll(images.map { it.toEntity() })

    override suspend fun delete(id: String) = imageDao.deleteById(id)

    override suspend fun setRating(id: String, rating: Int) = imageDao.setRating(id, rating)

    override suspend fun setFlag(id: String, flag: ImageFlag) = imageDao.setFlag(id, flag.name)

    override suspend fun setColorLabel(id: String, label: ColorLabel) =
        imageDao.setColorLabel(id, label.name)

    override suspend fun setHidden(id: String, hidden: Boolean) = imageDao.setHidden(id, hidden)

    override suspend fun setCurrentVersion(id: String, versionId: String) =
        imageDao.setCurrentVersion(id, versionId)

    override suspend fun setThumbnailPath(id: String, path: String?) =
        imageDao.setThumbnailPath(id, path)

    override suspend fun setAiMetadata(id: String, caption: String?, tags: List<String>, score: Float?) =
        imageDao.setAiMetadata(id, caption, tags.joinToString("\u0001"), score)

    override suspend fun count(): Int = imageDao.count()
    override suspend fun rawCount(): Int = imageDao.rawCount()
    override suspend fun pickCount(): Int = imageDao.pickCount()
    override suspend fun rejectCount(): Int = imageDao.rejectCount()
    override suspend fun distinctCameras(): List<String> = imageDao.distinctCameras()
    override suspend fun distinctLenses(): List<String> = imageDao.distinctLenses()

    private fun ImageEntity.toDomain(): ImageItem = ImageItem(
        id = id,
        sleevePath = sleevePath,
        originalUri = originalUri,
        displayName = displayName,
        fileExtension = fileExtension,
        fileSizeBytes = fileSizeBytes,
        width = width,
        height = height,
        dateAddedEpoch = dateAddedEpoch,
        dateCapturedEpoch = dateCapturedEpoch,
        rating = rating,
        flag = flag,
        colorLabel = colorLabel,
        isRaw = isRaw,
        isVirtualCopy = isVirtualCopy,
        parentId = parentId,
        thumbnailPath = thumbnailPath,
        currentVersionId = currentVersionId,
        aiCaption = aiCaption,
        aiTags = aiTags?.split("\u0001")?.filter { it.isNotEmpty() } ?: emptyList(),
        aiScore = aiScore,
        isHidden = isHidden,
        lensModel = lensModel,
        cameraModel = cameraModel,
        focalLength = focalLength,
        iso = iso,
        aperture = aperture,
        shutterSpeed = shutterSpeed,
    )

    private fun ImageItem.toEntity(): ImageEntity = ImageEntity(
        id = id,
        sleevePath = sleevePath,
        originalUri = originalUri,
        displayName = displayName,
        fileExtension = fileExtension,
        fileSizeBytes = fileSizeBytes,
        width = width,
        height = height,
        dateAddedEpoch = dateAddedEpoch,
        dateCapturedEpoch = dateCapturedEpoch,
        rating = rating,
        flag = flag,
        colorLabel = colorLabel,
        isRaw = isRaw,
        isVirtualCopy = isVirtualCopy,
        parentId = parentId,
        thumbnailPath = thumbnailPath,
        currentVersionId = currentVersionId,
        aiCaption = aiCaption,
        aiTags = aiTags.joinToString("\u0001"),
        aiScore = aiScore,
        isHidden = isHidden,
        lensModel = lensModel,
        cameraModel = cameraModel,
        focalLength = focalLength,
        iso = iso,
        aperture = aperture,
        shutterSpeed = shutterSpeed,
    )
}
