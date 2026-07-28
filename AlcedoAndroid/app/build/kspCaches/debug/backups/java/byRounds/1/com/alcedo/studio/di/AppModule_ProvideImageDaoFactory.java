package com.alcedo.studio.di;

import com.alcedo.studio.data.dao.ImageDao;
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
public final class AppModule_ProvideImageDaoFactory implements Factory<ImageDao> {
  private final Provider<SleeveDatabase> dbProvider;

  public AppModule_ProvideImageDaoFactory(Provider<SleeveDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public ImageDao get() {
    return provideImageDao(dbProvider.get());
  }

  public static AppModule_ProvideImageDaoFactory create(Provider<SleeveDatabase> dbProvider) {
    return new AppModule_ProvideImageDaoFactory(dbProvider);
  }

  public static ImageDao provideImageDao(SleeveDatabase db) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideImageDao(db));
  }
}
