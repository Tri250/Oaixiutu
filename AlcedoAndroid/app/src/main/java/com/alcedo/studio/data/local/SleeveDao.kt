package com.alcedo.studio.data.local

import androidx.room.*
import com.alcedo.studio.data.model.*
import kotlinx.coroutines.flow.Flow

// ================================================================
// SleeveElementDao - CRUD for sleeve elements
// ================================================================

@Dao
interface SleeveElementDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertElement(element: SleeveElementEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertElements(elements: List<SleeveElementEntity>)

    @Update
    suspend fun updateElement(element: SleeveElementEntity)

    @Delete
    suspend fun deleteElement(element: SleeveElementEntity)

    @Query("DELETE FROM sleeve_elements WHERE element_id = :elementId")
    suspend fun deleteElementById(elementId: Long)

    @Query("DELETE FROM sleeve_elements WHERE parent_id = :parentId")
    suspend fun deleteChildrenByParentId(parentId: Long)

    @Query("SELECT * FROM sleeve_elements WHERE element_id = :elementId")
    suspend fun getElementById(elementId: Long): SleeveElementEntity?

    @Query("SELECT * FROM sleeve_elements WHERE element_id = :elementId")
    fun observeElementById(elementId: Long): Flow<SleeveElementEntity?>

    @Query("SELECT * FROM sleeve_elements WHERE parent_id = :parentId ORDER BY element_type ASC, element_name ASC")
    suspend fun getChildrenByParentId(parentId: Long): List<SleeveElementEntity>

    @Query("SELECT * FROM sleeve_elements WHERE parent_id = :parentId ORDER BY element_type ASC, element_name ASC")
    fun observeChildrenByParentId(parentId: Long): Flow<List<SleeveElementEntity>>

    @Query("""
        SELECT * FROM sleeve_elements 
        WHERE parent_id = :parentId 
        ORDER BY element_type ASC, element_name ASC 
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getChildrenByParentIdPaginated(parentId: Long, limit: Int, offset: Int): List<SleeveElementEntity>

    @Query("SELECT COUNT(*) FROM sleeve_elements WHERE parent_id = :parentId")
    suspend fun getChildCount(parentId: Long): Int

    @Query("SELECT * FROM sleeve_elements WHERE parent_id IS NULL ORDER BY element_type ASC, element_name ASC")
    suspend fun getRootElements(): List<SleeveElementEntity>

    @Query("SELECT * FROM sleeve_elements WHERE parent_id IS NULL ORDER BY element_type ASC, element_name ASC")
    fun observeRootElements(): Flow<List<SleeveElementEntity>>

    @Query("SELECT * FROM sleeve_elements WHERE element_type = :type ORDER BY element_name ASC")
    suspend fun getElementsByType(type: Int): List<SleeveElementEntity>

    @Query("SELECT * FROM sleeve_elements WHERE element_name LIKE '%' || :query || '%' ORDER BY element_name ASC")
    suspend fun searchElementsByName(query: String): List<SleeveElementEntity>

    @Query("SELECT * FROM sleeve_elements WHERE element_name LIKE '%' || :query || '%' ORDER BY element_name ASC")
    fun observeSearchElementsByName(query: String): Flow<List<SleeveElementEntity>>

    @Query("SELECT * FROM sleeve_elements WHERE pinned = 1 ORDER BY added_time DESC")
    suspend fun getPinnedElements(): List<SleeveElementEntity>

    @Query("UPDATE sleeve_elements SET pinned = :pinned WHERE element_id = :elementId")
    suspend fun setPinned(elementId: Long, pinned: Boolean)

    @Query("UPDATE sleeve_elements SET ref_count = ref_count + 1 WHERE element_id = :elementId")
    suspend fun incrementRefCount(elementId: Long)

    @Query("UPDATE sleeve_elements SET ref_count = MAX(0, ref_count - 1) WHERE element_id = :elementId")
    suspend fun decrementRefCount(elementId: Long)

    @Query("UPDATE sleeve_elements SET element_name = :newName, last_modified_time = :now WHERE element_id = :elementId")
    suspend fun renameElement(elementId: Long, newName: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE sleeve_elements SET parent_id = :newParentId, last_modified_time = :now WHERE element_id = :elementId")
    suspend fun moveElement(elementId: Long, newParentId: Long?, now: Long = System.currentTimeMillis())

    @Query("UPDATE sleeve_elements SET sync_flag = :syncFlag WHERE element_id = :elementId")
    suspend fun setSyncFlag(elementId: Long, syncFlag: Int)

    @Query("SELECT COUNT(*) FROM sleeve_elements")
    suspend fun getTotalElementCount(): Int

    @Query("SELECT COUNT(*) FROM sleeve_elements WHERE element_type = 0")
    suspend fun getTotalFileCount(): Int

    @Query("SELECT COUNT(*) FROM sleeve_elements WHERE element_type = 1")
    suspend fun getTotalFolderCount(): Int

    // FTS queries
    @Query("SELECT * FROM sleeve_elements WHERE element_name LIKE '%' || :query || '%' ORDER BY element_name ASC")
    suspend fun ftsSearchElements(query: String): List<SleeveElementEntity>

    @Query("DELETE FROM sleeve_elements")
    suspend fun deleteAllElements()

    // Hierarchy queries
    @Query("""
        WITH RECURSIVE ancestors AS (
            SELECT * FROM sleeve_elements WHERE element_id = :elementId
            UNION ALL
            SELECT e.* FROM sleeve_elements e INNER JOIN ancestors a ON e.element_id = a.parent_id
        )
        SELECT * FROM ancestors WHERE element_id != :elementId
    """)
    suspend fun getAncestors(elementId: Long): List<SleeveElementEntity>

    @Query("""
        WITH RECURSIVE descendants AS (
            SELECT element_id, parent_id FROM sleeve_elements WHERE parent_id = :elementId
            UNION ALL
            SELECT e.element_id, e.parent_id 
            FROM sleeve_elements e INNER JOIN descendants d ON e.parent_id = d.element_id
        )
        SELECT COUNT(*) FROM descendants
    """)
    suspend fun getDescendantCount(elementId: Long): Int
}

// ================================================================
// SleeveFileDao - File-specific queries
// ================================================================

@Dao
interface SleeveFileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: SleeveFileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<SleeveFileEntity>)

    @Update
    suspend fun updateFile(file: SleeveFileEntity)

    @Query("SELECT * FROM sleeve_files WHERE element_id = :elementId")
    suspend fun getFileByElementId(elementId: Long): SleeveFileEntity?

    @Query("SELECT * FROM sleeve_files WHERE image_id = :imageId")
    suspend fun getFileByImageId(imageId: Long): SleeveFileEntity?

    @Query("SELECT * FROM sleeve_files WHERE file_path = :path")
    suspend fun getFileByPath(path: String): SleeveFileEntity?

    @Query("SELECT * FROM sleeve_files WHERE checksum = :checksum AND checksum != 0")
    suspend fun getFilesByChecksum(checksum: Long): List<SleeveFileEntity>

    @Query("""
        SELECT sf.* FROM sleeve_files sf
        INNER JOIN sleeve_elements se ON sf.element_id = se.element_id
        WHERE se.parent_id = :parentId
        ORDER BY se.element_name ASC
    """)
    suspend fun getFilesInFolder(parentId: Long): List<SleeveFileEntity>

    @Query("""
        SELECT sf.* FROM sleeve_files sf
        INNER JOIN sleeve_elements se ON sf.element_id = se.element_id
        WHERE se.parent_id = :parentId
        ORDER BY se.element_name ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getFilesInFolderPaginated(parentId: Long, limit: Int, offset: Int): List<SleeveFileEntity>

    @Query("""
        SELECT sf.* FROM sleeve_files sf
        INNER JOIN sleeve_elements se ON sf.element_id = se.element_id
        WHERE se.parent_id = :parentId AND sf.file_extension IN (:extensions)
        ORDER BY se.element_name ASC
    """)
    suspend fun getFilesInFolderByExtension(parentId: Long, extensions: List<String>): List<SleeveFileEntity>

    @Query("SELECT * FROM sleeve_files WHERE file_extension = :extension")
    suspend fun getFilesByExtension(extension: String): List<SleeveFileEntity>

    @Query("DELETE FROM sleeve_files WHERE element_id = :elementId")
    suspend fun deleteFileByElementId(elementId: Long)

    @Query("UPDATE sleeve_files SET has_thumbnail = :hasThumbnail WHERE element_id = :elementId")
    suspend fun setHasThumbnail(elementId: Long, hasThumbnail: Boolean)

    @Query("UPDATE sleeve_files SET has_full_image = :hasFullImage WHERE element_id = :elementId")
    suspend fun setHasFullImage(elementId: Long, hasFullImage: Boolean)

    @Query("SELECT COUNT(*) FROM sleeve_files")
    suspend fun getFileCount(): Int

    // Path resolution
    @Query("""
        SELECT sf.file_path FROM sleeve_files sf
        WHERE sf.element_id = :elementId
    """)
    suspend fun getFilePath(elementId: Long): String?

    @Query("""
        SELECT sf.* FROM sleeve_files sf
        INNER JOIN sleeve_elements se ON sf.element_id = se.element_id
        WHERE se.element_name = :fileName AND se.parent_id = :parentId
    """)
    suspend fun getFileByNameInFolder(fileName: String, parentId: Long): SleeveFileEntity?
}

// ================================================================
// SleeveFolderDao - Folder-specific queries
// ================================================================

@Dao
interface SleeveFolderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: SleeveFolderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolders(folders: List<SleeveFolderEntity>)

    @Update
    suspend fun updateFolder(folder: SleeveFolderEntity)

    @Query("SELECT * FROM sleeve_folders WHERE element_id = :elementId")
    suspend fun getFolderByElementId(elementId: Long): SleeveFolderEntity?

    @Query("SELECT * FROM sleeve_folders WHERE element_id = :elementId")
    fun observeFolderByElementId(elementId: Long): Flow<SleeveFolderEntity?>

    @Query("""
        SELECT sf.* FROM sleeve_folders sf
        INNER JOIN sleeve_elements se ON sf.element_id = se.element_id
        WHERE se.parent_id = :parentId
        ORDER BY se.element_name ASC
    """)
    suspend fun getSubFolders(parentId: Long): List<SleeveFolderEntity>

    @Query("""
        SELECT sf.* FROM sleeve_folders sf
        INNER JOIN sleeve_elements se ON sf.element_id = se.element_id
        WHERE se.parent_id = :parentId
        ORDER BY se.element_name ASC
    """)
    fun observeSubFolders(parentId: Long): Flow<List<SleeveFolderEntity>>

    @Query("DELETE FROM sleeve_folders WHERE element_id = :elementId")
    suspend fun deleteFolderByElementId(elementId: Long)

    @Query("""
        UPDATE sleeve_folders SET 
            child_count = (SELECT COUNT(*) FROM sleeve_elements WHERE parent_id = :elementId),
            file_count = (SELECT COUNT(*) FROM sleeve_elements WHERE parent_id = :elementId AND element_type = 0),
            folder_count = (SELECT COUNT(*) FROM sleeve_elements WHERE parent_id = :elementId AND element_type = 1)
        WHERE element_id = :elementId
    """)
    suspend fun recalculateCounts(elementId: Long)

    @Query("UPDATE sleeve_folders SET children_loaded = :loaded WHERE element_id = :elementId")
    suspend fun setChildrenLoaded(elementId: Long, loaded: Boolean)

    @Query("UPDATE sleeve_folders SET thumbnail_element_id = :thumbnailId WHERE element_id = :elementId")
    suspend fun setThumbnail(elementId: Long, thumbnailId: Long?)

    @Query("UPDATE sleeve_folders SET default_filter_id = :filterId WHERE element_id = :elementId")
    suspend fun setDefaultFilter(elementId: Long, filterId: Long)

    // Folder tree queries
    @Query("""
        WITH RECURSIVE folder_tree AS (
            SELECT sf.*, se.element_name, se.parent_id, 0 AS depth
            FROM sleeve_folders sf
            INNER JOIN sleeve_elements se ON sf.element_id = se.element_id
            WHERE sf.element_id = :rootId
            UNION ALL
            SELECT sf.*, se.element_name, se.parent_id, ft.depth + 1
            FROM sleeve_folders sf
            INNER JOIN sleeve_elements se ON sf.element_id = se.element_id
            INNER JOIN folder_tree ft ON se.parent_id = ft.element_id
        )
        SELECT * FROM folder_tree ORDER BY depth ASC, element_name ASC
    """)
    @RewriteQueriesToDropUnusedColumns
    suspend fun getFolderTree(rootId: Long): List<SleeveFolderEntity>

    @Query("""
        WITH RECURSIVE folder_path AS (
            SELECT sf.element_id, se.element_name, se.parent_id, 0 AS depth
            FROM sleeve_folders sf
            INNER JOIN sleeve_elements se ON sf.element_id = se.element_id
            WHERE sf.element_id = :folderId
            UNION ALL
            SELECT se.element_id, se.element_name, se.parent_id, fp.depth + 1
            FROM sleeve_elements se
            INNER JOIN folder_path fp ON se.element_id = fp.parent_id
        )
        SELECT element_name FROM folder_path ORDER BY depth DESC
    """)
    suspend fun getFolderPath(folderId: Long): List<String>
}

