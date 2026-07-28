package com.alcedo.studio.domain.service;

import com.alcedo.studio.ai.LlmCullingClient;
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
public final class SemanticGenerationService_Factory implements Factory<SemanticGenerationService> {
  private final Provider<AiSidecarRuntimeService> sidecarRuntimeProvider;

  private final Provider<ImageAnalysisEncoder> imageAnalysisEncoderProvider;

  private final Provider<LlmCullingClient> llmClientProvider;

  private final Provider<ImageRepository> imageRepositoryProvider;

  private final Provider<AiCredentialService> credentialServiceProvider;

  public SemanticGenerationService_Factory(Provider<AiSidecarRuntimeService> sidecarRuntimeProvider,
      Provider<ImageAnalysisEncoder> imageAnalysisEncoderProvider,
      Provider<LlmCullingClient> llmClientProvider,
      Provider<ImageRepository> imageRepositoryProvider,
      Provider<AiCredentialService> credentialServiceProvider) {
    this.sidecarRuntimeProvider = sidecarRuntimeProvider;
    this.imageAnalysisEncoderProvider = imageAnalysisEncoderProvider;
    this.llmClientProvider = llmClientProvider;
    this.imageRepositoryProvider = imageRepositoryProvider;
    this.credentialServiceProvider = credentialServiceProvider;
  }

  @Override
  public SemanticGenerationService get() {
    return newInstance(sidecarRuntimeProvider.get(), imageAnalysisEncoderProvider.get(), llmClientProvider.get(), imageRepositoryProvider.get(), credentialServiceProvider.get());
  }

  public static SemanticGenerationService_Factory create(
      Provider<AiSidecarRuntimeService> sidecarRuntimeProvider,
      Provider<ImageAnalysisEncoder> imageAnalysisEncoderProvider,
      Provider<LlmCullingClient> llmClientProvider,
      Provider<ImageRepository> imageRepositoryProvider,
      Provider<AiCredentialService> credentialServiceProvider) {
    return new SemanticGenerationService_Factory(sidecarRuntimeProvider, imageAnalysisEncoderProvider, llmClientProvider, imageRepositoryProvider, credentialServiceProvider);
  }

  public static SemanticGenerationService newInstance(AiSidecarRuntimeService sidecarRuntime,
      ImageAnalysisEncoder imageAnalysisEncoder, LlmCullingClient llmClient,
      ImageRepository imageRepository, AiCredentialService credentialService) {
    return new SemanticGenerationService(sidecarRuntime, imageAnalysisEncoder, llmClient, imageRepository, credentialService);
  }
}
