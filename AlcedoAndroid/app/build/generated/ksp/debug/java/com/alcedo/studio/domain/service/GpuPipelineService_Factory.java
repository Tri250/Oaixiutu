package com.alcedo.studio.domain.service;

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
public final class GpuPipelineService_Factory implements Factory<GpuPipelineService> {
  private final Provider<GpuService> gpuServiceProvider;

  public GpuPipelineService_Factory(Provider<GpuService> gpuServiceProvider) {
    this.gpuServiceProvider = gpuServiceProvider;
  }

  @Override
  public GpuPipelineService get() {
    return newInstance(gpuServiceProvider.get());
  }

  public static GpuPipelineService_Factory create(Provider<GpuService> gpuServiceProvider) {
    return new GpuPipelineService_Factory(gpuServiceProvider);
  }

  public static GpuPipelineService newInstance(GpuService gpuService) {
    return new GpuPipelineService(gpuService);
  }
}
