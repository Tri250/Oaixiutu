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
public final class ImportService_Factory implements Factory<ImportService> {
  private final Provider<ImageRepository> imageRepositoryProvider;

  private final Provider<SleeveRepository> sleeveRepositoryProvider;

  private final Provider<DecodeService> decodeServiceProvider;

  private final Provider<ThumbnailService> thumbnailServiceProvider;

  public ImportService_Factory(Provider<ImageRepository> imageRepositoryProvider,
      Provider<SleeveRepository> sleeveRepositoryProvider,
      Provider<DecodeService> decodeServiceProvider,
      Provider<ThumbnailService> thumbnailServiceProvider) {
    this.imageRepositoryProvider = imageRepositoryProvider;
    this.sleeveRepositoryProvider = sleeveRepositoryProvider;
    this.decodeServiceProvider = decodeServiceProvider;
    this.thumbnailServiceProvider = thumbnailServiceProvider;
  }

  @Override
  public ImportService get() {
    return newInstance(imageRepositoryProvider.get(), sleeveRepositoryProvider.get(), decodeServiceProvider.get(), thumbnailServiceProvider.get());
  }

  public static ImportService_Factory create(Provider<ImageRepository> imageRepositoryProvider,
      Provider<SleeveRepository> sleeveRepositoryProvider,
      Provider<DecodeService> decodeServiceProvider,
      Provider<ThumbnailService> thumbnailServiceProvider) {
    return new ImportService_Factory(imageRepositoryProvider, sleeveRepositoryProvider, decodeServiceProvider, thumbnailServiceProvider);
  }

  public static ImportService newInstance(ImageRepository imageRepository,
      SleeveRepository sleeveRepository, DecodeService decodeService,
      ThumbnailService thumbnailService) {
    return new ImportService(imageRepository, sleeveRepository, decodeService, thumbnailService);
  }
}
