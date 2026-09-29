package com.example.ui.profile

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AvatarView
import com.example.ui.theme.HeartRed
import com.example.ui.theme.RoseGoldPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onNavigateBack: () -> Unit,
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
                                .size(32.dp)
                                .background(RoseGoldPrimary, CircleShape)
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Change profile photo",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
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
                        // Passcode Config
                        ListItem(
                            headlineContent = { Text("Notes Secret Passcode") },
                            supportingContent = { Text("Current code: ${uiState.disguisePasscode}") },
                            leadingContent = { Icon(Icons.Default.Password, contentDescription = null) },
                            trailingContent = {
                                TextButton(onClick = {
                                    newPasscodeText = uiState.disguisePasscode
                                    showSetPasscodeDialog = true
                                }) {
                                    Text("Change")
                                }
                            }
                        )

                        // Secret Gestures Guide
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F7F9))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Secret Gestures to Open App:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "1. Type passcode (${uiState.disguisePasscode}) into the Search bar",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "2. Long-press the '+' Add Note button for 3 seconds",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

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

    // Set Passcode Dialog for Notes Disguise
    if (showSetPasscodeDialog) {
        AlertDialog(
            onDismissRequest = { showSetPasscodeDialog = false },
            title = { Text("Set Notes Secret Passcode") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter the secret passcode you can type in the Notes search bar or a note title to unlock your private space:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newPasscodeText,
                        onValueChange = {
                            if (it.length <= 10) {
                                newPasscodeText = it
                            }
                        },
                        label = { Text("Secret Passcode (e.g. love)") },
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
                    enabled = newPasscodeText.length >= 3
                ) {
                    Text("Save Passcode")
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
}
