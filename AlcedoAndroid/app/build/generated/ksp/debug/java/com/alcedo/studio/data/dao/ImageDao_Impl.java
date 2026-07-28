package com.alcedo.studio.data.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomDatabaseKt;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.alcedo.studio.data.local.ImageEntity;
import com.alcedo.studio.data.local.SleeveTypeConverters;
import com.alcedo.studio.data.model.ColorLabel;
import com.alcedo.studio.data.model.ImageFlag;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Float;
import java.lang.IllegalStateException;
import java.lang.Integer;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ImageDao_Impl implements ImageDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ImageEntity> __insertionAdapterOfImageEntity;

  private final SleeveTypeConverters __sleeveTypeConverters = new SleeveTypeConverters();

  private final EntityDeletionOrUpdateAdapter<ImageEntity> __deletionAdapterOfImageEntity;

  private final EntityDeletionOrUpdateAdapter<ImageEntity> __updateAdapterOfImageEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  private final SharedSQLiteStatement __preparedStmtOfSetRating;

  private final SharedSQLiteStatement __preparedStmtOfSetFlag;

  private final SharedSQLiteStatement __preparedStmtOfSetColorLabel;

  private final SharedSQLiteStatement __preparedStmtOfSetCurrentVersion;

  private final SharedSQLiteStatement __preparedStmtOfSetAiMetadata;

  private final SharedSQLiteStatement __preparedStmtOfSetThumbnailPath;

  private final SharedSQLiteStatement __preparedStmtOfSetHidden;

  public ImageDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfImageEntity = new EntityInsertionAdapter<ImageEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `images` (`id`,`sleevePath`,`originalUri`,`displayName`,`fileExtension`,`fileSizeBytes`,`width`,`height`,`dateAddedEpoch`,`dateCapturedEpoch`,`rating`,`flag`,`colorLabel`,`isRaw`,`isVirtualCopy`,`parentId`,`thumbnailPath`,`currentVersionId`,`aiCaption`,`aiTags`,`aiScore`,`isHidden`,`lensModel`,`cameraModel`,`focalLength`,`iso`,`aperture`,`shutterSpeed`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ImageEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getSleevePath());
        statement.bindString(3, entity.getOriginalUri());
        statement.bindString(4, entity.getDisplayName());
        statement.bindString(5, entity.getFileExtension());
        statement.bindLong(6, entity.getFileSizeBytes());
        statement.bindLong(7, entity.getWidth());
        statement.bindLong(8, entity.getHeight());
        statement.bindLong(9, entity.getDateAddedEpoch());
        statement.bindLong(10, entity.getDateCapturedEpoch());
        statement.bindLong(11, entity.getRating());
        final String _tmp = __sleeveTypeConverters.flagToString(entity.getFlag());
        if (_tmp == null) {
          statement.bindNull(12);
        } else {
          statement.bindString(12, _tmp);
        }
        final String _tmp_1 = __sleeveTypeConverters.colorLabelToString(entity.getColorLabel());
        if (_tmp_1 == null) {
          statement.bindNull(13);
        } else {
          statement.bindString(13, _tmp_1);
        }
        final int _tmp_2 = entity.isRaw() ? 1 : 0;
        statement.bindLong(14, _tmp_2);
        final int _tmp_3 = entity.isVirtualCopy() ? 1 : 0;
        statement.bindLong(15, _tmp_3);
        if (entity.getParentId() == null) {
          statement.bindNull(16);
        } else {
          statement.bindString(16, entity.getParentId());
        }
        if (entity.getThumbnailPath() == null) {
          statement.bindNull(17);
        } else {
          statement.bindString(17, entity.getThumbnailPath());
        }
        if (entity.getCurrentVersionId() == null) {
          statement.bindNull(18);
        } else {
          statement.bindString(18, entity.getCurrentVersionId());
        }
        if (entity.getAiCaption() == null) {
          statement.bindNull(19);
        } else {
          statement.bindString(19, entity.getAiCaption());
        }
        if (entity.getAiTags() == null) {
          statement.bindNull(20);
        } else {
          statement.bindString(20, entity.getAiTags());
        }
        if (entity.getAiScore() == null) {
          statement.bindNull(21);
        } else {
          statement.bindDouble(21, entity.getAiScore());
        }
        final int _tmp_4 = entity.isHidden() ? 1 : 0;
        statement.bindLong(22, _tmp_4);
        if (entity.getLensModel() == null) {
          statement.bindNull(23);
        } else {
          statement.bindString(23, entity.getLensModel());
        }
        if (entity.getCameraModel() == null) {
          statement.bindNull(24);
        } else {
          statement.bindString(24, entity.getCameraModel());
        }
        if (entity.getFocalLength() == null) {
          statement.bindNull(25);
        } else {
          statement.bindDouble(25, entity.getFocalLength());
        }
        if (entity.getIso() == null) {
          statement.bindNull(26);
        } else {
          statement.bindLong(26, entity.getIso());
        }
        if (entity.getAperture() == null) {
          statement.bindNull(27);
        } else {
          statement.bindDouble(27, entity.getAperture());
        }
        if (entity.getShutterSpeed() == null) {
          statement.bindNull(28);
        } else {
          statement.bindString(28, entity.getShutterSpeed());
        }
      }
    };
    this.__deletionAdapterOfImageEntity = new EntityDeletionOrUpdateAdapter<ImageEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `images` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ImageEntity entity) {
        statement.bindString(1, entity.getId());
      }
    };
    this.__updateAdapterOfImageEntity = new EntityDeletionOrUpdateAdapter<ImageEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `images` SET `id` = ?,`sleevePath` = ?,`originalUri` = ?,`displayName` = ?,`fileExtension` = ?,`fileSizeBytes` = ?,`width` = ?,`height` = ?,`dateAddedEpoch` = ?,`dateCapturedEpoch` = ?,`rating` = ?,`flag` = ?,`colorLabel` = ?,`isRaw` = ?,`isVirtualCopy` = ?,`parentId` = ?,`thumbnailPath` = ?,`currentVersionId` = ?,`aiCaption` = ?,`aiTags` = ?,`aiScore` = ?,`isHidden` = ?,`lensModel` = ?,`cameraModel` = ?,`focalLength` = ?,`iso` = ?,`aperture` = ?,`shutterSpeed` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ImageEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getSleevePath());
        statement.bindString(3, entity.getOriginalUri());
        statement.bindString(4, entity.getDisplayName());
        statement.bindString(5, entity.getFileExtension());
        statement.bindLong(6, entity.getFileSizeBytes());
        statement.bindLong(7, entity.getWidth());
        statement.bindLong(8, entity.getHeight());
        statement.bindLong(9, entity.getDateAddedEpoch());
        statement.bindLong(10, entity.getDateCapturedEpoch());
        statement.bindLong(11, entity.getRating());
        final String _tmp = __sleeveTypeConverters.flagToString(entity.getFlag());
        if (_tmp == null) {
          statement.bindNull(12);
        } else {
          statement.bindString(12, _tmp);
        }
        final String _tmp_1 = __sleeveTypeConverters.colorLabelToString(entity.getColorLabel());
        if (_tmp_1 == null) {
          statement.bindNull(13);
        } else {
          statement.bindString(13, _tmp_1);
        }
        final int _tmp_2 = entity.isRaw() ? 1 : 0;
        statement.bindLong(14, _tmp_2);
        final int _tmp_3 = entity.isVirtualCopy() ? 1 : 0;
        statement.bindLong(15, _tmp_3);
        if (entity.getParentId() == null) {
          statement.bindNull(16);
        } else {
          statement.bindString(16, entity.getParentId());
        }
        if (entity.getThumbnailPath() == null) {
          statement.bindNull(17);
        } else {
          statement.bindString(17, entity.getThumbnailPath());
        }
        if (entity.getCurrentVersionId() == null) {
          statement.bindNull(18);
        } else {
          statement.bindString(18, entity.getCurrentVersionId());
        }
        if (entity.getAiCaption() == null) {
          statement.bindNull(19);
        } else {
          statement.bindString(19, entity.getAiCaption());
        }
        if (entity.getAiTags() == null) {
          statement.bindNull(20);
        } else {
          statement.bindString(20, entity.getAiTags());
        }
        if (entity.getAiScore() == null) {
          statement.bindNull(21);
        } else {
          statement.bindDouble(21, entity.getAiScore());
        }
        final int _tmp_4 = entity.isHidden() ? 1 : 0;
        statement.bindLong(22, _tmp_4);
        if (entity.getLensModel() == null) {
          statement.bindNull(23);
        } else {
          statement.bindString(23, entity.getLensModel());
        }
        if (entity.getCameraModel() == null) {
          statement.bindNull(24);
        } else {
          statement.bindString(24, entity.getCameraModel());
        }
        if (entity.getFocalLength() == null) {
          statement.bindNull(25);
        } else {
          statement.bindDouble(25, entity.getFocalLength());
        }
        if (entity.getIso() == null) {
          statement.bindNull(26);
        } else {
          statement.bindLong(26, entity.getIso());
        }
        if (entity.getAperture() == null) {
          statement.bindNull(27);
        } else {
          statement.bindDouble(27, entity.getAperture());
        }
        if (entity.getShutterSpeed() == null) {
          statement.bindNull(28);
        } else {
          statement.bindString(28, entity.getShutterSpeed());
        }
        statement.bindString(29, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM images WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfSetRating = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE images SET rating = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfSetFlag = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE images SET flag = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfSetColorLabel = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE images SET colorLabel = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfSetCurrentVersion = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE images SET currentVersionId = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfSetAiMetadata = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE images SET aiCaption = ?, aiTags = ?, aiScore = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfSetThumbnailPath = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE images SET thumbnailPath = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfSetHidden = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE images SET isHidden = ? WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object upsert(final ImageEntity image, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfImageEntity.insert(image);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertAll(final List<ImageEntity> images,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfImageEntity.insert(images);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final ImageEntity image, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfImageEntity.handle(image);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final ImageEntity image, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfImageEntity.handle(image);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object replaceAll(final List<ImageEntity> images,
      final Continuation<? super Unit> $completion) {
    return RoomDatabaseKt.withTransaction(__db, (__cont) -> ImageDao.super.replaceAll(images, __cont), $completion);
  }

  @Override
  public Object deleteById(final String id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object setRating(final String id, final int rating,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSetRating.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, rating);
        _argIndex = 2;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfSetRating.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object setFlag(final String id, final String flag,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSetFlag.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, flag);
        _argIndex = 2;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfSetFlag.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object setColorLabel(final String id, final String label,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSetColorLabel.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, label);
        _argIndex = 2;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfSetColorLabel.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object setCurrentVersion(final String id, final String versionId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSetCurrentVersion.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, versionId);
        _argIndex = 2;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfSetCurrentVersion.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object setAiMetadata(final String id, final String caption, final String tags,
      final Float score, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSetAiMetadata.acquire();
        int _argIndex = 1;
        if (caption == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, caption);
        }
        _argIndex = 2;
        if (tags == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, tags);
        }
        _argIndex = 3;
        if (score == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindDouble(_argIndex, score);
        }
        _argIndex = 4;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfSetAiMetadata.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object setThumbnailPath(final String id, final String path,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSetThumbnailPath.acquire();
        int _argIndex = 1;
        if (path == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, path);
        }
        _argIndex = 2;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfSetThumbnailPath.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object setHidden(final String id, final boolean hidden,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSetHidden.acquire();
        int _argIndex = 1;
        final int _tmp = hidden ? 1 : 0;
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 2;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfSetHidden.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getById(final String id, final Continuation<? super ImageEntity> $completion) {
    final String _sql = "SELECT * FROM images WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ImageEntity>() {
      @Override
      @Nullable
      public ImageEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSleevePath = CursorUtil.getColumnIndexOrThrow(_cursor, "sleevePath");
          final int _cursorIndexOfOriginalUri = CursorUtil.getColumnIndexOrThrow(_cursor, "originalUri");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfFileExtension = CursorUtil.getColumnIndexOrThrow(_cursor, "fileExtension");
          final int _cursorIndexOfFileSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "fileSizeBytes");
          final int _cursorIndexOfWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "width");
          final int _cursorIndexOfHeight = CursorUtil.getColumnIndexOrThrow(_cursor, "height");
          final int _cursorIndexOfDateAddedEpoch = CursorUtil.getColumnIndexOrThrow(_cursor, "dateAddedEpoch");
          final int _cursorIndexOfDateCapturedEpoch = CursorUtil.getColumnIndexOrThrow(_cursor, "dateCapturedEpoch");
          final int _cursorIndexOfRating = CursorUtil.getColumnIndexOrThrow(_cursor, "rating");
          final int _cursorIndexOfFlag = CursorUtil.getColumnIndexOrThrow(_cursor, "flag");
          final int _cursorIndexOfColorLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "colorLabel");
          final int _cursorIndexOfIsRaw = CursorUtil.getColumnIndexOrThrow(_cursor, "isRaw");
          final int _cursorIndexOfIsVirtualCopy = CursorUtil.getColumnIndexOrThrow(_cursor, "isVirtualCopy");
          final int _cursorIndexOfParentId = CursorUtil.getColumnIndexOrThrow(_cursor, "parentId");
          final int _cursorIndexOfThumbnailPath = CursorUtil.getColumnIndexOrThrow(_cursor, "thumbnailPath");
          final int _cursorIndexOfCurrentVersionId = CursorUtil.getColumnIndexOrThrow(_cursor, "currentVersionId");
          final int _cursorIndexOfAiCaption = CursorUtil.getColumnIndexOrThrow(_cursor, "aiCaption");
          final int _cursorIndexOfAiTags = CursorUtil.getColumnIndexOrThrow(_cursor, "aiTags");
          final int _cursorIndexOfAiScore = CursorUtil.getColumnIndexOrThrow(_cursor, "aiScore");
          final int _cursorIndexOfIsHidden = CursorUtil.getColumnIndexOrThrow(_cursor, "isHidden");
          final int _cursorIndexOfLensModel = CursorUtil.getColumnIndexOrThrow(_cursor, "lensModel");
          final int _cursorIndexOfCameraModel = CursorUtil.getColumnIndexOrThrow(_cursor, "cameraModel");
          final int _cursorIndexOfFocalLength = CursorUtil.getColumnIndexOrThrow(_cursor, "focalLength");
          final int _cursorIndexOfIso = CursorUtil.getColumnIndexOrThrow(_cursor, "iso");
          final int _cursorIndexOfAperture = CursorUtil.getColumnIndexOrThrow(_cursor, "aperture");
          final int _cursorIndexOfShutterSpeed = CursorUtil.getColumnIndexOrThrow(_cursor, "shutterSpeed");
          final ImageEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpSleevePath;
            _tmpSleevePath = _cursor.getString(_cursorIndexOfSleevePath);
            final String _tmpOriginalUri;
            _tmpOriginalUri = _cursor.getString(_cursorIndexOfOriginalUri);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final String _tmpFileExtension;
            _tmpFileExtension = _cursor.getString(_cursorIndexOfFileExtension);
            final long _tmpFileSizeBytes;
            _tmpFileSizeBytes = _cursor.getLong(_cursorIndexOfFileSizeBytes);
            final int _tmpWidth;
            _tmpWidth = _cursor.getInt(_cursorIndexOfWidth);
            final int _tmpHeight;
            _tmpHeight = _cursor.getInt(_cursorIndexOfHeight);
            final long _tmpDateAddedEpoch;
            _tmpDateAddedEpoch = _cursor.getLong(_cursorIndexOfDateAddedEpoch);
            final long _tmpDateCapturedEpoch;
            _tmpDateCapturedEpoch = _cursor.getLong(_cursorIndexOfDateCapturedEpoch);
            final int _tmpRating;
            _tmpRating = _cursor.getInt(_cursorIndexOfRating);
            final ImageFlag _tmpFlag;
            final String _tmp;
            if (_cursor.isNull(_cursorIndexOfFlag)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getString(_cursorIndexOfFlag);
            }
            final ImageFlag _tmp_1 = __sleeveTypeConverters.stringToFlag(_tmp);
            if (_tmp_1 == null) {
              throw new IllegalStateException("Expected NON-NULL 'com.alcedo.studio.data.model.ImageFlag', but it was NULL.");
            } else {
              _tmpFlag = _tmp_1;
            }
            final ColorLabel _tmpColorLabel;
            final String _tmp_2;
            if (_cursor.isNull(_cursorIndexOfColorLabel)) {
              _tmp_2 = null;
            } else {
              _tmp_2 = _cursor.getString(_cursorIndexOfColorLabel);
            }
            final ColorLabel _tmp_3 = __sleeveTypeConverters.stringToColorLabel(_tmp_2);
            if (_tmp_3 == null) {
              throw new IllegalStateException("Expected NON-NULL 'com.alcedo.studio.data.model.ColorLabel', but it was NULL.");
            } else {
              _tmpColorLabel = _tmp_3;
            }
            final boolean _tmpIsRaw;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfIsRaw);
            _tmpIsRaw = _tmp_4 != 0;
            final boolean _tmpIsVirtualCopy;
            final int _tmp_5;
            _tmp_5 = _cursor.getInt(_cursorIndexOfIsVirtualCopy);
            _tmpIsVirtualCopy = _tmp_5 != 0;
            final String _tmpParentId;
            if (_cursor.isNull(_cursorIndexOfParentId)) {
              _tmpParentId = null;
            } else {
              _tmpParentId = _cursor.getString(_cursorIndexOfParentId);
            }
            final String _tmpThumbnailPath;
            if (_cursor.isNull(_cursorIndexOfThumbnailPath)) {
              _tmpThumbnailPath = null;
            } else {
              _tmpThumbnailPath = _cursor.getString(_cursorIndexOfThumbnailPath);
            }
            final String _tmpCurrentVersionId;
            if (_cursor.isNull(_cursorIndexOfCurrentVersionId)) {
              _tmpCurrentVersionId = null;
            } else {
              _tmpCurrentVersionId = _cursor.getString(_cursorIndexOfCurrentVersionId);
            }
            final String _tmpAiCaption;
            if (_cursor.isNull(_cursorIndexOfAiCaption)) {
              _tmpAiCaption = null;
            } else {
              _tmpAiCaption = _cursor.getString(_cursorIndexOfAiCaption);
            }
            final String _tmpAiTags;
            if (_cursor.isNull(_cursorIndexOfAiTags)) {
              _tmpAiTags = null;
            } else {
              _tmpAiTags = _cursor.getString(_cursorIndexOfAiTags);
            }
            final Float _tmpAiScore;
            if (_cursor.isNull(_cursorIndexOfAiScore)) {
              _tmpAiScore = null;
            } else {
              _tmpAiScore = _cursor.getFloat(_cursorIndexOfAiScore);
            }
            final boolean _tmpIsHidden;
            final int _tmp_6;
            _tmp_6 = _cursor.getInt(_cursorIndexOfIsHidden);
            _tmpIsHidden = _tmp_6 != 0;
            final String _tmpLensModel;
            if (_cursor.isNull(_cursorIndexOfLensModel)) {
              _tmpLensModel = null;
            } else {
              _tmpLensModel = _cursor.getString(_cursorIndexOfLensModel);
            }
            final String _tmpCameraModel;
            if (_cursor.isNull(_cursorIndexOfCameraModel)) {
              _tmpCameraModel = null;
            } else {
              _tmpCameraModel = _cursor.getString(_cursorIndexOfCameraModel);
            }
            final Float _tmpFocalLength;
            if (_cursor.isNull(_cursorIndexOfFocalLength)) {
              _tmpFocalLength = null;
            } else {
              _tmpFocalLength = _cursor.getFloat(_cursorIndexOfFocalLength);
            }
            final Integer _tmpIso;
            if (_cursor.isNull(_cursorIndexOfIso)) {
              _tmpIso = null;
            } else {
              _tmpIso = _cursor.getInt(_cursorIndexOfIso);
            }
            final Float _tmpAperture;
            if (_cursor.isNull(_cursorIndexOfAperture)) {
              _tmpAperture = null;
            } else {
              _tmpAperture = _cursor.getFloat(_cursorIndexOfAperture);
            }
            final String _tmpShutterSpeed;
            if (_cursor.isNull(_cursorIndexOfShutterSpeed)) {
              _tmpShutterSpeed = null;
            } else {
              _tmpShutterSpeed = _cursor.getString(_cursorIndexOfShutterSpeed);
            }
            _result = new ImageEntity(_tmpId,_tmpSleevePath,_tmpOriginalUri,_tmpDisplayName,_tmpFileExtension,_tmpFileSizeBytes,_tmpWidth,_tmpHeight,_tmpDateAddedEpoch,_tmpDateCapturedEpoch,_tmpRating,_tmpFlag,_tmpColorLabel,_tmpIsRaw,_tmpIsVirtualCopy,_tmpParentId,_tmpThumbnailPath,_tmpCurrentVersionId,_tmpAiCaption,_tmpAiTags,_tmpAiScore,_tmpIsHidden,_tmpLensModel,_tmpCameraModel,_tmpFocalLength,_tmpIso,_tmpAperture,_tmpShutterSpeed);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<ImageEntity> observeById(final String id) {
    final String _sql = "SELECT * FROM images WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"images"}, new Callable<ImageEntity>() {
      @Override
      @Nullable
      public ImageEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSleevePath = CursorUtil.getColumnIndexOrThrow(_cursor, "sleevePath");
          final int _cursorIndexOfOriginalUri = CursorUtil.getColumnIndexOrThrow(_cursor, "originalUri");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfFileExtension = CursorUtil.getColumnIndexOrThrow(_cursor, "fileExtension");
          final int _cursorIndexOfFileSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "fileSizeBytes");
          final int _cursorIndexOfWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "width");
          final int _cursorIndexOfHeight = CursorUtil.getColumnIndexOrThrow(_cursor, "height");
          final int _cursorIndexOfDateAddedEpoch = CursorUtil.getColumnIndexOrThrow(_cursor, "dateAddedEpoch");
          final int _cursorIndexOfDateCapturedEpoch = CursorUtil.getColumnIndexOrThrow(_cursor, "dateCapturedEpoch");
          final int _cursorIndexOfRating = CursorUtil.getColumnIndexOrThrow(_cursor, "rating");
          final int _cursorIndexOfFlag = CursorUtil.getColumnIndexOrThrow(_cursor, "flag");
          final int _cursorIndexOfColorLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "colorLabel");
          final int _cursorIndexOfIsRaw = CursorUtil.getColumnIndexOrThrow(_cursor, "isRaw");
          final int _cursorIndexOfIsVirtualCopy = CursorUtil.getColumnIndexOrThrow(_cursor, "isVirtualCopy");
          final int _cursorIndexOfParentId = CursorUtil.getColumnIndexOrThrow(_cursor, "parentId");
          final int _cursorIndexOfThumbnailPath = CursorUtil.getColumnIndexOrThrow(_cursor, "thumbnailPath");
          final int _cursorIndexOfCurrentVersionId = CursorUtil.getColumnIndexOrThrow(_cursor, "currentVersionId");
          final int _cursorIndexOfAiCaption = CursorUtil.getColumnIndexOrThrow(_cursor, "aiCaption");
          final int _cursorIndexOfAiTags = CursorUtil.getColumnIndexOrThrow(_cursor, "aiTags");
          final int _cursorIndexOfAiScore = CursorUtil.getColumnIndexOrThrow(_cursor, "aiScore");
          final int _cursorIndexOfIsHidden = CursorUtil.getColumnIndexOrThrow(_cursor, "isHidden");
          final int _cursorIndexOfLensModel = CursorUtil.getColumnIndexOrThrow(_cursor, "lensModel");
          final int _cursorIndexOfCameraModel = CursorUtil.getColumnIndexOrThrow(_cursor, "cameraModel");
          final int _cursorIndexOfFocalLength = CursorUtil.getColumnIndexOrThrow(_cursor, "focalLength");
          final int _cursorIndexOfIso = CursorUtil.getColumnIndexOrThrow(_cursor, "iso");
          final int _cursorIndexOfAperture = CursorUtil.getColumnIndexOrThrow(_cursor, "aperture");
          final int _cursorIndexOfShutterSpeed = CursorUtil.getColumnIndexOrThrow(_cursor, "shutterSpeed");
          final ImageEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpSleevePath;
            _tmpSleevePath = _cursor.getString(_cursorIndexOfSleevePath);
            final String _tmpOriginalUri;
            _tmpOriginalUri = _cursor.getString(_cursorIndexOfOriginalUri);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final String _tmpFileExtension;
            _tmpFileExtension = _cursor.getString(_cursorIndexOfFileExtension);
            final long _tmpFileSizeBytes;
            _tmpFileSizeBytes = _cursor.getLong(_cursorIndexOfFileSizeBytes);
            final int _tmpWidth;
            _tmpWidth = _cursor.getInt(_cursorIndexOfWidth);
            final int _tmpHeight;
            _tmpHeight = _cursor.getInt(_cursorIndexOfHeight);
            final long _tmpDateAddedEpoch;
            _tmpDateAddedEpoch = _cursor.getLong(_cursorIndexOfDateAddedEpoch);
            final long _tmpDateCapturedEpoch;
            _tmpDateCapturedEpoch = _cursor.getLong(_cursorIndexOfDateCapturedEpoch);
            final int _tmpRating;
            _tmpRating = _cursor.getInt(_cursorIndexOfRating);
            final ImageFlag _tmpFlag;
            final String _tmp;
            if (_cursor.isNull(_cursorIndexOfFlag)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getString(_cursorIndexOfFlag);
            }
            final ImageFlag _tmp_1 = __sleeveTypeConverters.stringToFlag(_tmp);
            if (_tmp_1 == null) {
              throw new IllegalStateException("Expected NON-NULL 'com.alcedo.studio.data.model.ImageFlag', but it was NULL.");
            } else {
              _tmpFlag = _tmp_1;
            }
            final ColorLabel _tmpColorLabel;
            final String _tmp_2;
            if (_cursor.isNull(_cursorIndexOfColorLabel)) {
              _tmp_2 = null;
            } else {
              _tmp_2 = _cursor.getString(_cursorIndexOfColorLabel);
            }
            final ColorLabel _tmp_3 = __sleeveTypeConverters.stringToColorLabel(_tmp_2);
            if (_tmp_3 == null) {
              throw new IllegalStateException("Expected NON-NULL 'com.alcedo.studio.data.model.ColorLabel', but it was NULL.");
            } else {
              _tmpColorLabel = _tmp_3;
            }
            final boolean _tmpIsRaw;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfIsRaw);
            _tmpIsRaw = _tmp_4 != 0;
            final boolean _tmpIsVirtualCopy;
            final int _tmp_5;
            _tmp_5 = _cursor.getInt(_cursorIndexOfIsVirtualCopy);
            _tmpIsVirtualCopy = _tmp_5 != 0;
            final String _tmpParentId;
            if (_cursor.isNull(_cursorIndexOfParentId)) {
              _tmpParentId = null;
            } else {
              _tmpParentId = _cursor.getString(_cursorIndexOfParentId);
            }
            final String _tmpThumbnailPath;
            if (_cursor.isNull(_cursorIndexOfThumbnailPath)) {
              _tmpThumbnailPath = null;
            } else {
              _tmpThumbnailPath = _cursor.getString(_cursorIndexOfThumbnailPath);
            }
            final String _tmpCurrentVersionId;
            if (_cursor.isNull(_cursorIndexOfCurrentVersionId)) {
              _tmpCurrentVersionId = null;
            } else {
              _tmpCurrentVersionId = _cursor.getString(_cursorIndexOfCurrentVersionId);
            }
            final String _tmpAiCaption;
            if (_cursor.isNull(_cursorIndexOfAiCaption)) {
              _tmpAiCaption = null;
            } else {
              _tmpAiCaption = _cursor.getString(_cursorIndexOfAiCaption);
            }
            final String _tmpAiTags;
            if (_cursor.isNull(_cursorIndexOfAiTags)) {
              _tmpAiTags = null;
            } else {
              _tmpAiTags = _cursor.getString(_cursorIndexOfAiTags);
            }
            final Float _tmpAiScore;
            if (_cursor.isNull(_cursorIndexOfAiScore)) {
              _tmpAiScore = null;
            } else {
              _tmpAiScore = _cursor.getFloat(_cursorIndexOfAiScore);
            }
            final boolean _tmpIsHidden;
            final int _tmp_6;
            _tmp_6 = _cursor.getInt(_cursorIndexOfIsHidden);
            _tmpIsHidden = _tmp_6 != 0;
            final String _tmpLensModel;
            if (_cursor.isNull(_cursorIndexOfLensModel)) {
              _tmpLensModel = null;
            } else {
              _tmpLensModel = _cursor.getString(_cursorIndexOfLensModel);
            }
            final String _tmpCameraModel;
            if (_cursor.isNull(_cursorIndexOfCameraModel)) {
              _tmpCameraModel = null;
            } else {
              _tmpCameraModel = _cursor.getString(_cursorIndexOfCameraModel);
            }
            final Float _tmpFocalLength;
            if (_cursor.isNull(_cursorIndexOfFocalLength)) {
              _tmpFocalLength = null;
            } else {
              _tmpFocalLength = _cursor.getFloat(_cursorIndexOfFocalLength);
            }
            final Integer _tmpIso;
            if (_cursor.isNull(_cursorIndexOfIso)) {
              _tmpIso = null;
            } else {
              _tmpIso = _cursor.getInt(_cursorIndexOfIso);
            }
            final Float _tmpAperture;
            if (_cursor.isNull(_cursorIndexOfAperture)) {
              _tmpAperture = null;
            } else {
              _tmpAperture = _cursor.getFloat(_cursorIndexOfAperture);
            }
            final String _tmpShutterSpeed;
            if (_cursor.isNull(_cursorIndexOfShutterSpeed)) {
              _tmpShutterSpeed = null;
            } else {
              _tmpShutterSpeed = _cursor.getString(_cursorIndexOfShutterSpeed);
            }
            _result = new ImageEntity(_tmpId,_tmpSleevePath,_tmpOriginalUri,_tmpDisplayName,_tmpFileExtension,_tmpFileSizeBytes,_tmpWidth,_tmpHeight,_tmpDateAddedEpoch,_tmpDateCapturedEpoch,_tmpRating,_tmpFlag,_tmpColorLabel,_tmpIsRaw,_tmpIsVirtualCopy,_tmpParentId,_tmpThumbnailPath,_tmpCurrentVersionId,_tmpAiCaption,_tmpAiTags,_tmpAiScore,_tmpIsHidden,_tmpLensModel,_tmpCameraModel,_tmpFocalLength,_tmpIso,_tmpAperture,_tmpShutterSpeed);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<ImageEntity>> observeByFolder(final String folderPath) {
    final String _sql = "SELECT * FROM images WHERE sleevePath = ? ORDER BY dateCapturedEpoch DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, folderPath);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"images"}, new Callable<List<ImageEntity>>() {
      @Override
      @NonNull
      public List<ImageEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSleevePath = CursorUtil.getColumnIndexOrThrow(_cursor, "sleevePath");
          final int _cursorIndexOfOriginalUri = CursorUtil.getColumnIndexOrThrow(_cursor, "originalUri");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfFileExtension = CursorUtil.getColumnIndexOrThrow(_cursor, "fileExtension");
          final int _cursorIndexOfFileSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "fileSizeBytes");
          final int _cursorIndexOfWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "width");
          final int _cursorIndexOfHeight = CursorUtil.getColumnIndexOrThrow(_cursor, "height");
          final int _cursorIndexOfDateAddedEpoch = CursorUtil.getColumnIndexOrThrow(_cursor, "dateAddedEpoch");
          final int _cursorIndexOfDateCapturedEpoch = CursorUtil.getColumnIndexOrThrow(_cursor, "dateCapturedEpoch");
          final int _cursorIndexOfRating = CursorUtil.getColumnIndexOrThrow(_cursor, "rating");
          final int _cursorIndexOfFlag = CursorUtil.getColumnIndexOrThrow(_cursor, "flag");
          final int _cursorIndexOfColorLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "colorLabel");
          final int _cursorIndexOfIsRaw = CursorUtil.getColumnIndexOrThrow(_cursor, "isRaw");
          final int _cursorIndexOfIsVirtualCopy = CursorUtil.getColumnIndexOrThrow(_cursor, "isVirtualCopy");
          final int _cursorIndexOfParentId = CursorUtil.getColumnIndexOrThrow(_cursor, "parentId");
          final int _cursorIndexOfThumbnailPath = CursorUtil.getColumnIndexOrThrow(_cursor, "thumbnailPath");
          final int _cursorIndexOfCurrentVersionId = CursorUtil.getColumnIndexOrThrow(_cursor, "currentVersionId");
          final int _cursorIndexOfAiCaption = CursorUtil.getColumnIndexOrThrow(_cursor, "aiCaption");
          final int _cursorIndexOfAiTags = CursorUtil.getColumnIndexOrThrow(_cursor, "aiTags");
          final int _cursorIndexOfAiScore = CursorUtil.getColumnIndexOrThrow(_cursor, "aiScore");
          final int _cursorIndexOfIsHidden = CursorUtil.getColumnIndexOrThrow(_cursor, "isHidden");
          final int _cursorIndexOfLensModel = CursorUtil.getColumnIndexOrThrow(_cursor, "lensModel");
          final int _cursorIndexOfCameraModel = CursorUtil.getColumnIndexOrThrow(_cursor, "cameraModel");
          final int _cursorIndexOfFocalLength = CursorUtil.getColumnIndexOrThrow(_cursor, "focalLength");
          final int _cursorIndexOfIso = CursorUtil.getColumnIndexOrThrow(_cursor, "iso");
          final int _cursorIndexOfAperture = CursorUtil.getColumnIndexOrThrow(_cursor, "aperture");
          final int _cursorIndexOfShutterSpeed = CursorUtil.getColumnIndexOrThrow(_cursor, "shutterSpeed");
          final List<ImageEntity> _result = new ArrayList<ImageEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ImageEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpSleevePath;
            _tmpSleevePath = _cursor.getString(_cursorIndexOfSleevePath);
            final String _tmpOriginalUri;
            _tmpOriginalUri = _cursor.getString(_cursorIndexOfOriginalUri);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final String _tmpFileExtension;
            _tmpFileExtension = _cursor.getString(_cursorIndexOfFileExtension);
            final long _tmpFileSizeBytes;
            _tmpFileSizeBytes = _cursor.getLong(_cursorIndexOfFileSizeBytes);
            final int _tmpWidth;
            _tmpWidth = _cursor.getInt(_cursorIndexOfWidth);
            final int _tmpHeight;
            _tmpHeight = _cursor.getInt(_cursorIndexOfHeight);
            final long _tmpDateAddedEpoch;
            _tmpDateAddedEpoch = _cursor.getLong(_cursorIndexOfDateAddedEpoch);
            final long _tmpDateCapturedEpoch;
            _tmpDateCapturedEpoch = _cursor.getLong(_cursorIndexOfDateCapturedEpoch);
            final int _tmpRating;
            _tmpRating = _cursor.getInt(_cursorIndexOfRating);
            final ImageFlag _tmpFlag;
            final String _tmp;
            if (_cursor.isNull(_cursorIndexOfFlag)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getString(_cursorIndexOfFlag);
            }
            final ImageFlag _tmp_1 = __sleeveTypeConverters.stringToFlag(_tmp);
            if (_tmp_1 == null) {
              throw new IllegalStateException("Expected NON-NULL 'com.alcedo.studio.data.model.ImageFlag', but it was NULL.");
            } else {
              _tmpFlag = _tmp_1;
            }
            final ColorLabel _tmpColorLabel;
            final String _tmp_2;
            if (_cursor.isNull(_cursorIndexOfColorLabel)) {
              _tmp_2 = null;
            } else {
              _tmp_2 = _cursor.getString(_cursorIndexOfColorLabel);
            }
            final ColorLabel _tmp_3 = __sleeveTypeConverters.stringToColorLabel(_tmp_2);
            if (_tmp_3 == null) {
              throw new IllegalStateException("Expected NON-NULL 'com.alcedo.studio.data.model.ColorLabel', but it was NULL.");
            } else {
              _tmpColorLabel = _tmp_3;
            }
            final boolean _tmpIsRaw;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfIsRaw);
            _tmpIsRaw = _tmp_4 != 0;
            final boolean _tmpIsVirtualCopy;
            final int _tmp_5;
            _tmp_5 = _cursor.getInt(_cursorIndexOfIsVirtualCopy);
            _tmpIsVirtualCopy = _tmp_5 != 0;
            final String _tmpParentId;
            if (_cursor.isNull(_cursorIndexOfParentId)) {
              _tmpParentId = null;
            } else {
              _tmpParentId = _cursor.getString(_cursorIndexOfParentId);
            }
            final String _tmpThumbnailPath;
            if (_cursor.isNull(_cursorIndexOfThumbnailPath)) {
              _tmpThumbnailPath = null;
            } else {
              _tmpThumbnailPath = _cursor.getString(_cursorIndexOfThumbnailPath);
            }
            final String _tmpCurrentVersionId;
            if (_cursor.isNull(_cursorIndexOfCurrentVersionId)) {
              _tmpCurrentVersionId = null;
            } else {
              _tmpCurrentVersionId = _cursor.getString(_cursorIndexOfCurrentVersionId);
            }
            final String _tmpAiCaption;
            if (_cursor.isNull(_cursorIndexOfAiCaption)) {
              _tmpAiCaption = null;
            } else {
              _tmpAiCaption = _cursor.getString(_cursorIndexOfAiCaption);
            }
            final String _tmpAiTags;
            if (_cursor.isNull(_cursorIndexOfAiTags)) {
              _tmpAiTags = null;
            } else {
              _tmpAiTags = _cursor.getString(_cursorIndexOfAiTags);
            }
            final Float _tmpAiScore;
            if (_cursor.isNull(_cursorIndexOfAiScore)) {
              _tmpAiScore = null;
            } else {
              _tmpAiScore = _cursor.getFloat(_cursorIndexOfAiScore);
            }
            final boolean _tmpIsHidden;
            final int _tmp_6;
            _tmp_6 = _cursor.getInt(_cursorIndexOfIsHidden);
            _tmpIsHidden = _tmp_6 != 0;
            final String _tmpLensModel;
            if (_cursor.isNull(_cursorIndexOfLensModel)) {
              _tmpLensModel = null;
            } else {
              _tmpLensModel = _cursor.getString(_cursorIndexOfLensModel);
            }
            final String _tmpCameraModel;
            if (_cursor.isNull(_cursorIndexOfCameraModel)) {
              _tmpCameraModel = null;
            } else {
              _tmpCameraModel = _cursor.getString(_cursorIndexOfCameraModel);
            }
            final Float _tmpFocalLength;
            if (_cursor.isNull(_cursorIndexOfFocalLength)) {
              _tmpFocalLength = null;
            } else {
              _tmpFocalLength = _cursor.getFloat(_cursorIndexOfFocalLength);
            }
            final Integer _tmpIso;
            if (_cursor.isNull(_cursorIndexOfIso)) {
              _tmpIso = null;
            } else {
              _tmpIso = _cursor.getInt(_cursorIndexOfIso);
            }
            final Float _tmpAperture;
            if (_cursor.isNull(_cursorIndexOfAperture)) {
              _tmpAperture = null;
            } else {
              _tmpAperture = _cursor.getFloat(_cursorIndexOfAperture);
            }
            final String _tmpShutterSpeed;
            if (_cursor.isNull(_cursorIndexOfShutterSpeed)) {
              _tmpShutterSpeed = null;
            } else {
              _tmpShutterSpeed = _cursor.getString(_cursorIndexOfShutterSpeed);
            }
            _item = new ImageEntity(_tmpId,_tmpSleevePath,_tmpOriginalUri,_tmpDisplayName,_tmpFileExtension,_tmpFileSizeBytes,_tmpWidth,_tmpHeight,_tmpDateAddedEpoch,_tmpDateCapturedEpoch,_tmpRating,_tmpFlag,_tmpColorLabel,_tmpIsRaw,_tmpIsVirtualCopy,_tmpParentId,_tmpThumbnailPath,_tmpCurrentVersionId,_tmpAiCaption,_tmpAiTags,_tmpAiScore,_tmpIsHidden,_tmpLensModel,_tmpCameraModel,_tmpFocalLength,_tmpIso,_tmpAperture,_tmpShutterSpeed);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<ImageEntity>> observeAll() {
    final String _sql = "SELECT * FROM images ORDER BY dateCapturedEpoch DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"images"}, new Callable<List<ImageEntity>>() {
      @Override
      @NonNull
      public List<ImageEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSleevePath = CursorUtil.getColumnIndexOrThrow(_cursor, "sleevePath");
          final int _cursorIndexOfOriginalUri = CursorUtil.getColumnIndexOrThrow(_cursor, "originalUri");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfFileExtension = CursorUtil.getColumnIndexOrThrow(_cursor, "fileExtension");
          final int _cursorIndexOfFileSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "fileSizeBytes");
          final int _cursorIndexOfWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "width");
          final int _cursorIndexOfHeight = CursorUtil.getColumnIndexOrThrow(_cursor, "height");
          final int _cursorIndexOfDateAddedEpoch = CursorUtil.getColumnIndexOrThrow(_cursor, "dateAddedEpoch");
          final int _cursorIndexOfDateCapturedEpoch = CursorUtil.getColumnIndexOrThrow(_cursor, "dateCapturedEpoch");
          final int _cursorIndexOfRating = CursorUtil.getColumnIndexOrThrow(_cursor, "rating");
          final int _cursorIndexOfFlag = CursorUtil.getColumnIndexOrThrow(_cursor, "flag");
          final int _cursorIndexOfColorLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "colorLabel");
          final int _cursorIndexOfIsRaw = CursorUtil.getColumnIndexOrThrow(_cursor, "isRaw");
          final int _cursorIndexOfIsVirtualCopy = CursorUtil.getColumnIndexOrThrow(_cursor, "isVirtualCopy");
          final int _cursorIndexOfParentId = CursorUtil.getColumnIndexOrThrow(_cursor, "parentId");
          final int _cursorIndexOfThumbnailPath = CursorUtil.getColumnIndexOrThrow(_cursor, "thumbnailPath");
          final int _cursorIndexOfCurrentVersionId = CursorUtil.getColumnIndexOrThrow(_cursor, "currentVersionId");
          final int _cursorIndexOfAiCaption = CursorUtil.getColumnIndexOrThrow(_cursor, "aiCaption");
          final int _cursorIndexOfAiTags = CursorUtil.getColumnIndexOrThrow(_cursor, "aiTags");
          final int _cursorIndexOfAiScore = CursorUtil.getColumnIndexOrThrow(_cursor, "aiScore");
          final int _cursorIndexOfIsHidden = CursorUtil.getColumnIndexOrThrow(_cursor, "isHidden");
          final int _cursorIndexOfLensModel = CursorUtil.getColumnIndexOrThrow(_cursor, "lensModel");
          final int _cursorIndexOfCameraModel = CursorUtil.getColumnIndexOrThrow(_cursor, "cameraModel");
          final int _cursorIndexOfFocalLength = CursorUtil.getColumnIndexOrThrow(_cursor, "focalLength");
          final int _cursorIndexOfIso = CursorUtil.getColumnIndexOrThrow(_cursor, "iso");
          final int _cursorIndexOfAperture = CursorUtil.getColumnIndexOrThrow(_cursor, "aperture");
          final int _cursorIndexOfShutterSpeed = CursorUtil.getColumnIndexOrThrow(_cursor, "shutterSpeed");
          final List<ImageEntity> _result = new ArrayList<ImageEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ImageEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpSleevePath;
            _tmpSleevePath = _cursor.getString(_cursorIndexOfSleevePath);
            final String _tmpOriginalUri;
            _tmpOriginalUri = _cursor.getString(_cursorIndexOfOriginalUri);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final String _tmpFileExtension;
            _tmpFileExtension = _cursor.getString(_cursorIndexOfFileExtension);
            final long _tmpFileSizeBytes;
            _tmpFileSizeBytes = _cursor.getLong(_cursorIndexOfFileSizeBytes);
            final int _tmpWidth;
            _tmpWidth = _cursor.getInt(_cursorIndexOfWidth);
            final int _tmpHeight;
            _tmpHeight = _cursor.getInt(_cursorIndexOfHeight);
            final long _tmpDateAddedEpoch;
            _tmpDateAddedEpoch = _cursor.getLong(_cursorIndexOfDateAddedEpoch);
            final long _tmpDateCapturedEpoch;
            _tmpDateCapturedEpoch = _cursor.getLong(_cursorIndexOfDateCapturedEpoch);
            final int _tmpRating;
            _tmpRating = _cursor.getInt(_cursorIndexOfRating);
            final ImageFlag _tmpFlag;
            final String _tmp;
            if (_cursor.isNull(_cursorIndexOfFlag)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getString(_cursorIndexOfFlag);
            }
            final ImageFlag _tmp_1 = __sleeveTypeConverters.stringToFlag(_tmp);
            if (_tmp_1 == null) {
              throw new IllegalStateException("Expected NON-NULL 'com.alcedo.studio.data.model.ImageFlag', but it was NULL.");
            } else {
              _tmpFlag = _tmp_1;
            }
            final ColorLabel _tmpColorLabel;
            final String _tmp_2;
            if (_cursor.isNull(_cursorIndexOfColorLabel)) {
              _tmp_2 = null;
            } else {
              _tmp_2 = _cursor.getString(_cursorIndexOfColorLabel);
            }
            final ColorLabel _tmp_3 = __sleeveTypeConverters.stringToColorLabel(_tmp_2);
            if (_tmp_3 == null) {
              throw new IllegalStateException("Expected NON-NULL 'com.alcedo.studio.data.model.ColorLabel', but it was NULL.");
            } else {
              _tmpColorLabel = _tmp_3;
            }
            final boolean _tmpIsRaw;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfIsRaw);
            _tmpIsRaw = _tmp_4 != 0;
            final boolean _tmpIsVirtualCopy;
            final int _tmp_5;
            _tmp_5 = _cursor.getInt(_cursorIndexOfIsVirtualCopy);
            _tmpIsVirtualCopy = _tmp_5 != 0;
            final String _tmpParentId;
            if (_cursor.isNull(_cursorIndexOfParentId)) {
              _tmpParentId = null;
            } else {
              _tmpParentId = _cursor.getString(_cursorIndexOfParentId);
            }
            final String _tmpThumbnailPath;
            if (_cursor.isNull(_cursorIndexOfThumbnailPath)) {
              _tmpThumbnailPath = null;
            } else {
              _tmpThumbnailPath = _cursor.getString(_cursorIndexOfThumbnailPath);
            }
            final String _tmpCurrentVersionId;
            if (_cursor.isNull(_cursorIndexOfCurrentVersionId)) {
              _tmpCurrentVersionId = null;
            } else {
              _tmpCurrentVersionId = _cursor.getString(_cursorIndexOfCurrentVersionId);
            }
            final String _tmpAiCaption;
            if (_cursor.isNull(_cursorIndexOfAiCaption)) {
              _tmpAiCaption = null;
            } else {
              _tmpAiCaption = _cursor.getString(_cursorIndexOfAiCaption);
            }
            final String _tmpAiTags;
            if (_cursor.isNull(_cursorIndexOfAiTags)) {
              _tmpAiTags = null;
            } else {
              _tmpAiTags = _cursor.getString(_cursorIndexOfAiTags);
            }
            final Float _tmpAiScore;
            if (_cursor.isNull(_cursorIndexOfAiScore)) {
              _tmpAiScore = null;
            } else {
              _tmpAiScore = _cursor.getFloat(_cursorIndexOfAiScore);
            }
            final boolean _tmpIsHidden;
            final int _tmp_6;
            _tmp_6 = _cursor.getInt(_cursorIndexOfIsHidden);
            _tmpIsHidden = _tmp_6 != 0;
            final String _tmpLensModel;
            if (_cursor.isNull(_cursorIndexOfLensModel)) {
              _tmpLensModel = null;
            } else {
              _tmpLensModel = _cursor.getString(_cursorIndexOfLensModel);
            }
            final String _tmpCameraModel;
            if (_cursor.isNull(_cursorIndexOfCameraModel)) {
              _tmpCameraModel = null;
            } else {
              _tmpCameraModel = _cursor.getString(_cursorIndexOfCameraModel);
            }
            final Float _tmpFocalLength;
            if (_cursor.isNull(_cursorIndexOfFocalLength)) {
              _tmpFocalLength = null;
            } else {
              _tmpFocalLength = _cursor.getFloat(_cursorIndexOfFocalLength);
            }
            final Integer _tmpIso;
            if (_cursor.isNull(_cursorIndexOfIso)) {
              _tmpIso = null;
            } else {
              _tmpIso = _cursor.getInt(_cursorIndexOfIso);
            }
            final Float _tmpAperture;
            if (_cursor.isNull(_cursorIndexOfAperture)) {
              _tmpAperture = null;
            } else {
              _tmpAperture = _cursor.getFloat(_cursorIndexOfAperture);
            }
            final String _tmpShutterSpeed;
            if (_cursor.isNull(_cursorIndexOfShutterSpeed)) {
              _tmpShutterSpeed = null;
            } else {
              _tmpShutterSpeed = _cursor.getString(_cursorIndexOfShutterSpeed);
            }
            _item = new ImageEntity(_tmpId,_tmpSleevePath,_tmpOriginalUri,_tmpDisplayName,_tmpFileExtension,_tmpFileSizeBytes,_tmpWidth,_tmpHeight,_tmpDateAddedEpoch,_tmpDateCapturedEpoch,_tmpRating,_tmpFlag,_tmpColorLabel,_tmpIsRaw,_tmpIsVirtualCopy,_tmpParentId,_tmpThumbnailPath,_tmpCurrentVersionId,_tmpAiCaption,_tmpAiTags,_tmpAiScore,_tmpIsHidden,_tmpLensModel,_tmpCameraModel,_tmpFocalLength,_tmpIso,_tmpAperture,_tmpShutterSpeed);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object queryFiltered(final String folderPath, final int includeHidden,
      final Integer ratingMin, final Integer ratingMax, final String searchText,
      final String sortField, final int limit, final int offset,
      final Continuation<? super List<ImageEntity>> $completion) {
    final String _sql = "\n"
            + "        SELECT * FROM images\n"
            + "        WHERE (? IS NULL OR sleevePath = ?)\n"
            + "          AND (? = 1 OR isHidden = 0)\n"
            + "          AND (? IS NULL OR rating >= ?)\n"
            + "          AND (? IS NULL OR rating <= ?)\n"
            + "          AND (? IS NULL OR displayName LIKE '%' || ? || '%' OR aiCaption LIKE '%' || ? || '%')\n"
            + "        ORDER BY\n"
            + "            CASE WHEN ? = 'DATE_CAPTURED' THEN dateCapturedEpoch END DESC,\n"
            + "            CASE WHEN ? = 'DATE_ADDED' THEN dateAddedEpoch END DESC,\n"
            + "            CASE WHEN ? = 'NAME' THEN displayName END ASC,\n"
            + "            CASE WHEN ? = 'RATING' THEN rating END DESC,\n"
            + "            CASE WHEN ? = 'FILE_SIZE' THEN fileSizeBytes END DESC,\n"
            + "            CASE WHEN ? = 'AI_SCORE' THEN aiScore END DESC\n"
            + "        LIMIT ? OFFSET ?\n"
            + "        ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 18);
    int _argIndex = 1;
    if (folderPath == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, folderPath);
    }
    _argIndex = 2;
    if (folderPath == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, folderPath);
    }
    _argIndex = 3;
    _statement.bindLong(_argIndex, includeHidden);
    _argIndex = 4;
    if (ratingMin == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindLong(_argIndex, ratingMin);
    }
    _argIndex = 5;
    if (ratingMin == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindLong(_argIndex, ratingMin);
    }
    _argIndex = 6;
    if (ratingMax == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindLong(_argIndex, ratingMax);
    }
    _argIndex = 7;
    if (ratingMax == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindLong(_argIndex, ratingMax);
    }
    _argIndex = 8;
    if (searchText == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, searchText);
    }
    _argIndex = 9;
    if (searchText == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, searchText);
    }
    _argIndex = 10;
    if (searchText == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, searchText);
    }
    _argIndex = 11;
    _statement.bindString(_argIndex, sortField);
    _argIndex = 12;
    _statement.bindString(_argIndex, sortField);
    _argIndex = 13;
    _statement.bindString(_argIndex, sortField);
    _argIndex = 14;
    _statement.bindString(_argIndex, sortField);
    _argIndex = 15;
    _statement.bindString(_argIndex, sortField);
    _argIndex = 16;
    _statement.bindString(_argIndex, sortField);
    _argIndex = 17;
    _statement.bindLong(_argIndex, limit);
    _argIndex = 18;
    _statement.bindLong(_argIndex, offset);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ImageEntity>>() {
      @Override
      @NonNull
      public List<ImageEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSleevePath = CursorUtil.getColumnIndexOrThrow(_cursor, "sleevePath");
          final int _cursorIndexOfOriginalUri = CursorUtil.getColumnIndexOrThrow(_cursor, "originalUri");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfFileExtension = CursorUtil.getColumnIndexOrThrow(_cursor, "fileExtension");
          final int _cursorIndexOfFileSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "fileSizeBytes");
          final int _cursorIndexOfWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "width");
          final int _cursorIndexOfHeight = CursorUtil.getColumnIndexOrThrow(_cursor, "height");
          final int _cursorIndexOfDateAddedEpoch = CursorUtil.getColumnIndexOrThrow(_cursor, "dateAddedEpoch");
          final int _cursorIndexOfDateCapturedEpoch = CursorUtil.getColumnIndexOrThrow(_cursor, "dateCapturedEpoch");
          final int _cursorIndexOfRating = CursorUtil.getColumnIndexOrThrow(_cursor, "rating");
          final int _cursorIndexOfFlag = CursorUtil.getColumnIndexOrThrow(_cursor, "flag");
          final int _cursorIndexOfColorLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "colorLabel");
          final int _cursorIndexOfIsRaw = CursorUtil.getColumnIndexOrThrow(_cursor, "isRaw");
          final int _cursorIndexOfIsVirtualCopy = CursorUtil.getColumnIndexOrThrow(_cursor, "isVirtualCopy");
          final int _cursorIndexOfParentId = CursorUtil.getColumnIndexOrThrow(_cursor, "parentId");
          final int _cursorIndexOfThumbnailPath = CursorUtil.getColumnIndexOrThrow(_cursor, "thumbnailPath");
          final int _cursorIndexOfCurrentVersionId = CursorUtil.getColumnIndexOrThrow(_cursor, "currentVersionId");
          final int _cursorIndexOfAiCaption = CursorUtil.getColumnIndexOrThrow(_cursor, "aiCaption");
          final int _cursorIndexOfAiTags = CursorUtil.getColumnIndexOrThrow(_cursor, "aiTags");
          final int _cursorIndexOfAiScore = CursorUtil.getColumnIndexOrThrow(_cursor, "aiScore");
          final int _cursorIndexOfIsHidden = CursorUtil.getColumnIndexOrThrow(_cursor, "isHidden");
          final int _cursorIndexOfLensModel = CursorUtil.getColumnIndexOrThrow(_cursor, "lensModel");
          final int _cursorIndexOfCameraModel = CursorUtil.getColumnIndexOrThrow(_cursor, "cameraModel");
          final int _cursorIndexOfFocalLength = CursorUtil.getColumnIndexOrThrow(_cursor, "focalLength");
          final int _cursorIndexOfIso = CursorUtil.getColumnIndexOrThrow(_cursor, "iso");
          final int _cursorIndexOfAperture = CursorUtil.getColumnIndexOrThrow(_cursor, "aperture");
          final int _cursorIndexOfShutterSpeed = CursorUtil.getColumnIndexOrThrow(_cursor, "shutterSpeed");
          final List<ImageEntity> _result = new ArrayList<ImageEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ImageEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpSleevePath;
            _tmpSleevePath = _cursor.getString(_cursorIndexOfSleevePath);
            final String _tmpOriginalUri;
            _tmpOriginalUri = _cursor.getString(_cursorIndexOfOriginalUri);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final String _tmpFileExtension;
            _tmpFileExtension = _cursor.getString(_cursorIndexOfFileExtension);
            final long _tmpFileSizeBytes;
            _tmpFileSizeBytes = _cursor.getLong(_cursorIndexOfFileSizeBytes);
            final int _tmpWidth;
            _tmpWidth = _cursor.getInt(_cursorIndexOfWidth);
            final int _tmpHeight;
            _tmpHeight = _cursor.getInt(_cursorIndexOfHeight);
            final long _tmpDateAddedEpoch;
            _tmpDateAddedEpoch = _cursor.getLong(_cursorIndexOfDateAddedEpoch);
            final long _tmpDateCapturedEpoch;
            _tmpDateCapturedEpoch = _cursor.getLong(_cursorIndexOfDateCapturedEpoch);
            final int _tmpRating;
            _tmpRating = _cursor.getInt(_cursorIndexOfRating);
            final ImageFlag _tmpFlag;
            final String _tmp;
            if (_cursor.isNull(_cursorIndexOfFlag)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getString(_cursorIndexOfFlag);
            }
            final ImageFlag _tmp_1 = __sleeveTypeConverters.stringToFlag(_tmp);
            if (_tmp_1 == null) {
              throw new IllegalStateException("Expected NON-NULL 'com.alcedo.studio.data.model.ImageFlag', but it was NULL.");
            } else {
              _tmpFlag = _tmp_1;
            }
            final ColorLabel _tmpColorLabel;
            final String _tmp_2;
            if (_cursor.isNull(_cursorIndexOfColorLabel)) {
              _tmp_2 = null;
            } else {
              _tmp_2 = _cursor.getString(_cursorIndexOfColorLabel);
            }
            final ColorLabel _tmp_3 = __sleeveTypeConverters.stringToColorLabel(_tmp_2);
            if (_tmp_3 == null) {
              throw new IllegalStateException("Expected NON-NULL 'com.alcedo.studio.data.model.ColorLabel', but it was NULL.");
            } else {
              _tmpColorLabel = _tmp_3;
            }
            final boolean _tmpIsRaw;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfIsRaw);
            _tmpIsRaw = _tmp_4 != 0;
            final boolean _tmpIsVirtualCopy;
            final int _tmp_5;
            _tmp_5 = _cursor.getInt(_cursorIndexOfIsVirtualCopy);
            _tmpIsVirtualCopy = _tmp_5 != 0;
            final String _tmpParentId;
            if (_cursor.isNull(_cursorIndexOfParentId)) {
              _tmpParentId = null;
            } else {
              _tmpParentId = _cursor.getString(_cursorIndexOfParentId);
            }
            final String _tmpThumbnailPath;
            if (_cursor.isNull(_cursorIndexOfThumbnailPath)) {
              _tmpThumbnailPath = null;
            } else {
              _tmpThumbnailPath = _cursor.getString(_cursorIndexOfThumbnailPath);
            }
            final String _tmpCurrentVersionId;
            if (_cursor.isNull(_cursorIndexOfCurrentVersionId)) {
              _tmpCurrentVersionId = null;
            } else {
              _tmpCurrentVersionId = _cursor.getString(_cursorIndexOfCurrentVersionId);
            }
            final String _tmpAiCaption;
            if (_cursor.isNull(_cursorIndexOfAiCaption)) {
              _tmpAiCaption = null;
            } else {
              _tmpAiCaption = _cursor.getString(_cursorIndexOfAiCaption);
            }
            final String _tmpAiTags;
            if (_cursor.isNull(_cursorIndexOfAiTags)) {
              _tmpAiTags = null;
            } else {
              _tmpAiTags = _cursor.getString(_cursorIndexOfAiTags);
            }
            final Float _tmpAiScore;
            if (_cursor.isNull(_cursorIndexOfAiScore)) {
              _tmpAiScore = null;
            } else {
              _tmpAiScore = _cursor.getFloat(_cursorIndexOfAiScore);
            }
            final boolean _tmpIsHidden;
            final int _tmp_6;
            _tmp_6 = _cursor.getInt(_cursorIndexOfIsHidden);
            _tmpIsHidden = _tmp_6 != 0;
            final String _tmpLensModel;
            if (_cursor.isNull(_cursorIndexOfLensModel)) {
              _tmpLensModel = null;
            } else {
              _tmpLensModel = _cursor.getString(_cursorIndexOfLensModel);
            }
            final String _tmpCameraModel;
            if (_cursor.isNull(_cursorIndexOfCameraModel)) {
              _tmpCameraModel = null;
            } else {
              _tmpCameraModel = _cursor.getString(_cursorIndexOfCameraModel);
            }
            final Float _tmpFocalLength;
            if (_cursor.isNull(_cursorIndexOfFocalLength)) {
              _tmpFocalLength = null;
            } else {
              _tmpFocalLength = _cursor.getFloat(_cursorIndexOfFocalLength);
            }
            final Integer _tmpIso;
            if (_cursor.isNull(_cursorIndexOfIso)) {
              _tmpIso = null;
            } else {
              _tmpIso = _cursor.getInt(_cursorIndexOfIso);
            }
            final Float _tmpAperture;
            if (_cursor.isNull(_cursorIndexOfAperture)) {
              _tmpAperture = null;
            } else {
              _tmpAperture = _cursor.getFloat(_cursorIndexOfAperture);
            }
            final String _tmpShutterSpeed;
            if (_cursor.isNull(_cursorIndexOfShutterSpeed)) {
              _tmpShutterSpeed = null;
            } else {
              _tmpShutterSpeed = _cursor.getString(_cursorIndexOfShutterSpeed);
            }
            _item = new ImageEntity(_tmpId,_tmpSleevePath,_tmpOriginalUri,_tmpDisplayName,_tmpFileExtension,_tmpFileSizeBytes,_tmpWidth,_tmpHeight,_tmpDateAddedEpoch,_tmpDateCapturedEpoch,_tmpRating,_tmpFlag,_tmpColorLabel,_tmpIsRaw,_tmpIsVirtualCopy,_tmpParentId,_tmpThumbnailPath,_tmpCurrentVersionId,_tmpAiCaption,_tmpAiTags,_tmpAiScore,_tmpIsHidden,_tmpLensModel,_tmpCameraModel,_tmpFocalLength,_tmpIso,_tmpAperture,_tmpShutterSpeed);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object count(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM images";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object rawCount(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM images WHERE isRaw = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object pickCount(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM images WHERE flag = 'PICK'";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object rejectCount(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM images WHERE flag = 'REJECT'";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getVirtualCopies(final String parentId,
      final Continuation<? super List<ImageEntity>> $completion) {
    final String _sql = "SELECT * FROM images WHERE parentId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, parentId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ImageEntity>>() {
      @Override
      @NonNull
      public List<ImageEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSleevePath = CursorUtil.getColumnIndexOrThrow(_cursor, "sleevePath");
          final int _cursorIndexOfOriginalUri = CursorUtil.getColumnIndexOrThrow(_cursor, "originalUri");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfFileExtension = CursorUtil.getColumnIndexOrThrow(_cursor, "fileExtension");
          final int _cursorIndexOfFileSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "fileSizeBytes");
          final int _cursorIndexOfWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "width");
          final int _cursorIndexOfHeight = CursorUtil.getColumnIndexOrThrow(_cursor, "height");
          final int _cursorIndexOfDateAddedEpoch = CursorUtil.getColumnIndexOrThrow(_cursor, "dateAddedEpoch");
          final int _cursorIndexOfDateCapturedEpoch = CursorUtil.getColumnIndexOrThrow(_cursor, "dateCapturedEpoch");
          final int _cursorIndexOfRating = CursorUtil.getColumnIndexOrThrow(_cursor, "rating");
          final int _cursorIndexOfFlag = CursorUtil.getColumnIndexOrThrow(_cursor, "flag");
          final int _cursorIndexOfColorLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "colorLabel");
          final int _cursorIndexOfIsRaw = CursorUtil.getColumnIndexOrThrow(_cursor, "isRaw");
          final int _cursorIndexOfIsVirtualCopy = CursorUtil.getColumnIndexOrThrow(_cursor, "isVirtualCopy");
          final int _cursorIndexOfParentId = CursorUtil.getColumnIndexOrThrow(_cursor, "parentId");
          final int _cursorIndexOfThumbnailPath = CursorUtil.getColumnIndexOrThrow(_cursor, "thumbnailPath");
          final int _cursorIndexOfCurrentVersionId = CursorUtil.getColumnIndexOrThrow(_cursor, "currentVersionId");
          final int _cursorIndexOfAiCaption = CursorUtil.getColumnIndexOrThrow(_cursor, "aiCaption");
          final int _cursorIndexOfAiTags = CursorUtil.getColumnIndexOrThrow(_cursor, "aiTags");
          final int _cursorIndexOfAiScore = CursorUtil.getColumnIndexOrThrow(_cursor, "aiScore");
          final int _cursorIndexOfIsHidden = CursorUtil.getColumnIndexOrThrow(_cursor, "isHidden");
          final int _cursorIndexOfLensModel = CursorUtil.getColumnIndexOrThrow(_cursor, "lensModel");
          final int _cursorIndexOfCameraModel = CursorUtil.getColumnIndexOrThrow(_cursor, "cameraModel");
          final int _cursorIndexOfFocalLength = CursorUtil.getColumnIndexOrThrow(_cursor, "focalLength");
          final int _cursorIndexOfIso = CursorUtil.getColumnIndexOrThrow(_cursor, "iso");
          final int _cursorIndexOfAperture = CursorUtil.getColumnIndexOrThrow(_cursor, "aperture");
          final int _cursorIndexOfShutterSpeed = CursorUtil.getColumnIndexOrThrow(_cursor, "shutterSpeed");
          final List<ImageEntity> _result = new ArrayList<ImageEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ImageEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpSleevePath;
            _tmpSleevePath = _cursor.getString(_cursorIndexOfSleevePath);
            final String _tmpOriginalUri;
            _tmpOriginalUri = _cursor.getString(_cursorIndexOfOriginalUri);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final String _tmpFileExtension;
            _tmpFileExtension = _cursor.getString(_cursorIndexOfFileExtension);
            final long _tmpFileSizeBytes;
            _tmpFileSizeBytes = _cursor.getLong(_cursorIndexOfFileSizeBytes);
            final int _tmpWidth;
            _tmpWidth = _cursor.getInt(_cursorIndexOfWidth);
            final int _tmpHeight;
            _tmpHeight = _cursor.getInt(_cursorIndexOfHeight);
            final long _tmpDateAddedEpoch;
            _tmpDateAddedEpoch = _cursor.getLong(_cursorIndexOfDateAddedEpoch);
            final long _tmpDateCapturedEpoch;
            _tmpDateCapturedEpoch = _cursor.getLong(_cursorIndexOfDateCapturedEpoch);
            final int _tmpRating;
            _tmpRating = _cursor.getInt(_cursorIndexOfRating);
            final ImageFlag _tmpFlag;
            final String _tmp;
            if (_cursor.isNull(_cursorIndexOfFlag)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getString(_cursorIndexOfFlag);
            }
            final ImageFlag _tmp_1 = __sleeveTypeConverters.stringToFlag(_tmp);
            if (_tmp_1 == null) {
              throw new IllegalStateException("Expected NON-NULL 'com.alcedo.studio.data.model.ImageFlag', but it was NULL.");
            } else {
              _tmpFlag = _tmp_1;
            }
            final ColorLabel _tmpColorLabel;
            final String _tmp_2;
            if (_cursor.isNull(_cursorIndexOfColorLabel)) {
              _tmp_2 = null;
            } else {
              _tmp_2 = _cursor.getString(_cursorIndexOfColorLabel);
            }
            final ColorLabel _tmp_3 = __sleeveTypeConverters.stringToColorLabel(_tmp_2);
            if (_tmp_3 == null) {
              throw new IllegalStateException("Expected NON-NULL 'com.alcedo.studio.data.model.ColorLabel', but it was NULL.");
            } else {
              _tmpColorLabel = _tmp_3;
            }
            final boolean _tmpIsRaw;
            final int _tmp_4;
            _tmp_4 = _cursor.getInt(_cursorIndexOfIsRaw);
            _tmpIsRaw = _tmp_4 != 0;
            final boolean _tmpIsVirtualCopy;
            final int _tmp_5;
            _tmp_5 = _cursor.getInt(_cursorIndexOfIsVirtualCopy);
            _tmpIsVirtualCopy = _tmp_5 != 0;
            final String _tmpParentId;
            if (_cursor.isNull(_cursorIndexOfParentId)) {
              _tmpParentId = null;
            } else {
              _tmpParentId = _cursor.getString(_cursorIndexOfParentId);
            }
            final String _tmpThumbnailPath;
            if (_cursor.isNull(_cursorIndexOfThumbnailPath)) {
              _tmpThumbnailPath = null;
            } else {
              _tmpThumbnailPath = _cursor.getString(_cursorIndexOfThumbnailPath);
            }
            final String _tmpCurrentVersionId;
            if (_cursor.isNull(_cursorIndexOfCurrentVersionId)) {
              _tmpCurrentVersionId = null;
            } else {
              _tmpCurrentVersionId = _cursor.getString(_cursorIndexOfCurrentVersionId);
            }
            final String _tmpAiCaption;
            if (_cursor.isNull(_cursorIndexOfAiCaption)) {
              _tmpAiCaption = null;
            } else {
              _tmpAiCaption = _cursor.getString(_cursorIndexOfAiCaption);
            }
            final String _tmpAiTags;
            if (_cursor.isNull(_cursorIndexOfAiTags)) {
              _tmpAiTags = null;
            } else {
              _tmpAiTags = _cursor.getString(_cursorIndexOfAiTags);
            }
            final Float _tmpAiScore;
            if (_cursor.isNull(_cursorIndexOfAiScore)) {
              _tmpAiScore = null;
            } else {
              _tmpAiScore = _cursor.getFloat(_cursorIndexOfAiScore);
            }
            final boolean _tmpIsHidden;
            final int _tmp_6;
            _tmp_6 = _cursor.getInt(_cursorIndexOfIsHidden);
            _tmpIsHidden = _tmp_6 != 0;
            final String _tmpLensModel;
            if (_cursor.isNull(_cursorIndexOfLensModel)) {
              _tmpLensModel = null;
            } else {
              _tmpLensModel = _cursor.getString(_cursorIndexOfLensModel);
            }
            final String _tmpCameraModel;
            if (_cursor.isNull(_cursorIndexOfCameraModel)) {
              _tmpCameraModel = null;
            } else {
              _tmpCameraModel = _cursor.getString(_cursorIndexOfCameraModel);
            }
            final Float _tmpFocalLength;
            if (_cursor.isNull(_cursorIndexOfFocalLength)) {
              _tmpFocalLength = null;
            } else {
              _tmpFocalLength = _cursor.getFloat(_cursorIndexOfFocalLength);
            }
            final Integer _tmpIso;
            if (_cursor.isNull(_cursorIndexOfIso)) {
              _tmpIso = null;
            } else {
              _tmpIso = _cursor.getInt(_cursorIndexOfIso);
            }
            final Float _tmpAperture;
            if (_cursor.isNull(_cursorIndexOfAperture)) {
              _tmpAperture = null;
            } else {
              _tmpAperture = _cursor.getFloat(_cursorIndexOfAperture);
            }
            final String _tmpShutterSpeed;
            if (_cursor.isNull(_cursorIndexOfShutterSpeed)) {
              _tmpShutterSpeed = null;
            } else {
              _tmpShutterSpeed = _cursor.getString(_cursorIndexOfShutterSpeed);
            }
            _item = new ImageEntity(_tmpId,_tmpSleevePath,_tmpOriginalUri,_tmpDisplayName,_tmpFileExtension,_tmpFileSizeBytes,_tmpWidth,_tmpHeight,_tmpDateAddedEpoch,_tmpDateCapturedEpoch,_tmpRating,_tmpFlag,_tmpColorLabel,_tmpIsRaw,_tmpIsVirtualCopy,_tmpParentId,_tmpThumbnailPath,_tmpCurrentVersionId,_tmpAiCaption,_tmpAiTags,_tmpAiScore,_tmpIsHidden,_tmpLensModel,_tmpCameraModel,_tmpFocalLength,_tmpIso,_tmpAperture,_tmpShutterSpeed);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object distinctCameras(final Continuation<? super List<String>> $completion) {
    final String _sql = "SELECT DISTINCT cameraModel FROM images WHERE cameraModel IS NOT NULL";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<String>>() {
      @Override
      @NonNull
      public List<String> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final List<String> _result = new ArrayList<String>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final String _item;
            _item = _cursor.getString(0);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object distinctLenses(final Continuation<? super List<String>> $completion) {
    final String _sql = "SELECT DISTINCT lensModel FROM images WHERE lensModel IS NOT NULL";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<String>>() {
      @Override
      @NonNull
      public List<String> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final List<String> _result = new ArrayList<String>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final String _item;
            _item = _cursor.getString(0);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
