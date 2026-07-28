package com.alcedo.studio.domain.service;

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
public final class DecodeService_Factory implements Factory<DecodeService> {
  private final Provider<MemoryGuard> memoryGuardProvider;

  public DecodeService_Factory(Provider<MemoryGuard> memoryGuardProvider) {
    this.memoryGuardProvider = memoryGuardProvider;
  }

  @Override
  public DecodeService get() {
    return newInstance(memoryGuardProvider.get());
  }

  public static DecodeService_Factory create(Provider<MemoryGuard> memoryGuardProvider) {
    return new DecodeService_Factory(memoryGuardProvider);
  }

  public static DecodeService newInstance(MemoryGuard memoryGuard) {
    return new DecodeService(memoryGuard);
  }
}
