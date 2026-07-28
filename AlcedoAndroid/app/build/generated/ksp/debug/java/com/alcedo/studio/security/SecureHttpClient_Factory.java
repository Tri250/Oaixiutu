package com.alcedo.studio.security;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class SecureHttpClient_Factory implements Factory<SecureHttpClient> {
  private final Provider<CertificatePinnerConfig> pinnerProvider;

  public SecureHttpClient_Factory(Provider<CertificatePinnerConfig> pinnerProvider) {
    this.pinnerProvider = pinnerProvider;
  }

  @Override
  public SecureHttpClient get() {
    return newInstance(pinnerProvider.get());
  }

  public static SecureHttpClient_Factory create(Provider<CertificatePinnerConfig> pinnerProvider) {
    return new SecureHttpClient_Factory(pinnerProvider);
  }

  public static SecureHttpClient newInstance(CertificatePinnerConfig pinner) {
    return new SecureHttpClient(pinner);
  }
}
