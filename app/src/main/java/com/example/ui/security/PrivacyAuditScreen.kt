package com.example.ui.security

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.SecurityPreferences

/** One protection: what it does, whether it's on, and the short status shown for it. */
data class PrivacyCheckItem(
    val id: String,
    val title: String,
    val description: String,
    val isPassed: Boolean,
    val actionText: String,
    val icon: ImageVector
)

/**
 * Privacy & Security Check: an overview of the protections and whether each is on. It only shows
 * them; they're changed in Settings (Privacy & Stealth Vault), so nothing here duplicates a
 * setting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyAuditScreen(
    securityPreferences: SecurityPreferences,
    onNavigateBack: () -> Unit,
    onNavigateToLockSetup: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val checks = remember {
        val appLockOn = securityPreferences.isAppLockEnabled() && securityPreferences.hasPin()
        val lockAfter = when (val seconds = securityPreferences.getAutoLockTimeoutSeconds()) {
            0 -> "right away"
            in 1..59 -> "after $seconds sec"
            in 60..Int.MAX_VALUE -> "after ${seconds / 60} min"
            else -> "on restart"
        }
        listOf(
            PrivacyCheckItem(
                id = "disguise",
                title = "Disguise as Notes",
                description = "The app opens as a Notes app; the chat needs your secret unlock",
                isPassed = securityPreferences.isDisguiseModeEnabled(),
                actionText = if (securityPreferences.isDisguiseModeEnabled()) "On" else "Off",
                icon = Icons.Filled.EditNote
            ),
            PrivacyCheckItem(
                id = "app_lock",
                title = "App lock",
                description = if (securityPreferences.isBiometricEnabled()) "PIN or fingerprint when you come back, $lockAfter"
                else "PIN when you come back, $lockAfter",
                isPassed = appLockOn,
                actionText = if (appLockOn) "On" else "Off",
                icon = Icons.Filled.Lock
            ),
            PrivacyCheckItem(
                id = "notification",
                title = "Notification masking",
                description = "Message notifications show your masked text instead of the message",
                isPassed = securityPreferences.isHideNotificationContent(),
                actionText = if (securityPreferences.isHideNotificationContent()) "On" else "Off",
                icon = Icons.Filled.NotificationsOff
            ),
            PrivacyCheckItem(
                id = "screenshot",
                title = "Screenshot blocking",
                description = "Blocks screenshots and screen recording inside the app",
                isPassed = securityPreferences.isScreenshotProtectionEnabled(),
                actionText = if (securityPreferences.isScreenshotProtectionEnabled()) "On" else "Off",
                icon = Icons.Filled.Shield
            ),
            PrivacyCheckItem(
                id = "recent_apps",
                title = "Recent-apps cover",
                description = "The recent-apps screen shows a cover instead of your chat",
                isPassed = true,
                actionText = "Always on",
                icon = Icons.Filled.VisibilityOff
            )
        )
    }
    val passedCount = checks.count { it.isPassed }
    val allOn = passedCount == checks.size
    val accent = MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Privacy & Security Check",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("privacy_audit_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = accent.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (allOn) Icons.Filled.VerifiedUser else Icons.Filled.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "$passedCount of ${checks.size} protections on",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (allOn) "Everything is switched on." else "Switch the others on in Settings > Privacy & Stealth Vault.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            checks.forEach { check ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("privacy_check_${check.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (check.isPassed) accent.copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = check.icon,
                                contentDescription = null,
                                tint = if (check.isPassed) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = check.title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = check.description,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (check.isPassed) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = check.actionText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (check.isPassed) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onNavigateBack,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("privacy_audit_open_settings")
            ) {
                Text("Change them in Settings")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
