# AlcedoStudio ProGuard Rules

# ====================================================================
# Kotlin metadata & reflection
# ====================================================================
-keepattributes *Annotation*, InnerClasses, RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations, RuntimeVisibleParameterAnnotations, RuntimeInvisibleParameterAnnotations, RuntimeVisibleTypeAnnotations, RuntimeInvisibleTypeAnnotations, Signature, EnclosingMethod, Exceptions, Deprecated, SourceFile, SourceDir, LineNumberTable
-keep @kotlin.Metadata class * { *; }
-dontwarn kotlin.**

# Keep native method names for JNI
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep NDK bridge classes
-keep class com.alcedo.studio.ndk.** { *; }

# Keep serializable models
-keepclassmembers class com.alcedo.studio.data.model.** implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep data classes / DTOs (used by serialization and reflection)
-keep class com.alcedo.studio.data.model.** { *; }
-keepclassmembers class com.alcedo.studio.data.model.** {
    *;
}

# ====================================================================
# kotlinx.serialization
# ====================================================================
-keepattributes *Annotation*, InnerClasses, RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations, Signature, EnclosingMethod
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.alcedo.studio.**$$serializer { *; }
-keepclassmembers class com.alcedo.studio.** { *** Companion; }
-keepclasseswithmembers class com.alcedo.studio.** { kotlinx.serialization.KSerializer serializer(...); }

# ====================================================================
# ONNX Runtime
# ====================================================================
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# ====================================================================
# Room Database and DAOs
# ====================================================================
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-keep class * extends androidx.room.RoomDatabase$Callback { *; }

# ====================================================================
# Hilt / Dagger
# ====================================================================
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @javax.inject.Inject class * { *; }
-keep @javax.inject.Singleton class * { *; }
-keepclassmembers class * { @javax.inject.Inject *; }

# ====================================================================
# OkHttp + Okio
# ====================================================================
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ====================================================================
# Retrofit
# ====================================================================
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking interface retrofit2.Response
-keep,allowobfuscation,allowshrinking class retrofit2.Retrofit

# Gson (used by converter-gson)
-keepattributes Signature
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# ====================================================================
# ExifInterface
# ====================================================================
-keep class androidx.exifinterface.media.ExifInterface { *; }

# ====================================================================
# Coroutines
# ====================================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ====================================================================
# Security Crypto (EncryptedSharedPreferences / Tink)
# ====================================================================
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }

# ====================================================================
# Application class
# ====================================================================
-keep class com.alcedo.studio.AlcedoApplication { *; }

# ====================================================================
# DocumentFile (SAF)
# ====================================================================
-keep class androidx.documentfile.** { *; }

# ====================================================================
# DataStore
# ====================================================================
-keep class androidx.datastore.** { *; }

# ====================================================================
# Splash Screen
# ====================================================================
-keep class androidx.core.splashscreen.** { *; }

# ====================================================================
# Jetpack Compose (compiler needs metadata + synthetic accessors)
# ====================================================================
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
-keep class kotlin.reflect.jvm.internal.** { *; }

# ViewModels
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# ====================================================================
# Coil image loading
# ====================================================================
-keep class coil.** { *; }
-dontwarn coil.**

# ====================================================================
# Misc compatibility
# ====================================================================
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**

# Tink transitive dependencies (not used at runtime)
-dontwarn com.google.api.client.http.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.joda.time.**

