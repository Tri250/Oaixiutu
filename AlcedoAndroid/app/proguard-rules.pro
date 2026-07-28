# =============================================================================
# AlcedoAndroid ProGuard / R8 rules
# =============================================================================

# ---------- Application / package identity -----------------------------------
-keep class com.alcedo.studio.AlcedoApplication { *; }
-keep class com.alcedo.studio.MainActivity { *; }

# Keep the JNI bridge classes — native code looks these up by name/signature.
-keep class com.alcedo.studio.ndk.AlcedoNativeBridge { *; }
-keep class com.alcedo.studio.ndk.AiNdkBridge { *; }
-keep class com.alcedo.studio.ndk.NdkSafeCall { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# ---------- Hilt / Dagger ----------------------------------------------------
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keepnames class * extends androidx.lifecycle.ViewModel
-keepnames class * extends androidx.lifecycle.AndroidViewModel

# ---------- Room -------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class androidx.room.** { *; }
-dontwarn androidx.room.paging.**

# ---------- Coroutines -------------------------------------------------------
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ---------- kotlinx.serialization -------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.alcedo.studio.**$$serializer { *; }
-keepclassmembers class com.alcedo.studio.** {
    *** Companion;
}
-keepclasseswithmembers class com.alcedo.studio.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------- ONNX Runtime -----------------------------------------------------
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# ---------- OkHttp / Retrofit (LLM clients) ---------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepattributes Signature, Exceptions

# ---------- Compose ----------------------------------------------------------
-dontwarn androidx.compose.**

# ---------- Reflection-based service loaders ---------------------------------
-keep class com.alcedo.studio.domain.service.** { *; }
-keep class com.alcedo.studio.data.repository.** { *; }
-keep class com.alcedo.studio.data.model.** { *; }

# ---------- Crash reporter ---------------------------------------------------
-keep class com.alcedo.studio.crash.CrashReportService { *; }
