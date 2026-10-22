package com.example

/**
 * Entry activity for the Samsung Calculator disguise.
 * Subclasses MainActivity so all app functionality remains identical,
 * but uses Theme.MyApplication.Calculator in AndroidManifest.xml so
 * cold-start splash screen displays a neutral background without the Notes logo.
 */
class CalculatorAlias : MainActivity()
