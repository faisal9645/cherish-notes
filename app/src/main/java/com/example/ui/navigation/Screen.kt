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
}
