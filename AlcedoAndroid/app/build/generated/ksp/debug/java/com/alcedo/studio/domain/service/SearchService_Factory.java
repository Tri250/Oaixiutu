package com.alcedo.studio.domain.service;

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
public final class SearchService_Factory implements Factory<SearchService> {
  private final Provider<SearchQueryClassifier> classifierProvider;

  private final Provider<ClipInferenceEngine> clipEngineProvider;

  private final Provider<ImageRepository> imageRepositoryProvider;

  private final Provider<AiSidecarRuntimeService> sidecarRuntimeProvider;

  public SearchService_Factory(Provider<SearchQueryClassifier> classifierProvider,
      Provider<ClipInferenceEngine> clipEngineProvider,
      Provider<ImageRepository> imageRepositoryProvider,
      Provider<AiSidecarRuntimeService> sidecarRuntimeProvider) {
    this.classifierProvider = classifierProvider;
    this.clipEngineProvider = clipEngineProvider;
    this.imageRepositoryProvider = imageRepositoryProvider;
    this.sidecarRuntimeProvider = sidecarRuntimeProvider;
  }

  @Override
  public SearchService get() {
    return newInstance(classifierProvider.get(), clipEngineProvider.get(), imageRepositoryProvider.get(), sidecarRuntimeProvider.get());
  }

  public static SearchService_Factory create(Provider<SearchQueryClassifier> classifierProvider,
      Provider<ClipInferenceEngine> clipEngineProvider,
      Provider<ImageRepository> imageRepositoryProvider,
      Provider<AiSidecarRuntimeService> sidecarRuntimeProvider) {
    return new SearchService_Factory(classifierProvider, clipEngineProvider, imageRepositoryProvider, sidecarRuntimeProvider);
  }

  public static SearchService newInstance(SearchQueryClassifier classifier,
      ClipInferenceEngine clipEngine, ImageRepository imageRepository,
      AiSidecarRuntimeService sidecarRuntime) {
    return new SearchService(classifier, clipEngine, imageRepository, sidecarRuntime);
  }
}
