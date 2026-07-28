package com.alcedo.studio.data.repository;

import com.alcedo.studio.data.local.SleeveDatabase;
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
public final class SleeveRepositoryImpl_Factory implements Factory<SleeveRepositoryImpl> {
  private final Provider<SleeveDatabase> databaseProvider;

  public SleeveRepositoryImpl_Factory(Provider<SleeveDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public SleeveRepositoryImpl get() {
    return newInstance(databaseProvider.get());
  }

  public static SleeveRepositoryImpl_Factory create(Provider<SleeveDatabase> databaseProvider) {
    return new SleeveRepositoryImpl_Factory(databaseProvider);
  }

  public static SleeveRepositoryImpl newInstance(SleeveDatabase database) {
    return new SleeveRepositoryImpl(database);
  }
}
