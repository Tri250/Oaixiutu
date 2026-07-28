package com.alcedo.studio.data.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.alcedo.studio.data.local.AiEmbeddingEntity;
import com.alcedo.studio.data.local.AiRatingEntity;
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

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AiEmbeddingDao_Impl implements AiEmbeddingDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<AiEmbeddingEntity> __insertionAdapterOfAiEmbeddingEntity;

  private final EntityInsertionAdapter<AiRatingEntity> __insertionAdapterOfAiRatingEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteForImage;

  private final SharedSQLiteStatement __preparedStmtOfDeleteForModel;

  private final SharedSQLiteStatement __preparedStmtOfDeleteRating;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAllRatings;

  public AiEmbeddingDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfAiEmbeddingEntity = new EntityInsertionAdapter<AiEmbeddingEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `ai_embeddings` (`id`,`imageId`,`modelId`,`dimensions`,`generatedAt`,`norm`,`embeddingBlob`) VALUES (?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final AiEmbeddingEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getImageId());
        statement.bindString(3, entity.getModelId());
        statement.bindLong(4, entity.getDimensions());
        statement.bindLong(5, entity.getGeneratedAt());
        statement.bindDouble(6, entity.getNorm());
        statement.bindBlob(7, entity.getEmbeddingBlob());
      }
    };
    this.__insertionAdapterOfAiRatingEntity = new EntityInsertionAdapter<AiRatingEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `ai_ratings` (`imageId`,`overallScore`,`technicalScore`,`aestheticScore`,`sharpnessScore`,`exposureScore`,`compositionScore`,`emotionScore`,`rationale`,`suggestedRating`,`suggestedFlag`,`generatedAt`,`modelId`,`provider`,`confidence`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final AiRatingEntity entity) {
        statement.bindString(1, entity.getImageId());
        statement.bindDouble(2, entity.getOverallScore());
        statement.bindDouble(3, entity.getTechnicalScore());
        statement.bindDouble(4, entity.getAestheticScore());
        statement.bindDouble(5, entity.getSharpnessScore());
        statement.bindDouble(6, entity.getExposureScore());
        statement.bindDouble(7, entity.getCompositionScore());
        statement.bindDouble(8, entity.getEmotionScore());
        statement.bindString(9, entity.getRationale());
        statement.bindLong(10, entity.getSuggestedRating());
        statement.bindString(11, entity.getSuggestedFlag());
        statement.bindLong(12, entity.getGeneratedAt());
        statement.bindString(13, entity.getModelId());
        statement.bindString(14, entity.getProvider());
        statement.bindDouble(15, entity.getConfidence());
      }
    };
    this.__preparedStmtOfDeleteForImage = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM ai_embeddings WHERE imageId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteForModel = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM ai_embeddings WHERE modelId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteRating = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM ai_ratings WHERE imageId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAllRatings = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM ai_ratings";
        return _query;
      }
    };
  }

  @Override
  public Object upsertEmbedding(final AiEmbeddingEntity embedding,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfAiEmbeddingEntity.insert(embedding);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertEmbeddings(final List<AiEmbeddingEntity> embeddings,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfAiEmbeddingEntity.insert(embeddings);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertRating(final AiRatingEntity rating,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfAiRatingEntity.insert(rating);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteForImage(final String imageId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteForImage.acquire();
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
          __preparedStmtOfDeleteForImage.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteForModel(final String modelId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteForModel.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, modelId);
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
          __preparedStmtOfDeleteForModel.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteRating(final String imageId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteRating.acquire();
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
          __preparedStmtOfDeleteRating.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAllRatings(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAllRatings.acquire();
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
          __preparedStmtOfDeleteAllRatings.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object get(final String imageId, final String modelId,
      final Continuation<? super AiEmbeddingEntity> $completion) {
    final String _sql = "SELECT * FROM ai_embeddings WHERE imageId = ? AND modelId = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, imageId);
    _argIndex = 2;
    _statement.bindString(_argIndex, modelId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<AiEmbeddingEntity>() {
      @Override
      @Nullable
      public AiEmbeddingEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfImageId = CursorUtil.getColumnIndexOrThrow(_cursor, "imageId");
          final int _cursorIndexOfModelId = CursorUtil.getColumnIndexOrThrow(_cursor, "modelId");
          final int _cursorIndexOfDimensions = CursorUtil.getColumnIndexOrThrow(_cursor, "dimensions");
          final int _cursorIndexOfGeneratedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "generatedAt");
          final int _cursorIndexOfNorm = CursorUtil.getColumnIndexOrThrow(_cursor, "norm");
          final int _cursorIndexOfEmbeddingBlob = CursorUtil.getColumnIndexOrThrow(_cursor, "embeddingBlob");
          final AiEmbeddingEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpImageId;
            _tmpImageId = _cursor.getString(_cursorIndexOfImageId);
            final String _tmpModelId;
            _tmpModelId = _cursor.getString(_cursorIndexOfModelId);
            final int _tmpDimensions;
            _tmpDimensions = _cursor.getInt(_cursorIndexOfDimensions);
            final long _tmpGeneratedAt;
            _tmpGeneratedAt = _cursor.getLong(_cursorIndexOfGeneratedAt);
            final float _tmpNorm;
            _tmpNorm = _cursor.getFloat(_cursorIndexOfNorm);
            final byte[] _tmpEmbeddingBlob;
            _tmpEmbeddingBlob = _cursor.getBlob(_cursorIndexOfEmbeddingBlob);
            _result = new AiEmbeddingEntity(_tmpId,_tmpImageId,_tmpModelId,_tmpDimensions,_tmpGeneratedAt,_tmpNorm,_tmpEmbeddingBlob);
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
  public Object getAllForModel(final String modelId,
      final Continuation<? super List<AiEmbeddingEntity>> $completion) {
    final String _sql = "SELECT * FROM ai_embeddings WHERE modelId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, modelId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<AiEmbeddingEntity>>() {
      @Override
      @NonNull
      public List<AiEmbeddingEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfImageId = CursorUtil.getColumnIndexOrThrow(_cursor, "imageId");
          final int _cursorIndexOfModelId = CursorUtil.getColumnIndexOrThrow(_cursor, "modelId");
          final int _cursorIndexOfDimensions = CursorUtil.getColumnIndexOrThrow(_cursor, "dimensions");
          final int _cursorIndexOfGeneratedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "generatedAt");
          final int _cursorIndexOfNorm = CursorUtil.getColumnIndexOrThrow(_cursor, "norm");
          final int _cursorIndexOfEmbeddingBlob = CursorUtil.getColumnIndexOrThrow(_cursor, "embeddingBlob");
          final List<AiEmbeddingEntity> _result = new ArrayList<AiEmbeddingEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final AiEmbeddingEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpImageId;
            _tmpImageId = _cursor.getString(_cursorIndexOfImageId);
            final String _tmpModelId;
            _tmpModelId = _cursor.getString(_cursorIndexOfModelId);
            final int _tmpDimensions;
            _tmpDimensions = _cursor.getInt(_cursorIndexOfDimensions);
            final long _tmpGeneratedAt;
            _tmpGeneratedAt = _cursor.getLong(_cursorIndexOfGeneratedAt);
            final float _tmpNorm;
            _tmpNorm = _cursor.getFloat(_cursorIndexOfNorm);
            final byte[] _tmpEmbeddingBlob;
            _tmpEmbeddingBlob = _cursor.getBlob(_cursorIndexOfEmbeddingBlob);
            _item = new AiEmbeddingEntity(_tmpId,_tmpImageId,_tmpModelId,_tmpDimensions,_tmpGeneratedAt,_tmpNorm,_tmpEmbeddingBlob);
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
  public Object embeddedImageIds(final String modelId,
      final Continuation<? super List<String>> $completion) {
    final String _sql = "SELECT imageId FROM ai_embeddings WHERE modelId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, modelId);
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
  public Object countForModel(final String modelId,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM ai_embeddings WHERE modelId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, modelId);
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
  public Object count(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM ai_embeddings";
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
  public Object getRating(final String imageId,
      final Continuation<? super AiRatingEntity> $completion) {
    final String _sql = "SELECT * FROM ai_ratings WHERE imageId = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, imageId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<AiRatingEntity>() {
      @Override
      @Nullable
      public AiRatingEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfImageId = CursorUtil.getColumnIndexOrThrow(_cursor, "imageId");
          final int _cursorIndexOfOverallScore = CursorUtil.getColumnIndexOrThrow(_cursor, "overallScore");
          final int _cursorIndexOfTechnicalScore = CursorUtil.getColumnIndexOrThrow(_cursor, "technicalScore");
          final int _cursorIndexOfAestheticScore = CursorUtil.getColumnIndexOrThrow(_cursor, "aestheticScore");
          final int _cursorIndexOfSharpnessScore = CursorUtil.getColumnIndexOrThrow(_cursor, "sharpnessScore");
          final int _cursorIndexOfExposureScore = CursorUtil.getColumnIndexOrThrow(_cursor, "exposureScore");
          final int _cursorIndexOfCompositionScore = CursorUtil.getColumnIndexOrThrow(_cursor, "compositionScore");
          final int _cursorIndexOfEmotionScore = CursorUtil.getColumnIndexOrThrow(_cursor, "emotionScore");
          final int _cursorIndexOfRationale = CursorUtil.getColumnIndexOrThrow(_cursor, "rationale");
          final int _cursorIndexOfSuggestedRating = CursorUtil.getColumnIndexOrThrow(_cursor, "suggestedRating");
          final int _cursorIndexOfSuggestedFlag = CursorUtil.getColumnIndexOrThrow(_cursor, "suggestedFlag");
          final int _cursorIndexOfGeneratedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "generatedAt");
          final int _cursorIndexOfModelId = CursorUtil.getColumnIndexOrThrow(_cursor, "modelId");
          final int _cursorIndexOfProvider = CursorUtil.getColumnIndexOrThrow(_cursor, "provider");
          final int _cursorIndexOfConfidence = CursorUtil.getColumnIndexOrThrow(_cursor, "confidence");
          final AiRatingEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpImageId;
            _tmpImageId = _cursor.getString(_cursorIndexOfImageId);
            final float _tmpOverallScore;
            _tmpOverallScore = _cursor.getFloat(_cursorIndexOfOverallScore);
            final float _tmpTechnicalScore;
            _tmpTechnicalScore = _cursor.getFloat(_cursorIndexOfTechnicalScore);
            final float _tmpAestheticScore;
            _tmpAestheticScore = _cursor.getFloat(_cursorIndexOfAestheticScore);
            final float _tmpSharpnessScore;
            _tmpSharpnessScore = _cursor.getFloat(_cursorIndexOfSharpnessScore);
            final float _tmpExposureScore;
            _tmpExposureScore = _cursor.getFloat(_cursorIndexOfExposureScore);
            final float _tmpCompositionScore;
            _tmpCompositionScore = _cursor.getFloat(_cursorIndexOfCompositionScore);
            final float _tmpEmotionScore;
            _tmpEmotionScore = _cursor.getFloat(_cursorIndexOfEmotionScore);
            final String _tmpRationale;
            _tmpRationale = _cursor.getString(_cursorIndexOfRationale);
            final int _tmpSuggestedRating;
            _tmpSuggestedRating = _cursor.getInt(_cursorIndexOfSuggestedRating);
            final String _tmpSuggestedFlag;
            _tmpSuggestedFlag = _cursor.getString(_cursorIndexOfSuggestedFlag);
            final long _tmpGeneratedAt;
            _tmpGeneratedAt = _cursor.getLong(_cursorIndexOfGeneratedAt);
            final String _tmpModelId;
            _tmpModelId = _cursor.getString(_cursorIndexOfModelId);
            final String _tmpProvider;
            _tmpProvider = _cursor.getString(_cursorIndexOfProvider);
            final float _tmpConfidence;
            _tmpConfidence = _cursor.getFloat(_cursorIndexOfConfidence);
            _result = new AiRatingEntity(_tmpImageId,_tmpOverallScore,_tmpTechnicalScore,_tmpAestheticScore,_tmpSharpnessScore,_tmpExposureScore,_tmpCompositionScore,_tmpEmotionScore,_tmpRationale,_tmpSuggestedRating,_tmpSuggestedFlag,_tmpGeneratedAt,_tmpModelId,_tmpProvider,_tmpConfidence);
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
  public Object topRated(final int limit,
      final Continuation<? super List<AiRatingEntity>> $completion) {
    final String _sql = "SELECT * FROM ai_ratings ORDER BY overallScore DESC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<AiRatingEntity>>() {
      @Override
      @NonNull
      public List<AiRatingEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfImageId = CursorUtil.getColumnIndexOrThrow(_cursor, "imageId");
          final int _cursorIndexOfOverallScore = CursorUtil.getColumnIndexOrThrow(_cursor, "overallScore");
          final int _cursorIndexOfTechnicalScore = CursorUtil.getColumnIndexOrThrow(_cursor, "technicalScore");
          final int _cursorIndexOfAestheticScore = CursorUtil.getColumnIndexOrThrow(_cursor, "aestheticScore");
          final int _cursorIndexOfSharpnessScore = CursorUtil.getColumnIndexOrThrow(_cursor, "sharpnessScore");
          final int _cursorIndexOfExposureScore = CursorUtil.getColumnIndexOrThrow(_cursor, "exposureScore");
          final int _cursorIndexOfCompositionScore = CursorUtil.getColumnIndexOrThrow(_cursor, "compositionScore");
          final int _cursorIndexOfEmotionScore = CursorUtil.getColumnIndexOrThrow(_cursor, "emotionScore");
          final int _cursorIndexOfRationale = CursorUtil.getColumnIndexOrThrow(_cursor, "rationale");
          final int _cursorIndexOfSuggestedRating = CursorUtil.getColumnIndexOrThrow(_cursor, "suggestedRating");
          final int _cursorIndexOfSuggestedFlag = CursorUtil.getColumnIndexOrThrow(_cursor, "suggestedFlag");
          final int _cursorIndexOfGeneratedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "generatedAt");
          final int _cursorIndexOfModelId = CursorUtil.getColumnIndexOrThrow(_cursor, "modelId");
          final int _cursorIndexOfProvider = CursorUtil.getColumnIndexOrThrow(_cursor, "provider");
          final int _cursorIndexOfConfidence = CursorUtil.getColumnIndexOrThrow(_cursor, "confidence");
          final List<AiRatingEntity> _result = new ArrayList<AiRatingEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final AiRatingEntity _item;
            final String _tmpImageId;
            _tmpImageId = _cursor.getString(_cursorIndexOfImageId);
            final float _tmpOverallScore;
            _tmpOverallScore = _cursor.getFloat(_cursorIndexOfOverallScore);
            final float _tmpTechnicalScore;
            _tmpTechnicalScore = _cursor.getFloat(_cursorIndexOfTechnicalScore);
            final float _tmpAestheticScore;
            _tmpAestheticScore = _cursor.getFloat(_cursorIndexOfAestheticScore);
            final float _tmpSharpnessScore;
            _tmpSharpnessScore = _cursor.getFloat(_cursorIndexOfSharpnessScore);
            final float _tmpExposureScore;
            _tmpExposureScore = _cursor.getFloat(_cursorIndexOfExposureScore);
            final float _tmpCompositionScore;
            _tmpCompositionScore = _cursor.getFloat(_cursorIndexOfCompositionScore);
            final float _tmpEmotionScore;
            _tmpEmotionScore = _cursor.getFloat(_cursorIndexOfEmotionScore);
            final String _tmpRationale;
            _tmpRationale = _cursor.getString(_cursorIndexOfRationale);
            final int _tmpSuggestedRating;
            _tmpSuggestedRating = _cursor.getInt(_cursorIndexOfSuggestedRating);
            final String _tmpSuggestedFlag;
            _tmpSuggestedFlag = _cursor.getString(_cursorIndexOfSuggestedFlag);
            final long _tmpGeneratedAt;
            _tmpGeneratedAt = _cursor.getLong(_cursorIndexOfGeneratedAt);
            final String _tmpModelId;
            _tmpModelId = _cursor.getString(_cursorIndexOfModelId);
            final String _tmpProvider;
            _tmpProvider = _cursor.getString(_cursorIndexOfProvider);
            final float _tmpConfidence;
            _tmpConfidence = _cursor.getFloat(_cursorIndexOfConfidence);
            _item = new AiRatingEntity(_tmpImageId,_tmpOverallScore,_tmpTechnicalScore,_tmpAestheticScore,_tmpSharpnessScore,_tmpExposureScore,_tmpCompositionScore,_tmpEmotionScore,_tmpRationale,_tmpSuggestedRating,_tmpSuggestedFlag,_tmpGeneratedAt,_tmpModelId,_tmpProvider,_tmpConfidence);
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
  public Object lowestRated(final int limit,
      final Continuation<? super List<AiRatingEntity>> $completion) {
    final String _sql = "SELECT * FROM ai_ratings ORDER BY overallScore ASC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<AiRatingEntity>>() {
      @Override
      @NonNull
      public List<AiRatingEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfImageId = CursorUtil.getColumnIndexOrThrow(_cursor, "imageId");
          final int _cursorIndexOfOverallScore = CursorUtil.getColumnIndexOrThrow(_cursor, "overallScore");
          final int _cursorIndexOfTechnicalScore = CursorUtil.getColumnIndexOrThrow(_cursor, "technicalScore");
          final int _cursorIndexOfAestheticScore = CursorUtil.getColumnIndexOrThrow(_cursor, "aestheticScore");
          final int _cursorIndexOfSharpnessScore = CursorUtil.getColumnIndexOrThrow(_cursor, "sharpnessScore");
          final int _cursorIndexOfExposureScore = CursorUtil.getColumnIndexOrThrow(_cursor, "exposureScore");
          final int _cursorIndexOfCompositionScore = CursorUtil.getColumnIndexOrThrow(_cursor, "compositionScore");
          final int _cursorIndexOfEmotionScore = CursorUtil.getColumnIndexOrThrow(_cursor, "emotionScore");
          final int _cursorIndexOfRationale = CursorUtil.getColumnIndexOrThrow(_cursor, "rationale");
          final int _cursorIndexOfSuggestedRating = CursorUtil.getColumnIndexOrThrow(_cursor, "suggestedRating");
          final int _cursorIndexOfSuggestedFlag = CursorUtil.getColumnIndexOrThrow(_cursor, "suggestedFlag");
          final int _cursorIndexOfGeneratedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "generatedAt");
          final int _cursorIndexOfModelId = CursorUtil.getColumnIndexOrThrow(_cursor, "modelId");
          final int _cursorIndexOfProvider = CursorUtil.getColumnIndexOrThrow(_cursor, "provider");
          final int _cursorIndexOfConfidence = CursorUtil.getColumnIndexOrThrow(_cursor, "confidence");
          final List<AiRatingEntity> _result = new ArrayList<AiRatingEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final AiRatingEntity _item;
            final String _tmpImageId;
            _tmpImageId = _cursor.getString(_cursorIndexOfImageId);
            final float _tmpOverallScore;
            _tmpOverallScore = _cursor.getFloat(_cursorIndexOfOverallScore);
            final float _tmpTechnicalScore;
            _tmpTechnicalScore = _cursor.getFloat(_cursorIndexOfTechnicalScore);
            final float _tmpAestheticScore;
            _tmpAestheticScore = _cursor.getFloat(_cursorIndexOfAestheticScore);
            final float _tmpSharpnessScore;
            _tmpSharpnessScore = _cursor.getFloat(_cursorIndexOfSharpnessScore);
            final float _tmpExposureScore;
            _tmpExposureScore = _cursor.getFloat(_cursorIndexOfExposureScore);
            final float _tmpCompositionScore;
            _tmpCompositionScore = _cursor.getFloat(_cursorIndexOfCompositionScore);
            final float _tmpEmotionScore;
            _tmpEmotionScore = _cursor.getFloat(_cursorIndexOfEmotionScore);
            final String _tmpRationale;
            _tmpRationale = _cursor.getString(_cursorIndexOfRationale);
            final int _tmpSuggestedRating;
            _tmpSuggestedRating = _cursor.getInt(_cursorIndexOfSuggestedRating);
            final String _tmpSuggestedFlag;
            _tmpSuggestedFlag = _cursor.getString(_cursorIndexOfSuggestedFlag);
            final long _tmpGeneratedAt;
            _tmpGeneratedAt = _cursor.getLong(_cursorIndexOfGeneratedAt);
            final String _tmpModelId;
            _tmpModelId = _cursor.getString(_cursorIndexOfModelId);
            final String _tmpProvider;
            _tmpProvider = _cursor.getString(_cursorIndexOfProvider);
            final float _tmpConfidence;
            _tmpConfidence = _cursor.getFloat(_cursorIndexOfConfidence);
            _item = new AiRatingEntity(_tmpImageId,_tmpOverallScore,_tmpTechnicalScore,_tmpAestheticScore,_tmpSharpnessScore,_tmpExposureScore,_tmpCompositionScore,_tmpEmotionScore,_tmpRationale,_tmpSuggestedRating,_tmpSuggestedFlag,_tmpGeneratedAt,_tmpModelId,_tmpProvider,_tmpConfidence);
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
  public Object ratingCount(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM ai_ratings";
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
