package com.alcedo.studio.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import kotlinx.coroutines.CoroutineDispatcher;

@ScopeMetadata
@QualifierMetadata("com.alcedo.studio.di.ThumbnailDispatcher")
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
public final class AppModule_ProvideThumbnailDispatcherFactory implements Factory<CoroutineDispatcher> {
  @Override
  public CoroutineDispatcher get() {
    return provideThumbnailDispatcher();
  }

  public static AppModule_ProvideThumbnailDispatcherFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static CoroutineDispatcher provideThumbnailDispatcher() {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideThumbnailDispatcher());
  }

  private static final class InstanceHolder {
    private static final AppModule_ProvideThumbnailDispatcherFactory INSTANCE = new AppModule_ProvideThumbnailDispatcherFactory();
  }
}
