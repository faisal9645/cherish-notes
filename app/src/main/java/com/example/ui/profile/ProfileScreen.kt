package com.example.ui.profile

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val updateState by viewModel.updateState.collectAsState()
    val backupState by viewModel.backupState.collectAsState()
    val user = uiState.currentUser
    val partner = uiState.partnerUser
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.reloadSettings()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showAvatarOptionsDialog by remember { mutableStateOf(false) }
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showSetPasscodeDialog by remember { mutableStateOf(false) }
    var showPairDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var newPinText by remember { mutableStateOf("") }
    var newPasscodeText by remember { mutableStateOf("") }
    var isPasscodeRevealed by remember { mutableStateOf(false) }
    var isDialogPasscodeRevealed by remember { mutableStateOf(false) }
    var showCheckAfterSheet by remember { mutableStateOf(false) }

    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            tempCameraUri?.let { uri ->
                viewModel.updateAvatar(uri) {
                    Toast.makeText(context, "Profile photo updated ✨", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val imagesDir = File(context.cacheDir, "avatar_snaps").apply { mkdirs() }
                val photoFile = File(imagesDir, "snap_${System.currentTimeMillis()}.jpg").apply {
                    createNewFile()
                }
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    photoFile
                )
                tempCameraUri = uri
                com.example.security.SecurityPreferences.getInstance(context).ignoreNextPause = true
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open camera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Camera permission needed to take photo", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchCameraForAvatar() {
        com.example.security.SecurityPreferences.getInstance(context).ignoreNextPause = true
        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.updateAvatar(uri) {
                Toast.makeText(context, "Profile photo updated ✨", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Scaffold(
        modifier = Modifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { _ -> dragAccumulator = 0f },
                onDragEnd = {
                    if (dragAccumulator > 80f) {
                        onNavigateBack()
                    }
                    dragAccumulator = 0f
                },
                onHorizontalDrag = { _, dragAmount ->
                    dragAccumulator += dragAmount
                }
            )
        },
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
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                            size = 88.dp,
                            isOnline = true,
                            showOnlineBadge = false,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { showAvatarOptionsDialog = true }
                        )

                        if (uiState.isUpdating) {
                            Box(
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    strokeWidth = 3.dp
                                )
                            }
                        }

                        IconButton(
                            onClick = { showAvatarOptionsDialog = true },
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

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showEditProfileDialog = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = user?.displayName?.ifBlank { "My Account" } ?: "My Account",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Profile",
                            tint = RoseGoldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = user?.email ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        color = RoseGoldPrimary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.clickable { showEditProfileDialog = true }
                    ) {
                        Text(
                            text = user?.statusMessage?.ifBlank { "Together forever ✨" } ?: "Together forever ✨",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RoseGoldPrimary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { showEditProfileDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoseGoldPrimary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("edit_profile_button")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit Profile & Note")
                    }
                }
            }

            // User Login & Couple Connection Settings
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("user_login_settings_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = RoseGoldPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "User Login Settings",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isDark) Color(0xFF1E3A8A) else Color(0xFFE8F5E9)
                        ) {
                            Text(
                                text = "Logged In",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color(0xFF93C5FD) else Color(0xFF2E7D32),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    ListItem(
                        headlineContent = { Text("My Username") },
                        supportingContent = {
                            Text(user?.displayName?.ifBlank { "Me" } ?: "Me", fontWeight = FontWeight.SemiBold)
                        },
                        leadingContent = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = RoseGoldPrimary)
                        }
                    )

                    ListItem(
                        headlineContent = { Text("Partner's Account") },
                        supportingContent = {
                            val partnerDisplay = user?.partnerEmail?.removeSuffix("@cherish.app")
                                ?: partner?.displayName
                                ?: "Connected"
                            Text(partnerDisplay, fontWeight = FontWeight.SemiBold)
                        },
                        leadingContent = {
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = HeartRed)
                        }
                    )

                    ListItem(
                        headlineContent = { Text("Couple Secret Passcode") },
                        supportingContent = { Text(uiState.coupleKey.ifBlank { "CHERISH-FOREVER" }, fontWeight = FontWeight.SemiBold) },
                        leadingContent = {
                            Icon(Icons.Default.Key, contentDescription = null, tint = RoseGoldPrimary)
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showPairDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("manage_login_credentials_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.ManageAccounts, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit Login", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.logout()
                                onLoggedOut()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("settings_logout_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Switch Account", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // 🛡️ Dedicated Privacy & Security Audit Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToPrivacyAudit() }
                    .testTag("profile_privacy_audit_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF1F8E9)),
                border = BorderStroke(1.dp, if (isDark) MaterialTheme.colorScheme.outlineVariant else Color(0xFF81C784)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                ListItem(
                    headlineContent = { Text("Privacy & Security Audit", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF1B5E20)) },
                    supportingContent = { Text("8-point automated test: auto-lock, stealth disguise & panic", fontSize = 12.sp, color = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF2E7D32)) },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color(0xFF1E3A8A) else Color(0xFF2E7D32)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF2E7D32))
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                                .background(MaterialTheme.colorScheme.surfaceVariant),
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                                .background(MaterialTheme.colorScheme.surfaceVariant),
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                                .background(MaterialTheme.colorScheme.surfaceVariant),
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                            supportingContent = {
                                Text(
                                    if (uiState.plusHoldDurationSec > 0)
                                        "Hold '+' for ${uiState.plusHoldDurationSec}s with subtle haptics to reveal secret chat"
                                    else "Long-press unlock is currently disabled"
                                )
                            },
                            leadingContent = { Icon(Icons.Default.TouchApp, contentDescription = null, tint = RoseGoldPrimary) }
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                1 to "1s",
                                2 to "2s",
                                3 to "3s",
                                5 to "5s (Secure)",
                                0 to "Off"
                            ).forEach { (duration, label) ->
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

                        // Fine-tuned Duration Slider for Full Dynamic Customization
                        if (uiState.plusHoldDurationSec > 0) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Custom Duration",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "${uiState.plusHoldDurationSec} seconds",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RoseGoldPrimary
                                    )
                                }
                                Slider(
                                    value = uiState.plusHoldDurationSec.toFloat(),
                                    onValueChange = { viewModel.setPlusHoldDuration(it.toInt()) },
                                    valueRange = 1f..10f,
                                    steps = 8,
                                    colors = SliderDefaults.colors(
                                        thumbColor = RoseGoldPrimary,
                                        activeTrackColor = RoseGoldPrimary
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Phone Lock (Password / PIN / Pattern) & Fingerprint after Hold Toggle
                        ListItem(
                            headlineContent = {
                                Text(
                                    "Phone Lock / Fingerprint After '+' Hold",
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            supportingContent = {
                                Text(
                                    "Prompt device screen lock (PIN, Password, Pattern) or fingerprint after hold before opening secret chat"
                                )
                            },
                            leadingContent = {
                                Icon(Icons.Default.Security, contentDescription = null, tint = RoseGoldPrimary)
                            },
                            trailingContent = {
                                Switch(
                                    checked = uiState.isRequirePhoneLockAfterHold,
                                    onCheckedChange = { viewModel.setRequirePhoneLockAfterHold(it) }
                                )
                            }
                        )

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
                        
                        Spacer(modifier = Modifier.height(4.dp))

                        // Recover & Show Everything Button
                        OutlinedButton(
                            onClick = {
                                viewModel.revealSecretHistory()
                                Toast.makeText(context, "Chat history recovered", Toast.LENGTH_SHORT).show()
                                onNavigateBack()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, GoldMilestone.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(18.dp), tint = GoldMilestone)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Recover & Show Everything", color = GoldMilestone)
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                    
                    // Theme Mode Selector
                    ListItem(
                        headlineContent = { Text("App Theme (Day / Night)") },
                        supportingContent = { Text("Select light or dark mode") },
                        leadingContent = { Icon(Icons.Default.BrightnessMedium, contentDescription = null, tint = RoseGoldPrimary) }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            0 to "System",
                            1 to "Day",
                            2 to "Dark"
                        ).forEach { (mode, label) ->
                            val isSelected = uiState.themeMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) RoseGoldPrimary else Color.Transparent)
                                    .clickable { viewModel.setThemeMode(mode) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Chat Background Theme Selector
                    ListItem(
                        headlineContent = { Text("Chat Background") },
                        supportingContent = { Text("Select wallpaper style for both Day & Night modes") },
                        leadingContent = { Icon(Icons.Default.Wallpaper, contentDescription = null, tint = RoseGoldPrimary) }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            0 to "Normal",
                            1 to "Theme 1",
                            2 to "Theme 2"
                        ).forEach { (theme, label) ->
                            val isSelected = uiState.chatBgTheme == theme
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) RoseGoldPrimary else Color.Transparent)
                                    .clickable { viewModel.setChatBgTheme(theme) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                                .background(MaterialTheme.colorScheme.surfaceVariant),
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
                            .background(MaterialTheme.colorScheme.surfaceVariant)
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                                .background(MaterialTheme.colorScheme.surfaceVariant),
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
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "❤️ Your Check-After is Active",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
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
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "💕 Partner's Check-After Time",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
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
                                    color = if (isExpired) (if (isDark) Color(0xFF86EFAC) else Color(0xFF2E7D32)) else MaterialTheme.colorScheme.onSurfaceVariant
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

            // ☁️ Always Automatic Backup & Recovery Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_cloud_backup_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(RoseGoldPrimary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.CloudDone, contentDescription = null, tint = RoseGoldPrimary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Backup & Recovery",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFE8F5E9)
                        ) {
                            Text(
                                text = "Always Active",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Everything is backed up automatically. All your chat messages, photo gallery, voice notes, and milestone memories are protected continuously.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Last Cloud Backup", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(backupState.lastBackupDate, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Items Protected", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${backupState.totalItemsBackedUp} items", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Buttons: Backup Everything Now & Recover Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.backupNow { success, msg ->
                                    Toast.makeText(context, if (success) "Backup completed! Everything is saved ✨" else msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !backupState.isBackingUp && !backupState.isRestoring,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("backup_now_settings_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (backupState.isBackingUp) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Backing up...", fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Backup Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = { showRestoreConfirmDialog = true },
                            enabled = !backupState.isBackingUp && !backupState.isRestoring,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("recover_settings_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RoseGoldPrimary)
                        ) {
                            if (backupState.isRestoring) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Recovering...", fontSize = 12.sp, color = Color.White)
                            } else {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Recover", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = onNavigateToCloudBackup,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp), tint = RoseGoldPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Google Drive Transfer & Details", fontSize = 12.sp, color = RoseGoldPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // App Update Button
            Button(
                onClick = {
                    viewModel.checkForUpdates(context)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("update_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = "Check for Updates",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Check for Updates", fontWeight = FontWeight.SemiBold)
            }
            
            Spacer(modifier = Modifier.height(12.dp))

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
                    contentColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Out", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // App Updates Dialog (Modern System Update Icon)
    if (updateState.showDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateDialog() },
            title = null,
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "System Update",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (updateState.isChecking) {
                            "Checking for Updates..."
                        } else if (updateState.isUpdateAvailable) {
                            "Update Available!"
                        } else {
                            "App is Up to Date"
                        },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Version ${updateState.latestVersion}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RoseGoldPrimary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (updateState.isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = RoseGoldPrimary,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = if (updateState.isUpdateAvailable) "What's New in this update:" else "Status:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = updateState.releaseNotes,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 19.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (updateState.isUpdateAvailable && !updateState.downloadUrl.isNullOrEmpty()) {
                    Button(
                        onClick = {
                            viewModel.dismissUpdateDialog()
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(updateState.downloadUrl)
                            )
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoseGoldPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Download Update", fontWeight = FontWeight.Bold)
                    }
                } else if (!updateState.isChecking) {
                    Button(
                        onClick = { viewModel.dismissUpdateDialog() },
                        colors = ButtonDefaults.buttonColors(containerColor = RoseGoldPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("OK", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                if (updateState.isUpdateAvailable) {
                    TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                        Text("Later", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Avatar Options Dialog (Camera, Gallery, Remove)
    if (showAvatarOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showAvatarOptionsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = RoseGoldPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Profile Photo", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Update your profile picture visible to your partner:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // Option 1: Camera
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAvatarOptionsDialog = false
                                launchCameraForAvatar()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = RoseGoldPrimary)
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text("Take Photo", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text("Use camera to snap a new picture", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Option 2: Gallery
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAvatarOptionsDialog = false
                                com.example.security.SecurityPreferences.getInstance(context).ignoreNextPause = true
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = RoseGoldPrimary)
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text("Choose from Gallery", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text("Select an image from device gallery", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Option 3: Remove Photo (if user has photo)
                    if (!user?.photoUrl.isNullOrEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAvatarOptionsDialog = false
                                    viewModel.removeAvatar {
                                        Toast.makeText(context, "Profile photo removed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text("Remove Photo", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                                    Text("Reset to default initial avatar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAvatarOptionsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Profile Dialog
    if (showEditProfileDialog) {
        var nameInput by remember { mutableStateOf(user?.displayName ?: "") }
        var statusInput by remember { mutableStateOf(user?.statusMessage ?: "") }

        val statusSuggestions = listOf(
            "Loving every moment with you ✨",
            "Forever yours 💕",
            "Thinking of you always 💭",
            "Miss you so much 🥰",
            "Working hard, talk soon! 💼",
            "Together forever & always 💍"
        )

        AlertDialog(
            onDismissRequest = {
                if (!uiState.isUpdating) showEditProfileDialog = false
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = RoseGoldPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit Profile", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Tap avatar to change photo
                    Box(
                        contentAlignment = Alignment.BottomEnd,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        AvatarView(
                            photoUrl = user?.photoUrl,
                            name = nameInput.ifBlank { user?.displayName ?: "Me" },
                            size = 76.dp,
                            isOnline = true,
                            showOnlineBadge = false,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    showAvatarOptionsDialog = true
                                }
                        )
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(RoseGoldPrimary)
                                .clickable { showAvatarOptionsDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Change photo",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Text(
                        "Tap photo to change or remove",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Display Name") },
                        placeholder = { Text("Your name") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = RoseGoldPrimary)
                        },
                        trailingIcon = {
                            if (nameInput.isNotBlank()) {
                                IconButton(onClick = { nameInput = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = statusInput,
                        onValueChange = { if (it.length <= 80) statusInput = it },
                        label = { Text("Status / Sweet Note") },
                        placeholder = { Text("Loving every moment with you ✨") },
                        leadingIcon = {
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = RoseGoldPrimary)
                        },
                        supportingText = {
                            Text("${statusInput.length}/80", style = MaterialTheme.typography.labelSmall)
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Quick Suggestions:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            statusSuggestions.forEach { suggestion ->
                                FilterChip(
                                    selected = statusInput == suggestion,
                                    onClick = { statusInput = suggestion },
                                    label = { Text(suggestion, style = MaterialTheme.typography.bodySmall) },
                                    shape = RoundedCornerShape(20.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalName = nameInput.trim().ifBlank { user?.displayName ?: "Me" }
                        val finalStatus = statusInput.trim().ifBlank { "Loving every moment with you ✨" }
                        viewModel.updateProfile(
                            displayName = finalName,
                            status = finalStatus,
                            newPhotoUri = null,
                            removePhoto = false
                        ) {
                            Toast.makeText(context, "Profile updated successfully ✨", Toast.LENGTH_SHORT).show()
                            showEditProfileDialog = false
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoseGoldPrimary,
                        contentColor = Color.White
                    ),
                    enabled = !uiState.isUpdating
                ) {
                    if (uiState.isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEditProfileDialog = false },
                    enabled = !uiState.isUpdating
                ) {
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

    // User Login Settings Dialog
    if (showPairDialog) {
        var usernameInput by remember { mutableStateOf(user?.displayName ?: "") }
        var partnerEmailInput by remember { mutableStateOf(user?.partnerEmail ?: partner?.displayName ?: "") }
        var coupleKeyInput by remember { mutableStateOf(uiState.coupleKey) }

        AlertDialog(
            onDismissRequest = { showPairDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = RoseGoldPrimary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("User Login Settings", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Update your username and shared couple connection credentials.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = { usernameInput = it },
                        label = { Text("My Username") },
                        placeholder = { Text("e.g. Faisal") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("edit_my_username_input")
                    )
                    OutlinedTextField(
                        value = partnerEmailInput,
                        onValueChange = { partnerEmailInput = it },
                        label = { Text("Partner's Account / Email") },
                        placeholder = { Text("e.g. partner@cherish.app") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("edit_partner_email_input")
                    )
                    OutlinedTextField(
                        value = coupleKeyInput,
                        onValueChange = { coupleKeyInput = it },
                        label = { Text("Couple Secret Passcode") },
                        placeholder = { Text("e.g. CHERISH-FOREVER") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("edit_couple_key_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateLoginCredentials(usernameInput, partnerEmailInput, coupleKeyInput) { success ->
                            Toast.makeText(context, if (success) "Login settings updated successfully! ✨" else "Update failed", Toast.LENGTH_SHORT).show()
                        }
                        showPairDialog = false
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save & Sync")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPairDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Recover from Cloud Backup Dialog inside Settings
    if (showRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmDialog = false },
            icon = {
                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = RoseGoldPrimary, modifier = Modifier.size(32.dp))
            },
            title = {
                Text("Recover All Data", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Recovering will restore all chat messages, shared photo gallery, voice notes, memories, important dates, and shared notes from your backup.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Last Backup:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(backupState.lastBackupDate, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Items Protected:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${backupState.totalItemsBackedUp} items", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirmDialog = false
                        viewModel.restoreNow { success, msg ->
                            Toast.makeText(context, if (success) "Recovery complete! Everything restored ✨" else msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoseGoldPrimary)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Recover Now", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmDialog = false }) {
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





