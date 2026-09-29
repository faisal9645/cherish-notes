package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Home : Screen("home")
    object Chat : Screen("chat")
    object Memories : Screen("memories")
    object ImportantDates : Screen("important_dates")
    object SharedNotes : Screen("shared_notes")
    object SharedGallery : Screen("shared_gallery")
    object Profile : Screen("profile")
    object AppLock : Screen("app_lock")
    object LifetimeJourney : Screen("lifetime_journey")
    object CloudBackup : Screen("cloud_backup")
    object PrivacyAudit : Screen("privacy_audit")
    object OpenWhen : Screen("open_when")
    object StorageManager : Screen("storage_manager")
    object DeviceSessions : Screen("device_sessions")
}
