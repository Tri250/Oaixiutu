pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven(uri("./local-repo"))
        mavenCentral()
        google()
        maven("https://jitpack.io")
    }
}

rootProject.name = "AlcedoAndroid"
include(":app")
