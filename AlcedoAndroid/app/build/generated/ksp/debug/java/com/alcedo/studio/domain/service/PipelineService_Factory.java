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
public final class PipelineService_Factory implements Factory<PipelineService> {
  private final Provider<DecodeService> decodeServiceProvider;

  public PipelineService_Factory(Provider<DecodeService> decodeServiceProvider) {
    this.decodeServiceProvider = decodeServiceProvider;
  }

  @Override
  public PipelineService get() {
    return newInstance(decodeServiceProvider.get());
  }

  public static PipelineService_Factory create(Provider<DecodeService> decodeServiceProvider) {
    return new PipelineService_Factory(decodeServiceProvider);
  }

  public static PipelineService newInstance(DecodeService decodeService) {
    return new PipelineService(decodeService);
  }
}
