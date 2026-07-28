package com.alcedo.studio.domain.service;

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
public final class LensCorrectionDatabase_Factory implements Factory<LensCorrectionDatabase> {
  private final Provider<SleeveDatabase> databaseProvider;

  public LensCorrectionDatabase_Factory(Provider<SleeveDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public LensCorrectionDatabase get() {
    return newInstance(databaseProvider.get());
  }

  public static LensCorrectionDatabase_Factory create(Provider<SleeveDatabase> databaseProvider) {
    return new LensCorrectionDatabase_Factory(databaseProvider);
  }

  public static LensCorrectionDatabase newInstance(SleeveDatabase database) {
    return new LensCorrectionDatabase(database);
  }
}
