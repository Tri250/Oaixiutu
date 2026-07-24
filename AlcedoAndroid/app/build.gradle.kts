import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    kotlin("kapt")
}

android {
    namespace = "com.alcedo.studio"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alcedo.studio"
        minSdk = 28
        targetSdk = 35
        // versionCode/versionName 支持从 git tag 动态读取（CI 环境），
        // 本地开发回退到硬编码值
        val gitTag = runCatching {
            ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
                .directory(rootProject.projectDir)
                .redirectErrorStream(true)
                .start()
                .inputStream.bufferedReader().readText().trim()
                .removePrefix("v")
        }.getOrNull()
        val parts = gitTag?.split(".")?.mapNotNull { it.toIntOrNull() }
        versionCode = if (parts != null && parts.size >= 3) {
            parts[0] * 10000 + parts[1] * 100 + parts[2]
        } else {
            19
        }
        versionName = gitTag?.ifBlank { null } ?: "1.2.9"

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
            // x86 ABI removed: virtually no Android 9+ (API 28+) x86 devices exist,
            // and ONNX Runtime Android AAR does not ship x86 libraries.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    // ── Signing Configuration ─────────────────────────────────────
    signingConfigs {
        create("release") {
            // Read from local.properties
            val props = Properties()
            val propsFile = rootProject.file("local.properties")
            if (propsFile.exists()) {
                propsFile.reader().use { reader -> props.load(reader) }
            }
            val ksPath = props.getProperty("keystorePath") ?: System.getenv("ALCEDO_KEYSTORE_PATH")
            val ksPass = props.getProperty("keystorePassword") ?: System.getenv("ALCEDO_KEYSTORE_PASSWORD")
            val ksAlias = props.getProperty("keyAlias") ?: System.getenv("ALCEDO_KEY_ALIAS")
            val ksKeyPass = props.getProperty("keyPassword") ?: System.getenv("ALCEDO_KEY_PASSWORD")

            if (!ksPath.isNullOrBlank() && !ksPass.isNullOrBlank() && !ksAlias.isNullOrBlank() && !ksKeyPass.isNullOrBlank()) {
                storeFile = file(ksPath)
                storePassword = ksPass
                this.keyAlias = ksAlias
                this.keyPassword = ksKeyPass
            }
        }
    }

    lint {
        abortOnError = false
        warningsAsErrors = false
        disable += setOf("MissingTranslation", "InvalidPackage", "Typos")
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

            // 保留 isProfileable 以支持生产环境性能分析（Android 14+ baseline profile 兼容）
            isProfileable = true

            // 如果 release 签名未配置，回退到 debug 签名（避免 CI 构建失败）
            val releaseSigning = signingConfigs.getByName("release")
            signingConfig = if (releaseSigning.storeFile != null) releaseSigning else signingConfigs.getByName("debug")

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
    // R8 full mode is disabled via gradle.properties to reduce memory
}

dependencies {
    // ── AndroidX Core ──────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
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
    kapt("androidx.room:room-compiler:2.6.1")

    // ── Database Encryption (SQLCipher) ────────────────────────────
    implementation("net.zetetic:sqlcipher-android:4.5.6")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // ── Serialization ──────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    // ── Coroutines ─────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // ── ONNX Runtime for on-device AI inference ────────────────────
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.0")

    // ── Security (EncryptedSharedPreferences) ──────────────────────
    implementation("androidx.security:security-crypto:1.1.0")

    // ── Testing ────────────────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // ── Leak Detection (Debug only) ──────────────────────────────────
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
}
