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
import com.alcedo.studio.data.local.EditTransactionEntity;
import com.alcedo.studio.data.local.EditVersionEntity;
import java.lang.Class;
import java.lang.Exception;
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
public final class EditHistoryDao_Impl implements EditHistoryDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<EditVersionEntity> __insertionAdapterOfEditVersionEntity;

  private final EntityInsertionAdapter<EditTransactionEntity> __insertionAdapterOfEditTransactionEntity;

  private final EntityDeletionOrUpdateAdapter<EditVersionEntity> __deletionAdapterOfEditVersionEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteVersionById;

  private final SharedSQLiteStatement __preparedStmtOfDeleteTransactionsFor;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAllForImage;

  private final SharedSQLiteStatement __preparedStmtOfDeactivateAllVersions;

  private final SharedSQLiteStatement __preparedStmtOfActivateVersion;

  private final SharedSQLiteStatement __preparedStmtOfUpdateCumulativeParams;

  public EditHistoryDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfEditVersionEntity = new EntityInsertionAdapter<EditVersionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `edit_versions` (`id`,`imageId`,`parentId`,`name`,`createdAt`,`cumulativeParamsJson`,`isVirtualCopy`,`isActive`,`note`) VALUES (?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final EditVersionEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getImageId());
        if (entity.getParentId() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getParentId());
        }
        statement.bindString(4, entity.getName());
        statement.bindLong(5, entity.getCreatedAt());
        statement.bindString(6, entity.getCumulativeParamsJson());
        final int _tmp = entity.isVirtualCopy() ? 1 : 0;
        statement.bindLong(7, _tmp);
        final int _tmp_1 = entity.isActive() ? 1 : 0;
        statement.bindLong(8, _tmp_1);
        if (entity.getNote() == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.getNote());
        }
      }
    };
    this.__insertionAdapterOfEditTransactionEntity = new EntityInsertionAdapter<EditTransactionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `edit_transactions` (`id`,`versionId`,`timestamp`,`label`,`paramDeltaJson`,`maskIds`,`source`) VALUES (?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final EditTransactionEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getVersionId());
        statement.bindLong(3, entity.getTimestamp());
        statement.bindString(4, entity.getLabel());
        statement.bindString(5, entity.getParamDeltaJson());
        if (entity.getMaskIds() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getMaskIds());
        }
        statement.bindString(7, entity.getSource());
      }
    };
    this.__deletionAdapterOfEditVersionEntity = new EntityDeletionOrUpdateAdapter<EditVersionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `edit_versions` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final EditVersionEntity entity) {
        statement.bindString(1, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteVersionById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM edit_versions WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteTransactionsFor = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM edit_transactions WHERE versionId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAllForImage = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM edit_versions WHERE imageId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeactivateAllVersions = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE edit_versions SET isActive = 0 WHERE imageId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfActivateVersion = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE edit_versions SET isActive = 1 WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateCumulativeParams = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE edit_versions SET cumulativeParamsJson = ? WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object upsertVersion(final EditVersionEntity version,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfEditVersionEntity.insert(version);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertVersions(final List<EditVersionEntity> versions,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfEditVersionEntity.insert(versions);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertTransaction(final EditTransactionEntity transaction,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfEditTransactionEntity.insert(transaction);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertTransactions(final List<EditTransactionEntity> transactions,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfEditTransactionEntity.insert(transactions);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteVersion(final EditVersionEntity version,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfEditVersionEntity.handle(version);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object setActiveVersion(final String imageId, final String versionId,
      final Continuation<? super Unit> $completion) {
    return RoomDatabaseKt.withTransaction(__db, (__cont) -> EditHistoryDao.super.setActiveVersion(imageId, versionId, __cont), $completion);
  }

  @Override
  public Object deleteVersionCascade(final String versionId,
      final Continuation<? super Unit> $completion) {
    return RoomDatabaseKt.withTransaction(__db, (__cont) -> EditHistoryDao.super.deleteVersionCascade(versionId, __cont), $completion);
  }

  @Override
  public Object deleteVersionById(final String versionId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteVersionById.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, versionId);
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
          __preparedStmtOfDeleteVersionById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteTransactionsFor(final String versionId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteTransactionsFor.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, versionId);
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
          __preparedStmtOfDeleteTransactionsFor.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAllForImage(final String imageId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAllForImage.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, imageId);
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
          __preparedStmtOfDeleteAllForImage.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deactivateAllVersions(final String imageId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeactivateAllVersions.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, imageId);
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
          __preparedStmtOfDeactivateAllVersions.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object activateVersion(final String versionId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfActivateVersion.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, versionId);
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
          __preparedStmtOfActivateVersion.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateCumulativeParams(final String versionId, final String json,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateCumulativeParams.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, json);
        _argIndex = 2;
        _stmt.bindString(_argIndex, versionId);
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
          __preparedStmtOfUpdateCumulativeParams.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getVersion(final String versionId,
      final Continuation<? super EditVersionEntity> $completion) {
    final String _sql = "SELECT * FROM edit_versions WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, versionId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<EditVersionEntity>() {
      @Override
      @Nullable
      public EditVersionEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfImageId = CursorUtil.getColumnIndexOrThrow(_cursor, "imageId");
          final int _cursorIndexOfParentId = CursorUtil.getColumnIndexOrThrow(_cursor, "parentId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfCumulativeParamsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "cumulativeParamsJson");
          final int _cursorIndexOfIsVirtualCopy = CursorUtil.getColumnIndexOrThrow(_cursor, "isVirtualCopy");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfNote = CursorUtil.getColumnIndexOrThrow(_cursor, "note");
          final EditVersionEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpImageId;
            _tmpImageId = _cursor.getString(_cursorIndexOfImageId);
            final String _tmpParentId;
            if (_cursor.isNull(_cursorIndexOfParentId)) {
              _tmpParentId = null;
            } else {
              _tmpParentId = _cursor.getString(_cursorIndexOfParentId);
            }
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final String _tmpCumulativeParamsJson;
            _tmpCumulativeParamsJson = _cursor.getString(_cursorIndexOfCumulativeParamsJson);
            final boolean _tmpIsVirtualCopy;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsVirtualCopy);
            _tmpIsVirtualCopy = _tmp != 0;
            final boolean _tmpIsActive;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp_1 != 0;
            final String _tmpNote;
            if (_cursor.isNull(_cursorIndexOfNote)) {
              _tmpNote = null;
            } else {
              _tmpNote = _cursor.getString(_cursorIndexOfNote);
            }
            _result = new EditVersionEntity(_tmpId,_tmpImageId,_tmpParentId,_tmpName,_tmpCreatedAt,_tmpCumulativeParamsJson,_tmpIsVirtualCopy,_tmpIsActive,_tmpNote);
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
  public Object getVersionsForImage(final String imageId,
      final Continuation<? super List<EditVersionEntity>> $completion) {
    final String _sql = "SELECT * FROM edit_versions WHERE imageId = ? ORDER BY createdAt ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, imageId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<EditVersionEntity>>() {
      @Override
      @NonNull
      public List<EditVersionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfImageId = CursorUtil.getColumnIndexOrThrow(_cursor, "imageId");
          final int _cursorIndexOfParentId = CursorUtil.getColumnIndexOrThrow(_cursor, "parentId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfCumulativeParamsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "cumulativeParamsJson");
          final int _cursorIndexOfIsVirtualCopy = CursorUtil.getColumnIndexOrThrow(_cursor, "isVirtualCopy");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfNote = CursorUtil.getColumnIndexOrThrow(_cursor, "note");
          final List<EditVersionEntity> _result = new ArrayList<EditVersionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final EditVersionEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpImageId;
            _tmpImageId = _cursor.getString(_cursorIndexOfImageId);
            final String _tmpParentId;
            if (_cursor.isNull(_cursorIndexOfParentId)) {
              _tmpParentId = null;
            } else {
              _tmpParentId = _cursor.getString(_cursorIndexOfParentId);
            }
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final String _tmpCumulativeParamsJson;
            _tmpCumulativeParamsJson = _cursor.getString(_cursorIndexOfCumulativeParamsJson);
            final boolean _tmpIsVirtualCopy;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsVirtualCopy);
            _tmpIsVirtualCopy = _tmp != 0;
            final boolean _tmpIsActive;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp_1 != 0;
            final String _tmpNote;
            if (_cursor.isNull(_cursorIndexOfNote)) {
              _tmpNote = null;
            } else {
              _tmpNote = _cursor.getString(_cursorIndexOfNote);
            }
            _item = new EditVersionEntity(_tmpId,_tmpImageId,_tmpParentId,_tmpName,_tmpCreatedAt,_tmpCumulativeParamsJson,_tmpIsVirtualCopy,_tmpIsActive,_tmpNote);
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
  public Flow<List<EditVersionEntity>> observeVersionsForImage(final String imageId) {
    final String _sql = "SELECT * FROM edit_versions WHERE imageId = ? ORDER BY createdAt ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, imageId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"edit_versions"}, new Callable<List<EditVersionEntity>>() {
      @Override
      @NonNull
      public List<EditVersionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfImageId = CursorUtil.getColumnIndexOrThrow(_cursor, "imageId");
          final int _cursorIndexOfParentId = CursorUtil.getColumnIndexOrThrow(_cursor, "parentId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfCumulativeParamsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "cumulativeParamsJson");
          final int _cursorIndexOfIsVirtualCopy = CursorUtil.getColumnIndexOrThrow(_cursor, "isVirtualCopy");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfNote = CursorUtil.getColumnIndexOrThrow(_cursor, "note");
          final List<EditVersionEntity> _result = new ArrayList<EditVersionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final EditVersionEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpImageId;
            _tmpImageId = _cursor.getString(_cursorIndexOfImageId);
            final String _tmpParentId;
            if (_cursor.isNull(_cursorIndexOfParentId)) {
              _tmpParentId = null;
            } else {
              _tmpParentId = _cursor.getString(_cursorIndexOfParentId);
            }
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final String _tmpCumulativeParamsJson;
            _tmpCumulativeParamsJson = _cursor.getString(_cursorIndexOfCumulativeParamsJson);
            final boolean _tmpIsVirtualCopy;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsVirtualCopy);
            _tmpIsVirtualCopy = _tmp != 0;
            final boolean _tmpIsActive;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp_1 != 0;
            final String _tmpNote;
            if (_cursor.isNull(_cursorIndexOfNote)) {
              _tmpNote = null;
            } else {
              _tmpNote = _cursor.getString(_cursorIndexOfNote);
            }
            _item = new EditVersionEntity(_tmpId,_tmpImageId,_tmpParentId,_tmpName,_tmpCreatedAt,_tmpCumulativeParamsJson,_tmpIsVirtualCopy,_tmpIsActive,_tmpNote);
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
  public Object getTransactionsFor(final String versionId,
      final Continuation<? super List<EditTransactionEntity>> $completion) {
    final String _sql = "SELECT * FROM edit_transactions WHERE versionId = ? ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, versionId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<EditTransactionEntity>>() {
      @Override
      @NonNull
      public List<EditTransactionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfVersionId = CursorUtil.getColumnIndexOrThrow(_cursor, "versionId");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "label");
          final int _cursorIndexOfParamDeltaJson = CursorUtil.getColumnIndexOrThrow(_cursor, "paramDeltaJson");
          final int _cursorIndexOfMaskIds = CursorUtil.getColumnIndexOrThrow(_cursor, "maskIds");
          final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
          final List<EditTransactionEntity> _result = new ArrayList<EditTransactionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final EditTransactionEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpVersionId;
            _tmpVersionId = _cursor.getString(_cursorIndexOfVersionId);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpLabel;
            _tmpLabel = _cursor.getString(_cursorIndexOfLabel);
            final String _tmpParamDeltaJson;
            _tmpParamDeltaJson = _cursor.getString(_cursorIndexOfParamDeltaJson);
            final String _tmpMaskIds;
            if (_cursor.isNull(_cursorIndexOfMaskIds)) {
              _tmpMaskIds = null;
            } else {
              _tmpMaskIds = _cursor.getString(_cursorIndexOfMaskIds);
            }
            final String _tmpSource;
            _tmpSource = _cursor.getString(_cursorIndexOfSource);
            _item = new EditTransactionEntity(_tmpId,_tmpVersionId,_tmpTimestamp,_tmpLabel,_tmpParamDeltaJson,_tmpMaskIds,_tmpSource);
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
  public Flow<List<EditTransactionEntity>> observeTransactionsFor(final String versionId) {
    final String _sql = "SELECT * FROM edit_transactions WHERE versionId = ? ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, versionId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"edit_transactions"}, new Callable<List<EditTransactionEntity>>() {
      @Override
      @NonNull
      public List<EditTransactionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfVersionId = CursorUtil.getColumnIndexOrThrow(_cursor, "versionId");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "label");
          final int _cursorIndexOfParamDeltaJson = CursorUtil.getColumnIndexOrThrow(_cursor, "paramDeltaJson");
          final int _cursorIndexOfMaskIds = CursorUtil.getColumnIndexOrThrow(_cursor, "maskIds");
          final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
          final List<EditTransactionEntity> _result = new ArrayList<EditTransactionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final EditTransactionEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpVersionId;
            _tmpVersionId = _cursor.getString(_cursorIndexOfVersionId);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpLabel;
            _tmpLabel = _cursor.getString(_cursorIndexOfLabel);
            final String _tmpParamDeltaJson;
            _tmpParamDeltaJson = _cursor.getString(_cursorIndexOfParamDeltaJson);
            final String _tmpMaskIds;
            if (_cursor.isNull(_cursorIndexOfMaskIds)) {
              _tmpMaskIds = null;
            } else {
              _tmpMaskIds = _cursor.getString(_cursorIndexOfMaskIds);
            }
            final String _tmpSource;
            _tmpSource = _cursor.getString(_cursorIndexOfSource);
            _item = new EditTransactionEntity(_tmpId,_tmpVersionId,_tmpTimestamp,_tmpLabel,_tmpParamDeltaJson,_tmpMaskIds,_tmpSource);
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
  public Object getActiveVersion(final String imageId,
      final Continuation<? super EditVersionEntity> $completion) {
    final String _sql = "SELECT * FROM edit_versions WHERE imageId = ? AND isActive = 1 LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, imageId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<EditVersionEntity>() {
      @Override
      @Nullable
      public EditVersionEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfImageId = CursorUtil.getColumnIndexOrThrow(_cursor, "imageId");
          final int _cursorIndexOfParentId = CursorUtil.getColumnIndexOrThrow(_cursor, "parentId");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfCumulativeParamsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "cumulativeParamsJson");
          final int _cursorIndexOfIsVirtualCopy = CursorUtil.getColumnIndexOrThrow(_cursor, "isVirtualCopy");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfNote = CursorUtil.getColumnIndexOrThrow(_cursor, "note");
          final EditVersionEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpImageId;
            _tmpImageId = _cursor.getString(_cursorIndexOfImageId);
            final String _tmpParentId;
            if (_cursor.isNull(_cursorIndexOfParentId)) {
              _tmpParentId = null;
            } else {
              _tmpParentId = _cursor.getString(_cursorIndexOfParentId);
            }
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final String _tmpCumulativeParamsJson;
            _tmpCumulativeParamsJson = _cursor.getString(_cursorIndexOfCumulativeParamsJson);
            final boolean _tmpIsVirtualCopy;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsVirtualCopy);
            _tmpIsVirtualCopy = _tmp != 0;
            final boolean _tmpIsActive;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp_1 != 0;
            final String _tmpNote;
            if (_cursor.isNull(_cursorIndexOfNote)) {
              _tmpNote = null;
            } else {
              _tmpNote = _cursor.getString(_cursorIndexOfNote);
            }
            _result = new EditVersionEntity(_tmpId,_tmpImageId,_tmpParentId,_tmpName,_tmpCreatedAt,_tmpCumulativeParamsJson,_tmpIsVirtualCopy,_tmpIsActive,_tmpNote);
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
  public Object virtualCopyCount(final String imageId,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM edit_versions WHERE imageId = ? AND isVirtualCopy = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, imageId);
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
  public Object versionCount(final String imageId,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM edit_versions WHERE imageId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, imageId);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
