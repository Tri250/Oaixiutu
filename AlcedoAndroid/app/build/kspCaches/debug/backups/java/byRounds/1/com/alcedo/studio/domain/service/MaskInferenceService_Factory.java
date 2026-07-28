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
public final class MaskInferenceService_Factory implements Factory<MaskInferenceService> {
  private final Provider<AiSidecarRuntimeService> sidecarRuntimeProvider;

  private final Provider<OnnxModelManager> onnxModelManagerProvider;

  private final Provider<DecodeService> decodeServiceProvider;

  public MaskInferenceService_Factory(Provider<AiSidecarRuntimeService> sidecarRuntimeProvider,
      Provider<OnnxModelManager> onnxModelManagerProvider,
      Provider<DecodeService> decodeServiceProvider) {
    this.sidecarRuntimeProvider = sidecarRuntimeProvider;
    this.onnxModelManagerProvider = onnxModelManagerProvider;
    this.decodeServiceProvider = decodeServiceProvider;
  }

  @Override
  public MaskInferenceService get() {
    return newInstance(sidecarRuntimeProvider.get(), onnxModelManagerProvider.get(), decodeServiceProvider.get());
  }

  public static MaskInferenceService_Factory create(
      Provider<AiSidecarRuntimeService> sidecarRuntimeProvider,
      Provider<OnnxModelManager> onnxModelManagerProvider,
      Provider<DecodeService> decodeServiceProvider) {
    return new MaskInferenceService_Factory(sidecarRuntimeProvider, onnxModelManagerProvider, decodeServiceProvider);
  }

  public static MaskInferenceService newInstance(AiSidecarRuntimeService sidecarRuntime,
      OnnxModelManager onnxModelManager, DecodeService decodeService) {
    return new MaskInferenceService(sidecarRuntime, onnxModelManager, decodeService);
  }
}
