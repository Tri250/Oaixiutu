package com.alcedo.studio.ui.editor;

import androidx.lifecycle.SavedStateHandle;
import com.alcedo.studio.domain.repository.ImageRepository;
import com.alcedo.studio.domain.service.ExifEditorService;
import com.alcedo.studio.domain.service.HistoryMgmtService;
import com.alcedo.studio.domain.service.MaskService;
import com.alcedo.studio.domain.service.PipelineService;
import com.alcedo.studio.domain.service.PresetService;
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
public final class EditorViewModel_Factory implements Factory<EditorViewModel> {
  private final Provider<PipelineService> pipelineServiceProvider;

  private final Provider<HistoryMgmtService> historyServiceProvider;

  private final Provider<PresetService> presetServiceProvider;

  private final Provider<MaskService> maskServiceProvider;

  private final Provider<ExifEditorService> exifServiceProvider;

  private final Provider<ImageRepository> imageRepositoryProvider;

  private final Provider<SavedStateHandle> savedStateHandleProvider;

  public EditorViewModel_Factory(Provider<PipelineService> pipelineServiceProvider,
      Provider<HistoryMgmtService> historyServiceProvider,
      Provider<PresetService> presetServiceProvider, Provider<MaskService> maskServiceProvider,
      Provider<ExifEditorService> exifServiceProvider,
      Provider<ImageRepository> imageRepositoryProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    this.pipelineServiceProvider = pipelineServiceProvider;
    this.historyServiceProvider = historyServiceProvider;
    this.presetServiceProvider = presetServiceProvider;
    this.maskServiceProvider = maskServiceProvider;
    this.exifServiceProvider = exifServiceProvider;
    this.imageRepositoryProvider = imageRepositoryProvider;
    this.savedStateHandleProvider = savedStateHandleProvider;
  }

  @Override
  public EditorViewModel get() {
    return newInstance(pipelineServiceProvider.get(), historyServiceProvider.get(), presetServiceProvider.get(), maskServiceProvider.get(), exifServiceProvider.get(), imageRepositoryProvider.get(), savedStateHandleProvider.get());
  }

  public static EditorViewModel_Factory create(Provider<PipelineService> pipelineServiceProvider,
      Provider<HistoryMgmtService> historyServiceProvider,
      Provider<PresetService> presetServiceProvider, Provider<MaskService> maskServiceProvider,
      Provider<ExifEditorService> exifServiceProvider,
      Provider<ImageRepository> imageRepositoryProvider,
      Provider<SavedStateHandle> savedStateHandleProvider) {
    return new EditorViewModel_Factory(pipelineServiceProvider, historyServiceProvider, presetServiceProvider, maskServiceProvider, exifServiceProvider, imageRepositoryProvider, savedStateHandleProvider);
  }

  public static EditorViewModel newInstance(PipelineService pipelineService,
      HistoryMgmtService historyService, PresetService presetService, MaskService maskService,
      ExifEditorService exifService, ImageRepository imageRepository,
      SavedStateHandle savedStateHandle) {
    return new EditorViewModel(pipelineService, historyService, presetService, maskService, exifService, imageRepository, savedStateHandle);
  }
}
