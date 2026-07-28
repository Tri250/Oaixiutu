package com.alcedo.studio.domain.service;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class AiCredentialStore_Factory implements Factory<AiCredentialStore> {
  private final Provider<Context> contextProvider;

  public AiCredentialStore_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public AiCredentialStore get() {
    return newInstance(contextProvider.get());
  }

  public static AiCredentialStore_Factory create(Provider<Context> contextProvider) {
    return new AiCredentialStore_Factory(contextProvider);
  }

  public static AiCredentialStore newInstance(Context context) {
    return new AiCredentialStore(context);
  }
}
