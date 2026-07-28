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
public final class ProjectPackageService_Factory implements Factory<ProjectPackageService> {
  @Override
  public ProjectPackageService get() {
    return newInstance();
  }

  public static ProjectPackageService_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static ProjectPackageService newInstance() {
    return new ProjectPackageService();
  }

  private static final class InstanceHolder {
    private static final ProjectPackageService_Factory INSTANCE = new ProjectPackageService_Factory();
  }
}
