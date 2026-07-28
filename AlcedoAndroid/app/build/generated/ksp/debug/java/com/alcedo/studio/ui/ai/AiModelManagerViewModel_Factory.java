package com.alcedo.studio.ui.ai;

import com.alcedo.studio.domain.service.AiSidecarRuntimeService;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class AiModelManagerViewModel_Factory implements Factory<AiModelManagerViewModel> {
  private final Provider<AiSidecarRuntimeService> sidecarRuntimeProvider;

  public AiModelManagerViewModel_Factory(Provider<AiSidecarRuntimeService> sidecarRuntimeProvider) {
    this.sidecarRuntimeProvider = sidecarRuntimeProvider;
  }

  @Override
  public AiModelManagerViewModel get() {
    return newInstance(sidecarRuntimeProvider.get());
  }

  public static AiModelManagerViewModel_Factory create(
      Provider<AiSidecarRuntimeService> sidecarRuntimeProvider) {
    return new AiModelManagerViewModel_Factory(sidecarRuntimeProvider);
  }

  public static AiModelManagerViewModel newInstance(AiSidecarRuntimeService sidecarRuntime) {
    return new AiModelManagerViewModel(sidecarRuntime);
  }
}
