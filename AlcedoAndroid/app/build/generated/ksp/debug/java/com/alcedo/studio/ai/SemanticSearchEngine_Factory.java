package com.alcedo.studio.ai;

import com.alcedo.studio.data.dao.AiEmbeddingDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class SemanticSearchEngine_Factory implements Factory<SemanticSearchEngine> {
  private final Provider<AiEmbeddingDao> embeddingDaoProvider;

  public SemanticSearchEngine_Factory(Provider<AiEmbeddingDao> embeddingDaoProvider) {
    this.embeddingDaoProvider = embeddingDaoProvider;
  }

  @Override
  public SemanticSearchEngine get() {
    return newInstance(embeddingDaoProvider.get());
  }

  public static SemanticSearchEngine_Factory create(Provider<AiEmbeddingDao> embeddingDaoProvider) {
    return new SemanticSearchEngine_Factory(embeddingDaoProvider);
  }

  public static SemanticSearchEngine newInstance(AiEmbeddingDao embeddingDao) {
    return new SemanticSearchEngine(embeddingDao);
  }
}
