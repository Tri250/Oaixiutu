package com.alcedo.studio.ui.ai;

import com.alcedo.studio.domain.repository.ImageRepository;
import com.alcedo.studio.domain.service.SearchService;
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
public final class AiSearchViewModel_Factory implements Factory<AiSearchViewModel> {
  private final Provider<SearchService> searchServiceProvider;

  private final Provider<ImageRepository> imageRepositoryProvider;

  public AiSearchViewModel_Factory(Provider<SearchService> searchServiceProvider,
      Provider<ImageRepository> imageRepositoryProvider) {
    this.searchServiceProvider = searchServiceProvider;
    this.imageRepositoryProvider = imageRepositoryProvider;
  }

  @Override
  public AiSearchViewModel get() {
    return newInstance(searchServiceProvider.get(), imageRepositoryProvider.get());
  }

  public static AiSearchViewModel_Factory create(Provider<SearchService> searchServiceProvider,
      Provider<ImageRepository> imageRepositoryProvider) {
    return new AiSearchViewModel_Factory(searchServiceProvider, imageRepositoryProvider);
  }

  public static AiSearchViewModel newInstance(SearchService searchService,
      ImageRepository imageRepository) {
    return new AiSearchViewModel(searchService, imageRepository);
  }
}
