# ================================================================
# ProGuard / R8 Rules for Alcedo Studio
# Comprehensive rules to prevent R8 over-stripping runtime components
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

# ── Kotlin Metadata (CRITICAL) ──────────────────────────────────
# R8 must keep Kotlin metadata annotations so reflection-based access
# (serialization, Compose, Room) does not fail at runtime.
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations
-keepattributes RuntimeVisibleTypeAnnotations
-keepattributes RuntimeInvisibleTypeAnnotations
-keepattributes AnnotationDefault

-keep class kotlin.Metadata { *; }
-keep @interface kotlin.Metadata { *; }
-keepclassmembers class * {
    @kotlin.Metadata <methods>;
}

# Keep kotlin.jvm internal classes used by reflection
-keep class kotlin.jvm.internal.** { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

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

# Keep Room Database subclass and ALL generated implementations
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.RoomDatabase {
    <init>(...);
    <methods>;
}

# Keep Room-generated _Impl classes (R8 often strips these)
-keep class **_Impl { *; }
-keep class **_Impl$* { *; }

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

# Keep the specific Room database class and its inner classes
-keep class com.alcedo.studio.data.local.SleeveDatabase { *; }
-keep class com.alcedo.studio.data.local.SleeveDatabase$* { *; }
-keep class com.alcedo.studio.data.local.SleeveDao { *; }
-keep class com.alcedo.studio.data.local.DatabaseMigrations { *; }
-keep class com.alcedo.studio.data.local.DentryCacheManager { *; }
-keep class com.alcedo.studio.data.local.PathResolver { *; }
-keep class com.alcedo.studio.data.local.ThumbnailDiskCache { *; }
-keep class com.alcedo.studio.data.dao.** { *; }

# Room Compiler generated classes
-keep class androidx.room.RoomDatabaseKt { *; }
-keep class androidx.room.util.** { *; }
-keep class androidx.room.paging.** { *; }

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

# ── Kotlin Serialization (COMPREHENSIVE) ────────────────────────
-keepattributes *Annotation*, InnerClasses

# Keep kotlinx.serialization runtime
-keep class kotlinx.serialization.** { *; }
-dontnote kotlinx.serialization.AnnotationsKt

# Keep serializer() method on Companion objects of @Serializable classes
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *** Companion;
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *** serializer(...);
}

# Keep the generated serializer companion objects ($Companion)
-keep class **.Companion { *; }
-keep class **$$serializer { *; }
-keep class **.serializer { *; }

# Keep all classes with @Serializable annotation and their fields
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
    @kotlinx.serialization.Serializable <methods>;
}

# Keep SerialName/SerialInfo annotations
-keep @interface kotlinx.serialization.SerialName { *; }
-keep @interface kotlinx.serialization.SerialInfo { *; }
-keep @interface kotlinx.serialization.Serializable { *; }
-keep @interface kotlinx.serialization.Transient { *; }
-keep @interface kotlinx.serialization.Required { *; }

# JSON serialization specifics
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Kotlin Coroutines (COMPREHENSIVE) ───────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembernames class kotlinx.** {
    <methods>;
}

# Keep coroutine dispatchers and contexts
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.coroutines.android.** { *; }
-keep class kotlinx.coroutines.internal.** { *; }
-keep class kotlinx.coroutines.flow.** { *; }
-keep class kotlinx.coroutines.channels.** { *; }
-keep class kotlinx.coroutines.selects.** { *; }
-keep class kotlinx.coroutines.sync.** { *; }
-keep class kotlinx.atomicfu.** { *; }

# ── Compose (COMPREHENSIVE) ─────────────────────────────────────
-dontwarn androidx.compose.**

# Keep ALL Compose runtime, foundation, ui, and animation classes
-keep class androidx.compose.** { *; }

# Keep Composable functions - this is the most critical rule
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Keep Compose stability configurations
-keep class * extends androidx.compose.runtime.Stable { *; }
-keep @androidx.compose.runtime.Stable class * { *; }
-keep @androidx.compose.runtime.Immutable class * { *; }
-keep class * extends androidx.compose.runtime.Immutable { *; }

# Keep Compose Modifier chains (R8 may inline and strip these)
-keep class androidx.compose.ui.Modifier { *; }
-keep class androidx.compose.ui.Modifier$* { *; }
-keep class androidx.compose.ui.Modifier$Element { *; }
-keep class androidx.compose.ui.Modifier$Companion { *; }
-keep class androidx.compose.ui.unit.** { *; }
-keep class androidx.compose.ui.geometry.** { *; }
-keep class androidx.compose.ui.graphics.** { *; }
-keep class androidx.compose.ui.text.** { *; }
-keep class androidx.compose.ui.input.** { *; }
-keep class androidx.compose.ui.layout.** { *; }
-keep class androidx.compose.ui.node.** { *; }
-keep class androidx.compose.ui.platform.** { *; }

# Keep Compose runtime internals
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.runtime.internal.** { *; }
-keep class androidx.compose.runtime.snapshots.** { *; }
-keep class androidx.compose.runtime.collection.** { *; }

# Keep Compose foundation/layout
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.foundation.layout.** { *; }
-keep class androidx.compose.foundation.gestures.** { *; }
-keep class androidx.compose.foundation.text.** { *; }
-keep class androidx.compose.foundation.selection.** { *; }
-keep class androidx.compose.foundation.lazy.** { *; }
-keep class androidx.compose.foundation.pager.** { *; }

# Keep Compose material
-keep class androidx.compose.material.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.material.icons.** { *; }

