package com.alcedo.studio.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import kotlinx.coroutines.CoroutineDispatcher;
import kotlinx.coroutines.CoroutineScope;

@ScopeMetadata
@QualifierMetadata({
    "com.alcedo.studio.di.ApplicationScope",
    "com.alcedo.studio.di.ComputeDispatcher"
})
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
public final class AppModule_ProvideApplicationScopeFactory implements Factory<CoroutineScope> {
  private final Provider<CoroutineDispatcher> dispatcherProvider;

  public AppModule_ProvideApplicationScopeFactory(
      Provider<CoroutineDispatcher> dispatcherProvider) {
    this.dispatcherProvider = dispatcherProvider;
  }

  @Override
  public CoroutineScope get() {
    return provideApplicationScope(dispatcherProvider.get());
  }

  public static AppModule_ProvideApplicationScopeFactory create(
      Provider<CoroutineDispatcher> dispatcherProvider) {
    return new AppModule_ProvideApplicationScopeFactory(dispatcherProvider);
  }

  public static CoroutineScope provideApplicationScope(CoroutineDispatcher dispatcher) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideApplicationScope(dispatcher));
  }
}
