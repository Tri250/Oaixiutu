package com.alcedo.studio.domain.service;

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
public final class ExportService_Factory implements Factory<ExportService> {
  private final Provider<PipelineService> pipelineServiceProvider;

  private final Provider<WatermarkService> watermarkServiceProvider;

  private final Provider<UltraHdrWriter> ultraHdrWriterProvider;

  public ExportService_Factory(Provider<PipelineService> pipelineServiceProvider,
      Provider<WatermarkService> watermarkServiceProvider,
      Provider<UltraHdrWriter> ultraHdrWriterProvider) {
    this.pipelineServiceProvider = pipelineServiceProvider;
    this.watermarkServiceProvider = watermarkServiceProvider;
    this.ultraHdrWriterProvider = ultraHdrWriterProvider;
  }

  @Override
  public ExportService get() {
    return newInstance(pipelineServiceProvider.get(), watermarkServiceProvider.get(), ultraHdrWriterProvider.get());
  }

  public static ExportService_Factory create(Provider<PipelineService> pipelineServiceProvider,
      Provider<WatermarkService> watermarkServiceProvider,
      Provider<UltraHdrWriter> ultraHdrWriterProvider) {
    return new ExportService_Factory(pipelineServiceProvider, watermarkServiceProvider, ultraHdrWriterProvider);
  }

  public static ExportService newInstance(PipelineService pipelineService,
      WatermarkService watermarkService, UltraHdrWriter ultraHdrWriter) {
    return new ExportService(pipelineService, watermarkService, ultraHdrWriter);
  }
}