# Keep Compose animation
-keep class androidx.compose.animation.** { *; }
-keep class androidx.compose.animation.core.** { *; }

# Keep Compose UI tooling / preview (needed for runtime)
-keep class androidx.compose.ui.tooling.** { *; }
-keep class androidx.compose.ui.tooling.preview.** { *; }

# Keep Compose State and StateFlow - R8 may strip these
-keep class androidx.compose.runtime.State { *; }
-keep class androidx.compose.runtime.MutableState { *; }
-keep class androidx.compose.runtime.DerivedState { *; }
-keep class androidx.compose.runtime.RecomposeScope { *; }
-keep class androidx.compose.runtime.Recomposer { *; }
-keep class androidx.compose.runtime.Composition { *; }
-keep class androidx.compose.runtime.Applier { *; }

# Keep Compose saveable
-keep class androidx.compose.runtime.saveable.** { *; }

# ── ONNX Runtime ────────────────────────────────────────────────
-keep class ai.onnxruntime.** { *; }
-keep class com.microsoft.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
-dontwarn com.microsoft.onnxruntime.**

# ONNX Runtime reflection-based model loading
-keep class ai.onnxruntime.OrtEnvironment { *; }
-keep class ai.onnxruntime.OrtSession { *; }
-keep class ai.onnxruntime.OnnxModelMetadata { *; }
-keep class ai.onnxruntime.NodeInfo { *; }

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
    <methods>;
}

# Prevent R8 from stripping interface information from TypeToken
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Keep Gson TypeAdapters
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory { *; }
-keep class * extends com.google.gson.TypeAdapter { *; }

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
-keep class androidx.annotation.** { *; }
-keep class androidx.collection.** { *; }
-keep class androidx.arch.core.** { *; }
-keep class androidx.savedstate.** { *; }
-keep class androidx.profileinstaller.** { *; }
-keep class androidx.startup.** { *; }

# Lifecycle observers
-keepclassmembers class * implements androidx.lifecycle.LifecycleObserver {
    <methods>;
}

# ViewModel - keep the class AND all its members, not just constructor
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
    <fields>;
    <methods>;
}

# ViewModel Factory - must be kept for ViewModel creation
-keep class * implements androidx.lifecycle.ViewModelProvider$Factory {
    <init>(...);
    <methods>;
}
-keep class androidx.lifecycle.ViewModelProvider { *; }
-keep class androidx.lifecycle.ViewModelProvider$* { *; }

# SavedStateHandle
-keep class androidx.lifecycle.SavedStateHandle { *; }

# ── Android Navigation Compose ──────────────────────────────────
# Keep Navigation Compose classes and route types
-keep class androidx.navigation.compose.** { *; }
-keep class androidx.navigation.NavHost { *; }
-keep class androidx.navigation.NavController { *; }
-keep class androidx.navigation.NavGraph { *; }
-keep class androidx.navigation.NavGraphBuilder { *; }
-keep class androidx.navigation.NavDestination { *; }
-keep class androidx.navigation.NavType { *; }
-keep class androidx.navigation.NavType$* { *; }

# Keep all sealed class/object route definitions (R8 may strip these)
-keepclassmembers class * {
    @androidx.navigation.NavHost <methods>;
}

# Keep route objects/sealed classes used in navigation
-keep class * extends androidx.navigation.NavRoute { *; }
-keep @interface androidx.navigation.NavHost { *; }

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

# ── AndroidManifest-declared Components ─────────────────────────
# FileProvider declared in manifest
-keep class androidx.core.content.FileProvider { *; }
# All Activities, Services, Receivers, Providers referenced by name
-keep class * extends android.app.Activity { <init>(...); }
-keep class * extends android.app.Service { <init>(...); }
-keep class * extends android.content.BroadcastReceiver { <init>(...); }
-keep class * extends android.content.ContentProvider { <init>(...); }

# ── ViewModels (COMPREHENSIVE) ─────────────────────────────────
-keep class com.alcedo.studio.viewmodel.** { *; }

# Keep ViewModel factory classes in UI package
-keep class com.alcedo.studio.ui.editor.EditorViewModelFactory { *; }

# Keep all ViewModel classes and their inner classes
-keep class com.alcedo.studio.viewmodel.EditorViewModel { *; }
-keep class com.alcedo.studio.viewmodel.EditorViewModel$* { *; }
-keep class com.alcedo.studio.viewmodel.ExportViewModel { *; }
-keep class com.alcedo.studio.viewmodel.ExportViewModel$* { *; }
-keep class com.alcedo.studio.viewmodel.AlbumViewModel { *; }
-keep class com.alcedo.studio.viewmodel.AlbumViewModel$* { *; }

# ── App UI Package (ALL Composable screens and panels) ─────────
# R8 aggressively strips Composable functions; keep ALL UI classes
-keep class com.alcedo.studio.ui.** { *; }

# ── App Data Package ────────────────────────────────────────────
# Keep all data models, repositories, DAOs, and data sources
-keep class com.alcedo.studio.data.** { *; }

# ── App Security Package ────────────────────────────────────────
# Security checks must never be stripped
-keep class com.alcedo.studio.security.** { *; }

# ── DI (Hilt / Manual) ─────────────────────────────────────────
-keep class com.alcedo.studio.di.** { *; }

# ── Domain Services ─────────────────────────────────────────────
-keep class com.alcedo.studio.domain.service.** { *; }
-keep class com.alcedo.studio.domain.repository.** { *; }

# ── Storage ─────────────────────────────────────────────────────
-keep class com.alcedo.studio.storage.** { *; }

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
