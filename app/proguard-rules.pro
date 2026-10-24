# ==============================================================================
# Cherish Notes - R8 configuration
# ==============================================================================

# Shrinking (unused code and resources out) and renaming only, which is where the size goes.
# R8's optimizer once wrote bytecode Android refused to load (VerifyError in CallRecordingViews:
# the app crashed at the end of every call, in 1.7.59 to 1.7.89), so it stays off.
-dontoptimize

# Keep line numbers and file names for readable crash stack traces
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Libraries (Firebase, Google Play services, Compose, coroutines, Room, Moshi, Coil, Media3, OkHttp)
# ship their own rules and aren't kept whole here, so R8 leaves out what the app doesn't use.
# (Keeping them whole made the minified app barely smaller than an unminified one.)
-dontwarn kotlinx.coroutines.**
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
-dontwarn androidx.compose.**
-dontwarn coil.**
-dontwarn androidx.room.**
-dontwarn com.squareup.moshi.**
-dontwarn okhttp3.**
-dontwarn okio.**

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
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
    @com.google.firebase.firestore.Exclude <fields>;
    @com.google.firebase.firestore.Exclude <methods>;
    @com.google.firebase.firestore.IgnoreExtraProperties <fields>;
    @com.google.firebase.firestore.IgnoreExtraProperties <methods>;
}

# Backups and the chat's offline copy: Moshi finds each generated adapter by its class's name
# (the rules Moshi generates per class keep both; this is a backstop. BackupJsonTest checks every
# class has an adapter)
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
    @com.squareup.moshi.Json <methods>;
}
-keepnames @com.squareup.moshi.JsonClass class **
-keep class **JsonAdapter { <init>(...); }

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
# Google sign-in (Credential Manager) & Biometric
# ------------------------------------------------------------------------------
# Credential Manager loads its Google Play provider by class name
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class androidx.biometric.** { *; }

# ------------------------------------------------------------------------------
# Room Database (the app's own database, entities and DAOs)
# ------------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Entity class * { *; }

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
