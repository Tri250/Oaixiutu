package com.alcedo.studio.domain.service;

import com.alcedo.studio.data.local.DentryCacheManager;
import com.alcedo.studio.domain.repository.SleeveRepository;
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
public final class SleeveService_Factory implements Factory<SleeveService> {
  private final Provider<SleeveRepository> sleeveRepositoryProvider;

  private final Provider<DentryCacheManager> dentryCacheProvider;

  public SleeveService_Factory(Provider<SleeveRepository> sleeveRepositoryProvider,
      Provider<DentryCacheManager> dentryCacheProvider) {
    this.sleeveRepositoryProvider = sleeveRepositoryProvider;
    this.dentryCacheProvider = dentryCacheProvider;
  }

  @Override
  public SleeveService get() {
    return newInstance(sleeveRepositoryProvider.get(), dentryCacheProvider.get());
  }

  public static SleeveService_Factory create(Provider<SleeveRepository> sleeveRepositoryProvider,
      Provider<DentryCacheManager> dentryCacheProvider) {
    return new SleeveService_Factory(sleeveRepositoryProvider, dentryCacheProvider);
  }

  public static SleeveService newInstance(SleeveRepository sleeveRepository,
      DentryCacheManager dentryCache) {
    return new SleeveService(sleeveRepository, dentryCache);
  }
}
