# AlcedoStudio ProGuard Rules

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

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.alcedo.studio.**$$serializer { *; }
-keepclassmembers class com.alcedo.studio.** { *** Companion; }
-keepclasseswithmembers class com.alcedo.studio.** { kotlinx.serialization.KSerializer serializer(...); }

# ONNX Runtime
-keep class ai.onnxruntime.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Hilt
-dontwarn dagger.hilt.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Retrofit
-keepattributes Signature, Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# ExifInterface
-keep class androidx.exifinterface.media.ExifInterface { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
