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
public final class HistoryMgmtService_Factory implements Factory<HistoryMgmtService> {
  private final Provider<EditHistoryRepository> editHistoryRepositoryProvider;

  private final Provider<ImageRepository> imageRepositoryProvider;

  public HistoryMgmtService_Factory(Provider<EditHistoryRepository> editHistoryRepositoryProvider,
      Provider<ImageRepository> imageRepositoryProvider) {
    this.editHistoryRepositoryProvider = editHistoryRepositoryProvider;
    this.imageRepositoryProvider = imageRepositoryProvider;
  }

  @Override
  public HistoryMgmtService get() {
    return newInstance(editHistoryRepositoryProvider.get(), imageRepositoryProvider.get());
  }

  public static HistoryMgmtService_Factory create(
      Provider<EditHistoryRepository> editHistoryRepositoryProvider,
      Provider<ImageRepository> imageRepositoryProvider) {
    return new HistoryMgmtService_Factory(editHistoryRepositoryProvider, imageRepositoryProvider);
  }

  public static HistoryMgmtService newInstance(EditHistoryRepository editHistoryRepository,
      ImageRepository imageRepository) {
    return new HistoryMgmtService(editHistoryRepository, imageRepository);
  }
}
