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

# Security Preferences & Application
-keep class com.example.security.** { *; }
-keep class com.example.CherishApplication { *; }

# Coroutines
-dontwarn kotlinx.coroutines.**

