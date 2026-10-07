# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Moshi
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.* <fields>;
}

# Retrofit & OkHttp
-dontwarn retrofit2.**
-dontwarn okhttp3.**

# Firebase Data Models
-keepattributes *Annotation*
-keepclassmembers class com.example.data.model.** {
    <fields>;
    <init>(...);
}
-keep class com.example.data.model.** { *; }

# Local backup payload (Moshi). Generated adapters are kept by Moshi's own rules; the reflective
# KotlinJsonAdapterFactory fallback reads Kotlin metadata
-keep class com.example.backup.BackupManifest { *; }
-keep class com.example.backup.FullAppBackupPayload { *; }
-keep class kotlin.Metadata { *; }

# Security Preferences & Application
-keep class com.example.security.** { *; }
-keep class com.example.CherishApplication { *; }

# Coroutines
-dontwarn kotlinx.coroutines.**

