package com.alcedo.studio.ai;

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
public final class LlmCullingClient_Factory implements Factory<LlmCullingClient> {
  private final Provider<SecureHttpClient> httpClientProvider;

  public LlmCullingClient_Factory(Provider<SecureHttpClient> httpClientProvider) {
    this.httpClientProvider = httpClientProvider;
  }

  @Override
  public LlmCullingClient get() {
    return newInstance(httpClientProvider.get());
  }

  public static LlmCullingClient_Factory create(Provider<SecureHttpClient> httpClientProvider) {
    return new LlmCullingClient_Factory(httpClientProvider);
  }

  public static LlmCullingClient newInstance(SecureHttpClient httpClient) {
    return new LlmCullingClient(httpClient);
  }
}