// ================================================================
// ImageMetadataDao - EXIF and metadata queries
// ================================================================

@Dao
interface ImageMetadataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: ImageMetadataEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadataBatch(metadata: List<ImageMetadataEntity>)

    @Update
    suspend fun updateMetadata(metadata: ImageMetadataEntity)

    @Query("SELECT * FROM image_metadata WHERE image_id = :imageId")
    suspend fun getMetadataByImageId(imageId: Long): ImageMetadataEntity?

    @Query("SELECT * FROM image_metadata WHERE image_id = :imageId")
    fun observeMetadataByImageId(imageId: Long): Flow<ImageMetadataEntity?>

    @Query("SELECT * FROM image_metadata WHERE image_path = :path")
    suspend fun getMetadataByPath(path: String): ImageMetadataEntity?

    @Query("SELECT * FROM image_metadata WHERE checksum = :checksum AND checksum != 0")
    suspend fun getMetadataByChecksum(checksum: Long): List<ImageMetadataEntity>

    @Query("DELETE FROM image_metadata WHERE image_id = :imageId")
    suspend fun deleteMetadataByImageId(imageId: Long)

    @Query("SELECT * FROM image_metadata ORDER BY image_name ASC")
    suspend fun getAllMetadata(): List<ImageMetadataEntity>

    @Query("SELECT * FROM image_metadata ORDER BY image_name ASC")
    fun observeAllMetadata(): Flow<List<ImageMetadataEntity>>

    @Query("SELECT * FROM image_metadata ORDER BY imported_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getMetadataPaginated(limit: Int, offset: Int): List<ImageMetadataEntity>

    @Query("SELECT COUNT(*) FROM image_metadata")
    suspend fun getMetadataCount(): Int

    // EXIF facet queries
    @Query("SELECT camera_make, camera_model, COUNT(*) as cnt FROM image_metadata WHERE camera_make != '' GROUP BY camera_make, camera_model ORDER BY cnt DESC")
    suspend fun getCameraFacets(): List<CameraFacetResult>

    @Query("SELECT lens_model, COUNT(*) as cnt FROM image_metadata WHERE lens_model != '' GROUP BY lens_model ORDER BY cnt DESC")
    suspend fun getLensFacets(): List<LensFacetResult>

    @Query("SELECT MIN(focal_length) as min_fl, MAX(focal_length) as max_fl, COUNT(*) as cnt FROM image_metadata WHERE focal_length > 0")
    suspend fun getFocalLengthRange(): FocalLengthRangeResult?

    @Query("SELECT MIN(aperture) as min_ap, MAX(aperture) as max_ap, COUNT(*) as cnt FROM image_metadata WHERE aperture > 0")
    suspend fun getApertureRange(): ApertureRangeResult?

    @Query("SELECT MIN(iso) as min_iso, MAX(iso) as max_iso, COUNT(*) as cnt FROM image_metadata WHERE iso > 0")
    suspend fun getIsoRange(): IsoRangeResult?

    @Query("SELECT MIN(capture_date) as min_date, MAX(capture_date) as max_date, COUNT(*) as cnt FROM image_metadata WHERE capture_date > 0")
    suspend fun getDateRange(): DateRangeResult?

    @Query("""
        SELECT strftime('%Y', capture_date / 1000, 'unixepoch') as year, 
               COUNT(*) as cnt 
        FROM image_metadata WHERE capture_date > 0 
        GROUP BY year ORDER BY year DESC
    """)
    suspend fun getDateFacets(): List<DateFacetResult>

    @Query("SELECT image_type, COUNT(*) as cnt FROM image_metadata GROUP BY image_type ORDER BY cnt DESC")
    suspend fun getFileTypeDistribution(): List<FileTypeDistributionResult>

    @Query("SELECT * FROM image_metadata WHERE image_type = :imageType ORDER BY image_name ASC")
    suspend fun getMetadataByImageType(imageType: Int): List<ImageMetadataEntity>

    // EXIF-based queries with filters
    @Query("""
        SELECT * FROM image_metadata 
        WHERE (:cameraMake IS NULL OR camera_make LIKE '%' || :cameraMake || '%')
        AND (:cameraModel IS NULL OR camera_model LIKE '%' || :cameraModel || '%')
        AND (:lensModel IS NULL OR lens_model LIKE '%' || :lensModel || '%')
        AND (:minFocalLength IS NULL OR focal_length >= :minFocalLength)
        AND (:maxFocalLength IS NULL OR focal_length <= :maxFocalLength)
        AND (:minAperture IS NULL OR aperture >= :minAperture)
        AND (:maxAperture IS NULL OR aperture <= :maxAperture)
        AND (:minIso IS NULL OR iso >= :minIso)
        AND (:maxIso IS NULL OR iso <= :maxIso)
        AND (:minShutterSpeed IS NULL OR shutter_speed >= :minShutterSpeed)
        AND (:maxShutterSpeed IS NULL OR shutter_speed <= :maxShutterSpeed)
        AND (:dateFrom IS NULL OR capture_date >= :dateFrom)
        AND (:dateTo IS NULL OR capture_date <= :dateTo)
        ORDER BY image_name ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getMetadataByExifFilter(
        cameraMake: String?, cameraModel: String?, lensModel: String?,
        minFocalLength: Float?, maxFocalLength: Float?,
        minAperture: Float?, maxAperture: Float?,
        minIso: Int?, maxIso: Int?,
        minShutterSpeed: Float?, maxShutterSpeed: Float?,
        dateFrom: Long?, dateTo: Long?,
        limit: Int, offset: Int
    ): List<ImageMetadataEntity>

    @Query("""
        SELECT COUNT(*) FROM image_metadata 
        WHERE (:cameraMake IS NULL OR camera_make LIKE '%' || :cameraMake || '%')
        AND (:cameraModel IS NULL OR camera_model LIKE '%' || :cameraModel || '%')
        AND (:lensModel IS NULL OR lens_model LIKE '%' || :lensModel || '%')
        AND (:minFocalLength IS NULL OR focal_length >= :minFocalLength)
        AND (:maxFocalLength IS NULL OR focal_length <= :maxFocalLength)
        AND (:minAperture IS NULL OR aperture >= :minAperture)
        AND (:maxAperture IS NULL OR aperture <= :maxAperture)
        AND (:minIso IS NULL OR iso >= :minIso)
        AND (:maxIso IS NULL OR iso <= :maxIso)
        AND (:minShutterSpeed IS NULL OR shutter_speed >= :minShutterSpeed)
        AND (:maxShutterSpeed IS NULL OR shutter_speed <= :maxShutterSpeed)
        AND (:dateFrom IS NULL OR capture_date >= :dateFrom)
        AND (:dateTo IS NULL OR capture_date <= :dateTo)
    """)
    suspend fun getMetadataByExifFilterCount(
        cameraMake: String?, cameraModel: String?, lensModel: String?,
        minFocalLength: Float?, maxFocalLength: Float?,
        minAperture: Float?, maxAperture: Float?,
        minIso: Int?, maxIso: Int?,
        minShutterSpeed: Float?, maxShutterSpeed: Float?,
        dateFrom: Long?, dateTo: Long?
    ): Int

    @Query("UPDATE image_metadata SET last_accessed_at = :now WHERE image_id = :imageId")
    suspend fun updateLastAccessed(imageId: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE image_metadata SET rating = :rating WHERE image_id = :imageId")
    suspend fun updateRating(imageId: Long, rating: Int)

    @Query("SELECT * FROM image_metadata ORDER BY rating DESC LIMIT :limit")
    suspend fun getTopRated(limit: Int): List<ImageMetadataEntity>

    @Query("SELECT * FROM image_metadata ORDER BY imported_at DESC LIMIT :limit")
    suspend fun getRecentlyImported(limit: Int): List<ImageMetadataEntity>
}

// ================================================================
// RatingDao - Star ratings
// ================================================================

@Dao
interface RatingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRating(rating: RatingEntity)

    @Update
    suspend fun updateRating(rating: RatingEntity)

    @Query("DELETE FROM ratings WHERE image_id = :imageId")
    suspend fun deleteRating(imageId: Long)

    @Query("SELECT * FROM ratings WHERE image_id = :imageId")
    suspend fun getRatingByImageId(imageId: Long): RatingEntity?

    @Query("SELECT * FROM ratings WHERE image_id = :imageId")
    fun observeRatingByImageId(imageId: Long): Flow<RatingEntity?>

    @Query("SELECT * FROM ratings WHERE rating = :stars ORDER BY rated_at DESC")
    suspend fun getImagesByRating(stars: Int): List<RatingEntity>

    @Query("SELECT * FROM ratings WHERE rating >= :minStars ORDER BY rating DESC")
    suspend fun getImagesByMinRating(minStars: Int): List<RatingEntity>

    @Query("""
        SELECT rating, COUNT(*) as cnt FROM ratings 
        GROUP BY rating ORDER BY rating DESC
    """)
    suspend fun getRatingDistribution(): List<RatingDistributionResult>

    @Query("SELECT AVG(rating) FROM ratings WHERE rating > 0")
    suspend fun getAverageRating(): Float?

    @Query("SELECT COUNT(*) FROM ratings WHERE rating > 0")
    suspend fun getRatedImageCount(): Int

    @Query("SELECT * FROM ratings ORDER BY rated_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getRatingsPaginated(limit: Int, offset: Int): List<RatingEntity>

    @Query("DELETE FROM ratings")
    suspend fun deleteAllRatings()
}

// ================================================================
// SemanticLabelDao - Labels and label-based search
// ================================================================

@Dao
interface SemanticLabelDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLabel(label: SemanticLabelEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLabels(labels: List<SemanticLabelEntity>)

    @Query("DELETE FROM semantic_labels WHERE image_id = :imageId")
    suspend fun deleteLabelsByImageId(imageId: Long)

    @Query("DELETE FROM semantic_labels WHERE label_id = :labelId")
    suspend fun deleteLabelById(labelId: String)

    @Query("SELECT * FROM semantic_labels WHERE image_id = :imageId ORDER BY confidence DESC")
    suspend fun getLabelsByImageId(imageId: Long): List<SemanticLabelEntity>

    @Query("SELECT * FROM semantic_labels WHERE label = :label ORDER BY confidence DESC")
    suspend fun getImagesByLabel(label: String): List<SemanticLabelEntity>

    @Query("SELECT * FROM semantic_labels WHERE label LIKE '%' || :query || '%' ORDER BY confidence DESC")
    suspend fun searchLabels(query: String): List<SemanticLabelEntity>

    @Query("SELECT DISTINCT image_id FROM semantic_labels WHERE label IN (:labels)")
    suspend fun getImageIdsByLabels(labels: List<String>): List<Long>

    @Query("""
        SELECT DISTINCT sl.image_id FROM semantic_labels sl
        WHERE sl.label IN (:includeLabels)
        AND sl.image_id NOT IN (
            SELECT DISTINCT image_id FROM semantic_labels WHERE label IN (:excludeLabels)
        )
    """)
    suspend fun getImageIdsByLabelFilter(includeLabels: List<String>, excludeLabels: List<String>): List<Long>

    @Query("SELECT label, COUNT(*) as cnt FROM semantic_labels GROUP BY label ORDER BY cnt DESC LIMIT :limit")
    suspend fun getLabelFrequency(limit: Int): List<LabelFrequencyResult>

    @Query("SELECT label, COUNT(*) as cnt FROM semantic_labels GROUP BY label ORDER BY cnt DESC")
    suspend fun getLabelFrequencyAll(): List<LabelFrequencyResult>

    // FTS label search
    @Query("SELECT * FROM semantic_labels WHERE label LIKE '%' || :query || '%' ORDER BY confidence DESC")
    suspend fun ftsSearchLabels(query: String): List<SemanticLabelEntity>

    @Query("SELECT DISTINCT image_id FROM semantic_labels WHERE label LIKE '%' || :query || '%'")
    suspend fun ftsSearchImageIdsByLabel(query: String): List<Long>

    @Query("SELECT COUNT(DISTINCT label) FROM semantic_labels")
    suspend fun getDistinctLabelCount(): Int

    @Query("DELETE FROM semantic_labels")
    suspend fun deleteAllLabels()
}

// ================================================================
// CollectionDao - Collection membership
// ================================================================

@Dao
interface CollectionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CollectionEntity): Long

    @Update
    suspend fun updateCollection(collection: CollectionEntity)

    @Query("DELETE FROM collections WHERE collection_id = :collectionId")
    suspend fun deleteCollection(collectionId: Long)

    @Query("SELECT * FROM collections WHERE collection_id = :collectionId")
    suspend fun getCollectionById(collectionId: Long): CollectionEntity?

    @Query("SELECT * FROM collections WHERE collection_name = :name")
    suspend fun getCollectionByName(name: String): CollectionEntity?

    @Query("SELECT * FROM collections ORDER BY updated_at DESC")
    suspend fun getAllCollections(): List<CollectionEntity>

    @Query("SELECT * FROM collections ORDER BY updated_at DESC")
    fun observeAllCollections(): Flow<List<CollectionEntity>>

    @Query("UPDATE collections SET updated_at = :now WHERE collection_id = :collectionId")
    suspend fun touchCollection(collectionId: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE collections SET cover_image_id = :imageId WHERE collection_id = :collectionId")
    suspend fun setCoverImage(collectionId: Long, imageId: Long?)

    @Query("SELECT COUNT(*) FROM collections")
    suspend fun getCollectionCount(): Int

    // Collection-Image mapping
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addImageToCollection(mapping: CollectionImageEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addImagesToCollection(mappings: List<CollectionImageEntity>)

    @Query("DELETE FROM collection_images WHERE collection_id = :collectionId AND image_id = :imageId")
    suspend fun removeImageFromCollection(collectionId: Long, imageId: Long)

    @Query("DELETE FROM collection_images WHERE collection_id = :collectionId")
    suspend fun removeAllImagesFromCollection(collectionId: Long)

    @Query("SELECT image_id FROM collection_images WHERE collection_id = :collectionId ORDER BY added_at DESC")
    suspend fun getImageIdsInCollection(collectionId: Long): List<Long>

    @Query("""
        SELECT image_id FROM collection_images 
        WHERE collection_id = :collectionId 
        ORDER BY added_at DESC 
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getImageIdsInCollectionPaginated(collectionId: Long, limit: Int, offset: Int): List<Long>

    @Query("SELECT COUNT(*) FROM collection_images WHERE collection_id = :collectionId")
    suspend fun getImageCountInCollection(collectionId: Long): Int

    @Query("SELECT collection_id FROM collection_images WHERE image_id = :imageId")
    suspend fun getCollectionIdsForImage(imageId: Long): List<Long>

    @Query("SELECT * FROM collections WHERE collection_id IN (:collectionIds)")
    suspend fun getCollectionsByIds(collectionIds: List<Long>): List<CollectionEntity>

    @Query("SELECT COUNT(*) FROM collection_images WHERE collection_id = :collectionId AND image_id = :imageId")
    suspend fun isImageInCollection(collectionId: Long, imageId: Long): Boolean

    @Query("DELETE FROM collection_images")
    suspend fun deleteAllCollectionMappings()
}

// ================================================================
// FilterDao - Filter presets
// ================================================================

@Dao
interface FilterDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: FilterPresetEntity): Long

    @Update
    suspend fun updatePreset(preset: FilterPresetEntity)

    @Query("DELETE FROM filter_presets WHERE preset_id = :presetId")
    suspend fun deletePreset(presetId: Long)

    @Query("SELECT * FROM filter_presets WHERE preset_id = :presetId")
    suspend fun getPresetById(presetId: Long): FilterPresetEntity?

    @Query("SELECT * FROM filter_presets WHERE name = :name")
    suspend fun getPresetByName(name: String): FilterPresetEntity?

    @Query("SELECT * FROM filter_presets ORDER BY created_at DESC")
    suspend fun getAllPresets(): List<FilterPresetEntity>

    @Query("SELECT * FROM filter_presets ORDER BY created_at DESC")
    fun observeAllPresets(): Flow<List<FilterPresetEntity>>

    @Query("SELECT * FROM filter_presets WHERE is_default = 1")
    suspend fun getDefaultPresets(): List<FilterPresetEntity>

    @Query("UPDATE filter_presets SET is_default = :isDefault WHERE preset_id = :presetId")
    suspend fun setDefault(presetId: Long, isDefault: Boolean)

    @Query("DELETE FROM filter_presets")
    suspend fun deleteAllPresets()
}

