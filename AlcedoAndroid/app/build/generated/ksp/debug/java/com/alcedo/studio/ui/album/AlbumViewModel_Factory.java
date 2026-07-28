package com.alcedo.studio.ui.album;

import com.alcedo.studio.domain.repository.ImageRepository;
import com.alcedo.studio.domain.service.AiRatingService;
import com.alcedo.studio.domain.service.AlbumBrowseService;
import com.alcedo.studio.domain.service.BackgroundTaskService;
import com.alcedo.studio.domain.service.ImportService;
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
public final class AlbumViewModel_Factory implements Factory<AlbumViewModel> {
  private final Provider<AlbumBrowseService> albumServiceProvider;

  private final Provider<ImportService> importServiceProvider;

  private final Provider<ImageRepository> imageRepositoryProvider;

  private final Provider<SearchService> searchServiceProvider;

  private final Provider<AiRatingService> aiRatingServiceProvider;

  private final Provider<BackgroundTaskService> taskServiceProvider;

  public AlbumViewModel_Factory(Provider<AlbumBrowseService> albumServiceProvider,
      Provider<ImportService> importServiceProvider,
      Provider<ImageRepository> imageRepositoryProvider,
      Provider<SearchService> searchServiceProvider,
      Provider<AiRatingService> aiRatingServiceProvider,
      Provider<BackgroundTaskService> taskServiceProvider) {
    this.albumServiceProvider = albumServiceProvider;
    this.importServiceProvider = importServiceProvider;
    this.imageRepositoryProvider = imageRepositoryProvider;
    this.searchServiceProvider = searchServiceProvider;
    this.aiRatingServiceProvider = aiRatingServiceProvider;
    this.taskServiceProvider = taskServiceProvider;
  }

  @Override
  public AlbumViewModel get() {
    return newInstance(albumServiceProvider.get(), importServiceProvider.get(), imageRepositoryProvider.get(), searchServiceProvider.get(), aiRatingServiceProvider.get(), taskServiceProvider.get());
  }

  public static AlbumViewModel_Factory create(Provider<AlbumBrowseService> albumServiceProvider,
      Provider<ImportService> importServiceProvider,
      Provider<ImageRepository> imageRepositoryProvider,
      Provider<SearchService> searchServiceProvider,
      Provider<AiRatingService> aiRatingServiceProvider,
      Provider<BackgroundTaskService> taskServiceProvider) {
    return new AlbumViewModel_Factory(albumServiceProvider, importServiceProvider, imageRepositoryProvider, searchServiceProvider, aiRatingServiceProvider, taskServiceProvider);
  }

  public static AlbumViewModel newInstance(AlbumBrowseService albumService,
      ImportService importService, ImageRepository imageRepository, SearchService searchService,
      AiRatingService aiRatingService, BackgroundTaskService taskService) {
    return new AlbumViewModel(albumService, importService, imageRepository, searchService, aiRatingService, taskService);
  }
}
