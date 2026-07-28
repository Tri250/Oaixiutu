package com.alcedo.studio.domain.service;

import com.alcedo.studio.ai.LlmCullingClient;
import com.alcedo.studio.data.dao.AiEmbeddingDao;
import com.alcedo.studio.domain.repository.ImageRepository;
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
public final class AiRatingService_Factory implements Factory<AiRatingService> {
  private final Provider<LlmCullingClient> llmClientProvider;

  private final Provider<ImageAnalysisEncoder> imageAnalysisEncoderProvider;

  private final Provider<AiCredentialService> credentialServiceProvider;

  private final Provider<ImageRepository> imageRepositoryProvider;

  private final Provider<AiEmbeddingDao> ratingDaoProvider;

  public AiRatingService_Factory(Provider<LlmCullingClient> llmClientProvider,
      Provider<ImageAnalysisEncoder> imageAnalysisEncoderProvider,
      Provider<AiCredentialService> credentialServiceProvider,
      Provider<ImageRepository> imageRepositoryProvider,
      Provider<AiEmbeddingDao> ratingDaoProvider) {
    this.llmClientProvider = llmClientProvider;
    this.imageAnalysisEncoderProvider = imageAnalysisEncoderProvider;
    this.credentialServiceProvider = credentialServiceProvider;
    this.imageRepositoryProvider = imageRepositoryProvider;
    this.ratingDaoProvider = ratingDaoProvider;
  }

  @Override
  public AiRatingService get() {
    return newInstance(llmClientProvider.get(), imageAnalysisEncoderProvider.get(), credentialServiceProvider.get(), imageRepositoryProvider.get(), ratingDaoProvider.get());
  }

  public static AiRatingService_Factory create(Provider<LlmCullingClient> llmClientProvider,
      Provider<ImageAnalysisEncoder> imageAnalysisEncoderProvider,
      Provider<AiCredentialService> credentialServiceProvider,
      Provider<ImageRepository> imageRepositoryProvider,
      Provider<AiEmbeddingDao> ratingDaoProvider) {
    return new AiRatingService_Factory(llmClientProvider, imageAnalysisEncoderProvider, credentialServiceProvider, imageRepositoryProvider, ratingDaoProvider);
  }

  public static AiRatingService newInstance(LlmCullingClient llmClient,
      ImageAnalysisEncoder imageAnalysisEncoder, AiCredentialService credentialService,
      ImageRepository imageRepository, AiEmbeddingDao ratingDao) {
    return new AiRatingService(llmClient, imageAnalysisEncoder, credentialService, imageRepository, ratingDao);
  }
}
