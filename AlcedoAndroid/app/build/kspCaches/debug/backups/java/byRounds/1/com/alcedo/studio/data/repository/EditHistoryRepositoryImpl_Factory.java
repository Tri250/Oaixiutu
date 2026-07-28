package com.alcedo.studio.data.repository;

import com.alcedo.studio.data.dao.EditHistoryDao;
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
public final class EditHistoryRepositoryImpl_Factory implements Factory<EditHistoryRepositoryImpl> {
  private final Provider<EditHistoryDao> daoProvider;

  public EditHistoryRepositoryImpl_Factory(Provider<EditHistoryDao> daoProvider) {
    this.daoProvider = daoProvider;
  }

  @Override
  public EditHistoryRepositoryImpl get() {
    return newInstance(daoProvider.get());
  }

  public static EditHistoryRepositoryImpl_Factory create(Provider<EditHistoryDao> daoProvider) {
    return new EditHistoryRepositoryImpl_Factory(daoProvider);
  }

  public static EditHistoryRepositoryImpl newInstance(EditHistoryDao dao) {
    return new EditHistoryRepositoryImpl(dao);
  }
}
