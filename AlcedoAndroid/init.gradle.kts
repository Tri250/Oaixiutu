import org.gradle.api.initialization.resolve.DependencyResolutionManagement
import org.gradle.api.initialization.resolve.RepositoriesMode

// Redirect ALL repository URLs to Aliyun mirrors
fun redirectIfNeeded(repo: Any) {
    if (repo is org.gradle.api.artifacts.repositories.MavenArtifactRepository) {
        val url = repo.url.toString().trimEnd('/')
        val newUrl = when {
            url.contains("dl.google.com") || url.contains("maven.google.com") -> 
                "https://maven.aliyun.com/repository/google"
            url.contains("repo1.maven.org") || url.contains("repo.maven.apache.org") -> 
                "https://maven.aliyun.com/repository/central"
            url.contains("plugins.gradle.org") -> 
                "https://maven.aliyun.com/repository/gradle-plugin"
            url.contains("jcenter.bintray.com") -> 
                "https://maven.aliyun.com/repository/public"
            else -> null
        }
        if (newUrl != null && url != newUrl) {
            repo.setUrl(newUrl)
        }
    }
}

beforeSettings {
    pluginManagement.repositories.all { redirectIfNeeded(this) }
    dependencyResolutionManagement.repositories.all { redirectIfNeeded(this) }
    dependencyResolutionManagement.repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
}

allprojects {
    buildscript.repositories.all { redirectIfNeeded(this) }
    repositories.all { redirectIfNeeded(this) }
}