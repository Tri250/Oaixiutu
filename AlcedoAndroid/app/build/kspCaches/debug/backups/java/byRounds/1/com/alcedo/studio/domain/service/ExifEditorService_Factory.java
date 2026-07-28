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
public final class ExifEditorService_Factory implements Factory<ExifEditorService> {
  @Override
  public ExifEditorService get() {
    return newInstance();
  }

  public static ExifEditorService_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static ExifEditorService newInstance() {
    return new ExifEditorService();
  }

  private static final class InstanceHolder {
    private static final ExifEditorService_Factory INSTANCE = new ExifEditorService_Factory();
  }
}
