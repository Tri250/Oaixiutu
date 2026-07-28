// Top-level build file
buildscript {
    dependencies {
        classpath(files("./local-repo/org/jetbrains/kotlin/kotlin-serialization/2.1.0/kotlin-serialization-2.1.0.jar"))
    }
}

plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
    id("com.google.dagger.hilt.android") version "2.53.1" apply false
}
