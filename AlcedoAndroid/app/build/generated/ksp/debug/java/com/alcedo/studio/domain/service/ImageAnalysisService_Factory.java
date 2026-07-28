package com.alcedo.studio.domain.service;

import com.alcedo.studio.ai.LlmCullingClient;
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
public final class ImageAnalysisService_Factory implements Factory<ImageAnalysisService> {
  private final Provider<LlmCullingClient> llmClientProvider;

  private final Provider<ImageAnalysisEncoder> encoderProvider;

  private final Provider<AiCredentialService> credentialServiceProvider;

  public ImageAnalysisService_Factory(Provider<LlmCullingClient> llmClientProvider,
      Provider<ImageAnalysisEncoder> encoderProvider,
      Provider<AiCredentialService> credentialServiceProvider) {
    this.llmClientProvider = llmClientProvider;
    this.encoderProvider = encoderProvider;
    this.credentialServiceProvider = credentialServiceProvider;
  }

  @Override
  public ImageAnalysisService get() {
    return newInstance(llmClientProvider.get(), encoderProvider.get(), credentialServiceProvider.get());
  }

  public static ImageAnalysisService_Factory create(Provider<LlmCullingClient> llmClientProvider,
      Provider<ImageAnalysisEncoder> encoderProvider,
      Provider<AiCredentialService> credentialServiceProvider) {
    return new ImageAnalysisService_Factory(llmClientProvider, encoderProvider, credentialServiceProvider);
  }

  public static ImageAnalysisService newInstance(LlmCullingClient llmClient,
      ImageAnalysisEncoder encoder, AiCredentialService credentialService) {
    return new ImageAnalysisService(llmClient, encoder, credentialService);
  }
}
