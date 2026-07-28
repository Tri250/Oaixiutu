package com.alcedo.studio.ai;

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
public final class OnnxModelManager_Factory implements Factory<OnnxModelManager> {
  @Override
  public OnnxModelManager get() {
    return newInstance();
  }

  public static OnnxModelManager_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static OnnxModelManager newInstance() {
    return new OnnxModelManager();
  }

  private static final class InstanceHolder {
    private static final OnnxModelManager_Factory INSTANCE = new OnnxModelManager_Factory();
  }
}
