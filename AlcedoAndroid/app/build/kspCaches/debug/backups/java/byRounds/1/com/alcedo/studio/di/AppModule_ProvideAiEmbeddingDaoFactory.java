package com.alcedo.studio.di;

import com.alcedo.studio.data.dao.AiEmbeddingDao;
import com.alcedo.studio.data.local.SleeveDatabase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class AppModule_ProvideAiEmbeddingDaoFactory implements Factory<AiEmbeddingDao> {
  private final Provider<SleeveDatabase> dbProvider;

  public AppModule_ProvideAiEmbeddingDaoFactory(Provider<SleeveDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public AiEmbeddingDao get() {
    return provideAiEmbeddingDao(dbProvider.get());
  }

  public static AppModule_ProvideAiEmbeddingDaoFactory create(Provider<SleeveDatabase> dbProvider) {
    return new AppModule_ProvideAiEmbeddingDaoFactory(dbProvider);
  }

  public static AiEmbeddingDao provideAiEmbeddingDao(SleeveDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideAiEmbeddingDao(db));
  }
}
