package com.alcedo.studio.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import kotlinx.coroutines.CoroutineDispatcher;

@ScopeMetadata
@QualifierMetadata("com.alcedo.studio.di.ComputeDispatcher")
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
public final class AppModule_ProvideComputeDispatcherFactory implements Factory<CoroutineDispatcher> {
  @Override
  public CoroutineDispatcher get() {
    return provideComputeDispatcher();
  }

  public static AppModule_ProvideComputeDispatcherFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static CoroutineDispatcher provideComputeDispatcher() {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideComputeDispatcher());
  }

  private static final class InstanceHolder {
    private static final AppModule_ProvideComputeDispatcherFactory INSTANCE = new AppModule_ProvideComputeDispatcherFactory();
  }
}