// ================================================================
// Result types for facet queries (non-entity)
// ================================================================

data class CameraFacetResult(
    @ColumnInfo(name = "camera_make") val cameraMake: String,
    @ColumnInfo(name = "camera_model") val cameraModel: String,
    @ColumnInfo(name = "cnt") val count: Int
)

data class LensFacetResult(
    @ColumnInfo(name = "lens_model") val lensModel: String,
    @ColumnInfo(name = "cnt") val count: Int
)

data class FocalLengthRangeResult(
    @ColumnInfo(name = "min_fl") val minFl: Float,
    @ColumnInfo(name = "max_fl") val maxFl: Float,
    @ColumnInfo(name = "cnt") val count: Int
)

data class ApertureRangeResult(
    @ColumnInfo(name = "min_ap") val minAp: Float,
    @ColumnInfo(name = "max_ap") val maxAp: Float,
    @ColumnInfo(name = "cnt") val count: Int
)

data class IsoRangeResult(
    @ColumnInfo(name = "min_iso") val minIso: Int,
    @ColumnInfo(name = "max_iso") val maxIso: Int,
    @ColumnInfo(name = "cnt") val count: Int
)

data class DateRangeResult(
    @ColumnInfo(name = "min_date") val minDate: Long,
    @ColumnInfo(name = "max_date") val maxDate: Long,
    @ColumnInfo(name = "cnt") val count: Int
)

