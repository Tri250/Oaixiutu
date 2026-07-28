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
public final class MaskService_Factory implements Factory<MaskService> {
  @Override
  public MaskService get() {
    return newInstance();
  }

  public static MaskService_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static MaskService newInstance() {
    return new MaskService();
  }

  private static final class InstanceHolder {
    private static final MaskService_Factory INSTANCE = new MaskService_Factory();
  }
}
