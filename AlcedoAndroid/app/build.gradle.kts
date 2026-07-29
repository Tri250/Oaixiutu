import org.gradle.api.tasks.compile.JavaCompile
import java.io.FileInputStream
import java.util.Properties

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.alcedo.studio"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alcedo.studio"
        minSdk = 29
        targetSdk = 35

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Versioning from git: versionCode from commit count, versionName from
        // the most recent tag (falls back to a sane default outside a git repo).
        val gitCommitCount = try {
            providers.exec {
                commandLine("git", "rev-list", "--count", "HEAD")
            }.standardOutput.asText.get().trim().toInt()
        } catch (e: Exception) { 1 }
        versionCode = gitCommitCount
        versionName = try {
            providers.exec {
                commandLine("git", "describe", "--tags", "--always", "--dirty")
            }.standardOutput.asText.get().trim()
        } catch (e: Exception) { "0.3.0" }

        // Certificate pins (SPKI SHA-256, base64) for hosts whose live certs
        // could not be baked in here. Populate with the verified pin for each
        // host at release; leave blank in debug to disable pinning for that
        // host (OkHttp treats an absent pin as "no enforcement"). Obtain a pin
        // with:  echo | openssl s_client -connect HOST:443 -servername HOST \
        //   | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der \
        //   | openssl dgst -sha256 -binary | openssl base64
        buildConfigField("String", "ALCEDO_PIN_OPENAI", "\"\"")
        buildConfigField("String", "ALCEDO_PIN_HUGGINGFACE", "\"\"")
        buildConfigField("String", "ALCEDO_PIN_HF_CDN", "\"\"")
        // Expected release signing-certificate SHA-256 (hex). Blank in debug =>
        // signature verification is skipped (debug builds use the debug key).
        // Set to the release key's SHA-256 for release builds.
        buildConfigField("String", "ALCEDO_RELEASE_SIGNATURE_SHA256", "\"\"")

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf(
                    "-std=c++20",
                    "-fexceptions",
                    "-frtti",
                    "-O2",
                    "-DNDEBUG",
                    "-DANDROID",
                    "-DPUERHLAB_VULKAN_BACKEND"
                )
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_PLATFORM=android-29"
                )
            }
        }
    }

    // Whether a production release keystore is configured via keystore.properties.
    // When false, the release build type falls back to the debug signing config so
    // CI can still produce a release APK for testing (it must be re-signed with the
    // production key before distribution).
    val hasReleaseKeystore = run {
        val storeFilePath = keystoreProperties.getProperty("storeFile", "")
        storeFilePath.isNotEmpty() && rootProject.file(storeFilePath).exists()
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKeystore) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword", "")
                keyAlias = keystoreProperties.getProperty("keyAlias", "")
                keyPassword = keystoreProperties.getProperty("keyPassword", "")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isZipAlignEnabled = true
            isCrunchPngs = true
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-Xjvm-default=all",
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*"
            )
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java")
            res.srcDirs("src/main/res")
            assets.srcDirs("src/main/assets")
        }
    }

    lint {
        // Release builds must fail on lint errors to catch issues early.
        abortOnError = true
        checkReleaseBuilds = true
        disable += listOf("MissingTranslation", "ExtraTranslation", "OldTargetApi")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Jetpack Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.runtime:runtime-livedata")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.53.1")
    ksp("com.google.dagger:hilt-android-compiler:2.53.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // OkHttp + Retrofit for LLM API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ONNX Runtime for AI inference (1.20.1 was never published on Maven
    // Central; 1.20.0 -> 1.21.0 is the actual release sequence. 1.22.0 is a
    // stable, broadly-tested release whose Java API matches OnnxModelManager.)
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")

    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // DataStore for preferences
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ExifInterface for EXIF metadata
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // DocumentFile for SAF
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Security for credential storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Splash Screen
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
