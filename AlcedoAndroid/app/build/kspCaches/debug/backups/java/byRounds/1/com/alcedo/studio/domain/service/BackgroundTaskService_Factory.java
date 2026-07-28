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
public final class BackgroundTaskService_Factory implements Factory<BackgroundTaskService> {
  @Override
  public BackgroundTaskService get() {
    return newInstance();
  }

  public static BackgroundTaskService_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static BackgroundTaskService newInstance() {
    return new BackgroundTaskService();
  }

  private static final class InstanceHolder {
    private static final BackgroundTaskService_Factory INSTANCE = new BackgroundTaskService_Factory();
  }
}
