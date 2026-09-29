package com.example.ui.profile

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AvatarView
import com.example.ui.chat.CheckAfterBottomSheet
import com.example.ui.chat.CheckAfterHelper
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCloudBackup: () -> Unit = {},
    onNavigateToPrivacyAudit: () -> Unit = {},
    onNavigateToStorageManager: () -> Unit = {},
    onNavigateToDeviceSessions: () -> Unit = {},
    onNavigateToOpenWhen: () -> Unit = {},
    onLoggedOut: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val user = uiState.currentUser
    val partner = uiState.partnerUser

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showSetPasscodeDialog by remember { mutableStateOf(false) }
    var showPairDialog by remember { mutableStateOf(false) }
    var newPinText by remember { mutableStateOf("") }
    var newPasscodeText by remember { mutableStateOf("") }
    var isPasscodeRevealed by remember { mutableStateOf(false) }
    var isDialogPasscodeRevealed by remember { mutableStateOf(false) }
    var showCheckAfterSheet by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.updateProfile(
                displayName = user?.displayName ?: "Me",
                status = user?.statusMessage ?: "",
                newPhotoUri = uri
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings & Privacy",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("profile_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        containerColor = Color.White
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // User Profile Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0F0F2)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        AvatarView(
                            photoUrl = user?.photoUrl,
                            name = user?.displayName ?: "Me",
                            size = 84.dp,
                            isOnline = true,
                            showOnlineBadge = false
                        )
                        IconButton(
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .minimumInteractiveComponentSize()
                                .background(RoseGoldPrimary, CircleShape)
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Change profile photo",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = user?.displayName?.ifBlank { "My Account" } ?: "My Account",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = user?.email ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = user?.statusMessage ?: "Together forever ✨",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoseGoldPrimary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = { showEditProfileDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("edit_profile_button")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit Name & Status")
                    }
                }
            }

            // Couple Linking Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0F0F2))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Couple Connection",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { showPairDialog = true }) {
                            Text("Change")
                        }
                    }

                    ListItem(
                        headlineContent = { Text("Partner's Account") },
                        supportingContent = {
                            Text(user?.partnerEmail?.ifBlank { partner?.email ?: "Not configured yet" } ?: "Not configured yet")
                        },
                        leadingContent = {
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = HeartRed)
                        }
                    )

                    ListItem(
                        headlineContent = { Text("Couple Secret Passcode") },
                        supportingContent = { Text(uiState.coupleKey.ifBlank { "CHERISH-FOREVER" }) },
                        leadingContent = {
                            Icon(Icons.Default.Key, contentDescription = null, tint = RoseGoldPrimary)
                        }
                    )
                }
            }

            // 🛡️ Dedicated Privacy & Security Audit Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToPrivacyAudit() }
                    .testTag("profile_privacy_audit_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9)),
                border = BorderStroke(1.dp, Color(0xFF81C784)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                ListItem(
                    headlineContent = { Text("Privacy & Security Audit", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1B5E20)) },
                    supportingContent = { Text("8-point automated test: auto-lock, stealth disguise & panic", fontSize = 12.sp, color = Color(0xFF2E7D32)) },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2E7D32)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF2E7D32))
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            // 💌 Dedicated 'Open When...' Letters Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToOpenWhen() }
                    .testTag("profile_open_when_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFF0F0F2)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                ListItem(
                    headlineContent = { Text("Open When... Envelopes 💌", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                    supportingContent = { Text("Sealed love letters locked until the right emotional moment", fontSize = 12.sp) },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SoftPinkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.MarkEmailRead, contentDescription = null, tint = RoseGoldPrimary, modifier = Modifier.size(20.dp))
                        }
                    },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = RoseGoldPrimary)
                    }
                )
            }

            // 📱 Authorized Devices & Sessions Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToDeviceSessions() }
                    .testTag("profile_device_sessions_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFF0F0F2)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                ListItem(
                    headlineContent = { Text("Authorized Devices & Sessions", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                    supportingContent = { Text("View active logins, manage pairing & remote logout", fontSize = 12.sp) },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SoftPinkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Devices, contentDescription = null, tint = RoseGoldPrimary, modifier = Modifier.size(20.dp))
                        }
                    },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = RoseGoldPrimary)
                    }
                )
            }

            // 🧹 Storage & Cache Manager Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToStorageManager() }
                    .testTag("profile_storage_manager_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFF0F0F2)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                ListItem(
                    headlineContent = { Text("Storage & Cache Manager", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                    supportingContent = { Text("Clear temporary media cache without deleting chats", fontSize = 12.sp) },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SoftPinkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, tint = RoseGoldPrimary, modifier = Modifier.size(20.dp))
                        }
                    },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = RoseGoldPrimary)
                    }
                )
            }

            // Privacy & Security Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0F0F2))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Privacy & Disguise Vault",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Notes Disguise Toggle
                    ListItem(
                        headlineContent = { Text("Disguise as Notes App") },
                        supportingContent = { Text("Opens as a functional notes app with secret gesture unlock") },
                        leadingContent = { Icon(Icons.Filled.EditNote, contentDescription = null, tint = RoseGoldPrimary) },
                        trailingContent = {
                            Switch(
                                checked = uiState.isDisguiseModeEnabled,
                                onCheckedChange = { viewModel.setDisguiseMode(it) }
                            )
                        }
                    )

                    if (uiState.isDisguiseModeEnabled) {
                        // Keyword Trigger Enable/Disable Switch
                        ListItem(
                            headlineContent = { Text("Search Bar Trigger Word") },
                            supportingContent = { Text("Type word in Notes search to open Secret Chat") },
                            leadingContent = { Icon(Icons.Default.Password, contentDescription = null, tint = RoseGoldPrimary) },
                            trailingContent = {
                                Switch(
                                    checked = uiState.isKeywordTriggerEnabled,
                                    onCheckedChange = { viewModel.setKeywordTriggerEnabled(it) }
                                )
                            }
                        )

                        // Secret Word Config (Hidden by default from normal UI)
                        if (uiState.isKeywordTriggerEnabled) {
                            ListItem(
                                headlineContent = { Text("Custom Trigger Word") },
                                supportingContent = {
                                    Text(
                                        text = "Current: " + if (isPasscodeRevealed) uiState.disguisePasscode else "••••••••",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                leadingContent = {
                                    IconButton(onClick = { isPasscodeRevealed = !isPasscodeRevealed }) {
                                        Icon(
                                            imageVector = if (isPasscodeRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (isPasscodeRevealed) "Hide trigger word" else "Reveal trigger word",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                trailingContent = {
                                    TextButton(onClick = {
                                        newPasscodeText = uiState.disguisePasscode
                                        isDialogPasscodeRevealed = false
                                        showSetPasscodeDialog = true
                                    }) {
                                        Text("Change")
                                    }
                                }
                            )
                        }

                        // Plus (+) Icon Long-Press Hold Duration Selector
                        ListItem(
                            headlineContent = { Text("Hold '+' Button to Unlock") },
                            supportingContent = { Text("Hold the Notes '+' icon with subtle haptics") },
                            leadingContent = { Icon(Icons.Default.TouchApp, contentDescription = null, tint = RoseGoldPrimary) }
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF4F4F8))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(1 to "1s", 2 to "2s (Default)", 3 to "3s", 0 to "Off").forEach { (duration, label) ->
                                val isSelected = uiState.plusHoldDurationSec == duration
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) RoseGoldPrimary else Color.Transparent)
                                        .clickable { viewModel.setPlusHoldDuration(duration) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Test Disguise Now Button
                        OutlinedButton(
                            onClick = { viewModel.triggerInstantDisguise() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Outlined.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Lock to Notes Disguise Now")
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = Color(0xFFF0F0F2)
                        )
                    }

                    // App Lock Toggle
                    ListItem(
                        headlineContent = { Text("4-Digit Secret App Lock") },
                        supportingContent = { Text("Locks the app whenever you close or leave it") },
                        leadingContent = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = uiState.isAppLockEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        showSetPinDialog = true
                                    } else {
                                        viewModel.setAppLock(false)
                                    }
                                }
                            )
                        }
                    )

                    // Biometrics Toggle
                    ListItem(
                        headlineContent = { Text("Fingerprint / Face Unlock") },
                        supportingContent = { Text("Use device biometrics to unlock quickly") },
                        leadingContent = { Icon(Icons.Default.Fingerprint, contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = uiState.isBiometricEnabled,
                                onCheckedChange = { viewModel.setBiometric(it) }
                            )
                        }
                    )

                    // Screenshot Protection Toggle
                    ListItem(
                        headlineContent = { Text("Screenshot Protection") },
                        supportingContent = { Text("Block screenshots and screen recording inside the app") },
                        leadingContent = { Icon(Icons.Default.Security, contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = uiState.isScreenshotProtectionEnabled,
                                onCheckedChange = { enabled ->
                                    viewModel.setScreenshotProtection(enabled) { isProtected ->
                                        (context as? Activity)?.window?.apply {
                                            if (isProtected) {
                                                setFlags(
                                                    WindowManager.LayoutParams.FLAG_SECURE,
                                                    WindowManager.LayoutParams.FLAG_SECURE
                                                )
                                            } else {
                                                clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    )

                    // Notification Content Privacy Toggle
                    ListItem(
                        headlineContent = { Text("Hide Notification Preview") },
                        supportingContent = { Text("Mask private messages on lock screen and banner") },
                        leadingContent = { Icon(Icons.Default.NotificationsOff, contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = uiState.isHideNotificationContent,
                                onCheckedChange = { viewModel.setHideNotificationContent(it) }
                            )
                        }
                    )
                }
            }

            // Dedicated Media & Gallery Preferences Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFF0F0F2)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SoftPinkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.PhotoLibrary,
                                contentDescription = null,
                                tint = RoseGoldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "Media & Gallery",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Adaptive Telegram-style layout & preview sizing",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Gallery Thumbnail Size Choice
                    Text(
                        "Chat Photo Thumbnail Size",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFF4F4F8))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("small" to "Small", "medium" to "Medium", "large" to "Large").forEach { (key, label) ->
                            val isSelected = uiState.gallerySize == key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) RoseGoldPrimary else Color.Transparent)
                                    .clickable { viewModel.setImageGallerySize(key) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Auto-Play Media
                    ListItem(
                        headlineContent = { Text("Auto-Play Media") },
                        supportingContent = { Text("Play videos and GIFs automatically in chat") },
                        trailingContent = {
                            Switch(
                                checked = uiState.isAutoPlayMedia,
                                onCheckedChange = { viewModel.setAutoPlayMedia(it) }
                            )
                        }
                    )

                    // High-Quality Loading
                    ListItem(
                        headlineContent = { Text("High-Quality Photo Rendering") },
                        supportingContent = { Text("Preserve sharp details and full resolution") },
                        trailingContent = {
                            Switch(
                                checked = uiState.isHighQualityMedia,
                                onCheckedChange = { viewModel.setHighQualityMedia(it) }
                            )
                        }
                    )

                    // Double-Tap Zoom in Viewer
                    ListItem(
                        headlineContent = { Text("Double-Tap Zoom in Viewer") },
                        supportingContent = { Text("Instantly toggle fit and 2.8x zoom on double-tap") },
                        trailingContent = {
                            Switch(
                                checked = uiState.isDoubleTapZoomEnabled,
                                onCheckedChange = { viewModel.setDoubleTapZoom(it) }
                            )
                        }
                    )

                    // Haptic Feedback
                    ListItem(
                        headlineContent = { Text("Haptic Feedback") },
                        supportingContent = { Text("Tactile pulses on gestures, reactions & secret unlocks") },
                        trailingContent = {
                            Switch(
                                checked = uiState.isHapticEnabled,
                                onCheckedChange = { viewModel.setHapticEnabled(it) }
                            )
                        }
                    )
                }
            }

            // Check After & Presence Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_check_after_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFF0F0F2)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SoftPinkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.HourglassTop, contentDescription = null, tint = RoseGoldPrimary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Check After & Presence",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Reduce checking anxiety with peaceful sync",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val myActiveCheckAfter = user?.hasActiveCheckAfter() == true
                    val partnerActiveCheckAfter = partner?.hasActiveCheckAfter() == true

                    if (myActiveCheckAfter) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SoftPinkSurfaceVariant,
                            border = BorderStroke(1.dp, SoftBorderOutline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "❤️ Your Check-After is Active",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = DarkAubergine
                                )
                                Text(
                                    text = "Until ${CheckAfterHelper.formatTargetTime(user?.checkAfterTimeMillis ?: 0L)}",
                                    fontSize = 12.sp,
                                    color = RoseGoldPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilledTonalButton(
                                        onClick = { viewModel.extendCheckAfter(30 * 60 * 1000L) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("+30m", fontSize = 11.sp)
                                    }
                                    FilledTonalButton(
                                        onClick = { viewModel.extendCheckAfter(60 * 60 * 1000L) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("+1h", fontSize = 11.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { showCheckAfterSheet = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Change", fontSize = 11.sp)
                                    }
                                    TextButton(
                                        onClick = { viewModel.cancelCheckAfter() },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Cancel", fontSize = 11.sp, color = Color(0xFFD32F2F))
                                    }
                                }
                            }
                        }
                    } else if (partnerActiveCheckAfter) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SoftPinkSurfaceVariant,
                            border = BorderStroke(1.dp, SoftBorderOutline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "💕 Partner's Check-After Time",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = DarkAubergine
                                )
                                Text(
                                    text = "${CheckAfterHelper.formatTargetTime(partner?.checkAfterTimeMillis ?: 0L)}",
                                    fontSize = 13.sp,
                                    color = RoseGoldPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                val (remaining, isExpired) = CheckAfterHelper.calculateRemaining(partner?.checkAfterTimeMillis ?: 0L)
                                Text(
                                    text = if (isExpired) "✨ You can check now" else "⏳ $remaining",
                                    fontSize = 12.sp,
                                    color = if (isExpired) Color(0xFF2E7D32) else DarkAubergine
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = { showCheckAfterSheet = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RoseGoldPrimary),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Icon(Icons.Filled.HourglassTop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Set Check-After Time", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Notification Reminder
                    ListItem(
                        headlineContent = { Text("Arrival Reminder Notification", fontSize = 14.sp) },
                        supportingContent = { Text("Subtle alert when partner's check-after period completes", fontSize = 12.sp) },
                        trailingContent = {
                            Switch(
                                checked = uiState.isCheckAfterReminderEnabled,
                                onCheckedChange = { viewModel.setCheckAfterReminderEnabled(it) }
                            )
                        }
                    )
                }
            }

            // Google Drive Cloud Backup & Transfer
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToCloudBackup() }
                    .testTag("profile_cloud_backup_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFF0F0F2)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                ListItem(
                    headlineContent = { Text("Google Drive Backup & Restore", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                    supportingContent = { Text("Transfer all chats, gallery & memories to a new mobile", fontSize = 12.sp) },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SoftPinkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CloudDone, contentDescription = null, tint = RoseGoldPrimary, modifier = Modifier.size(22.dp))
                        }
                    },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = RoseGoldPrimary)
                    }
                )
            }

            // Logout Button
            Button(
                onClick = {
                    viewModel.logout()
                    onLoggedOut()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("logout_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = HeartRed
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Out of Our Space", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Edit Profile Dialog
    if (showEditProfileDialog) {
        var nameInput by remember { mutableStateOf(user?.displayName ?: "") }
        var statusInput by remember { mutableStateOf(user?.statusMessage ?: "") }

        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Edit Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = statusInput,
                        onValueChange = { statusInput = it },
                        label = { Text("Status / Sweet Note") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateProfile(nameInput, statusInput, null)
                        showEditProfileDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Set PIN Dialog
    if (showSetPinDialog) {
        AlertDialog(
            onDismissRequest = { showSetPinDialog = false },
            title = { Text("Set 4-Digit Secret PIN") },
            text = {
                Column {
                    Text("Enter a 4-digit numeric code to protect your chat:")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPinText,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPinText = it },
                        label = { Text("4-Digit PIN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPinText.length == 4) {
                            viewModel.setAppLock(true, newPinText)
                            newPinText = ""
                            showSetPinDialog = false
                        }
                    },
                    enabled = newPinText.length == 4
                ) {
                    Text("Enable Lock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetPinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Set Passcode Dialog for Notes Disguise (Hidden input with reveal toggle)
    if (showSetPasscodeDialog) {
        AlertDialog(
            onDismissRequest = { showSetPasscodeDialog = false },
            title = { Text("Set Secret Trigger Word") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Set your private trigger word. Typing this into the Notes search bar will immediately open Secret Chat without showing the word.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newPasscodeText,
                        onValueChange = {
                            if (it.length <= 15) {
                                newPasscodeText = it
                            }
                        },
                        label = { Text("Secret Trigger Word (e.g. love)") },
                        visualTransformation = if (isDialogPasscodeRevealed) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isDialogPasscodeRevealed = !isDialogPasscodeRevealed }) {
                                Icon(
                                    imageVector = if (isDialogPasscodeRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isDialogPasscodeRevealed) "Hide" else "Show"
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPasscodeText.isNotBlank()) {
                            viewModel.setDisguisePasscode(newPasscodeText)
                            showSetPasscodeDialog = false
                        }
                    },
                    enabled = newPasscodeText.length >= 2
                ) {
                    Text("Save Trigger Word")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetPasscodeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Pair Dialog
    if (showPairDialog) {
        var partnerEmailInput by remember { mutableStateOf(user?.partnerEmail ?: "") }
        var coupleKeyInput by remember { mutableStateOf(uiState.coupleKey) }

        AlertDialog(
            onDismissRequest = { showPairDialog = false },
            title = { Text("Couple Connection") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = partnerEmailInput,
                        onValueChange = { partnerEmailInput = it },
                        label = { Text("Partner's Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = coupleKeyInput,
                        onValueChange = { coupleKeyInput = it },
                        label = { Text("Couple Secret Passcode") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updatePartnerEmailAndKey(partnerEmailInput, coupleKeyInput)
                        showPairDialog = false
                    }
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPairDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Check-After Setup & Modification Bottom Sheet
    CheckAfterBottomSheet(
        isOpen = showCheckAfterSheet,
        onDismiss = { showCheckAfterSheet = false },
        currentTargetMillis = user?.checkAfterTimeMillis,
        currentNote = user?.checkAfterNote,
        isCurrentlyActive = user?.hasActiveCheckAfter() == true,
        isSetByMe = true,
        partnerName = partner?.displayName?.ifBlank { "My Partner" } ?: "My Partner",
        isReminderEnabled = uiState.isCheckAfterReminderEnabled,
        onToggleReminder = { viewModel.setCheckAfterReminderEnabled(it) },
        onSaveCheckAfter = { target, note ->
            viewModel.setCheckAfter(target, note)
        },
        onCancelCheckAfter = {
            viewModel.cancelCheckAfter()
        },
        onExtend = { duration ->
            viewModel.extendCheckAfter(duration)
        }
    )
}