data class DateFacetResult(
    @ColumnInfo(name = "year") val year: String,
    @ColumnInfo(name = "cnt") val count: Int
)

data class FileTypeDistributionResult(
    @ColumnInfo(name = "image_type") val imageType: Int,
    @ColumnInfo(name = "cnt") val count: Int
)

data class RatingDistributionResult(
    @ColumnInfo(name = "rating") val rating: Int,
    @ColumnInfo(name = "cnt") val count: Int
)

data class LabelFrequencyResult(
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "cnt") val count: Int
)

// ================================================================
// ImageDao - Desktop schema image table CRUD
// ================================================================

@Dao
interface ImageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: ImageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImages(images: List<ImageEntity>)

    @Update
    suspend fun updateImage(image: ImageEntity)

    @Delete
    suspend fun deleteImage(image: ImageEntity)

    @Query("DELETE FROM images WHERE file_id = :fileId")
    suspend fun deleteImageByFileId(fileId: Long)

    @Query("SELECT * FROM images WHERE file_id = :fileId")
    suspend fun getImageByFileId(fileId: Long): ImageEntity?

    @Query("SELECT * FROM images WHERE file_id = :fileId")
    fun observeImageByFileId(fileId: Long): Flow<ImageEntity?>

    @Query("SELECT * FROM images WHERE is_hdr = 1")
    suspend fun getHdrImages(): List<ImageEntity>

    @Query("SELECT * FROM images WHERE rating >= :minRating ORDER BY rating DESC")
    suspend fun getImagesByMinRating(minRating: Int): List<ImageEntity>

    @Query("SELECT * FROM images WHERE format = :format ORDER BY import_date DESC")
    suspend fun getImagesByFormat(format: String): List<ImageEntity>

    @Query("SELECT * FROM images WHERE color_space = :colorSpace")
    suspend fun getImagesByColorSpace(colorSpace: String): List<ImageEntity>

    @Query("SELECT * FROM images ORDER BY import_date DESC LIMIT :limit OFFSET :offset")
    suspend fun getImagesPaginated(limit: Int, offset: Int): List<ImageEntity>

    @Query("SELECT COUNT(*) FROM images")
    suspend fun getImageCount(): Int

    @Query("UPDATE images SET rating = :rating WHERE file_id = :fileId")
    suspend fun updateRating(fileId: Long, rating: Int)

    @Query("UPDATE images SET last_modified = :lastModified WHERE file_id = :fileId")
    suspend fun updateLastModified(fileId: Long, lastModified: Long = System.currentTimeMillis())

    @Query("DELETE FROM images")
    suspend fun deleteAllImages()
}

