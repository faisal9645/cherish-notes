import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.cherish.notes"
    minSdk = 24
    targetSdk = 35
    versionCode = 231
    versionName = "1.8.59"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    resourceConfigurations += listOf("en")
    // 64-bit phones only (both phones, a Galaxy S24 and a Vivo V29 Pro, are): the call audio
    // engine's native code is large, and a 32-bit copy of it added 8 MB to every update
    ndk { abiFilters += listOf("arm64-v8a") }
  }

  // Native code compressed in the APK (unpacked at install), so updates download less
  packaging { jniLibs { useLegacyPackaging = true } }

  signingConfigs {
    val envPath = System.getenv("KEYSTORE_PATH")?.trim()
    val releaseKeyFile = if (!envPath.isNullOrEmpty()) file(envPath) else file("${rootDir}/my-upload-key.jks")
    if (releaseKeyFile.exists() && !System.getenv("STORE_PASSWORD").isNullOrBlank()) {
      create("release") {
        storeFile = releaseKeyFile
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD") ?: System.getenv("STORE_PASSWORD")
      }
    }
    create("debugConfig") {
      val localDebugKeystore = file("${rootDir}/debug.keystore")
      val userHomeDebugKeystore = file("${System.getProperty("user.home")}/.android/debug.keystore")
      storeFile = if (localDebugKeystore.exists()) localDebugKeystore else userHomeDebugKeystore
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  lint {
    abortOnError = false
    checkReleaseBuilds = false
  }

  buildTypes {
    release {
      isCrunchPngs = true
      // R8 shrinks and renames for a smaller app; it doesn't optimize (see proguard-rules.pro)
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
      val hasReleaseConfig = signingConfigs.findByName("release") != null
      signingConfig = if (hasReleaseConfig) signingConfigs.getByName("release") else signingConfigs.getByName("debugConfig")
    }
    debug {
      isMinifyEnabled = false
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}



// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation("androidx.compose.foundation:foundation")
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.profileinstaller)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.coil.video)
  // Animated GIFs (keyboard GIFs and picked ones) move in the chat and the gallery
  implementation(libs.coil.gif)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.auth)
  implementation(libs.firebase.storage)
  implementation(libs.firebase.messaging)
  implementation(libs.firebase.functions)
  implementation(libs.agora.voice.rtc.basic)
  implementation(libs.agora.ains)
  // Makes videos and call recordings smaller before they upload (the phone's own encoders)
  implementation(libs.androidx.media3.transformer)
  implementation(libs.androidx.media3.effect)
  implementation(libs.androidx.media3.common)
  // Plays video notes and videos; already in the app through the transformer, so it adds no size
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.biometric)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // JSON for backups and the chat's offline copy: adapters generated at build time (ksp below),
  // so no kotlin-reflect (about 1,800 classes) in the app. Retrofit, OkHttp's logger and App Check
  // were never used and are gone (Coil brings its own OkHttp).
  implementation(libs.moshi)
  // implementation(libs.play.services.location)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
