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
public final class WatermarkService_Factory implements Factory<WatermarkService> {
  @Override
  public WatermarkService get() {
    return newInstance();
  }

  public static WatermarkService_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static WatermarkService newInstance() {
    return new WatermarkService();
  }

  private static final class InstanceHolder {
    private static final WatermarkService_Factory INSTANCE = new WatermarkService_Factory();
  }
}
