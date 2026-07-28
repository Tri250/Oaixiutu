package com.alcedo.studio.data.local;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.alcedo.studio.data.dao.AiEmbeddingDao;
import com.alcedo.studio.data.dao.AiEmbeddingDao_Impl;
import com.alcedo.studio.data.dao.EditHistoryDao;
import com.alcedo.studio.data.dao.EditHistoryDao_Impl;
import com.alcedo.studio.data.dao.ImageDao;
import com.alcedo.studio.data.dao.ImageDao_Impl;
import com.alcedo.studio.data.dao.PipelinePresetDao;
import com.alcedo.studio.data.dao.PipelinePresetDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class SleeveDatabase_Impl extends SleeveDatabase {
  private volatile ImageDao _imageDao;

  private volatile EditHistoryDao _editHistoryDao;

  private volatile AiEmbeddingDao _aiEmbeddingDao;

  private volatile PipelinePresetDao _pipelinePresetDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `images` (`id` TEXT NOT NULL, `sleevePath` TEXT NOT NULL, `originalUri` TEXT NOT NULL, `displayName` TEXT NOT NULL, `fileExtension` TEXT NOT NULL, `fileSizeBytes` INTEGER NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `dateAddedEpoch` INTEGER NOT NULL, `dateCapturedEpoch` INTEGER NOT NULL, `rating` INTEGER NOT NULL, `flag` TEXT NOT NULL, `colorLabel` TEXT NOT NULL, `isRaw` INTEGER NOT NULL, `isVirtualCopy` INTEGER NOT NULL, `parentId` TEXT, `thumbnailPath` TEXT, `currentVersionId` TEXT, `aiCaption` TEXT, `aiTags` TEXT, `aiScore` REAL, `isHidden` INTEGER NOT NULL, `lensModel` TEXT, `cameraModel` TEXT, `focalLength` REAL, `iso` INTEGER, `aperture` REAL, `shutterSpeed` TEXT, PRIMARY KEY(`id`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_images_sleevePath` ON `images` (`sleevePath`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_images_rating` ON `images` (`rating`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_images_dateCapturedEpoch` ON `images` (`dateCapturedEpoch`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `sleeve_elements` (`id` TEXT NOT NULL, `parentId` TEXT, `name` TEXT NOT NULL, `sleevePath` TEXT NOT NULL, `isFolder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `modifiedAt` INTEGER NOT NULL, `imageId` TEXT, `childCount` INTEGER NOT NULL, `imageCount` INTEGER NOT NULL, `isSmartCollection` INTEGER NOT NULL, `smartFilterJson` TEXT, PRIMARY KEY(`id`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sleeve_elements_parentId` ON `sleeve_elements` (`parentId`)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sleeve_elements_sleevePath` ON `sleeve_elements` (`sleevePath`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `projects` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `filePath` TEXT NOT NULL, `rootSleeveId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `modifiedAt` INTEGER NOT NULL, `description` TEXT NOT NULL, `version` INTEGER NOT NULL, `schemaVersion` INTEGER NOT NULL, `imageCount` INTEGER NOT NULL, `totalSizeBytes` INTEGER NOT NULL, `thumbnailPath` TEXT, `tags` TEXT, `isFavorite` INTEGER NOT NULL, `lastOpenedAt` INTEGER, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `edit_versions` (`id` TEXT NOT NULL, `imageId` TEXT NOT NULL, `parentId` TEXT, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `cumulativeParamsJson` TEXT NOT NULL, `isVirtualCopy` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, `note` TEXT, PRIMARY KEY(`id`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_edit_versions_imageId` ON `edit_versions` (`imageId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `edit_transactions` (`id` TEXT NOT NULL, `versionId` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `label` TEXT NOT NULL, `paramDeltaJson` TEXT NOT NULL, `maskIds` TEXT, `source` TEXT NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_edit_transactions_versionId` ON `edit_transactions` (`versionId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `ai_embeddings` (`id` TEXT NOT NULL, `imageId` TEXT NOT NULL, `modelId` TEXT NOT NULL, `dimensions` INTEGER NOT NULL, `generatedAt` INTEGER NOT NULL, `norm` REAL NOT NULL, `embeddingBlob` BLOB NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_embeddings_imageId` ON `ai_embeddings` (`imageId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_embeddings_modelId` ON `ai_embeddings` (`modelId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `ai_ratings` (`imageId` TEXT NOT NULL, `overallScore` REAL NOT NULL, `technicalScore` REAL NOT NULL, `aestheticScore` REAL NOT NULL, `sharpnessScore` REAL NOT NULL, `exposureScore` REAL NOT NULL, `compositionScore` REAL NOT NULL, `emotionScore` REAL NOT NULL, `rationale` TEXT NOT NULL, `suggestedRating` INTEGER NOT NULL, `suggestedFlag` TEXT NOT NULL, `generatedAt` INTEGER NOT NULL, `modelId` TEXT NOT NULL, `provider` TEXT NOT NULL, `confidence` REAL NOT NULL, PRIMARY KEY(`imageId`))");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ai_ratings_imageId` ON `ai_ratings` (`imageId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `pipeline_presets` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `adjustmentsJson` TEXT NOT NULL, `isBuiltIn` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, `thumbnailPath` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pipeline_presets_category` ON `pipeline_presets` (`category`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `ai_models` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `version` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `downloadUrl` TEXT NOT NULL, `sha256` TEXT NOT NULL, `localPath` TEXT, `isDownloaded` INTEGER NOT NULL, `isDefault` INTEGER NOT NULL, `dimensions` INTEGER NOT NULL, `description` TEXT NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `lens_profiles` (`id` TEXT NOT NULL, `lensId` TEXT NOT NULL, `displayName` TEXT NOT NULL, `maker` TEXT NOT NULL, `profileJson` TEXT NOT NULL, `isCalibrated` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_lens_profiles_lensId` ON `lens_profiles` (`lensId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '06e95033970084286fde1843e9241fc1')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `images`");
        db.execSQL("DROP TABLE IF EXISTS `sleeve_elements`");
        db.execSQL("DROP TABLE IF EXISTS `projects`");
        db.execSQL("DROP TABLE IF EXISTS `edit_versions`");
        db.execSQL("DROP TABLE IF EXISTS `edit_transactions`");
        db.execSQL("DROP TABLE IF EXISTS `ai_embeddings`");
        db.execSQL("DROP TABLE IF EXISTS `ai_ratings`");
        db.execSQL("DROP TABLE IF EXISTS `pipeline_presets`");
        db.execSQL("DROP TABLE IF EXISTS `ai_models`");
        db.execSQL("DROP TABLE IF EXISTS `lens_profiles`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsImages = new HashMap<String, TableInfo.Column>(28);
        _columnsImages.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsImages.put("sleevePath", new TableInfo.Column("sleevePath", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsImages.put("originalUri", new TableInfo.Column("originalUri", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsImages.put("displayName", new TableInfo.Column("displayName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsImages.put("fileExtension", new TableInfo.Column("fileExtension", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsImages.put("fileSizeBytes", new TableInfo.Column("fileSizeBytes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsImages.put("width", new TableInfo.Column("width", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsImages.put("height", new TableInfo.Column("height", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsImages.put("dateAddedEpoch", new TableInfo.Column("dateAddedEpoch", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsImages.put("dateCapturedEpoch", new TableInfo.Column("dateCapturedEpoch", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsImages.put("rating", new TableInfo.Column("rating", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsImages.put("flag", new TableInfo.Column("flag", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsImages.put("colorLabel", new TableInfo.Column("colorLabel", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsImages.put("isRaw", new TableInfo.Column("isRaw", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsImages.put("isVirtualCopy", new TableInfo.Column("isVirtualCopy", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsImages.put("parentId", new TableInfo.Column("parentId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsImages.put("thumbnailPath", new TableInfo.Column("thumbnailPath", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsImages.put("currentVersionId", new TableInfo.Column("currentVersionId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsImages.put("aiCaption", new TableInfo.Column("aiCaption", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsImages.put("aiTags", new TableInfo.Column("aiTags", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsImages.put("aiScore", new TableInfo.Column("aiScore", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsImages.put("isHidden", new TableInfo.Column("isHidden", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsImages.put("lensModel", new TableInfo.Column("lensModel", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsImages.put("cameraModel", new TableInfo.Column("cameraModel", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsImages.put("focalLength", new TableInfo.Column("focalLength", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsImages.put("iso", new TableInfo.Column("iso", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsImages.put("aperture", new TableInfo.Column("aperture", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsImages.put("shutterSpeed", new TableInfo.Column("shutterSpeed", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysImages = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesImages = new HashSet<TableInfo.Index>(3);
        _indicesImages.add(new TableInfo.Index("index_images_sleevePath", false, Arrays.asList("sleevePath"), Arrays.asList("ASC")));
        _indicesImages.add(new TableInfo.Index("index_images_rating", false, Arrays.asList("rating"), Arrays.asList("ASC")));
        _indicesImages.add(new TableInfo.Index("index_images_dateCapturedEpoch", false, Arrays.asList("dateCapturedEpoch"), Arrays.asList("ASC")));
        final TableInfo _infoImages = new TableInfo("images", _columnsImages, _foreignKeysImages, _indicesImages);
        final TableInfo _existingImages = TableInfo.read(db, "images");
        if (!_infoImages.equals(_existingImages)) {
          return new RoomOpenHelper.ValidationResult(false, "images(com.alcedo.studio.data.local.ImageEntity).\n"
                  + " Expected:\n" + _infoImages + "\n"
                  + " Found:\n" + _existingImages);
        }
        final HashMap<String, TableInfo.Column> _columnsSleeveElements = new HashMap<String, TableInfo.Column>(12);
        _columnsSleeveElements.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSleeveElements.put("parentId", new TableInfo.Column("parentId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSleeveElements.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSleeveElements.put("sleevePath", new TableInfo.Column("sleevePath", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSleeveElements.put("isFolder", new TableInfo.Column("isFolder", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSleeveElements.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSleeveElements.put("modifiedAt", new TableInfo.Column("modifiedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSleeveElements.put("imageId", new TableInfo.Column("imageId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSleeveElements.put("childCount", new TableInfo.Column("childCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSleeveElements.put("imageCount", new TableInfo.Column("imageCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSleeveElements.put("isSmartCollection", new TableInfo.Column("isSmartCollection", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSleeveElements.put("smartFilterJson", new TableInfo.Column("smartFilterJson", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSleeveElements = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSleeveElements = new HashSet<TableInfo.Index>(2);
        _indicesSleeveElements.add(new TableInfo.Index("index_sleeve_elements_parentId", false, Arrays.asList("parentId"), Arrays.asList("ASC")));
        _indicesSleeveElements.add(new TableInfo.Index("index_sleeve_elements_sleevePath", true, Arrays.asList("sleevePath"), Arrays.asList("ASC")));
        final TableInfo _infoSleeveElements = new TableInfo("sleeve_elements", _columnsSleeveElements, _foreignKeysSleeveElements, _indicesSleeveElements);
        final TableInfo _existingSleeveElements = TableInfo.read(db, "sleeve_elements");
        if (!_infoSleeveElements.equals(_existingSleeveElements)) {
          return new RoomOpenHelper.ValidationResult(false, "sleeve_elements(com.alcedo.studio.data.local.SleeveElementEntity).\n"
                  + " Expected:\n" + _infoSleeveElements + "\n"
                  + " Found:\n" + _existingSleeveElements);
        }
        final HashMap<String, TableInfo.Column> _columnsProjects = new HashMap<String, TableInfo.Column>(15);
        _columnsProjects.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProjects.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProjects.put("filePath", new TableInfo.Column("filePath", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProjects.put("rootSleeveId", new TableInfo.Column("rootSleeveId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProjects.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProjects.put("modifiedAt", new TableInfo.Column("modifiedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProjects.put("description", new TableInfo.Column("description", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProjects.put("version", new TableInfo.Column("version", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProjects.put("schemaVersion", new TableInfo.Column("schemaVersion", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProjects.put("imageCount", new TableInfo.Column("imageCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProjects.put("totalSizeBytes", new TableInfo.Column("totalSizeBytes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProjects.put("thumbnailPath", new TableInfo.Column("thumbnailPath", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProjects.put("tags", new TableInfo.Column("tags", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProjects.put("isFavorite", new TableInfo.Column("isFavorite", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProjects.put("lastOpenedAt", new TableInfo.Column("lastOpenedAt", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysProjects = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesProjects = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoProjects = new TableInfo("projects", _columnsProjects, _foreignKeysProjects, _indicesProjects);
        final TableInfo _existingProjects = TableInfo.read(db, "projects");
        if (!_infoProjects.equals(_existingProjects)) {
          return new RoomOpenHelper.ValidationResult(false, "projects(com.alcedo.studio.data.local.ProjectEntity).\n"
                  + " Expected:\n" + _infoProjects + "\n"
                  + " Found:\n" + _existingProjects);
        }
        final HashMap<String, TableInfo.Column> _columnsEditVersions = new HashMap<String, TableInfo.Column>(9);
        _columnsEditVersions.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEditVersions.put("imageId", new TableInfo.Column("imageId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEditVersions.put("parentId", new TableInfo.Column("parentId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEditVersions.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEditVersions.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEditVersions.put("cumulativeParamsJson", new TableInfo.Column("cumulativeParamsJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEditVersions.put("isVirtualCopy", new TableInfo.Column("isVirtualCopy", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEditVersions.put("isActive", new TableInfo.Column("isActive", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEditVersions.put("note", new TableInfo.Column("note", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysEditVersions = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesEditVersions = new HashSet<TableInfo.Index>(1);
        _indicesEditVersions.add(new TableInfo.Index("index_edit_versions_imageId", false, Arrays.asList("imageId"), Arrays.asList("ASC")));
        final TableInfo _infoEditVersions = new TableInfo("edit_versions", _columnsEditVersions, _foreignKeysEditVersions, _indicesEditVersions);
        final TableInfo _existingEditVersions = TableInfo.read(db, "edit_versions");
        if (!_infoEditVersions.equals(_existingEditVersions)) {
          return new RoomOpenHelper.ValidationResult(false, "edit_versions(com.alcedo.studio.data.local.EditVersionEntity).\n"
                  + " Expected:\n" + _infoEditVersions + "\n"
                  + " Found:\n" + _existingEditVersions);
        }
        final HashMap<String, TableInfo.Column> _columnsEditTransactions = new HashMap<String, TableInfo.Column>(7);
        _columnsEditTransactions.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEditTransactions.put("versionId", new TableInfo.Column("versionId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEditTransactions.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEditTransactions.put("label", new TableInfo.Column("label", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEditTransactions.put("paramDeltaJson", new TableInfo.Column("paramDeltaJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEditTransactions.put("maskIds", new TableInfo.Column("maskIds", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEditTransactions.put("source", new TableInfo.Column("source", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysEditTransactions = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesEditTransactions = new HashSet<TableInfo.Index>(1);
        _indicesEditTransactions.add(new TableInfo.Index("index_edit_transactions_versionId", false, Arrays.asList("versionId"), Arrays.asList("ASC")));
        final TableInfo _infoEditTransactions = new TableInfo("edit_transactions", _columnsEditTransactions, _foreignKeysEditTransactions, _indicesEditTransactions);
        final TableInfo _existingEditTransactions = TableInfo.read(db, "edit_transactions");
        if (!_infoEditTransactions.equals(_existingEditTransactions)) {
          return new RoomOpenHelper.ValidationResult(false, "edit_transactions(com.alcedo.studio.data.local.EditTransactionEntity).\n"
                  + " Expected:\n" + _infoEditTransactions + "\n"
                  + " Found:\n" + _existingEditTransactions);
        }
        final HashMap<String, TableInfo.Column> _columnsAiEmbeddings = new HashMap<String, TableInfo.Column>(7);
        _columnsAiEmbeddings.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAiEmbeddings.put("imageId", new TableInfo.Column("imageId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAiEmbeddings.put("modelId", new TableInfo.Column("modelId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAiEmbeddings.put("dimensions", new TableInfo.Column("dimensions", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAiEmbeddings.put("generatedAt", new TableInfo.Column("generatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAiEmbeddings.put("norm", new TableInfo.Column("norm", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAiEmbeddings.put("embeddingBlob", new TableInfo.Column("embeddingBlob", "BLOB", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysAiEmbeddings = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesAiEmbeddings = new HashSet<TableInfo.Index>(2);
        _indicesAiEmbeddings.add(new TableInfo.Index("index_ai_embeddings_imageId", false, Arrays.asList("imageId"), Arrays.asList("ASC")));
        _indicesAiEmbeddings.add(new TableInfo.Index("index_ai_embeddings_modelId", false, Arrays.asList("modelId"), Arrays.asList("ASC")));
        final TableInfo _infoAiEmbeddings = new TableInfo("ai_embeddings", _columnsAiEmbeddings, _foreignKeysAiEmbeddings, _indicesAiEmbeddings);
        final TableInfo _existingAiEmbeddings = TableInfo.read(db, "ai_embeddings");
        if (!_infoAiEmbeddings.equals(_existingAiEmbeddings)) {
          return new RoomOpenHelper.ValidationResult(false, "ai_embeddings(com.alcedo.studio.data.local.AiEmbeddingEntity).\n"
                  + " Expected:\n" + _infoAiEmbeddings + "\n"
                  + " Found:\n" + _existingAiEmbeddings);
        }
        final HashMap<String, TableInfo.Column> _columnsAiRatings = new HashMap<String, TableInfo.Column>(15);
        _columnsAiRatings.put("imageId", new TableInfo.Column("imageId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAiRatings.put("overallScore", new TableInfo.Column("overallScore", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAiRatings.put("technicalScore", new TableInfo.Column("technicalScore", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAiRatings.put("aestheticScore", new TableInfo.Column("aestheticScore", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAiRatings.put("sharpnessScore", new TableInfo.Column("sharpnessScore", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAiRatings.put("exposureScore", new TableInfo.Column("exposureScore", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAiRatings.put("compositionScore", new TableInfo.Column("compositionScore", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAiRatings.put("emotionScore", new TableInfo.Column("emotionScore", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAiRatings.put("rationale", new TableInfo.Column("rationale", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAiRatings.put("suggestedRating", new TableInfo.Column("suggestedRating", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAiRatings.put("suggestedFlag", new TableInfo.Column("suggestedFlag", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAiRatings.put("generatedAt", new TableInfo.Column("generatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAiRatings.put("modelId", new TableInfo.Column("modelId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAiRatings.put("provider", new TableInfo.Column("provider", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAiRatings.put("confidence", new TableInfo.Column("confidence", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysAiRatings = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesAiRatings = new HashSet<TableInfo.Index>(1);
        _indicesAiRatings.add(new TableInfo.Index("index_ai_ratings_imageId", true, Arrays.asList("imageId"), Arrays.asList("ASC")));
        final TableInfo _infoAiRatings = new TableInfo("ai_ratings", _columnsAiRatings, _foreignKeysAiRatings, _indicesAiRatings);
        final TableInfo _existingAiRatings = TableInfo.read(db, "ai_ratings");
        if (!_infoAiRatings.equals(_existingAiRatings)) {
          return new RoomOpenHelper.ValidationResult(false, "ai_ratings(com.alcedo.studio.data.local.AiRatingEntity).\n"
                  + " Expected:\n" + _infoAiRatings + "\n"
                  + " Found:\n" + _existingAiRatings);
        }
        final HashMap<String, TableInfo.Column> _columnsPipelinePresets = new HashMap<String, TableInfo.Column>(8);
        _columnsPipelinePresets.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPipelinePresets.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPipelinePresets.put("category", new TableInfo.Column("category", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPipelinePresets.put("adjustmentsJson", new TableInfo.Column("adjustmentsJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPipelinePresets.put("isBuiltIn", new TableInfo.Column("isBuiltIn", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPipelinePresets.put("isFavorite", new TableInfo.Column("isFavorite", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPipelinePresets.put("thumbnailPath", new TableInfo.Column("thumbnailPath", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPipelinePresets.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysPipelinePresets = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesPipelinePresets = new HashSet<TableInfo.Index>(1);
        _indicesPipelinePresets.add(new TableInfo.Index("index_pipeline_presets_category", false, Arrays.asList("category"), Arrays.asList("ASC")));
        final TableInfo _infoPipelinePresets = new TableInfo("pipeline_presets", _columnsPipelinePresets, _foreignKeysPipelinePresets, _indicesPipelinePresets);
        final TableInfo _existingPipelinePresets = TableInfo.read(db, "pipeline_presets");
        if (!_infoPipelinePresets.equals(_existingPipelinePresets)) {
          return new RoomOpenHelper.ValidationResult(false, "pipeline_presets(com.alcedo.studio.data.local.PipelinePresetEntity).\n"
                  + " Expected:\n" + _infoPipelinePresets + "\n"
                  + " Found:\n" + _existingPipelinePresets);
        }
        final HashMap<String, TableInfo.Column> _columnsAiModels = new HashMap<String, TableInfo.Column>(12);
        _columnsAiModels.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAiModels.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAiModels.put("kind", new TableInfo.Column("kind", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAiModels.put("version", new TableInfo.Column("version", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAiModels.put("sizeBytes", new TableInfo.Column("sizeBytes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAiModels.put("downloadUrl", new TableInfo.Column("downloadUrl", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAiModels.put("sha256", new TableInfo.Column("sha256", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAiModels.put("localPath", new TableInfo.Column("localPath", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAiModels.put("isDownloaded", new TableInfo.Column("isDownloaded", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAiModels.put("isDefault", new TableInfo.Column("isDefault", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAiModels.put("dimensions", new TableInfo.Column("dimensions", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAiModels.put("description", new TableInfo.Column("description", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysAiModels = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesAiModels = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoAiModels = new TableInfo("ai_models", _columnsAiModels, _foreignKeysAiModels, _indicesAiModels);
        final TableInfo _existingAiModels = TableInfo.read(db, "ai_models");
        if (!_infoAiModels.equals(_existingAiModels)) {
          return new RoomOpenHelper.ValidationResult(false, "ai_models(com.alcedo.studio.data.local.AiModelEntity).\n"
                  + " Expected:\n" + _infoAiModels + "\n"
                  + " Found:\n" + _existingAiModels);
        }
        final HashMap<String, TableInfo.Column> _columnsLensProfiles = new HashMap<String, TableInfo.Column>(6);
        _columnsLensProfiles.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLensProfiles.put("lensId", new TableInfo.Column("lensId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLensProfiles.put("displayName", new TableInfo.Column("displayName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLensProfiles.put("maker", new TableInfo.Column("maker", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLensProfiles.put("profileJson", new TableInfo.Column("profileJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsLensProfiles.put("isCalibrated", new TableInfo.Column("isCalibrated", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysLensProfiles = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesLensProfiles = new HashSet<TableInfo.Index>(1);
        _indicesLensProfiles.add(new TableInfo.Index("index_lens_profiles_lensId", true, Arrays.asList("lensId"), Arrays.asList("ASC")));
        final TableInfo _infoLensProfiles = new TableInfo("lens_profiles", _columnsLensProfiles, _foreignKeysLensProfiles, _indicesLensProfiles);
        final TableInfo _existingLensProfiles = TableInfo.read(db, "lens_profiles");
        if (!_infoLensProfiles.equals(_existingLensProfiles)) {
          return new RoomOpenHelper.ValidationResult(false, "lens_profiles(com.alcedo.studio.data.local.LensProfileEntity).\n"
                  + " Expected:\n" + _infoLensProfiles + "\n"
                  + " Found:\n" + _existingLensProfiles);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "06e95033970084286fde1843e9241fc1", "54000f937924cc323da6cc0af6807062");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "images","sleeve_elements","projects","edit_versions","edit_transactions","ai_embeddings","ai_ratings","pipeline_presets","ai_models","lens_profiles");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `images`");
      _db.execSQL("DELETE FROM `sleeve_elements`");
      _db.execSQL("DELETE FROM `projects`");
      _db.execSQL("DELETE FROM `edit_versions`");
      _db.execSQL("DELETE FROM `edit_transactions`");
      _db.execSQL("DELETE FROM `ai_embeddings`");
      _db.execSQL("DELETE FROM `ai_ratings`");
      _db.execSQL("DELETE FROM `pipeline_presets`");
      _db.execSQL("DELETE FROM `ai_models`");
      _db.execSQL("DELETE FROM `lens_profiles`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(ImageDao.class, ImageDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(EditHistoryDao.class, EditHistoryDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(AiEmbeddingDao.class, AiEmbeddingDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(PipelinePresetDao.class, PipelinePresetDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public ImageDao imageDao() {
    if (_imageDao != null) {
      return _imageDao;
    } else {
      synchronized(this) {
        if(_imageDao == null) {
          _imageDao = new ImageDao_Impl(this);
        }
        return _imageDao;
      }
    }
  }

  @Override
  public EditHistoryDao editHistoryDao() {
    if (_editHistoryDao != null) {
      return _editHistoryDao;
    } else {
      synchronized(this) {
        if(_editHistoryDao == null) {
          _editHistoryDao = new EditHistoryDao_Impl(this);
        }
        return _editHistoryDao;
      }
    }
  }

  @Override
  public AiEmbeddingDao aiEmbeddingDao() {
    if (_aiEmbeddingDao != null) {
      return _aiEmbeddingDao;
    } else {
      synchronized(this) {
        if(_aiEmbeddingDao == null) {
          _aiEmbeddingDao = new AiEmbeddingDao_Impl(this);
        }
        return _aiEmbeddingDao;
      }
    }
  }

  @Override
  public PipelinePresetDao pipelinePresetDao() {
    if (_pipelinePresetDao != null) {
      return _pipelinePresetDao;
    } else {
      synchronized(this) {
        if(_pipelinePresetDao == null) {
          _pipelinePresetDao = new PipelinePresetDao_Impl(this);
        }
        return _pipelinePresetDao;
      }
    }
  }
}
