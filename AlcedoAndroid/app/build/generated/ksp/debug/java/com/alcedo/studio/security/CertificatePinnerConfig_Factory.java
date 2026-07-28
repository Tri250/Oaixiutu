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
public final class CertificatePinnerConfig_Factory implements Factory<CertificatePinnerConfig> {
  @Override
  public CertificatePinnerConfig get() {
    return newInstance();
  }

  public static CertificatePinnerConfig_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static CertificatePinnerConfig newInstance() {
    return new CertificatePinnerConfig();
  }

  private static final class InstanceHolder {
    private static final CertificatePinnerConfig_Factory INSTANCE = new CertificatePinnerConfig_Factory();
  }
}
