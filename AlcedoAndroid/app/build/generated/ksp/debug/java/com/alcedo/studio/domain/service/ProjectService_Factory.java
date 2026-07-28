package com.alcedo.studio.domain.service;

import com.alcedo.studio.domain.repository.ProjectRepository;
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
public final class ProjectService_Factory implements Factory<ProjectService> {
  private final Provider<ProjectRepository> projectRepositoryProvider;

  private final Provider<ProjectPackageService> packageServiceProvider;

  public ProjectService_Factory(Provider<ProjectRepository> projectRepositoryProvider,
      Provider<ProjectPackageService> packageServiceProvider) {
    this.projectRepositoryProvider = projectRepositoryProvider;
    this.packageServiceProvider = packageServiceProvider;
  }

  @Override
  public ProjectService get() {
    return newInstance(projectRepositoryProvider.get(), packageServiceProvider.get());
  }

  public static ProjectService_Factory create(Provider<ProjectRepository> projectRepositoryProvider,
      Provider<ProjectPackageService> packageServiceProvider) {
    return new ProjectService_Factory(projectRepositoryProvider, packageServiceProvider);
  }

  public static ProjectService newInstance(ProjectRepository projectRepository,
      ProjectPackageService packageService) {
    return new ProjectService(projectRepository, packageService);
  }
}
