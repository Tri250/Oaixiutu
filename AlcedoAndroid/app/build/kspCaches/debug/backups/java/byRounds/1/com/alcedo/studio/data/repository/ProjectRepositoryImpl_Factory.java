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
public final class ProjectRepositoryImpl_Factory implements Factory<ProjectRepositoryImpl> {
  private final Provider<SleeveDatabase> databaseProvider;

  public ProjectRepositoryImpl_Factory(Provider<SleeveDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public ProjectRepositoryImpl get() {
    return newInstance(databaseProvider.get());
  }

  public static ProjectRepositoryImpl_Factory create(Provider<SleeveDatabase> databaseProvider) {
    return new ProjectRepositoryImpl_Factory(databaseProvider);
  }

  public static ProjectRepositoryImpl newInstance(SleeveDatabase database) {
    return new ProjectRepositoryImpl(database);
  }
}
