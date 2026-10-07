// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.test) apply false
  alias(libs.plugins.baselineprofile) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}

// Auto-restore debug.keystore and .env when cloned or pulled in external environments (e.g., Antigravity, GitHub CI)
val debugKeystore = file("${rootDir}/debug.keystore")
val base64Keystore = file("${rootDir}/debug.keystore.base64")
if (!debugKeystore.exists() && base64Keystore.exists()) {
  try {
    val decoded = java.util.Base64.getMimeDecoder().decode(base64Keystore.readText().trim())
    debugKeystore.writeBytes(decoded)
    logger.lifecycle("[AutoSetup] Restored debug.keystore from debug.keystore.base64")
  } catch (e: Exception) {
    logger.warn("[AutoSetup] Could not restore debug.keystore: ${e.message}")
  }
}

val envTarget = file("${rootDir}/.env")
val envTemplate = file("${rootDir}/.env.example")
if (!envTarget.exists() && envTemplate.exists()) {
  try {
    envTarget.writeText(envTemplate.readText())
    logger.lifecycle("[AutoSetup] Initialized .env from .env.example")
  } catch (e: Exception) {
    logger.warn("[AutoSetup] Could not initialize .env: ${e.message}")
  }
}

