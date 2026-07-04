plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.alcedo.studio"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alcedo.studio"
        minSdk = 28
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++20 -O3 -fexceptions -frtti"
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_CPP_FEATURES=rtti exceptions"
                )
            }
        }
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }

    // ── Signing Configuration ─────────────────────────────────────
    signingConfigs {
        create("release") {
            // Read from local.properties or environment variables
            val keystorePathProp: String? by project
            val keystorePasswordProp: String? by project
            val keyAliasProp: String? by project
            val keyPasswordProp: String? by project

            storeFile = keystorePathProp?.let { file(it) }
            storePassword = keystorePasswordProp ?: System.getenv("ALCEDO_KEYSTORE_PASSWORD")
            this.keyAlias = keyAliasProp ?: System.getenv("ALCEDO_KEY_ALIAS")
            this.keyPassword = keyPasswordProp ?: System.getenv("ALCEDO_KEY_PASSWORD")
        }
    }

    // ── Build Types ───────────────────────────────────────────────
    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"

            buildConfigField("boolean", "ENABLE_LOGGING", "true")
            buildConfigField("boolean", "ENABLE_GPU_DEBUG", "true")
            buildConfigField("boolean", "ENABLE_AI_DEBUG", "true")

            externalNativeBuild {
                cmake {
                    cppFlags += "-g -DDEBUG -DALCEDO_DEBUG=1"
                    arguments += listOf(
                        "-DANDROID_STL=c++_shared",
                        "-DANDROID_CPP_FEATURES=rtti exceptions"
                    )
                }
            }
        }

        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true

            // R8 full mode
            isProfileable = true

            signingConfig = signingConfigs.getByName("release")

            buildConfigField("boolean", "ENABLE_LOGGING", "false")
            buildConfigField("boolean", "ENABLE_GPU_DEBUG", "false")
            buildConfigField("boolean", "ENABLE_AI_DEBUG", "false")

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            externalNativeBuild {
                cmake {
                    cppFlags += "-DNDEBUG -DALCEDO_RELEASE=1"
                    arguments += listOf(
                        "-DANDROID_STL=c++_shared",
                        "-DANDROID_CPP_FEATURES=rtti exceptions",
                        "-DCMAKE_BUILD_TYPE=Release"
                    )
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    ndkVersion = "26.3.11579264"

    // ── R8 Full Mode Configuration ────────────────────────────────
    // Enable R8 full mode for aggressive optimization
    // (enabled by default when isMinifyEnabled = true)
}

dependencies {
    // ── AndroidX Core ──────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")

    // ── Compose ────────────────────────────────────────────────────
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class:1.2.1")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ── EXIF / Metadata ────────────────────────────────────────────
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("com.drewnoakes:metadata-extractor:2.19.0")

    // ── DataStore ──────────────────────────────────────────────────
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ── Networking ─────────────────────────────────────────────────
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ── Room Database ──────────────────────────────────────────────
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // ── Database Encryption (SQLCipher) ────────────────────────────
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // ── Serialization ──────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    // ── Coroutines ─────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // ── ONNX Runtime for on-device AI inference ────────────────────
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.0")

    // ── Security (EncryptedSharedPreferences) ──────────────────────
    implementation("androidx.security:security-crypto:1.0.0")

    // ── Testing ────────────────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
