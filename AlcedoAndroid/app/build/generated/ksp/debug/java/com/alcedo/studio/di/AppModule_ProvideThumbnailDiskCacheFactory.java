package com.alcedo.studio.di;

import android.content.Context;
import com.alcedo.studio.data.local.ThumbnailDiskCache;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class AppModule_ProvideThumbnailDiskCacheFactory implements Factory<ThumbnailDiskCache> {
  private final Provider<Context> contextProvider;

  public AppModule_ProvideThumbnailDiskCacheFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public ThumbnailDiskCache get() {
    return provideThumbnailDiskCache(contextProvider.get());
  }

  public static AppModule_ProvideThumbnailDiskCacheFactory create(
      Provider<Context> contextProvider) {
    return new AppModule_ProvideThumbnailDiskCacheFactory(contextProvider);
  }

  public static ThumbnailDiskCache provideThumbnailDiskCache(Context context) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideThumbnailDiskCache(context));
  }
}
