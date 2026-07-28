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
public final class MaskRenderService_Factory implements Factory<MaskRenderService> {
  @Override
  public MaskRenderService get() {
    return newInstance();
  }

  public static MaskRenderService_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static MaskRenderService newInstance() {
    return new MaskRenderService();
  }

  private static final class InstanceHolder {
    private static final MaskRenderService_Factory INSTANCE = new MaskRenderService_Factory();
  }
}
