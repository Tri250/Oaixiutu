package com.alcedo.studio.domain.service;

import com.alcedo.studio.domain.repository.EditHistoryRepository;
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
public final class BatchEditService_Factory implements Factory<BatchEditService> {
  private final Provider<ImageRepository> imageRepositoryProvider;

  private final Provider<EditHistoryRepository> editHistoryRepositoryProvider;

  public BatchEditService_Factory(Provider<ImageRepository> imageRepositoryProvider,
      Provider<EditHistoryRepository> editHistoryRepositoryProvider) {
    this.imageRepositoryProvider = imageRepositoryProvider;
    this.editHistoryRepositoryProvider = editHistoryRepositoryProvider;
  }

  @Override
  public BatchEditService get() {
    return newInstance(imageRepositoryProvider.get(), editHistoryRepositoryProvider.get());
  }

  public static BatchEditService_Factory create(Provider<ImageRepository> imageRepositoryProvider,
      Provider<EditHistoryRepository> editHistoryRepositoryProvider) {
    return new BatchEditService_Factory(imageRepositoryProvider, editHistoryRepositoryProvider);
  }

  public static BatchEditService newInstance(ImageRepository imageRepository,
      EditHistoryRepository editHistoryRepository) {
    return new BatchEditService(imageRepository, editHistoryRepository);
  }
}