// ================================================================
// PipelineDao - Desktop schema pipeline table CRUD
// ================================================================

@Dao
interface PipelineDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPipeline(pipeline: PipelineEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPipelines(pipelines: List<PipelineEntity>)

    @Update
    suspend fun updatePipeline(pipeline: PipelineEntity)

    @Delete
    suspend fun deletePipeline(pipeline: PipelineEntity)

    @Query("DELETE FROM pipelines WHERE pipeline_id = :pipelineId")
    suspend fun deletePipelineById(pipelineId: Long)

    @Query("DELETE FROM pipelines WHERE file_id = :fileId")
    suspend fun deletePipelinesByFileId(fileId: Long)

    @Query("SELECT * FROM pipelines WHERE pipeline_id = :pipelineId")
    suspend fun getPipelineById(pipelineId: Long): PipelineEntity?

    @Query("SELECT * FROM pipelines WHERE file_id = :fileId AND is_active = 1 LIMIT 1")
    suspend fun getActivePipelineByFileId(fileId: Long): PipelineEntity?

    @Query("SELECT * FROM pipelines WHERE file_id = :fileId ORDER BY created_at DESC")
    suspend fun getPipelinesByFileId(fileId: Long): List<PipelineEntity>

    @Query("SELECT * FROM pipelines WHERE file_id = :fileId ORDER BY created_at DESC")
    fun observePipelinesByFileId(fileId: Long): Flow<List<PipelineEntity>>

    @Query("UPDATE pipelines SET is_active = 0 WHERE file_id = :fileId")
    suspend fun deactivateAllPipelinesForFile(fileId: Long)

    @Query("UPDATE pipelines SET is_active = 1 WHERE pipeline_id = :pipelineId")
    suspend fun activatePipeline(pipelineId: Long)

    @Query("SELECT COUNT(*) FROM pipelines WHERE file_id = :fileId")
    suspend fun getPipelineCountForFile(fileId: Long): Int

    @Query("DELETE FROM pipelines")
    suspend fun deleteAllPipelines()
}

