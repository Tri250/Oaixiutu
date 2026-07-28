package com.alcedo.studio.di;

import android.content.Context;
import com.alcedo.studio.utils.MemoryGuard;
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
public final class AppModule_ProvideMemoryGuardFactory implements Factory<MemoryGuard> {
  private final Provider<Context> contextProvider;

  public AppModule_ProvideMemoryGuardFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public MemoryGuard get() {
    return provideMemoryGuard(contextProvider.get());
  }

  public static AppModule_ProvideMemoryGuardFactory create(Provider<Context> contextProvider) {
    return new AppModule_ProvideMemoryGuardFactory(contextProvider);
  }

  public static MemoryGuard provideMemoryGuard(Context context) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideMemoryGuard(context));
  }
}
