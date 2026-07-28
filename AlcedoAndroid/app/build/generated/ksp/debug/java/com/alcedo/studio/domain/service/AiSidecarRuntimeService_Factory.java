package com.alcedo.studio.domain.service;

import com.alcedo.studio.ai.OnnxModelManager;
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
public final class AiSidecarRuntimeService_Factory implements Factory<AiSidecarRuntimeService> {
  private final Provider<OnnxModelManager> onnxModelManagerProvider;

  private final Provider<ModelDownloadService> modelDownloadServiceProvider;

  public AiSidecarRuntimeService_Factory(Provider<OnnxModelManager> onnxModelManagerProvider,
      Provider<ModelDownloadService> modelDownloadServiceProvider) {
    this.onnxModelManagerProvider = onnxModelManagerProvider;
    this.modelDownloadServiceProvider = modelDownloadServiceProvider;
  }

  @Override
  public AiSidecarRuntimeService get() {
    return newInstance(onnxModelManagerProvider.get(), modelDownloadServiceProvider.get());
  }

  public static AiSidecarRuntimeService_Factory create(
      Provider<OnnxModelManager> onnxModelManagerProvider,
      Provider<ModelDownloadService> modelDownloadServiceProvider) {
    return new AiSidecarRuntimeService_Factory(onnxModelManagerProvider, modelDownloadServiceProvider);
  }

  public static AiSidecarRuntimeService newInstance(OnnxModelManager onnxModelManager,
      ModelDownloadService modelDownloadService) {
    return new AiSidecarRuntimeService(onnxModelManager, modelDownloadService);
  }
}
