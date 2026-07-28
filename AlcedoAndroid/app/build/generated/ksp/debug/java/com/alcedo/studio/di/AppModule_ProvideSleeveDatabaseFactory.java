package com.alcedo.studio.di;

import android.content.Context;
import com.alcedo.studio.data.local.SleeveDatabase;
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
public final class AppModule_ProvideSleeveDatabaseFactory implements Factory<SleeveDatabase> {
  private final Provider<Context> contextProvider;

  public AppModule_ProvideSleeveDatabaseFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public SleeveDatabase get() {
    return provideSleeveDatabase(contextProvider.get());
  }

  public static AppModule_ProvideSleeveDatabaseFactory create(Provider<Context> contextProvider) {
    return new AppModule_ProvideSleeveDatabaseFactory(contextProvider);
  }

  public static SleeveDatabase provideSleeveDatabase(Context context) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideSleeveDatabase(context));
  }
}
