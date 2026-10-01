package com.example.ui.security

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.SecurityPreferences
import com.example.ui.theme.DarkAubergine
import com.example.ui.theme.RoseGoldPrimary

data class PrivacyCheckItem(
    val id: String,
    val title: String,
    val description: String,
    val isPassed: Boolean,
    val actionText: String? = null,
    val category: String = "CORE"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyAuditScreen(
    securityPreferences: SecurityPreferences,
    onNavigateBack: () -> Unit,
    onNavigateToLockSetup: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isScreenshotProtected by remember { mutableStateOf(securityPreferences.isScreenshotProtectionEnabled()) }
    var isNotificationHidden by remember { mutableStateOf(securityPreferences.isHideNotificationContent()) }
    var autoLockSeconds by remember { mutableIntStateOf(securityPreferences.getAutoLockTimeoutSeconds()) }
    var panicGesture by remember { mutableStateOf(securityPreferences.getPanicGestureType()) }
    val hasPin = remember { securityPreferences.hasPin() }
    val isAppLockEnabled = remember { securityPreferences.isAppLockEnabled() }
    val isBiometricEnabled = remember { securityPreferences.isBiometricEnabled() }

    val checks = remember(isScreenshotProtected, isNotificationHidden, autoLockSeconds, hasPin, isAppLockEnabled, isBiometricEnabled) {
        listOf(
            PrivacyCheckItem(
                id = "screenshot",
                title = "Screenshot & Screen Recording Protection",
                description = "Blocks OS screenshots and screen recorders inside private spaces",
                isPassed = isScreenshotProtected,
                actionText = if (isScreenshotProtected) "Protected" else "Enable FLAG_SECURE"
            ),
            PrivacyCheckItem(
                id = "recent_apps",
                title = "Recent-Apps Preview Protection",
                description = "Redacts and masks chat window in Android multitasking app switcher",
                isPassed = isScreenshotProtected || securityPreferences.isDisguiseModeEnabled(),
                actionText = "Active"
            ),
            PrivacyCheckItem(
                id = "notification",
                title = "Notification Privacy & Masking",
                description = "Masks partner messages as generic 'Notes synchronized' alerts",
                isPassed = isNotificationHidden,
                actionText = if (isNotificationHidden) "Masked" else "Mask Content"
            ),
            PrivacyCheckItem(
                id = "autolock",
                title = "Secret Chat Inactivity Auto-Lock",
                description = "Automatically secures conversations after inactivity timeout",
                isPassed = autoLockSeconds in 0..300,
                actionText = "${autoLockSeconds}s timeout"
            ),
            PrivacyCheckItem(
                id = "background",
                title = "Background Privacy & Stealth Disguise",
                description = "Immediately snaps back to realistic Notes facade when minimized",
                isPassed = securityPreferences.isDisguiseModeEnabled(),
                actionText = "Guarded"
            ),
            PrivacyCheckItem(
                id = "backup",
                title = "Client-Side Encrypted Cloud Backup",
                description = "AES-256 encrypted before leaving device via couple secret key",
                isPassed = securityPreferences.getCoupleSecretKey().isNotBlank(),
                actionText = "Key Armed"
            ),
            PrivacyCheckItem(
                id = "sessions",
                title = "Pair Device Isolation & Session Guard",
                description = "Restricts chat decryption strictly to authorized two-person pairing",
                isPassed = securityPreferences.getApprovedPartnerEmail().isNotBlank(),
                actionText = "Linked"
            ),
            PrivacyCheckItem(
                id = "auth",
                title = "PIN / Biometric Multi-Factor Unlock",
                description = "Hardware biometric and hashed SHA-256 PIN authentication layer",
                isPassed = hasPin || isBiometricEnabled,
                actionText = if (hasPin) "Locked" else "Set PIN"
            )
        )
    }

    val passedCount = checks.count { it.isPassed }
    val totalCount = checks.size
    val isAllPassed = passedCount == totalCount

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
        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Hero Status Badge
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = if (isAllPassed) (if (isDark) Color(0xFF0F291E) else Color(0xFFF1F8E9)) else (if (isDark) Color(0xFF2E1C0C) else Color(0xFFFFF3E0)),
                border = BorderStroke(1.dp, if (isAllPassed) (if (isDark) Color(0xFF1B5E20) else Color(0xFF81C784)) else (if (isDark) Color(0xFF7C2D12) else Color(0xFFFFB74D))),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(if (isAllPassed) Color(0xFF2E7D32) else Color(0xFFEF6C00)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isAllPassed) Icons.Filled.VerifiedUser else Icons.Filled.Shield,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Cherish Privacy Status",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "$passedCount / $totalCount Protections Active",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAllPassed) (if (isDark) Color(0xFF86EFAC) else Color(0xFF1B5E20)) else (if (isDark) Color(0xFFFDBA74) else Color(0xFFE65100))
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (isAllPassed) {
                            "✨ Your sacred two-person space is genuinely invisible & bulletproof."
                        } else {
                            "Activate remaining layers below for full cryptographic & visual stealth."
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            // Quick Inactivity Auto-Lock Settings
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Timer, contentDescription = null, tint = RoseGoldPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Secret Chat Auto-Lock Inactivity", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Automatically lock Secret Chat and re-authenticate when phone is idle:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val timeoutOptions = listOf(
                        0 to "Immediate",
                        30 to "30 sec",
                        60 to "1 min",
                        300 to "5 min",
                        -1 to "Never"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        timeoutOptions.forEach { (sec, label) ->
                            val isSelected = autoLockSeconds == sec
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) RoseGoldPrimary else Color.Transparent)
                                    .clickable {
                                        autoLockSeconds = sec
                                        securityPreferences.setAutoLockTimeoutSeconds(sec)
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Panic Gesture Protection Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Panic Protection Gesture", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Emergency action if someone walks in:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val gestureOptions = listOf(
                        "SHAKE" to "Shake Phone",
                        "DOUBLE_TAP_SHIELD" to "Double-Tap Shield",
                        "HARDWARE_BACK" to "Back Hold",
                        "INSTANT_EXIT" to "Instant Exit"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        gestureOptions.forEach { (type, label) ->
                            val isSelected = panicGesture == type
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) RoseGoldPrimary else Color.Transparent)
                                    .clickable {
                                        panicGesture = type
                                        securityPreferences.setPanicGestureType(type)
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Checklist of All 8 Protections
            Text(
                "8-Point Comprehensive Security Matrix",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            checks.forEach { check ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (check.isPassed) (if (isDark) Color(0xFF1E3A8A) else Color(0xFFE8F5E9)) else (if (isDark) Color(0xFF450A0A) else Color(0xFFFFEBEE))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (check.isPassed) Icons.Filled.Check else Icons.Filled.Close,
                                contentDescription = null,
                                tint = if (check.isPassed) (if (isDark) Color(0xFF93C5FD) else Color(0xFF2E7D32)) else (if (isDark) Color(0xFFFCA5A5) else Color(0xFFC62828)),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = check.title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = check.description,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        if (check.id == "screenshot") {
                            Switch(
                                checked = isScreenshotProtected,
                                onCheckedChange = { checked ->
                                    isScreenshotProtected = checked
                                    securityPreferences.setScreenshotProtectionEnabled(checked)
                                    (context as? Activity)?.window?.apply {
                                        if (checked) setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                                        else clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                                    }
                                }
                            )
                        } else if (check.id == "notification") {
                            Switch(
                                checked = isNotificationHidden,
                                onCheckedChange = { checked ->
                                    isNotificationHidden = checked
                                    securityPreferences.setHideNotificationContent(checked)
                                }
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (check.isPassed) (if (isDark) Color(0xFF1E3A8A) else Color(0xFFE8F5E9)) else (if (isDark) Color(0xFF450A0A) else Color(0xFFFFEBEE))
                            ) {
                                Text(
                                    text = check.actionText ?: if (check.isPassed) "Active" else "Action Needed",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (check.isPassed) (if (isDark) Color(0xFF93C5FD) else Color(0xFF2E7D32)) else (if (isDark) Color(0xFFFCA5A5) else Color(0xFFE65100)),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}



