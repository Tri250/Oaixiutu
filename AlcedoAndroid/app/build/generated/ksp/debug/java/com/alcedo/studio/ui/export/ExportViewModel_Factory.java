package com.alcedo.studio.ui.export;

import com.alcedo.studio.domain.repository.ImageRepository;
import com.alcedo.studio.domain.service.BackgroundTaskService;
import com.alcedo.studio.domain.service.ExportService;
import com.alcedo.studio.domain.service.PipelineService;
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
public final class ExportViewModel_Factory implements Factory<ExportViewModel> {
  private final Provider<ExportService> exportServiceProvider;

  private final Provider<PipelineService> pipelineServiceProvider;

  private final Provider<ImageRepository> imageRepositoryProvider;

  private final Provider<BackgroundTaskService> taskServiceProvider;

  public ExportViewModel_Factory(Provider<ExportService> exportServiceProvider,
      Provider<PipelineService> pipelineServiceProvider,
      Provider<ImageRepository> imageRepositoryProvider,
      Provider<BackgroundTaskService> taskServiceProvider) {
    this.exportServiceProvider = exportServiceProvider;
    this.pipelineServiceProvider = pipelineServiceProvider;
    this.imageRepositoryProvider = imageRepositoryProvider;
    this.taskServiceProvider = taskServiceProvider;
  }

  @Override
  public ExportViewModel get() {
    return newInstance(exportServiceProvider.get(), pipelineServiceProvider.get(), imageRepositoryProvider.get(), taskServiceProvider.get());
  }

  public static ExportViewModel_Factory create(Provider<ExportService> exportServiceProvider,
      Provider<PipelineService> pipelineServiceProvider,
      Provider<ImageRepository> imageRepositoryProvider,
      Provider<BackgroundTaskService> taskServiceProvider) {
    return new ExportViewModel_Factory(exportServiceProvider, pipelineServiceProvider, imageRepositoryProvider, taskServiceProvider);
  }

  public static ExportViewModel newInstance(ExportService exportService,
      PipelineService pipelineService, ImageRepository imageRepository,
      BackgroundTaskService taskService) {
    return new ExportViewModel(exportService, pipelineService, imageRepository, taskService);
  }
}
