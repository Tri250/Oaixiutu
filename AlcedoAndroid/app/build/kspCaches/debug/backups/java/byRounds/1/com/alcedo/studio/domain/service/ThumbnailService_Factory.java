package com.alcedo.studio.domain.service;

import com.alcedo.studio.data.local.ThumbnailDiskCache;
import com.alcedo.studio.utils.MemoryGuard;
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
public final class ThumbnailService_Factory implements Factory<ThumbnailService> {
  private final Provider<ThumbnailDiskCache> diskCacheProvider;

  private final Provider<MemoryGuard> memoryGuardProvider;

  public ThumbnailService_Factory(Provider<ThumbnailDiskCache> diskCacheProvider,
      Provider<MemoryGuard> memoryGuardProvider) {
    this.diskCacheProvider = diskCacheProvider;
    this.memoryGuardProvider = memoryGuardProvider;
  }

  @Override
  public ThumbnailService get() {
    return newInstance(diskCacheProvider.get(), memoryGuardProvider.get());
  }

  public static ThumbnailService_Factory create(Provider<ThumbnailDiskCache> diskCacheProvider,
      Provider<MemoryGuard> memoryGuardProvider) {
    return new ThumbnailService_Factory(diskCacheProvider, memoryGuardProvider);
  }

  public static ThumbnailService newInstance(ThumbnailDiskCache diskCache,
      MemoryGuard memoryGuard) {
    return new ThumbnailService(diskCache, memoryGuard);
  }
}
