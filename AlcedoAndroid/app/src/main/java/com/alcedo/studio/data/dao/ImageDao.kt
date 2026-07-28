package com.alcedo.studio.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.alcedo.studio.data.local.ImageEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access for the projected image catalog. Supports filtered/paged queries
 * used by the album grid, rating/flag updates, and sleeve-path lookups.
 */
@Dao
interface ImageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(image: ImageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(images: List<ImageEntity>)

    @Update
    suspend fun update(image: ImageEntity)

    @Delete
    suspend fun delete(image: ImageEntity)

    @Query("DELETE FROM images WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM images WHERE id = :id")
    suspend fun getById(id: String): ImageEntity?

    @Query("SELECT * FROM images WHERE id = :id")
    fun observeById(id: String): Flow<ImageEntity?>

    @Query("SELECT * FROM images WHERE sleevePath = :folderPath ORDER BY dateCapturedEpoch DESC")
    fun observeByFolder(folderPath: String): Flow<List<ImageEntity>>

    @Query("SELECT * FROM images ORDER BY dateCapturedEpoch DESC")
    fun observeAll(): Flow<List<ImageEntity>>

    @Query(
        """
        SELECT * FROM images
        WHERE (:folderPath IS NULL OR sleevePath = :folderPath)
          AND (:includeHidden = 1 OR isHidden = 0)
          AND (:ratingMin IS NULL OR rating >= :ratingMin)
          AND (:ratingMax IS NULL OR rating <= :ratingMax)
          AND (:searchText IS NULL OR displayName LIKE '%' || :searchText || '%' OR aiCaption LIKE '%' || :searchText || '%')
        ORDER BY
            CASE WHEN :sortField = 'DATE_CAPTURED' THEN dateCapturedEpoch END DESC,
            CASE WHEN :sortField = 'DATE_ADDED' THEN dateAddedEpoch END DESC,
            CASE WHEN :sortField = 'NAME' THEN displayName END ASC,
            CASE WHEN :sortField = 'RATING' THEN rating END DESC,
            CASE WHEN :sortField = 'FILE_SIZE' THEN fileSizeBytes END DESC,
            CASE WHEN :sortField = 'AI_SCORE' THEN aiScore END DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun queryFiltered(
        folderPath: String?,
        includeHidden: Int,
        ratingMin: Int?,
        ratingMax: Int?,
        searchText: String?,
        sortField: String,
        limit: Int,
        offset: Int,
    ): List<ImageEntity>

    @Query("UPDATE images SET rating = :rating WHERE id = :id")
    suspend fun setRating(id: String, rating: Int)

    @Query("UPDATE images SET flag = :flag WHERE id = :id")
    suspend fun setFlag(id: String, flag: String)

    @Query("UPDATE images SET colorLabel = :label WHERE id = :id")
    suspend fun setColorLabel(id: String, label: String)

    @Query("UPDATE images SET currentVersionId = :versionId WHERE id = :id")
    suspend fun setCurrentVersion(id: String, versionId: String)

    @Query("UPDATE images SET aiCaption = :caption, aiTags = :tags, aiScore = :score WHERE id = :id")
    suspend fun setAiMetadata(id: String, caption: String?, tags: String?, score: Float?)

    @Query("UPDATE images SET thumbnailPath = :path WHERE id = :id")
    suspend fun setThumbnailPath(id: String, path: String?)

    @Query("UPDATE images SET isHidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean)

    @Query("SELECT COUNT(*) FROM images")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM images WHERE isRaw = 1")
    suspend fun rawCount(): Int

    @Query("SELECT COUNT(*) FROM images WHERE flag = 'PICK'")
    suspend fun pickCount(): Int

    @Query("SELECT COUNT(*) FROM images WHERE flag = 'REJECT'")
    suspend fun rejectCount(): Int

    @Query("SELECT * FROM images WHERE parentId = :parentId")
    suspend fun getVirtualCopies(parentId: String): List<ImageEntity>

    @Query("SELECT DISTINCT cameraModel FROM images WHERE cameraModel IS NOT NULL")
    suspend fun distinctCameras(): List<String>

    @Query("SELECT DISTINCT lensModel FROM images WHERE lensModel IS NOT NULL")
    suspend fun distinctLenses(): List<String>

    @Transaction
    suspend fun replaceAll(images: List<ImageEntity>) {
        upsertAll(images)
    }
}
