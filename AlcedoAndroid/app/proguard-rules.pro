# ================================================================
# ProGuard / R8 Rules for Alcedo Studio
# ================================================================

# ── Generic Android ──────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes Signature
-keepattributes Exceptions
-keepattributes SourceFile,LineNumberTable
-keepattributes EnclosingMethod

-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.codehaus.**

# ── NDK Bridge Classes ──────────────────────────────────────────
# Keep all NDK bridge objects and their native methods
-keep class com.alcedo.studio.ndk.AlcedoNdkBridge { *; }
-keep class com.alcedo.studio.ndk.AiNdkBridge { *; }
-keep class com.alcedo.studio.ndk.AlcedoNativeBridge { *; }
-keep class com.alcedo.studio.ndk.NdkSafeCall { *; }
# Legacy bridges (may still be referenced by native code)
-keep class com.alcedo.studio.ndk.DecodeNdkBridge { *; }
-keep class com.alcedo.studio.ndk.SleeveNdkBridge { *; }
-keep class com.alcedo.studio.domain.service.NativePipelineBridge { *; }
-keep class com.alcedo.studio.security.NativeSecurityChecker { *; }
# JNI-constructed data classes (NewObject in native-lib.cpp)
-keep class com.alcedo.studio.domain.service.NativeDecodeResult { *; }
-keep class com.alcedo.studio.domain.service.NativeThumbnailResult { *; }
-keep class com.alcedo.studio.domain.service.NativeRawInfoResult { *; }
-keep class com.alcedo.studio.domain.service.DecodeService$RawImageInfo { *; }

# Keep any class with native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# ── Room Database ───────────────────────────────────────────────
# Keep Room entities (使用注解匹配,而非无效的 extends 注解)
-keep @androidx.room.Entity class * { *; }

# Keep Room DAOs (使用注解匹配)
-keep @androidx.room.Dao class * { *; }

# Keep Room Database subclass
-keep class * extends androidx.room.RoomDatabase { *; }

# Keep all entity classes referenced by the database
-keep class com.alcedo.studio.data.model.SleeveElementEntity { *; }
-keep class com.alcedo.studio.data.model.SleeveFileEntity { *; }
-keep class com.alcedo.studio.data.model.SleeveFolderEntity { *; }
-keep class com.alcedo.studio.data.model.ElementFts { *; }
-keep class com.alcedo.studio.data.model.CollectionEntity { *; }
-keep class com.alcedo.studio.data.model.CollectionImageEntity { *; }
-keep class com.alcedo.studio.data.model.RatingEntity { *; }
-keep class com.alcedo.studio.data.model.FilterPresetEntity { *; }
-keep class com.alcedo.studio.data.model.ImageMetadataEntity { *; }
-keep class com.alcedo.studio.data.model.SemanticLabelEntity { *; }
-keep class com.alcedo.studio.data.model.VectorIndexEntity { *; }
-keep class com.alcedo.studio.data.model.ImageEntity { *; }
-keep class com.alcedo.studio.data.model.PipelineEntity { *; }
-keep class com.alcedo.studio.data.model.HistoryEntity { *; }
-keep class com.alcedo.studio.data.model.FilterEntity { *; }
-keep class com.alcedo.studio.data.model.AiDescriptionEntity { *; }
-keep class com.alcedo.studio.data.model.AiRatingEntity { *; }
-keep class com.alcedo.studio.data.model.SemanticEmbeddingEntity { *; }
-keep class com.alcedo.studio.data.model.SemanticLabelV2Entity { *; }
-keep class com.alcedo.studio.data.model.CollectionV2Entity { *; }
-keep class com.alcedo.studio.data.model.CollectionImageV2Entity { *; }
-keep class com.alcedo.studio.data.model.EditHistoryEntity { *; }
-keep class com.alcedo.studio.data.model.PipelinePresetEntity { *; }
-keep class com.alcedo.studio.data.model.AiEmbeddingEntity { *; }

# Keep Room DAO interfaces (all DAOs in SleeveDao.kt and others)
-keep @interface androidx.room.Dao { *; }
-keep @interface androidx.room.Entity { *; }
-keep @interface androidx.room.Database { *; }

# Keep Room-generated implementations
-keep class * { @androidx.room.Query <methods>; }
-keep class * { @androidx.room.Insert <methods>; }
-keep class * { @androidx.room.Update <methods>; }
-keep class * { @androidx.room.Delete <methods>; }
-keep class * { @androidx.room.RawQuery <methods>; }

