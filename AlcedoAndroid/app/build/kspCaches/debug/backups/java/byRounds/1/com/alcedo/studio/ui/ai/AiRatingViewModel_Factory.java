package com.alcedo.studio.ui.ai;

import com.alcedo.studio.domain.repository.ImageRepository;
import com.alcedo.studio.domain.service.AiRatingService;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class AiRatingViewModel_Factory implements Factory<AiRatingViewModel> {
  private final Provider<AiRatingService> aiRatingServiceProvider;

  private final Provider<ImageRepository> imageRepositoryProvider;

  public AiRatingViewModel_Factory(Provider<AiRatingService> aiRatingServiceProvider,
      Provider<ImageRepository> imageRepositoryProvider) {
    this.aiRatingServiceProvider = aiRatingServiceProvider;
    this.imageRepositoryProvider = imageRepositoryProvider;
  }

  @Override
  public AiRatingViewModel get() {
    return newInstance(aiRatingServiceProvider.get(), imageRepositoryProvider.get());
  }

  public static AiRatingViewModel_Factory create(Provider<AiRatingService> aiRatingServiceProvider,
      Provider<ImageRepository> imageRepositoryProvider) {
    return new AiRatingViewModel_Factory(aiRatingServiceProvider, imageRepositoryProvider);
  }

  public static AiRatingViewModel newInstance(AiRatingService aiRatingService,
      ImageRepository imageRepository) {
    return new AiRatingViewModel(aiRatingService, imageRepository);
  }
}
