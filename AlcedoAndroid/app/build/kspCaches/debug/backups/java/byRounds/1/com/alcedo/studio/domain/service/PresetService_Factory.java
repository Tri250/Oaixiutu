package com.alcedo.studio.domain.service;

import com.alcedo.studio.data.dao.PipelinePresetDao;
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
public final class PresetService_Factory implements Factory<PresetService> {
  private final Provider<PipelinePresetDao> presetDaoProvider;

  public PresetService_Factory(Provider<PipelinePresetDao> presetDaoProvider) {
    this.presetDaoProvider = presetDaoProvider;
  }

  @Override
  public PresetService get() {
    return newInstance(presetDaoProvider.get());
  }

  public static PresetService_Factory create(Provider<PipelinePresetDao> presetDaoProvider) {
    return new PresetService_Factory(presetDaoProvider);
  }

  public static PresetService newInstance(PipelinePresetDao presetDao) {
    return new PresetService(presetDao);
  }
}