# Keep Room TypeConverters
-keep class * { @androidx.room.TypeConverter <methods>; }
-keepclasseswithmembers class * {
    @androidx.room.TypeConverter <methods>;
}

# Room schema export
-keep class androidx.room.migration.Migration { *; }

# Keep ColumnInfo-annotated fields
-keepclassmembers class * {
    @androidx.room.ColumnInfo <fields>;
}

# ── Parcelable / Serializable ───────────────────────────────────
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep Parcelize data classes
-keep @kotlinx.parcelize.Parcelize class * { *; }
-keep class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ── Kotlin Serialization ────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keepclassmembers class * {
    *** Companion;
    *** serializer(...);
}

-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <methods>;
}

-keep class **.serializer { *; }
-keepclassmembers class * {
    *** serializer(...);
}

# ── Kotlin Coroutines ───────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembernames class kotlinx.** {
    <methods>;
}

# ── Compose ─────────────────────────────────────────────────────
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# Keep Composable functions
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Keep Compose stability configurations
-keep class * extends androidx.compose.runtime.Stable { *; }
-keep @androidx.compose.runtime.Stable class * { *; }

# ── ONNX Runtime ────────────────────────────────────────────────
-keep class ai.onnxruntime.** { *; }
-keep class com.microsoft.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
-dontwarn com.microsoft.onnxruntime.**

# ── Metadata-Extractor ──────────────────────────────────────────
-keep class com.drew.** { *; }
-keep class com.drew.imaging.** { *; }
-keep class com.drew.metadata.** { *; }
-dontwarn com.drew.**

# ── Gson ────────────────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*

# Keep Gson TypeToken and its subclasses
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Keep classes used as Gson serialization targets
-keep class com.alcedo.studio.data.model.** {
    <fields>;
    <init>(...);
}

# Prevent R8 from stripping interface information from TypeToken
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# ── Retrofit ────────────────────────────────────────────────────
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# Keep Retrofit service interfaces
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowshrinking class retrofit2.Retrofit { *; }

# Keep generic return types of Retrofit services
-keep,allowobfuscation,allowshrinking class * {
    @retrofit2.http.* <methods>;
}

# ── OkHttp ──────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }

# OkHttp platform
-keep class okhttp3.internal.platform.** { *; }

# ── AndroidX Libraries ──────────────────────────────────────────
-keep class androidx.core.** { *; }
-keep class androidx.activity.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.navigation.** { *; }
-keep class androidx.datastore.** { *; }
-keep class androidx.exifinterface.** { *; }
-keep class androidx.security.** { *; }

# Lifecycle observers
-keepclassmembers class * implements androidx.lifecycle.LifecycleObserver {
    <methods>;
}

# ViewModel
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# SavedStateHandle
-keep class androidx.lifecycle.SavedStateHandle { *; }

# ── DataStore ───────────────────────────────────────────────────
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# ── SQLCipher ───────────────────────────────────────────────────
-keep class net.zetetic.database.sqlcipher.** { *; }
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**
-keepclassmembers class * {
    @net.sqlcipher.database.* <fields>;
}

# ── Security Crypto ─────────────────────────────────────────────
-keep class androidx.security.crypto.** { *; }
-dontwarn com.google.api.client.http.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.joda.time.**

# ── Application Class ───────────────────────────────────────────
-keep class com.alcedo.studio.AlcedoApplication { *; }
-keep class com.alcedo.studio.MainActivity { *; }

# ── ViewModels ──────────────────────────────────────────────────
-keep class com.alcedo.studio.viewmodel.** { *; }

# ── DI (Hilt / Manual) ─────────────────────────────────────────
-keep class com.alcedo.studio.di.** { *; }

# ── Domain Services ─────────────────────────────────────────────
-keep class com.alcedo.studio.domain.service.** { *; }
-keep class com.alcedo.studio.domain.repository.** { *; }

# ── Callbacks & Listeners ──────────────────────────────────────
-keepclassmembers class * implements android.content.ComponentCallbacks2 {
    void onTrimMemory(int);
}

# ── Enum keep rules ────────────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Build Config ────────────────────────────────────────────────
-keep class com.alcedo.studio.BuildConfig { *; }

# ── R8 Full Mode ────────────────────────────────────────────────
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
