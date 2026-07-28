package com.alcedo.studio.security;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class SecurityChecker_Factory implements Factory<SecurityChecker> {
  private final Provider<Context> contextProvider;

  private final Provider<NativeSecurityChecker> nativeCheckerProvider;

  public SecurityChecker_Factory(Provider<Context> contextProvider,
      Provider<NativeSecurityChecker> nativeCheckerProvider) {
    this.contextProvider = contextProvider;
    this.nativeCheckerProvider = nativeCheckerProvider;
  }

  @Override
  public SecurityChecker get() {
    return newInstance(contextProvider.get(), nativeCheckerProvider.get());
  }

  public static SecurityChecker_Factory create(Provider<Context> contextProvider,
      Provider<NativeSecurityChecker> nativeCheckerProvider) {
    return new SecurityChecker_Factory(contextProvider, nativeCheckerProvider);
  }

  public static SecurityChecker newInstance(Context context, NativeSecurityChecker nativeChecker) {
    return new SecurityChecker(context, nativeChecker);
  }
}
