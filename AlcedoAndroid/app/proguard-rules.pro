# ProGuard rules for Alcedo Studio
-keep class com.alcedo.studio.** { *; }
-keepclassmembers class com.alcedo.studio.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Compose
-keepclassmembers class androidx.compose.** { *; }

# Serialization
-keepattributes *Annotation*, InnerClasses
-keepattributes Signature
-keepattributes Exceptions
