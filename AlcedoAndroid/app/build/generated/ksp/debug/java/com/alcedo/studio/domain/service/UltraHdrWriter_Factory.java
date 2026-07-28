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
public final class UltraHdrWriter_Factory implements Factory<UltraHdrWriter> {
  @Override
  public UltraHdrWriter get() {
    return newInstance();
  }

  public static UltraHdrWriter_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static UltraHdrWriter newInstance() {
    return new UltraHdrWriter();
  }

  private static final class InstanceHolder {
    private static final UltraHdrWriter_Factory INSTANCE = new UltraHdrWriter_Factory();
  }
}
