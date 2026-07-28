package com.alcedo.studio.domain.service;

import com.alcedo.studio.ai.OnnxModelManager;
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
public final class ClipInferenceEngine_Factory implements Factory<ClipInferenceEngine> {
  private final Provider<OnnxModelManager> onnxModelManagerProvider;

  private final Provider<AiSidecarRuntimeService> sidecarRuntimeProvider;

  private final Provider<AiEmbeddingDao> embeddingDaoProvider;

  private final Provider<ClipTokenizer> tokenizerProvider;

  public ClipInferenceEngine_Factory(Provider<OnnxModelManager> onnxModelManagerProvider,
      Provider<AiSidecarRuntimeService> sidecarRuntimeProvider,
      Provider<AiEmbeddingDao> embeddingDaoProvider, Provider<ClipTokenizer> tokenizerProvider) {
    this.onnxModelManagerProvider = onnxModelManagerProvider;
    this.sidecarRuntimeProvider = sidecarRuntimeProvider;
    this.embeddingDaoProvider = embeddingDaoProvider;
    this.tokenizerProvider = tokenizerProvider;
  }

  @Override
  public ClipInferenceEngine get() {
    return newInstance(onnxModelManagerProvider.get(), sidecarRuntimeProvider.get(), embeddingDaoProvider.get(), tokenizerProvider.get());
  }

  public static ClipInferenceEngine_Factory create(
      Provider<OnnxModelManager> onnxModelManagerProvider,
      Provider<AiSidecarRuntimeService> sidecarRuntimeProvider,
      Provider<AiEmbeddingDao> embeddingDaoProvider, Provider<ClipTokenizer> tokenizerProvider) {
    return new ClipInferenceEngine_Factory(onnxModelManagerProvider, sidecarRuntimeProvider, embeddingDaoProvider, tokenizerProvider);
  }

  public static ClipInferenceEngine newInstance(OnnxModelManager onnxModelManager,
      AiSidecarRuntimeService sidecarRuntime, AiEmbeddingDao embeddingDao,
      ClipTokenizer tokenizer) {
    return new ClipInferenceEngine(onnxModelManager, sidecarRuntime, embeddingDao, tokenizer);
  }
}