// ================================================================
// HistoryDao - Desktop schema history table CRUD
// ================================================================

@Dao
interface HistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: HistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistories(histories: List<HistoryEntity>)

    @Update
    suspend fun updateHistory(history: HistoryEntity)

    @Delete
    suspend fun deleteHistory(history: HistoryEntity)

    @Query("DELETE FROM histories WHERE row_id = :rowId")
    suspend fun deleteHistoryById(rowId: Long)

    @Query("DELETE FROM histories WHERE file_id = :fileId")
    suspend fun deleteHistoriesByFileId(fileId: Long)

    @Query("SELECT * FROM histories WHERE row_id = :rowId")
    suspend fun getHistoryById(rowId: Long): HistoryEntity?

    @Query("SELECT * FROM histories WHERE file_id = :fileId ORDER BY created_at DESC")
    suspend fun getHistoriesByFileId(fileId: Long): List<HistoryEntity>

    @Query("SELECT * FROM histories WHERE file_id = :fileId AND is_active = 1 LIMIT 1")
    suspend fun getActiveHistoryByFileId(fileId: Long): HistoryEntity?

    @Query("SELECT * FROM histories WHERE file_id = :fileId AND version_id = :versionId")
    suspend fun getHistoryByVersionId(fileId: Long, versionId: String): HistoryEntity?

    @Query("SELECT * FROM histories WHERE file_id = :fileId ORDER BY created_at DESC")
    fun observeHistoriesByFileId(fileId: Long): Flow<List<HistoryEntity>>

    @Query("UPDATE histories SET is_active = 0 WHERE file_id = :fileId")
    suspend fun deactivateAllHistoriesForFile(fileId: Long)

    @Query("UPDATE histories SET is_active = 1 WHERE row_id = :rowId")
    suspend fun activateHistory(rowId: Long)

    @Query("SELECT COUNT(*) FROM histories WHERE file_id = :fileId")
    suspend fun getHistoryCountForFile(fileId: Long): Int

    @Query("""
        WITH RECURSIVE version_chain AS (
            SELECT * FROM histories WHERE version_id = :versionId AND file_id = :fileId
            UNION ALL
            SELECT h.* FROM histories h INNER JOIN version_chain vc ON h.version_id = vc.parent_version_id
        )
        SELECT * FROM version_chain ORDER BY created_at ASC
    """)
    suspend fun getVersionChain(fileId: Long, versionId: String): List<HistoryEntity>

    @Query("DELETE FROM histories")
    suspend fun deleteAllHistories()
}

// ================================================================
// FilterDao (desktop schema) - Filter CRUD
// ================================================================

@Dao
interface FilterV2Dao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFilter(filter: FilterEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFilters(filters: List<FilterEntity>)

    @Update
    suspend fun updateFilter(filter: FilterEntity)

    @Delete
    suspend fun deleteFilter(filter: FilterEntity)

    @Query("DELETE FROM filters WHERE filter_id = :filterId")
    suspend fun deleteFilterById(filterId: Long)

    @Query("SELECT * FROM filters WHERE filter_id = :filterId")
    suspend fun getFilterById(filterId: Long): FilterEntity?

    @Query("SELECT * FROM filters WHERE name = :name")
    suspend fun getFilterByName(name: String): FilterEntity?

    @Query("SELECT * FROM filters ORDER BY created_at DESC")
    suspend fun getAllFilters(): List<FilterEntity>

    @Query("SELECT * FROM filters ORDER BY created_at DESC")
    fun observeAllFilters(): Flow<List<FilterEntity>>

    @Query("SELECT COUNT(*) FROM filters")
    suspend fun getFilterCount(): Int

    @Query("DELETE FROM filters")
    suspend fun deleteAllFilters()
}

