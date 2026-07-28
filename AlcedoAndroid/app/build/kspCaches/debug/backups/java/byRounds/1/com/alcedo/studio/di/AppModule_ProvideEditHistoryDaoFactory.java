package com.alcedo.studio.di;

import com.alcedo.studio.data.dao.EditHistoryDao;
import com.alcedo.studio.data.local.SleeveDatabase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class AppModule_ProvideEditHistoryDaoFactory implements Factory<EditHistoryDao> {
  private final Provider<SleeveDatabase> dbProvider;

  public AppModule_ProvideEditHistoryDaoFactory(Provider<SleeveDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public EditHistoryDao get() {
    return provideEditHistoryDao(dbProvider.get());
  }

  public static AppModule_ProvideEditHistoryDaoFactory create(Provider<SleeveDatabase> dbProvider) {
    return new AppModule_ProvideEditHistoryDaoFactory(dbProvider);
  }

  public static EditHistoryDao provideEditHistoryDao(SleeveDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideEditHistoryDao(db));
  }
}
