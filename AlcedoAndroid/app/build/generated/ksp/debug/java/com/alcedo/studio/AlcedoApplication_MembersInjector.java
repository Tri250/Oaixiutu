package com.alcedo.studio;

import com.alcedo.studio.data.local.SleeveDatabase;
import com.alcedo.studio.domain.service.GpuService;
import com.alcedo.studio.domain.service.PresetService;
import com.alcedo.studio.domain.service.SleeveService;
import com.alcedo.studio.privacy.PrivacyManager;
import com.alcedo.studio.security.TempFileManager;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class AlcedoApplication_MembersInjector implements MembersInjector<AlcedoApplication> {
  private final Provider<SleeveService> sleeveServiceProvider;

  private final Provider<PresetService> presetServiceProvider;

  private final Provider<GpuService> gpuServiceProvider;

  private final Provider<TempFileManager> tempFileManagerProvider;

  private final Provider<SleeveDatabase> sleeveDatabaseProvider;

  private final Provider<PrivacyManager> privacyManagerProvider;

  public AlcedoApplication_MembersInjector(Provider<SleeveService> sleeveServiceProvider,
      Provider<PresetService> presetServiceProvider, Provider<GpuService> gpuServiceProvider,
      Provider<TempFileManager> tempFileManagerProvider,
      Provider<SleeveDatabase> sleeveDatabaseProvider,
      Provider<PrivacyManager> privacyManagerProvider) {
    this.sleeveServiceProvider = sleeveServiceProvider;
    this.presetServiceProvider = presetServiceProvider;
    this.gpuServiceProvider = gpuServiceProvider;
    this.tempFileManagerProvider = tempFileManagerProvider;
    this.sleeveDatabaseProvider = sleeveDatabaseProvider;
    this.privacyManagerProvider = privacyManagerProvider;
  }

  public static MembersInjector<AlcedoApplication> create(
      Provider<SleeveService> sleeveServiceProvider, Provider<PresetService> presetServiceProvider,
      Provider<GpuService> gpuServiceProvider, Provider<TempFileManager> tempFileManagerProvider,
      Provider<SleeveDatabase> sleeveDatabaseProvider,
      Provider<PrivacyManager> privacyManagerProvider) {
    return new AlcedoApplication_MembersInjector(sleeveServiceProvider, presetServiceProvider, gpuServiceProvider, tempFileManagerProvider, sleeveDatabaseProvider, privacyManagerProvider);
  }

  @Override
  public void injectMembers(AlcedoApplication instance) {
    injectSleeveService(instance, sleeveServiceProvider.get());
    injectPresetService(instance, presetServiceProvider.get());
    injectGpuService(instance, gpuServiceProvider.get());
    injectTempFileManager(instance, tempFileManagerProvider.get());
    injectSleeveDatabase(instance, sleeveDatabaseProvider.get());
    injectPrivacyManager(instance, privacyManagerProvider.get());
  }

  @InjectedFieldSignature("com.alcedo.studio.AlcedoApplication.sleeveService")
  public static void injectSleeveService(AlcedoApplication instance, SleeveService sleeveService) {
    instance.sleeveService = sleeveService;
  }

  @InjectedFieldSignature("com.alcedo.studio.AlcedoApplication.presetService")
  public static void injectPresetService(AlcedoApplication instance, PresetService presetService) {
    instance.presetService = presetService;
  }

  @InjectedFieldSignature("com.alcedo.studio.AlcedoApplication.gpuService")
  public static void injectGpuService(AlcedoApplication instance, GpuService gpuService) {
    instance.gpuService = gpuService;
  }

  @InjectedFieldSignature("com.alcedo.studio.AlcedoApplication.tempFileManager")
  public static void injectTempFileManager(AlcedoApplication instance,
      TempFileManager tempFileManager) {
    instance.tempFileManager = tempFileManager;
  }

  @InjectedFieldSignature("com.alcedo.studio.AlcedoApplication.sleeveDatabase")
  public static void injectSleeveDatabase(AlcedoApplication instance,
      SleeveDatabase sleeveDatabase) {
    instance.sleeveDatabase = sleeveDatabase;
  }

  @InjectedFieldSignature("com.alcedo.studio.AlcedoApplication.privacyManager")
  public static void injectPrivacyManager(AlcedoApplication instance,
      PrivacyManager privacyManager) {
    instance.privacyManager = privacyManager;
  }
}
