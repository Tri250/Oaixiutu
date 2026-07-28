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
public final class ClipTokenizer_Factory implements Factory<ClipTokenizer> {
  @Override
  public ClipTokenizer get() {
    return newInstance();
  }

  public static ClipTokenizer_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static ClipTokenizer newInstance() {
    return new ClipTokenizer();
  }

  private static final class InstanceHolder {
    private static final ClipTokenizer_Factory INSTANCE = new ClipTokenizer_Factory();
  }
}