// ================================================================
// AiDescriptionDao - AI description CRUD
// ================================================================

@Dao
interface AiDescriptionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDescription(description: AiDescriptionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDescriptions(descriptions: List<AiDescriptionEntity>)

    @Update
    suspend fun updateDescription(description: AiDescriptionEntity)

    @Delete
    suspend fun deleteDescription(description: AiDescriptionEntity)

    @Query("DELETE FROM ai_descriptions WHERE row_id = :rowId")
    suspend fun deleteDescriptionById(rowId: Long)

    @Query("DELETE FROM ai_descriptions WHERE file_id = :fileId")
    suspend fun deleteDescriptionsByFileId(fileId: Long)

    @Query("SELECT * FROM ai_descriptions WHERE file_id = :fileId AND is_active = 1 ORDER BY row_id DESC LIMIT 1")
    suspend fun getActiveDescriptionByFileId(fileId: Long): AiDescriptionEntity?

    @Query("SELECT * FROM ai_descriptions WHERE file_id = :fileId ORDER BY row_id DESC")
    suspend fun getDescriptionsByFileId(fileId: Long): List<AiDescriptionEntity>

    @Query("SELECT * FROM ai_descriptions WHERE file_id = :fileId ORDER BY row_id DESC")
    fun observeDescriptionsByFileId(fileId: Long): Flow<List<AiDescriptionEntity>>

    @Query("SELECT * FROM ai_descriptions WHERE task_id = :taskId")
    suspend fun getDescriptionsByTaskId(taskId: String): List<AiDescriptionEntity>

    @Query("SELECT * FROM ai_descriptions WHERE provider_id = :providerId")
    suspend fun getDescriptionsByProviderId(providerId: String): List<AiDescriptionEntity>

    @Query("SELECT * FROM ai_descriptions WHERE model_id = :modelId")
    suspend fun getDescriptionsByModelId(modelId: String): List<AiDescriptionEntity>

    @Query("SELECT * FROM ai_descriptions WHERE confidence >= :minConfidence ORDER BY confidence DESC")
    suspend fun getDescriptionsByMinConfidence(minConfidence: Float): List<AiDescriptionEntity>

    @Query("UPDATE ai_descriptions SET is_active = 0 WHERE file_id = :fileId")
    suspend fun deactivateAllDescriptionsForFile(fileId: Long)

    @Query("UPDATE ai_descriptions SET is_active = 1 WHERE row_id = :rowId")
    suspend fun activateDescription(rowId: Long)

    @Query("SELECT COUNT(*) FROM ai_descriptions WHERE is_active = 1")
    suspend fun getActiveDescriptionCount(): Int

    @Query("DELETE FROM ai_descriptions")
    suspend fun deleteAllDescriptions()
}

// ================================================================
// AiRatingDao - AI rating CRUD
// ================================================================

@Dao
interface AiRatingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAiRating(rating: AiRatingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAiRatings(ratings: List<AiRatingEntity>)

    @Update
    suspend fun updateAiRating(rating: AiRatingEntity)

    @Delete
    suspend fun deleteAiRating(rating: AiRatingEntity)

    @Query("DELETE FROM ai_ratings WHERE row_id = :rowId")
    suspend fun deleteAiRatingById(rowId: Long)

    @Query("DELETE FROM ai_ratings WHERE file_id = :fileId")
    suspend fun deleteAiRatingsByFileId(fileId: Long)

    @Query("SELECT * FROM ai_ratings WHERE file_id = :fileId AND is_active = 1 ORDER BY row_id DESC LIMIT 1")
    suspend fun getActiveAiRatingByFileId(fileId: Long): AiRatingEntity?

    @Query("SELECT * FROM ai_ratings WHERE file_id = :fileId ORDER BY row_id DESC")
    suspend fun getAiRatingsByFileId(fileId: Long): List<AiRatingEntity>

    @Query("SELECT * FROM ai_ratings WHERE file_id = :fileId ORDER BY row_id DESC")
    fun observeAiRatingsByFileId(fileId: Long): Flow<List<AiRatingEntity>>

    @Query("SELECT * FROM ai_ratings WHERE task_id = :taskId")
    suspend fun getAiRatingsByTaskId(taskId: String): List<AiRatingEntity>

    @Query("SELECT * FROM ai_ratings WHERE provider_id = :providerId")
    suspend fun getAiRatingsByProviderId(providerId: String): List<AiRatingEntity>

    @Query("SELECT * FROM ai_ratings WHERE rating >= :minRating ORDER BY rating DESC")
    suspend fun getAiRatingsByMinRating(minRating: Int): List<AiRatingEntity>

    @Query("UPDATE ai_ratings SET is_active = 0 WHERE file_id = :fileId")
    suspend fun deactivateAllAiRatingsForFile(fileId: Long)

    @Query("UPDATE ai_ratings SET is_active = 1 WHERE row_id = :rowId")
    suspend fun activateAiRating(rowId: Long)

    @Query("SELECT AVG(rating) FROM ai_ratings WHERE is_active = 1")
    suspend fun getAverageAiRating(): Float?

    @Query("""
        SELECT rating, COUNT(*) as cnt FROM ai_ratings 
        WHERE is_active = 1
        GROUP BY rating ORDER BY rating DESC
    """)
    suspend fun getAiRatingDistribution(): List<AiRatingDistributionResult>

    @Query("DELETE FROM ai_ratings")
    suspend fun deleteAllAiRatings()
}

// ================================================================
// SemanticEmbeddingDao - Semantic embedding CRUD
// ================================================================

@Dao
interface SemanticEmbeddingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmbedding(embedding: SemanticEmbeddingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmbeddings(embeddings: List<SemanticEmbeddingEntity>)

    @Update
    suspend fun updateEmbedding(embedding: SemanticEmbeddingEntity)

    @Delete
    suspend fun deleteEmbedding(embedding: SemanticEmbeddingEntity)

    @Query("DELETE FROM semantic_embeddings WHERE row_id = :rowId")
    suspend fun deleteEmbeddingById(rowId: Long)

    @Query("DELETE FROM semantic_embeddings WHERE file_id = :fileId")
    suspend fun deleteEmbeddingsByFileId(fileId: Long)

    @Query("SELECT * FROM semantic_embeddings WHERE file_id = :fileId AND model_id = :modelId LIMIT 1")
    suspend fun getEmbeddingByFileAndModel(fileId: Long, modelId: String): SemanticEmbeddingEntity?

    @Query("SELECT * FROM semantic_embeddings WHERE file_id = :fileId ORDER BY created_at DESC")
    suspend fun getEmbeddingsByFileId(fileId: Long): List<SemanticEmbeddingEntity>

    @Query("SELECT * FROM semantic_embeddings WHERE model_id = :modelId")
    suspend fun getEmbeddingsByModelId(modelId: String): List<SemanticEmbeddingEntity>

    @Query("SELECT file_id FROM semantic_embeddings WHERE model_id = :modelId")
    suspend fun getFileIdsWithEmbeddings(modelId: String): List<Long>

    @Query("SELECT COUNT(*) FROM semantic_embeddings WHERE model_id = :modelId")
    suspend fun getEmbeddingCountForModel(modelId: String): Int

    @Query("SELECT COUNT(*) FROM semantic_embeddings")
    suspend fun getTotalEmbeddingCount(): Int

    @Query("DELETE FROM semantic_embeddings")
    suspend fun deleteAllEmbeddings()
}

