# ==============================================================================
# Cherish Notes - R8 / ProGuard Configuration
# ==============================================================================

# Shrinking (unused code and resources out) and renaming only, which is where the size goes.
# R8's optimizer once wrote bytecode Android refused to load (VerifyError in CallRecordingViews:
# the app crashed at the end of every call, in 1.7.59 to 1.7.89), so it stays off.
-dontoptimize

# Keep line numbers and file names for readable crash stack traces
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ------------------------------------------------------------------------------
# Kotlin & Coroutines
# ------------------------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# ------------------------------------------------------------------------------
# App Data Models & Call Models (Firestore reflection & JSON serialization)
# ------------------------------------------------------------------------------
# Firestore uses reflection (toObject) which requires preserving field names,
# getters, setters, and default no-arg constructors.
-keep class com.example.data.model.** { *; }
-keepclassmembers class com.example.data.model.** { *; }
-keep class com.example.call.** { *; }
-keepclassmembers class com.example.call.** { *; }
-keep class com.example.data.local.notes.** { *; }
-keepclassmembers class com.example.data.local.notes.** { *; }
-keep class com.example.update.** { *; }
-keepclassmembers class com.example.update.** { *; }
-keep class com.example.security.** { *; }
-keepclassmembers class com.example.security.** { *; }

# ------------------------------------------------------------------------------
# Firebase (Firestore, Auth, Storage, Messaging, Functions, AppCheck)
# ------------------------------------------------------------------------------
-dontwarn com.google.firebase.**
-keep class com.google.firebase.** { *; }
-keepclassmembers class com.google.firebase.** { *; }
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
    @com.google.firebase.firestore.Exclude <fields>;
    @com.google.firebase.firestore.Exclude <methods>;
    @com.google.firebase.firestore.IgnoreExtraProperties <fields>;
    @com.google.firebase.firestore.IgnoreExtraProperties <methods>;
}

# ------------------------------------------------------------------------------
# Agora Voice RTC SDK
# ------------------------------------------------------------------------------
# Agora uses JNI native bindings and reflection across internal engine classes
-dontwarn io.agora.**
-keep class io.agora.** { *; }
-keepclassmembers class io.agora.** { *; }
-keep class io.agora.rtc2.** { *; }
-keepclassmembers class io.agora.rtc2.** { *; }
-keep class io.agora.base.** { *; }
-keepclassmembers class io.agora.base.** { *; }

# ------------------------------------------------------------------------------
# Google Play Services, Credentials & Biometric
# ------------------------------------------------------------------------------
-dontwarn com.google.android.gms.**
-keep class com.google.android.gms.** { *; }
-keep class androidx.credentials.** { *; }
-keep class androidx.biometric.** { *; }

# ------------------------------------------------------------------------------
# Room Database
# ------------------------------------------------------------------------------
-dontwarn androidx.room.**
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Entity class * { *; }

# ------------------------------------------------------------------------------
# Moshi (JSON Serialization)
# ------------------------------------------------------------------------------
-dontwarn com.squareup.moshi.**
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.JsonClass <fields>;
    @com.squareup.moshi.JsonClass <methods>;
    @com.squareup.moshi.Json <fields>;
    @com.squareup.moshi.Json <methods>;
}
-keep class *JsonAdapter { *; }
-keep class **$$JsonAdapter { *; }

# ------------------------------------------------------------------------------
# Retrofit, OkHttp & Okio
# ------------------------------------------------------------------------------
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-dontwarn okio.**
-keep class okio.** { *; }

# ------------------------------------------------------------------------------
# Jetpack Compose & Coil
# ------------------------------------------------------------------------------
# Both ship their own rules. Keeping all of Compose kept every Material icon (thousands,
# the app uses a few dozen), so the R8 builds were barely smaller than unminified ones.
-dontwarn androidx.compose.**
-dontwarn coil.**

# ------------------------------------------------------------------------------
# Android Components & Receivers
# ------------------------------------------------------------------------------
-keep class com.example.call.CallActivity { *; }
-keep class com.example.call.CallService { *; }
-keep class com.example.call.CallActionReceiver { *; }
-keep class com.example.notifications.CherishFirebaseMessagingService { *; }
-keep class com.example.notifications.BootReceiver { *; }
-keep class com.example.notifications.ScheduledMessageReceiver { *; }
-keep class com.example.notifications.CheckAfterReminderReceiver { *; }
-keep class com.example.notifications.NoteReminderReceiver { *; }
-keep class com.example.sync.** { *; }
-keepclassmembers class com.example.sync.** { *; }
-keep class com.example.ui.home.LovePerson { *; }
-keepclassmembers class com.example.ui.home.LovePerson { *; }
