// Records the Baseline Profile that release builds of :app ship with. Nothing here goes into the APK.
plugins {
  alias(libs.plugins.android.test)
  alias(libs.plugins.baselineprofile)
}

android {
  namespace = "com.example.baselineprofile"
  compileSdk = 35

  defaultConfig {
    // Profile collection needs Android 9+ (rooted) or 13+ (any device/emulator)
    minSdk = 28
    targetSdk = 35
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  targetProjectPath = ":app"
}

baselineProfile {
  // Uses whichever emulator or device is connected over adb
  useConnectedDevices = true
}

dependencies {
  implementation(libs.androidx.junit)
  implementation(libs.androidx.uiautomator)
  implementation(libs.androidx.benchmark.macro.junit4)
}
