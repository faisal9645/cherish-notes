package com.example.ui.profile

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.positionChange
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
import androidx.compose.ui.graphics.Brush
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

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                content = content
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onNavigateBack: () -> Unit,
    onQuickDisguise: () -> Unit = {},
    onNavigateToCloudBackup: () -> Unit = {},
    onNavigateToPrivacyAudit: () -> Unit = {},
    onNavigateToStorageManager: () -> Unit = {},
    onNavigateToDeviceSessions: () -> Unit = {},
    onNavigateToOpenWhen: () -> Unit = {},
    onNavigateToChat: () -> Unit = {},
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
                val prefs = com.example.security.SecurityPreferences.getInstance(context)
                prefs.isExternalPickerActive = true
                prefs.ignoreNextPause = true
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                val prefs = com.example.security.SecurityPreferences.getInstance(context)
                prefs.isExternalPickerActive = false
                prefs.ignoreNextPause = false
                Toast.makeText(context, "Could not open camera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Camera permission needed to take photo", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchCameraForAvatar() {
        val prefs = com.example.security.SecurityPreferences.getInstance(context)
        prefs.isExternalPickerActive = true
        prefs.ignoreNextPause = true
        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val prefs = com.example.security.SecurityPreferences.getInstance(context)
        prefs.isExternalPickerActive = false
        prefs.ignoreNextPause = false
        if (uri != null) {
            viewModel.updateAvatar(uri) {
                Toast.makeText(context, "Profile photo updated ✨", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Android back button & edge gesture: re-disguise directly to Notes app for security
    BackHandler {
        onQuickDisguise()
    }

    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Scaffold(
        modifier = Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var accX = 0f
                var accY = 0f
                var directionLocked = false
                var isHorizontal = false

                while (true) {
                    val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break

                    if (change.isConsumed) {
                        break
                    }

                    val delta = change.positionChange()
                    accX += delta.x
                    accY += delta.y

                    if (!directionLocked && (kotlin.math.abs(accX) > 16f || kotlin.math.abs(accY) > 16f)) {
                        isHorizontal = kotlin.math.abs(accX) > kotlin.math.abs(accY) * 1.8f
                        directionLocked = true
                    }

                    if (directionLocked && isHorizontal) {
                        if (accX > 70f) {
                            change.consume()
                            onNavigateToChat()
                            break
                        }
                    }
                }
            }
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
                        onClick = onNavigateToChat,
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
        val primaryAccent = MaterialTheme.colorScheme.primary
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Premium Couple Profile Showcase Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_header_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF141923) else Color(0xFFFBFDFF)
                ),
                border = BorderStroke(
                    1.dp,
                    if (isDark) Color(0xFF232D3F) else Color(0xFFE2E8F0)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Subtle romantic ambient glow in header background
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        primaryAccent.copy(alpha = if (isDark) 0.18f else 0.12f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val myName = user?.displayName?.ifBlank { "Me" } ?: "Me"
                        val partnerName = partner?.displayName?.ifBlank {
                            user?.partnerEmail?.removeSuffix("@cherish.app") ?: "Partner"
                        } ?: (user?.partnerEmail?.removeSuffix("@cherish.app") ?: "Partner")

                        // Symmetrical, Intimate Couple Avatars Nestled Close Together with Center Love Heart
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy((-14).dp),
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.wrapContentWidth()
                            ) {
                                // My Avatar Column
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(contentAlignment = Alignment.BottomStart) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surface,
                                            shadowElevation = 4.dp,
                                            border = BorderStroke(2.5.dp, primaryAccent)
                                        ) {
                                            AvatarView(
                                                photoUrl = user?.photoUrl,
                                                name = myName,
                                                size = 90.dp,
                                                isOnline = user?.isOnline == true || user?.isEffectivelyOnline() == true,
                                                showOnlineBadge = true,
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .clickable { showAvatarOptionsDialog = true }
                                            )
                                        }

                                        if (uiState.isUpdating) {
                                            Box(
                                                modifier = Modifier
                                                    .size(80.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.Black.copy(alpha = 0.45f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    color = Color.White,
                                                    strokeWidth = 2.5.dp
                                                )
                                            }
                                        }

                                        // Camera edit pill on bottom-LEFT side (keeps avatars close without obstruction)
                                        Surface(
                                            shape = CircleShape,
                                            color = primaryAccent,
                                            border = BorderStroke(2.dp, if (isDark) Color(0xFF141923) else Color.White),
                                            shadowElevation = 3.dp,
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clickable { showAvatarOptionsDialog = true }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.CameraAlt,
                                                    contentDescription = "Change profile photo",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = myName,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                    // My live battery indicator
                                    val myBattery = user?.batteryLevel
                                    if (myBattery != null && myBattery in 0..100) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Text(
                                                text = if (user.isCharging) "⚡ $myBattery%" else "🔋 $myBattery%",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (user.isCharging) Color(0xFF10B981) else if (myBattery <= 20) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                // Partner Avatar Column
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val isPartnerOnline = partner?.isEffectivelyOnline() == true || partner?.isOnline == true
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surface,
                                        shadowElevation = 4.dp,
                                        border = BorderStroke(2.5.dp, HeartRed.copy(alpha = 0.8f))
                                    ) {
                                        AvatarView(
                                            photoUrl = partner?.photoUrl,
                                            name = partnerName,
                                            size = 90.dp,
                                            isOnline = isPartnerOnline,
                                            showOnlineBadge = isPartnerOnline,
                                            modifier = Modifier.clip(CircleShape)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = partnerName,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                    // Partner presence status
                                    val partnerLastSeen = partner?.lastSeen ?: 0L
                                    val partnerStatusText = when {
                                        isPartnerOnline -> "Online"
                                        partnerLastSeen > 0L -> {
                                            val diffSec = ((System.currentTimeMillis() - partnerLastSeen) / 1000).coerceAtLeast(0)
                                            when {
                                                diffSec < 60 -> "just now"
                                                diffSec < 3600 -> "${diffSec / 60}m ago"
                                                diffSec < 86400 -> "${diffSec / 3600}h ago"
                                                else -> "Offline"
                                            }
                                        }
                                        else -> "Offline"
                                    }
                                    Text(
                                        text = partnerStatusText,
                                        fontSize = 11.sp,
                                        color = if (isPartnerOnline) OnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (isPartnerOnline) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                    // Partner live battery: ONLY show when partner is online! Old battery status is hidden.
                                    val partnerBattery = partner?.batteryLevel
                                    if (isPartnerOnline && partnerBattery != null && partnerBattery in 0..100) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Text(
                                                text = if (partner.isCharging) "⚡ $partnerBattery%" else "🔋 $partnerBattery%",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (partner.isCharging) Color(0xFF10B981) else if (partnerBattery <= 20) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            // Glowing Interlocking Heart Connector at the center intersection
                            Surface(
                                shape = CircleShape,
                                color = HeartRed,
                                border = BorderStroke(2.dp, if (isDark) Color(0xFF141923) else Color.White),
                                shadowElevation = 5.dp,
                                modifier = Modifier
                                    .size(30.dp)
                                    .align(Alignment.Center)
                                    .offset(y = (-14).dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Favorite,
                                        contentDescription = "Together in Love",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // Couple Title
                        Text(
                            text = "$myName & $partnerName",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Clean Status Pill: "Strictly Connected Couple Channel"
                        Surface(
                            color = primaryAccent.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(0.8.dp, primaryAccent.copy(alpha = 0.3f)),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = primaryAccent,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Private Couple Channel • 2-Way Encrypted",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryAccent
                                )
                            }
                        }

                        if (!user?.email.isNullOrBlank()) {
                            Text(
                                text = user.email,
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Custom Bio / Love Note Quote Bubble
                        Surface(
                            color = if (isDark) Color(0xFF1A2230) else Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, if (isDark) Color(0xFF2A364F) else Color(0xFFE2E8F0)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showEditProfileDialog = true }
                                .padding(horizontal = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "“",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryAccent
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = user?.statusMessage?.ifBlank { "Loving every moment with you ✨" } ?: "Loving every moment with you ✨",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit note",
                                    tint = primaryAccent.copy(alpha = 0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Dual Action Buttons: Edit Profile & Change Photo
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showEditProfileDialog = true },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = primaryAccent,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("edit_profile_button")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Edit Profile", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = { showAvatarOptionsDialog = true },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = primaryAccent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("change_photo_button")
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Change Photo", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // 2. Couple Connection & Credentials Section
            SettingsSection(
                title = "Couple Connection",
                icon = Icons.Default.Favorite
            ) {
                ListItem(
                    headlineContent = { Text("My Username", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(user?.displayName?.ifBlank { "Me" } ?: "Me", fontSize = 13.sp)
                    },
                    leadingContent = {
                        Icon(Icons.Default.Person, contentDescription = null, tint = primaryAccent)
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)
                ListItem(
                    headlineContent = { Text("Partner's Account", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        val partnerDisplay = user?.partnerEmail?.removeSuffix("@cherish.app")
                            ?: partner?.displayName
                            ?: "Connected"
                        Text(partnerDisplay, fontSize = 13.sp)
                    },
                    leadingContent = {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = HeartRed)
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)
                ListItem(
                    headlineContent = { Text("Shared Couple Passcode", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(uiState.coupleKey.ifBlank { "CHERISH-FOREVER" }, fontSize = 13.sp) },
                    leadingContent = {
                        Icon(Icons.Default.Key, contentDescription = null, tint = primaryAccent)
                    }
                )
            }



            // 3. Privacy, Stealth Disguise & Vault
            SettingsSection(
                title = "Privacy & Stealth Vault",
                icon = Icons.Default.Security
            ) {
                ListItem(
                    headlineContent = { Text("Recover All Chats & Gallery", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Restore all older & cleared messages and all gallery items immediately", fontSize = 13.sp) },
                    leadingContent = { Icon(Icons.Filled.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        FilledTonalButton(
                            onClick = {
                                viewModel.recoverAllChatsAndGallery()
                                Toast.makeText(context, "All chats & gallery recovered ✨", Toast.LENGTH_SHORT).show()
                                onNavigateToChat()
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Recover All", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                // Notes Disguise Toggle
                ListItem(
                    headlineContent = { Text("Disguise as Notes App", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Opens as functional notes app with secret unlock gesture", fontSize = 13.sp) },
                    leadingContent = { Icon(Icons.Filled.Favorite, contentDescription = null, tint = HeartRed) },
                    trailingContent = {
                        Switch(
                            checked = uiState.isDisguiseModeEnabled,
                            onCheckedChange = { viewModel.setDisguiseMode(it) }
                        )
                    }
                )

                if (uiState.isDisguiseModeEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)
                    // Keyword Trigger Enable/Disable Switch
                    ListItem(
                        headlineContent = { Text("Search Bar Trigger Word", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Type secret word in Notes search to reveal chat", fontSize = 13.sp) },
                        leadingContent = { Icon(Icons.Default.Password, contentDescription = null, tint = primaryAccent) },
                        trailingContent = {
                            Switch(
                                checked = uiState.isKeywordTriggerEnabled,
                                onCheckedChange = { viewModel.setKeywordTriggerEnabled(it) }
                            )
                        }
                    )

                    if (uiState.isKeywordTriggerEnabled) {
                        ListItem(
                            headlineContent = { Text("Custom Trigger Word", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                            supportingContent = {
                                Text(
                                    text = "Current: " + if (isPasscodeRevealed) uiState.disguisePasscode else "••••••••",
                                    fontSize = 13.sp,
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
                                    Text("Change", fontSize = 13.sp)
                                }
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                    // Plus (+) Icon Long-Press Hold Duration Selector
                    ListItem(
                        headlineContent = { Text("Hold '+' Button to Unlock", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Text(
                                if (uiState.plusHoldDurationSec > 0)
                                    "Hold '+' for ${uiState.plusHoldDurationSec}s with haptics to reveal chat"
                                else "Long-press unlock is disabled",
                                fontSize = 13.sp
                            )
                        },
                        leadingContent = { Icon(Icons.Default.TouchApp, contentDescription = null, tint = primaryAccent) }
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
                            5 to "5s",
                            0 to "Off"
                        ).forEach { (duration, label) ->
                            val isSelected = uiState.plusHoldDurationSec == duration
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) primaryAccent else Color.Transparent)
                                    .clickable { viewModel.setPlusHoldDuration(duration) }
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

                    Spacer(modifier = Modifier.height(4.dp))

                    ListItem(
                        headlineContent = {
                            Text("Device Screen Lock After '+' Hold", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        },
                        supportingContent = {
                            Text("Require phone PIN/fingerprint before revealing chat", fontSize = 13.sp)
                        },
                        leadingContent = {
                            Icon(Icons.Default.Security, contentDescription = null, tint = primaryAccent)
                        },
                        trailingContent = {
                            Switch(
                                checked = uiState.isRequirePhoneLockAfterHold,
                                onCheckedChange = { viewModel.setRequirePhoneLockAfterHold(it) }
                            )
                        }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onQuickDisguise() },
                            modifier = Modifier.fillMaxWidth().height(38.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Outlined.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Lock Disguise", fontSize = 12.sp)
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                // Side Screen Floating Emergency Exit Toggle & Custom Invisibility / Opacity
                ListItem(
                    headlineContent = { Text("Side Floating Exit Toggle", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Discreet full-thumb switch on center-right screen edge for instant 1-tap Notes disguise", fontSize = 13.sp) },
                    leadingContent = { Icon(Icons.Default.ExitToApp, contentDescription = null, tint = HeartRed) },
                    trailingContent = {
                        Switch(
                            checked = uiState.isSideEmergencyExitEnabled,
                            onCheckedChange = { viewModel.setSideEmergencyExitEnabled(it) }
                        )
                    }
                )

                if (uiState.isSideEmergencyExitEnabled) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Toggle Visibility / Stealth: ${(uiState.sideEmergencyExitOpacity * 100).toInt()}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            // Live preview of the stealth edge toggle at current opacity!
                            Surface(
                                shape = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp, topEnd = 0.dp, bottomEnd = 0.dp),
                                color = if (isDark) Color(0xFF26272B).copy(alpha = uiState.sideEmergencyExitOpacity)
                                        else Color(0xFF1F2937).copy(alpha = uiState.sideEmergencyExitOpacity),
                                border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = (uiState.sideEmergencyExitOpacity * 0.4f).coerceIn(0.05f, 0.9f))
                                        else Color.Black.copy(alpha = (uiState.sideEmergencyExitOpacity * 0.4f).coerceIn(0.05f, 0.9f))),
                                modifier = Modifier
                                    .width(26.dp)
                                    .height(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.ExitToApp,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = uiState.sideEmergencyExitOpacity),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }

                        Slider(
                            value = uiState.sideEmergencyExitOpacity,
                            onValueChange = { viewModel.setSideEmergencyExitOpacity(it) },
                            valueRange = 0.05f..1.0f,
                            steps = 18,
                            colors = SliderDefaults.colors(
                                thumbColor = HeartRed,
                                activeTrackColor = HeartRed
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("5% (Ultra-Stealth)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("35% (Subtle)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("100% (Solid)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                // 4-Digit App Lock
                ListItem(
                    headlineContent = { Text("4-Digit Secret App Lock", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Locks the app whenever you leave it", fontSize = 13.sp) },
                    leadingContent = { Icon(Icons.Default.Lock, contentDescription = null, tint = primaryAccent) },
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

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                // Biometrics Toggle
                ListItem(
                    headlineContent = { Text("Fingerprint / Face Unlock", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Use device biometrics to unlock quickly", fontSize = 13.sp) },
                    leadingContent = { Icon(Icons.Default.Fingerprint, contentDescription = null, tint = primaryAccent) },
                    trailingContent = {
                        Switch(
                            checked = uiState.isBiometricEnabled,
                            onCheckedChange = { viewModel.setBiometric(it) }
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                // Screenshot Protection
                ListItem(
                    headlineContent = { Text("Screenshot Protection", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Block screenshots and screen recording in app", fontSize = 13.sp) },
                    leadingContent = { Icon(Icons.Default.Shield, contentDescription = null, tint = primaryAccent) },
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

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                // Push Notifications On / Off
                ListItem(
                    headlineContent = { Text("Push Notifications", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(
                            if (uiState.isNotificationsEnabled) "Receive notifications for new love messages & reminders"
                            else "All incoming notifications are muted",
                            fontSize = 13.sp
                        )
                    },
                    leadingContent = {
                        Icon(
                            if (uiState.isNotificationsEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                            contentDescription = null,
                            tint = if (uiState.isNotificationsEnabled) primaryAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = uiState.isNotificationsEnabled,
                            onCheckedChange = { viewModel.setNotificationsEnabled(context, it) }
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                // Notification Content Privacy (Mask private notification)
                ListItem(
                    headlineContent = { Text("Hide Notification Content", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Mask private messages on banner and lock screen", fontSize = 13.sp) },
                    leadingContent = { Icon(Icons.Default.NotificationsOff, contentDescription = null, tint = primaryAccent) },
                    trailingContent = {
                        Switch(
                            checked = uiState.isHideNotificationContent,
                            onCheckedChange = { viewModel.setHideNotificationContent(it) },
                            enabled = uiState.isNotificationsEnabled
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                // App Icon Badge Notification
                ListItem(
                    headlineContent = { Text("App Icon Badge", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Show unread indicator dot or count on launcher app icon", fontSize = 13.sp) },
                    leadingContent = { Icon(Icons.Default.MarkChatUnread, contentDescription = null, tint = primaryAccent) },
                    trailingContent = {
                        Switch(
                            checked = uiState.isBadgeNotificationEnabled,
                            onCheckedChange = { viewModel.setBadgeNotificationEnabled(context, it) },
                            enabled = uiState.isNotificationsEnabled
                        )
                    }
                )
            }

            // 4. Appearance & Experience Section
            SettingsSection(
                title = "Appearance & Experience",
                icon = Icons.Default.Palette
            ) {
                ListItem(
                    headlineContent = { Text("App Theme", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("System, Day, or Dark mode", fontSize = 13.sp) },
                    leadingContent = { Icon(Icons.Default.BrightnessMedium, contentDescription = null, tint = primaryAccent) }
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
                                .background(if (isSelected) primaryAccent else Color.Transparent)
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

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                ListItem(
                    headlineContent = { Text("Chat Wallpaper Style", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Select wallpaper background pattern", fontSize = 13.sp) },
                    leadingContent = { Icon(Icons.Default.Wallpaper, contentDescription = null, tint = primaryAccent) }
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
                        0 to "Classic",
                        1 to "Theme 1",
                        2 to "Theme 2"
                    ).forEach { (theme, label) ->
                        val isSelected = uiState.chatBgTheme == theme
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) primaryAccent else Color.Transparent)
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

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                ListItem(
                    headlineContent = { Text("Photo Thumbnail Size", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Grid sizing in chat photo gallery", fontSize = 13.sp) },
                    leadingContent = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = primaryAccent) }
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
                    listOf("small" to "Small", "medium" to "Medium", "large" to "Large").forEach { (key, label) ->
                        val isSelected = uiState.gallerySize == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) primaryAccent else Color.Transparent)
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

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                ListItem(
                    headlineContent = { Text("Auto-Play Media", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Play video clips and voice notes inline", fontSize = 13.sp) },
                    trailingContent = {
                        Switch(
                            checked = uiState.isAutoPlayMedia,
                            onCheckedChange = { viewModel.setAutoPlayMedia(it) }
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                ListItem(
                    headlineContent = { Text("High-Quality Photo Rendering", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Preserve full original image resolution", fontSize = 13.sp) },
                    trailingContent = {
                        Switch(
                            checked = uiState.isHighQualityMedia,
                            onCheckedChange = { viewModel.setHighQualityMedia(it) }
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                ListItem(
                    headlineContent = { Text("Double-Tap Zoom in Viewer", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Instant 2.8x zoom on photo double-tap", fontSize = 13.sp) },
                    trailingContent = {
                        Switch(
                            checked = uiState.isDoubleTapZoomEnabled,
                            onCheckedChange = { viewModel.setDoubleTapZoom(it) }
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                ListItem(
                    headlineContent = { Text("Haptic Feedback", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Tactile pulses on gestures and reactions", fontSize = 13.sp) },
                    trailingContent = {
                        Switch(
                            checked = uiState.isHapticEnabled,
                            onCheckedChange = { viewModel.setHapticEnabled(it) }
                        )
                    }
                )
            }
            // Chat Experience Mode Selector
            SettingsSection(title = "Chat Experience", icon = Icons.Default.ChatBubble) {
                val currentMode = uiState.chatExperienceMode

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // ❤️ Normal Mode option
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (currentMode == com.example.ui.chat.ChatExperienceMode.NORMAL)
                            RoseGoldPrimary.copy(alpha = 0.12f) 
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = if (currentMode == com.example.ui.chat.ChatExperienceMode.NORMAL)
                            BorderStroke(1.5.dp, RoseGoldPrimary)
                        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                viewModel.setChatExperienceMode(com.example.ui.chat.ChatExperienceMode.NORMAL) 
                                onNavigateToChat()
                            }
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                            RadioButton(
                                selected = currentMode == com.example.ui.chat.ChatExperienceMode.NORMAL,
                                onClick = { 
                                    viewModel.setChatExperienceMode(com.example.ui.chat.ChatExperienceMode.NORMAL) 
                                    onNavigateToChat()
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = RoseGoldPrimary)
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text("❤️ Normal Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    "Your full Cherish couple experience with partner identity, " +
                                    "presence and personalized love-focused interactions.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    // 🔒 Private Mode option
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (currentMode == com.example.ui.chat.ChatExperienceMode.PRIVATE)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) 
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = if (currentMode == com.example.ui.chat.ChatExperienceMode.PRIVATE)
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                viewModel.setChatExperienceMode(com.example.ui.chat.ChatExperienceMode.PRIVATE) 
                                onNavigateToChat()
                            }
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                            RadioButton(
                                selected = currentMode == com.example.ui.chat.ChatExperienceMode.PRIVATE,
                                onClick = { 
                                    viewModel.setChatExperienceMode(com.example.ui.chat.ChatExperienceMode.PRIVATE) 
                                    onNavigateToChat()
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text("🔒 Private Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    "Keep the complete chat experience while minimizing " +
                                    "partner identity and presence information.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 🎵 In-Chat Sound Effects Toggle
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(RoseGoldPrimary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (uiState.isChatSoundsEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                        contentDescription = null,
                                        tint = RoseGoldPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "In-Chat Sound Effects",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Warm acoustic chimes for sent, received, reaction & voice notes",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                            Switch(
                                checked = uiState.isChatSoundsEnabled,
                                onCheckedChange = { viewModel.setChatSoundsEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = RoseGoldPrimary
                                )
                            )
                        }
                    }
                }
            }

            // 5. Check-After & Shared Presence Section
            SettingsSection(
                title = "Check-After & Presence",
                icon = Icons.Filled.HourglassTop
            ) {
                val myActiveCheckAfter = user?.hasActiveCheckAfter() == true
                val partnerActiveCheckAfter = partner?.hasActiveCheckAfter() == true

                if (myActiveCheckAfter) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "❤️ Your Check-After is Active",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Until ${CheckAfterHelper.formatTargetTime(user?.checkAfterTimeMillis ?: 0L)}",
                            fontSize = 13.sp,
                            color = primaryAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(
                                onClick = { viewModel.extendCheckAfter(30 * 60 * 1000L) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("+30m", fontSize = 12.sp)
                            }
                            FilledTonalButton(
                                onClick = { viewModel.extendCheckAfter(60 * 60 * 1000L) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("+1h", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { showCheckAfterSheet = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Change", fontSize = 12.sp)
                            }
                            TextButton(
                                onClick = { viewModel.cancelCheckAfter() },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Cancel", fontSize = 12.sp, color = Color(0xFFD32F2F))
                            }
                        }
                    }
                } else if (partnerActiveCheckAfter) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "💕 Partner's Check-After Time",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = CheckAfterHelper.formatTargetTime(partner?.checkAfterTimeMillis ?: 0L),
                            fontSize = 14.sp,
                            color = primaryAccent,
                            fontWeight = FontWeight.Bold
                        )
                        val (remaining, isExpired) = CheckAfterHelper.calculateRemaining(partner?.checkAfterTimeMillis ?: 0L)
                        Text(
                            text = if (isExpired) "✨ You can check now" else "⏳ $remaining",
                            fontSize = 13.sp,
                            color = if (isExpired) (if (isDark) Color(0xFF86EFAC) else Color(0xFF2E7D32)) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Button(
                            onClick = { showCheckAfterSheet = true },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryAccent),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Icon(Icons.Filled.HourglassTop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Set Check-After Time", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                ListItem(
                    headlineContent = { Text("Arrival Reminder Notification", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Discreet alert when partner's quiet period completes", fontSize = 13.sp) },
                    trailingContent = {
                        Switch(
                            checked = uiState.isCheckAfterReminderEnabled,
                            onCheckedChange = { viewModel.setCheckAfterReminderEnabled(it) }
                        )
                    }
                )
            }

            // 6. Security Tools & Vault Section
            SettingsSection(
                title = "Security & Tools",
                icon = Icons.Default.VerifiedUser
            ) {
                ListItem(
                    headlineContent = { Text("Privacy & Security Audit", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("8-point automated test: auto-lock, stealth disguise & panic", fontSize = 13.sp) },
                    leadingContent = {
                        Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = primaryAccent)
                    },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    modifier = Modifier.clickable { onNavigateToPrivacyAudit() }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                ListItem(
                    headlineContent = { Text("Authorized Devices & Sessions", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("View active logins, manage pairing & remote logout", fontSize = 13.sp) },
                    leadingContent = {
                        Icon(Icons.Default.Devices, contentDescription = null, tint = primaryAccent)
                    },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    modifier = Modifier.clickable { onNavigateToDeviceSessions() }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)
                ListItem(
                    headlineContent = { Text("Storage & Cache Manager", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Clear temporary media cache without deleting chats", fontSize = 13.sp) },
                    leadingContent = {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, tint = primaryAccent)
                    },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    modifier = Modifier.clickable { onNavigateToStorageManager() }
                )
            }

            // 7. Backup & Recovery Section
            SettingsSection(
                title = "Backup & Recovery",
                icon = Icons.Default.CloudDone
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Automatic Cloud Protection",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
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

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Everything is backed up automatically. Chats, photo gallery, voice notes, and milestone memories are protected continuously.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Last Backup", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(backupState.lastBackupDate, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Protected Items", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${backupState.totalItemsBackedUp} items", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = onNavigateToCloudBackup,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(15.dp), tint = primaryAccent)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Google Drive Transfer & Details", fontSize = 12.sp, color = primaryAccent, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // 8. Updates & Logout Actions
            Button(
                onClick = { viewModel.checkForUpdates(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("update_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = primaryAccent
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = "Check for Updates",
                    tint = primaryAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Check for Updates", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }

            Button(
                onClick = {
                    viewModel.logout()
                    onLoggedOut()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("logout_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Out", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
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
                        tint = MaterialTheme.colorScheme.primary,
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
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                                val prefs = com.example.security.SecurityPreferences.getInstance(context)
                                prefs.isExternalPickerActive = true
                                prefs.ignoreNextPause = true
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                    Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
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
                                .background(MaterialTheme.colorScheme.primary)
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
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                        containerColor = MaterialTheme.colorScheme.primary,
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
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
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
                        placeholder = { Text("e.g. Faisal or Shali") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("edit_my_username_input")
                    )
                    OutlinedTextField(
                        value = partnerEmailInput,
                        onValueChange = { partnerEmailInput = it },
                        label = { Text("Partner's Account / Username") },
                        placeholder = { Text("e.g. Shali or Faisal") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("edit_partner_email_input")
                    )
                    OutlinedTextField(
                        value = coupleKeyInput,
                        onValueChange = { coupleKeyInput = it },
                        label = { Text("Couple Secret Passcode") },
                        placeholder = { Text("e.g. faisal-shali") },
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






