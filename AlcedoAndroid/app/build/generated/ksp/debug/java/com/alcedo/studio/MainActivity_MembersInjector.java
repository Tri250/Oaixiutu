package com.alcedo.studio;

import com.alcedo.studio.privacy.PrivacyManager;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class MainActivity_MembersInjector implements MembersInjector<MainActivity> {
  private final Provider<PrivacyManager> privacyManagerProvider;

  public MainActivity_MembersInjector(Provider<PrivacyManager> privacyManagerProvider) {
    this.privacyManagerProvider = privacyManagerProvider;
  }

  public static MembersInjector<MainActivity> create(
      Provider<PrivacyManager> privacyManagerProvider) {
    return new MainActivity_MembersInjector(privacyManagerProvider);
  }

  @Override
  public void injectMembers(MainActivity instance) {
    injectPrivacyManager(instance, privacyManagerProvider.get());
  }

  @InjectedFieldSignature("com.alcedo.studio.MainActivity.privacyManager")
  public static void injectPrivacyManager(MainActivity instance, PrivacyManager privacyManager) {
    instance.privacyManager = privacyManager;
  }
}
