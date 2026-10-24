package com.example.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.CherishApplication
import com.example.ui.components.rememberAppIsResumed
import kotlinx.coroutines.delay

/** Sends only a category while the screen is visible; never sends filenames or URLs. */
@Composable
internal fun ReportChatMediaActivity(activity: String, active: Boolean = true) {
    val app = LocalContext.current.applicationContext as CherishApplication
    val owner = remember { java.util.UUID.randomUUID().toString() }
    val foreground = rememberAppIsResumed()
    val disguised by app.securityPreferences.isDisguiseActive.collectAsState()
    val user by app.authRepository.currentUserState.collectAsState()
    val hidden = user?.isActivityHidden == true
    LaunchedEffect(activity, active, foreground, disguised, hidden, user?.id) {
        if (!active || !foreground || disguised || hidden || user == null) return@LaunchedEffect
        try {
            while (true) {
                app.authRepository.setChatMediaActivity(owner, activity)
                delay(20_000)
            }
        } finally {
            app.authRepository.setChatMediaActivity(owner, null)
        }
    }
}
