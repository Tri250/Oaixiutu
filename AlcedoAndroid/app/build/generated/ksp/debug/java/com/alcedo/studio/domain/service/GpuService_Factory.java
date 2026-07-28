package com.alcedo.studio.domain.service;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class GpuService_Factory implements Factory<GpuService> {
  @Override
  public GpuService get() {
    return newInstance();
  }

  public static GpuService_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static GpuService newInstance() {
    return new GpuService();
  }

  private static final class InstanceHolder {
    private static final GpuService_Factory INSTANCE = new GpuService_Factory();
  }
}
