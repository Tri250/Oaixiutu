package com.alcedo.studio.ui.settings;

import com.alcedo.studio.domain.service.GpuService;
import com.alcedo.studio.domain.service.PresetService;
import com.alcedo.studio.privacy.PrivacyManager;
import com.alcedo.studio.security.TempFileManager;
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
public final class SettingsViewModel_Factory implements Factory<SettingsViewModel> {
  private final Provider<PrivacyManager> privacyManagerProvider;

  private final Provider<TempFileManager> tempFileManagerProvider;

  private final Provider<GpuService> gpuServiceProvider;

  private final Provider<PresetService> presetServiceProvider;

  public SettingsViewModel_Factory(Provider<PrivacyManager> privacyManagerProvider,
      Provider<TempFileManager> tempFileManagerProvider, Provider<GpuService> gpuServiceProvider,
      Provider<PresetService> presetServiceProvider) {
    this.privacyManagerProvider = privacyManagerProvider;
    this.tempFileManagerProvider = tempFileManagerProvider;
    this.gpuServiceProvider = gpuServiceProvider;
    this.presetServiceProvider = presetServiceProvider;
  }

  @Override
  public SettingsViewModel get() {
    return newInstance(privacyManagerProvider.get(), tempFileManagerProvider.get(), gpuServiceProvider.get(), presetServiceProvider.get());
  }

  public static SettingsViewModel_Factory create(Provider<PrivacyManager> privacyManagerProvider,
      Provider<TempFileManager> tempFileManagerProvider, Provider<GpuService> gpuServiceProvider,
      Provider<PresetService> presetServiceProvider) {
    return new SettingsViewModel_Factory(privacyManagerProvider, tempFileManagerProvider, gpuServiceProvider, presetServiceProvider);
  }

  public static SettingsViewModel newInstance(PrivacyManager privacyManager,
      TempFileManager tempFileManager, GpuService gpuService, PresetService presetService) {
    return new SettingsViewModel(privacyManager, tempFileManager, gpuService, presetService);
  }
}
