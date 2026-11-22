# SplitFlow R8 / ProGuard Rules for Public Release APKs

# Preserve source/line numbers for readable crash reports without exposing file paths
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Firestore & Kotlin Serialization data models (reflected by Firestore toObject / kotlinx.serialization)
-keep class com.example.model.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Cactus Compute local inference SDK & JNI native bindings
-keep class com.cactus.** { *; }
-dontwarn com.cactus.**

# On-device ML Kit Text Recognition
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Strip verbose, debug, and info logs in release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
