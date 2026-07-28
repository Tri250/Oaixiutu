package com.alcedo.studio.security;

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
public final class TempFileManager_Factory implements Factory<TempFileManager> {
  @Override
  public TempFileManager get() {
    return newInstance();
  }

  public static TempFileManager_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static TempFileManager newInstance() {
    return new TempFileManager();
  }

  private static final class InstanceHolder {
    private static final TempFileManager_Factory INSTANCE = new TempFileManager_Factory();
  }
}
