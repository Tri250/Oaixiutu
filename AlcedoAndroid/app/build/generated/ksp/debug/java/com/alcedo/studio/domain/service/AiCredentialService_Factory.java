package com.alcedo.studio.domain.service;

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
public final class AiCredentialService_Factory implements Factory<AiCredentialService> {
  private final Provider<AiCredentialStore> storeProvider;

  public AiCredentialService_Factory(Provider<AiCredentialStore> storeProvider) {
    this.storeProvider = storeProvider;
  }

  @Override
  public AiCredentialService get() {
    return newInstance(storeProvider.get());
  }

  public static AiCredentialService_Factory create(Provider<AiCredentialStore> storeProvider) {
    return new AiCredentialService_Factory(storeProvider);
  }

  public static AiCredentialService newInstance(AiCredentialStore store) {
    return new AiCredentialService(store);
  }
}