// ================================================================
// SemanticLabelV2Dao - Desktop schema semantic label CRUD
// ================================================================

@Dao
interface SemanticLabelV2Dao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLabel(label: SemanticLabelV2Entity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLabels(labels: List<SemanticLabelV2Entity>)

    @Update
    suspend fun updateLabel(label: SemanticLabelV2Entity)

    @Delete
    suspend fun deleteLabel(label: SemanticLabelV2Entity)

    @Query("DELETE FROM semantic_labels_v2 WHERE row_id = :rowId")
    suspend fun deleteLabelById(rowId: Long)

    @Query("DELETE FROM semantic_labels_v2 WHERE file_id = :fileId")
    suspend fun deleteLabelsByFileId(fileId: Long)

    @Query("SELECT * FROM semantic_labels_v2 WHERE file_id = :fileId ORDER BY primary_confidence DESC")
    suspend fun getLabelsByFileId(fileId: Long): List<SemanticLabelV2Entity>

    @Query("SELECT * FROM semantic_labels_v2 WHERE file_id = :fileId ORDER BY primary_confidence DESC")
    fun observeLabelsByFileId(fileId: Long): Flow<List<SemanticLabelV2Entity>>

    @Query("SELECT * FROM semantic_labels_v2 WHERE primary_label = :label ORDER BY primary_confidence DESC")
    suspend fun getFilesByPrimaryLabel(label: String): List<SemanticLabelV2Entity>

    @Query("SELECT * FROM semantic_labels_v2 WHERE model_id = :modelId ORDER BY primary_confidence DESC")
    suspend fun getLabelsByModelId(modelId: String): List<SemanticLabelV2Entity>

    @Query("SELECT DISTINCT file_id FROM semantic_labels_v2 WHERE primary_label IN (:labels)")
    suspend fun getFileIdsByLabels(labels: List<String>): List<Long>

    @Query("""
        SELECT primary_label AS label, COUNT(*) as cnt FROM semantic_labels_v2
        GROUP BY primary_label ORDER BY cnt DESC LIMIT :limit
    """)
    suspend fun getLabelFrequency(limit: Int): List<LabelFrequencyResult>

    @Query("SELECT COUNT(DISTINCT primary_label) FROM semantic_labels_v2")
    suspend fun getDistinctLabelCount(): Int

    @Query("DELETE FROM semantic_labels_v2")
    suspend fun deleteAllLabels()
}

// ================================================================
// CollectionV2Dao - Desktop schema collection CRUD
// ================================================================

@Dao
interface CollectionV2Dao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CollectionV2Entity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollections(collections: List<CollectionV2Entity>)

    @Update
    suspend fun updateCollection(collection: CollectionV2Entity)

    @Delete
    suspend fun deleteCollection(collection: CollectionV2Entity)

    @Query("DELETE FROM collections_v2 WHERE collection_id = :collectionId")
    suspend fun deleteCollectionById(collectionId: Long)

    @Query("SELECT * FROM collections_v2 WHERE collection_id = :collectionId")
    suspend fun getCollectionById(collectionId: Long): CollectionV2Entity?

    @Query("SELECT * FROM collections_v2 WHERE name = :name")
    suspend fun getCollectionByName(name: String): CollectionV2Entity?

    @Query("SELECT * FROM collections_v2 ORDER BY updated_at DESC")
    suspend fun getAllCollections(): List<CollectionV2Entity>

    @Query("SELECT * FROM collections_v2 ORDER BY updated_at DESC")
    fun observeAllCollections(): Flow<List<CollectionV2Entity>>

    @Query("UPDATE collections_v2 SET updated_at = :now WHERE collection_id = :collectionId")
    suspend fun touchCollection(collectionId: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE collections_v2 SET cover_file_id = :fileId WHERE collection_id = :collectionId")
    suspend fun setCoverFile(collectionId: Long, fileId: Long?)

    @Query("SELECT COUNT(*) FROM collections_v2")
    suspend fun getCollectionCount(): Int

    // Collection-File mapping
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFileToCollection(mapping: CollectionImageV2Entity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFilesToCollection(mappings: List<CollectionImageV2Entity>)

    @Query("DELETE FROM collection_images_v2 WHERE collection_id = :collectionId AND file_id = :fileId")
    suspend fun removeFileFromCollection(collectionId: Long, fileId: Long)

    @Query("DELETE FROM collection_images_v2 WHERE collection_id = :collectionId")
    suspend fun removeAllFilesFromCollection(collectionId: Long)

    @Query("SELECT file_id FROM collection_images_v2 WHERE collection_id = :collectionId ORDER BY added_at DESC")
    suspend fun getFileIdsInCollection(collectionId: Long): List<Long>

    @Query("""
        SELECT file_id FROM collection_images_v2 
        WHERE collection_id = :collectionId 
        ORDER BY added_at DESC 
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getFileIdsInCollectionPaginated(collectionId: Long, limit: Int, offset: Int): List<Long>

    @Query("SELECT COUNT(*) FROM collection_images_v2 WHERE collection_id = :collectionId")
    suspend fun getFileCountInCollection(collectionId: Long): Int

    @Query("SELECT collection_id FROM collection_images_v2 WHERE file_id = :fileId")
    suspend fun getCollectionIdsForFile(fileId: Long): List<Long>

    @Query("SELECT COUNT(*) FROM collection_images_v2 WHERE collection_id = :collectionId AND file_id = :fileId")
    suspend fun isFileInCollection(collectionId: Long, fileId: Long): Boolean

    @Query("DELETE FROM collections_v2")
    suspend fun deleteAllCollections()

    @Query("DELETE FROM collection_images_v2")
    suspend fun deleteAllCollectionMappings()
}

// ================================================================
// AI Rating distribution result
// ================================================================

data class AiRatingDistributionResult(
    @ColumnInfo(name = "rating") val rating: Int,
    @ColumnInfo(name = "cnt") val count: Int
)