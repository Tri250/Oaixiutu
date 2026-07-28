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
public final class SleeveFilterService_Factory implements Factory<SleeveFilterService> {
  private final Provider<ImageRepository> imageRepositoryProvider;

  public SleeveFilterService_Factory(Provider<ImageRepository> imageRepositoryProvider) {
    this.imageRepositoryProvider = imageRepositoryProvider;
  }

  @Override
  public SleeveFilterService get() {
    return newInstance(imageRepositoryProvider.get());
  }

  public static SleeveFilterService_Factory create(
      Provider<ImageRepository> imageRepositoryProvider) {
    return new SleeveFilterService_Factory(imageRepositoryProvider);
  }

  public static SleeveFilterService newInstance(ImageRepository imageRepository) {
    return new SleeveFilterService(imageRepository);
  }
}
