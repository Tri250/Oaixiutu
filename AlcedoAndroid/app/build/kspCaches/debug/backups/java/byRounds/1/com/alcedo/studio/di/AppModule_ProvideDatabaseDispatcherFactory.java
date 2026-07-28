package com.alcedo.studio.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import kotlinx.coroutines.CoroutineDispatcher;

@ScopeMetadata
@QualifierMetadata("com.alcedo.studio.di.DatabaseDispatcher")
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
public final class AppModule_ProvideDatabaseDispatcherFactory implements Factory<CoroutineDispatcher> {
  @Override
  public CoroutineDispatcher get() {
    return provideDatabaseDispatcher();
  }

  public static AppModule_ProvideDatabaseDispatcherFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static CoroutineDispatcher provideDatabaseDispatcher() {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideDatabaseDispatcher());
  }

  private static final class InstanceHolder {
    private static final AppModule_ProvideDatabaseDispatcherFactory INSTANCE = new AppModule_ProvideDatabaseDispatcherFactory();
  }
}
