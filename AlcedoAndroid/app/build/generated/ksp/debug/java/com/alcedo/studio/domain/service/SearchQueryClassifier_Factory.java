package com.alcedo.studio.domain.service;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class SearchQueryClassifier_Factory implements Factory<SearchQueryClassifier> {
  @Override
  public SearchQueryClassifier get() {
    return newInstance();
  }

  public static SearchQueryClassifier_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static SearchQueryClassifier newInstance() {
    return new SearchQueryClassifier();
  }

  private static final class InstanceHolder {
    private static final SearchQueryClassifier_Factory INSTANCE = new SearchQueryClassifier_Factory();
  }
}
