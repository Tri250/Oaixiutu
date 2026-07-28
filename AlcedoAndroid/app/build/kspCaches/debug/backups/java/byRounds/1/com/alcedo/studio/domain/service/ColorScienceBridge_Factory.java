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
public final class ColorScienceBridge_Factory implements Factory<ColorScienceBridge> {
  @Override
  public ColorScienceBridge get() {
    return newInstance();
  }

  public static ColorScienceBridge_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static ColorScienceBridge newInstance() {
    return new ColorScienceBridge();
  }

  private static final class InstanceHolder {
    private static final ColorScienceBridge_Factory INSTANCE = new ColorScienceBridge_Factory();
  }
}
