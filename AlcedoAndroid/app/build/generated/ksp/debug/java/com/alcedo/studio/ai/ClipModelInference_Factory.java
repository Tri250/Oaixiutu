package com.alcedo.studio.ai;

import com.alcedo.studio.domain.service.AiSidecarRuntimeService;
import com.alcedo.studio.domain.service.ClipTokenizer;
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
public final class ClipModelInference_Factory implements Factory<ClipModelInference> {
  private final Provider<OnnxModelManager> onnxModelManagerProvider;

  private final Provider<AiSidecarRuntimeService> sidecarRuntimeProvider;

  private final Provider<ClipTokenizer> tokenizerProvider;

  public ClipModelInference_Factory(Provider<OnnxModelManager> onnxModelManagerProvider,
      Provider<AiSidecarRuntimeService> sidecarRuntimeProvider,
      Provider<ClipTokenizer> tokenizerProvider) {
    this.onnxModelManagerProvider = onnxModelManagerProvider;
    this.sidecarRuntimeProvider = sidecarRuntimeProvider;
    this.tokenizerProvider = tokenizerProvider;
  }

  @Override
  public ClipModelInference get() {
    return newInstance(onnxModelManagerProvider.get(), sidecarRuntimeProvider.get(), tokenizerProvider.get());
  }

  public static ClipModelInference_Factory create(
      Provider<OnnxModelManager> onnxModelManagerProvider,
      Provider<AiSidecarRuntimeService> sidecarRuntimeProvider,
      Provider<ClipTokenizer> tokenizerProvider) {
    return new ClipModelInference_Factory(onnxModelManagerProvider, sidecarRuntimeProvider, tokenizerProvider);
  }

  public static ClipModelInference newInstance(OnnxModelManager onnxModelManager,
      AiSidecarRuntimeService sidecarRuntime, ClipTokenizer tokenizer) {
    return new ClipModelInference(onnxModelManager, sidecarRuntime, tokenizer);
  }
}
