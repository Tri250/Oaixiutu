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
public final class NativeSecurityChecker_Factory implements Factory<NativeSecurityChecker> {
  @Override
  public NativeSecurityChecker get() {
    return newInstance();
  }

  public static NativeSecurityChecker_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static NativeSecurityChecker newInstance() {
    return new NativeSecurityChecker();
  }

  private static final class InstanceHolder {
    private static final NativeSecurityChecker_Factory INSTANCE = new NativeSecurityChecker_Factory();
  }
}
