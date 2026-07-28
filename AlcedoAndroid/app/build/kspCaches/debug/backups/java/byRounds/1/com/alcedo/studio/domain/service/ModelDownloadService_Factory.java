package com.alcedo.studio.domain.service;

import com.alcedo.studio.security.SecureHttpClient;
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
public final class ModelDownloadService_Factory implements Factory<ModelDownloadService> {
  private final Provider<SecureHttpClient> httpClientProvider;

  public ModelDownloadService_Factory(Provider<SecureHttpClient> httpClientProvider) {
    this.httpClientProvider = httpClientProvider;
  }

  @Override
  public ModelDownloadService get() {
    return newInstance(httpClientProvider.get());
  }

  public static ModelDownloadService_Factory create(Provider<SecureHttpClient> httpClientProvider) {
    return new ModelDownloadService_Factory(httpClientProvider);
  }

  public static ModelDownloadService newInstance(SecureHttpClient httpClient) {
    return new ModelDownloadService(httpClient);
  }
}
