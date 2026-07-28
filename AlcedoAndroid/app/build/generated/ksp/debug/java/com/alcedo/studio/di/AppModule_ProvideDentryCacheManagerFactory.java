package com.alcedo.studio.di;

import com.alcedo.studio.data.local.DentryCacheManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class AppModule_ProvideDentryCacheManagerFactory implements Factory<DentryCacheManager> {
  @Override
  public DentryCacheManager get() {
    return provideDentryCacheManager();
  }

  public static AppModule_ProvideDentryCacheManagerFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static DentryCacheManager provideDentryCacheManager() {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideDentryCacheManager());
  }

  private static final class InstanceHolder {
    private static final AppModule_ProvideDentryCacheManagerFactory INSTANCE = new AppModule_ProvideDentryCacheManagerFactory();
  }
}
