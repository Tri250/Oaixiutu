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
public final class ImageAnalysisEncoder_Factory implements Factory<ImageAnalysisEncoder> {
  private final Provider<MemoryGuard> memoryGuardProvider;

  public ImageAnalysisEncoder_Factory(Provider<MemoryGuard> memoryGuardProvider) {
    this.memoryGuardProvider = memoryGuardProvider;
  }

  @Override
  public ImageAnalysisEncoder get() {
    return newInstance(memoryGuardProvider.get());
  }

  public static ImageAnalysisEncoder_Factory create(Provider<MemoryGuard> memoryGuardProvider) {
    return new ImageAnalysisEncoder_Factory(memoryGuardProvider);
  }

  public static ImageAnalysisEncoder newInstance(MemoryGuard memoryGuard) {
    return new ImageAnalysisEncoder(memoryGuard);
  }
}
