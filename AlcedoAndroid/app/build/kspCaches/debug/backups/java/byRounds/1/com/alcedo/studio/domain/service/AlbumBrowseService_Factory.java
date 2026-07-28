package com.alcedo.studio.domain.service;

import com.alcedo.studio.domain.repository.ImageRepository;
import com.alcedo.studio.domain.repository.SleeveRepository;
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
public final class AlbumBrowseService_Factory implements Factory<AlbumBrowseService> {
  private final Provider<ImageRepository> imageRepositoryProvider;

  private final Provider<SleeveRepository> sleeveRepositoryProvider;

  public AlbumBrowseService_Factory(Provider<ImageRepository> imageRepositoryProvider,
      Provider<SleeveRepository> sleeveRepositoryProvider) {
    this.imageRepositoryProvider = imageRepositoryProvider;
    this.sleeveRepositoryProvider = sleeveRepositoryProvider;
  }

  @Override
  public AlbumBrowseService get() {
    return newInstance(imageRepositoryProvider.get(), sleeveRepositoryProvider.get());
  }

  public static AlbumBrowseService_Factory create(Provider<ImageRepository> imageRepositoryProvider,
      Provider<SleeveRepository> sleeveRepositoryProvider) {
    return new AlbumBrowseService_Factory(imageRepositoryProvider, sleeveRepositoryProvider);
  }

  public static AlbumBrowseService newInstance(ImageRepository imageRepository,
      SleeveRepository sleeveRepository) {
    return new AlbumBrowseService(imageRepository, sleeveRepository);
  }
}
