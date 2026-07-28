package com.alcedo.studio.privacy;

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
public final class PrivacyManager_Factory implements Factory<PrivacyManager> {
  private final Provider<Context> contextProvider;

  public PrivacyManager_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public PrivacyManager get() {
    return newInstance(contextProvider.get());
  }

  public static PrivacyManager_Factory create(Provider<Context> contextProvider) {
    return new PrivacyManager_Factory(contextProvider);
  }

  public static PrivacyManager newInstance(Context context) {
    return new PrivacyManager(context);
  }
}
