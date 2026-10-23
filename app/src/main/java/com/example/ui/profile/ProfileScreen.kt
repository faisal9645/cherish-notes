package com.example.ui.profile

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.SettingsPower
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MarkChatUnread
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.PhonelinkRing
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.notifications.NotificationHelper
import com.example.security.SecurityPreferences
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AvatarView
import com.example.ui.chat.CheckAfterBottomSheet
import com.example.ui.chat.CheckAfterHelper
import com.example.ui.theme.*
import com.example.ui.components.CherishToast
import com.example.ui.components.CherishAlertDialog
import com.example.ui.components.CherishButton
import com.example.ui.components.CherishOutlinedButton
import com.example.ui.components.CherishTonalButton
import com.example.ui.components.pressScaleTarget
import com.example.ui.components.springClickable
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import com.example.ui.components.CherishFilterChip
import com.example.ui.components.CherishRadioButton
import com.example.ui.components.CherishSlider
import com.example.ui.components.CherishSwitch
import com.example.ui.components.scrollFadingEdges

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    modifier: Modifier = Modifier,
    // At the end of the title row: e.g. one edit button for the whole section
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp)
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
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            action?.invoke()
        }
        Spacer(modifier = Modifier.height(4.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = if (com.example.ui.theme.isAppInDark()) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface),
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

/** Settings & Privacy's pages, like Telegram's: the main list opens one page per kind of setting. */
private enum class SettingsPage(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Account("Account & Couple", "Partner, passcode and check-after", Icons.Rounded.Favorite),
    Privacy("Privacy", "Stealth vault, app lock, security audit and devices", Icons.Rounded.Security),
    Chats("Chats", "Theme, wallpaper, text size, font and chat mode", Icons.Rounded.ChatBubble),
    Notifications("Notifications", "Alerts, badges and reminders", Icons.Rounded.Notifications),
    Calls("Calls", "Ringing, noise reduction and call recording", Icons.Rounded.PhoneInTalk),
    Storage("Storage", "Backup and cache", Icons.Rounded.CloudDone)
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
    val backupState by viewModel.backupState.collectAsState()
    val user = uiState.currentUser
    val partner = uiState.partnerUser
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val coroutineScope = rememberCoroutineScope()
    
    var isBatteryOptimizationIgnored by remember { mutableStateOf(NotificationHelper.isBatteryOptimizationIgnored(context)) }
    var isCallScreenAllowed by remember { mutableStateOf(com.example.notifications.BackgroundDelivery.isCallScreenAllowed(context)) }
    // The switches this phone (Xiaomi, Vivo, Samsung...) still needs on for pushes to reach a closed Notes
    var deliveryMissing by remember { mutableStateOf(com.example.notifications.BackgroundDelivery.missingCount(context)) }
    var showDeliverySetup by remember { mutableStateOf(false) }
    var showSamsungVivoPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (com.example.notifications.BackgroundDelivery.shouldPromptSamsungVivo(context)) {
            showSamsungVivoPrompt = true
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.reloadSettings()
                viewModel.checkDigitalWellbeingStatus(context)
                isBatteryOptimizationIgnored = NotificationHelper.isBatteryOptimizationIgnored(context)
                isCallScreenAllowed = com.example.notifications.BackgroundDelivery.isCallScreenAllowed(context)
                deliveryMissing = com.example.notifications.BackgroundDelivery.missingCount(context)
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
    var showReinstallConfirm by remember { mutableStateOf(false) }
    var isReinstallLoginRevealed by remember { mutableStateOf(false) }

    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            tempCameraUri?.let { uri ->
                viewModel.updateAvatar(uri) {
                    CherishToast.show("Profile photo updated ✨")
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
                CherishToast.show("Could not open camera: ${e.message}")
            }
        } else {
            CherishToast.show("Camera permission needed to take photo")
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
                CherishToast.show("Profile photo updated ✨")
            }
        }
    }

    val customWallpaperPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val prefs = com.example.security.SecurityPreferences.getInstance(context)
        prefs.isExternalPickerActive = false
        prefs.ignoreNextPause = false
        if (uri != null) {
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val timestamp = System.currentTimeMillis()
                    val file = java.io.File(context.filesDir, "custom_chat_wallpaper_${timestamp}.jpg")
                    val inputStream = context.contentResolver.openInputStream(uri)
                    inputStream?.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    // Clean up older wallpaper files
                    context.filesDir.listFiles { f ->
                        f.name.startsWith("custom_chat_wallpaper") && f.name != file.name
                    }?.forEach { it.delete() }

                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        viewModel.setCustomWallpaperPath(file.absolutePath)
                        viewModel.setChatBgTheme(3)
                        CherishToast.show("Custom wallpaper set ✨")
                    }
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        CherishToast.show("Failed to load wallpaper")
                    }
                }
            }
        }
    }

    // Which page of settings is open (null: the main list)
    var settingsPage by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<SettingsPage?>(null) }

    val profileApp = remember { context.applicationContext as com.example.CherishApplication }
    val isDisguiseActive by profileApp.securityPreferences.isDisguiseActive.collectAsState()

    // Android back button & edge gesture: from a page, back to the main list; from the list,
    // re-disguise directly to Notes app for security
    BackHandler(enabled = !isDisguiseActive) {
        if (settingsPage != null) settingsPage = null else onQuickDisguise()
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
                            // A page goes back to the main list; the list goes to the chat
                            if (settingsPage != null) settingsPage = null else onNavigateToChat()
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
                        settingsPage?.title ?: "Settings & Privacy",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { if (settingsPage != null) settingsPage = null else onNavigateToChat() },
                        modifier = Modifier.testTag("profile_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
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
        // The main list, or one of its pages: a page slides in from the right, and back out
        AnimatedContent(
            targetState = settingsPage,
            transitionSpec = {
                val opening = targetState != null
                (slideInHorizontally(androidx.compose.animation.core.tween(280)) { if (opening) it else -it / 4 } +
                    fadeIn(androidx.compose.animation.core.tween(220)))
                    .togetherWith(
                        slideOutHorizontally(androidx.compose.animation.core.tween(280)) { if (opening) -it / 4 else it } +
                            fadeOut(androidx.compose.animation.core.tween(180))
                    )
            },
            label = "settings_page",
            modifier = Modifier.padding(paddingValues)
        ) { page ->
        val settingsScroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Settings fade softly under the top bar and at the bottom edge as they scroll
                .scrollFadingEdges(settingsScroll)
                .verticalScroll(settingsScroll)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (page == null) {
            // 1. My profile, plain like any settings screen: photo, name, email and note; tap the
            // photo to change it, the row or the pencil to edit (the two of us are on Love & Us)
            val myName = user?.displayName?.ifBlank { "Me" } ?: "Me"
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_header_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (com.example.ui.theme.isAppInDark()) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showEditProfileDialog = true }
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AvatarView(
                            photoUrl = user?.photoUrl,
                            name = myName,
                            size = 68.dp,
                            showOnlineBadge = false,
                            modifier = Modifier
                                .pressScaleTarget()
                                .clip(CircleShape)
                                .springClickable { showAvatarOptionsDialog = true }
                                .testTag("change_photo_button")
                        )
                        if (uiState.isUpdating) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                        // Small camera badge: change photo
                        Surface(
                            shape = CircleShape,
                            color = primaryAccent,
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(22.dp)
                                .clickable { showAvatarOptionsDialog = true }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.CameraAlt,
                                    contentDescription = "Change profile photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = myName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!user?.email.isNullOrBlank()) {
                            Text(
                                text = user.email,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = user?.statusMessage?.ifBlank { null } ?: "Add a note",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { showEditProfileDialog = true },
                        modifier = Modifier.testTag("edit_profile_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit profile",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // The kinds of settings, each on its own page
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (com.example.ui.theme.isAppInDark()) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    SettingsPage.entries.forEachIndexed { index, item ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)
                        }
                        ListItem(
                            headlineContent = { Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text(item.subtitle, fontSize = 13.sp) },
                            leadingContent = { Icon(item.icon, contentDescription = null, tint = primaryAccent) },
                            trailingContent = {
                                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            modifier = Modifier
                                .clickable { settingsPage = item }
                                .testTag("settings_page_${item.name}")
                        )
                    }
                }
            }
            } // Main list

            // 2. Couple Connection & Credentials Section
            if (page == SettingsPage.Account) {
            SettingsSection(
                title = "Couple Connection",
                icon = Icons.Rounded.Favorite,
                // One edit for both: partner's account and passcode, together (my name is in Edit Profile)
                action = {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .pressScaleTarget()
                            .clip(CircleShape)
                            .springClickable { showPairDialog = true }
                            .testTag("edit_couple_connection_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit couple connection",
                            tint = primaryAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            ) {
                ListItem(
                    modifier = Modifier.clickable { showPairDialog = true },
                    headlineContent = { Text("Partner's Account", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        val partnerDisplay = user?.partnerEmail?.removeSuffix("@cherish.app")
                            ?: partner?.displayName
                            ?: "Connected"
                        Text(partnerDisplay, fontSize = 13.sp)
                    },
                    leadingContent = {
                        Icon(Icons.Rounded.Favorite, contentDescription = null, tint = HeartRed)
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)
                ListItem(
                    modifier = Modifier.clickable { showPairDialog = true },
                    headlineContent = { Text("Shared Couple Passcode", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(uiState.coupleKey.removePrefix("COUPLE_").removePrefix("couple_").ifBlank { "CHERISH-FOREVER" }, fontSize = 13.sp) },
                    leadingContent = {
                        Icon(Icons.Rounded.Key, contentDescription = null, tint = primaryAccent)
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)
                ListItem(
                    headlineContent = { Text("Your Gender", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    leadingContent = {
                        Icon(Icons.Rounded.Person, contentDescription = null, tint = primaryAccent)
                    },
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CherishFilterChip(
                                selected = uiState.userGender == com.example.security.SecurityPreferences.GENDER_MALE,
                                onClick = { viewModel.setUserGender(com.example.security.SecurityPreferences.GENDER_MALE) },
                                label = { Text("Male ♂", fontSize = 12.sp) },
                                shape = RoundedCornerShape(20.dp)
                            )
                            CherishFilterChip(
                                selected = uiState.userGender == com.example.security.SecurityPreferences.GENDER_FEMALE,
                                onClick = { viewModel.setUserGender(com.example.security.SecurityPreferences.GENDER_FEMALE) },
                                label = { Text("Female ♀", fontSize = 12.sp) },
                                shape = RoundedCornerShape(20.dp)
                            )
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)
                ListItem(
                    headlineContent = { Text("Partner's Gender", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    leadingContent = {
                        Icon(Icons.Rounded.Favorite, contentDescription = null, tint = HeartRed)
                    },
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CherishFilterChip(
                                selected = uiState.partnerGender == com.example.security.SecurityPreferences.GENDER_MALE,
                                onClick = { viewModel.setPartnerGender(com.example.security.SecurityPreferences.GENDER_MALE) },
                                label = { Text("Male ♂", fontSize = 12.sp) },
                                shape = RoundedCornerShape(20.dp)
                            )
                            CherishFilterChip(
                                selected = uiState.partnerGender == com.example.security.SecurityPreferences.GENDER_FEMALE,
                                onClick = { viewModel.setPartnerGender(com.example.security.SecurityPreferences.GENDER_FEMALE) },
                                label = { Text("Female ♀", fontSize = 12.sp) },
                                shape = RoundedCornerShape(20.dp)
                            )
                        }
                    }
                )
            }



            } // Account

            // 3. Privacy, Stealth Disguise & Vault
            if (page == SettingsPage.Privacy) {
            SettingsSection(
                title = "Privacy & Stealth Vault",
                icon = Icons.Rounded.Security
            ) {
                ListItem(
                    headlineContent = { Text("Recover All Chats & Gallery", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Restore all older & cleared messages and all gallery items immediately", fontSize = 13.sp) },
                    leadingContent = { Icon(Icons.Rounded.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        CherishTonalButton(
                            onClick = {
                                viewModel.recoverAllChatsAndGallery()
                                CherishToast.show("All chats & gallery recovered ✨")
                                onNavigateToChat()
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Recover All", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                // Disguise Decoy App Toggle
                ListItem(
                    headlineContent = { Text("Disguise Mode", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Opens as functional notes app or calculator with secret unlock", fontSize = 13.sp) },
                    leadingContent = {
                        if (uiState.disguiseStyle == SecurityPreferences.DISGUISE_STYLE_CALCULATOR) {
                            androidx.compose.foundation.Image(
                                painter = painterResource(id = com.example.R.drawable.ic_samsung_calculator),
                                contentDescription = "Samsung Calculator",
                                modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.EditNote,
                                contentDescription = null,
                                tint = primaryAccent
                            )
                        }
                    },
                    trailingContent = {
                        CherishSwitch(
                            checked = uiState.isDisguiseModeEnabled,
                            onCheckedChange = { viewModel.setDisguiseMode(it) }
                        )
                    }
                )

                // Partner's Disguise Status Card
                val partner = uiState.partnerUser
                val partnerName = partner?.displayName?.ifBlank { "Partner" } ?: "Partner"
                val partnerDisguiseOn = partner?.isDisguiseEnabled ?: true
                val partnerDisguiseStyle = partner?.disguiseStyle ?: SecurityPreferences.DISGUISE_STYLE_NOTES
                val isPartnerCalc = partnerDisguiseStyle == SecurityPreferences.DISGUISE_STYLE_CALCULATOR

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                ListItem(
                    headlineContent = { Text("$partnerName's Disguise", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(
                            if (!partnerDisguiseOn) "Disguise mode is turned off"
                            else if (isPartnerCalc) "Currently using Samsung Calculator"
                            else "Currently using Notes app",
                            fontSize = 13.sp
                        )
                    },
                    leadingContent = {
                        if (!partnerDisguiseOn) {
                            Icon(Icons.Rounded.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (isPartnerCalc) {
                            androidx.compose.foundation.Image(
                                painter = painterResource(id = com.example.R.drawable.ic_samsung_calculator),
                                contentDescription = "Samsung Calculator",
                                modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
                            )
                        } else {
                            androidx.compose.foundation.Image(
                                painter = painterResource(id = com.example.R.drawable.ic_notes_app),
                                contentDescription = "Notes App",
                                modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
                            )
                        }
                    },
                    trailingContent = {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (partnerDisguiseOn) primaryAccent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = if (!partnerDisguiseOn) "Off" else if (isPartnerCalc) "Calculator" else "Notes",
                                color = if (partnerDisguiseOn) primaryAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                )

                if (uiState.isDisguiseModeEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                    // Disguise Decoy App Selector (Notes vs Calculator)
                    ListItem(
                        headlineContent = { Text("Choose Disguise App", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Text(
                                if (uiState.disguiseStyle == SecurityPreferences.DISGUISE_STYLE_CALCULATOR)
                                    "Calculator: Type passcode/PIN and press '=', or long-press '='"
                                else "Notes App: Search trigger word or hold '+' button",
                                fontSize = 13.sp
                            )
                        },
                        leadingContent = { Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = primaryAccent) }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val disguiseOptions = remember {
                            listOf(
                                Triple(SecurityPreferences.DISGUISE_STYLE_NOTES, "Notes", Icons.Rounded.EditNote),
                                Triple(SecurityPreferences.DISGUISE_STYLE_CALCULATOR, "Calculator", Icons.Rounded.Calculate)
                            )
                        }
                        disguiseOptions.forEach { (style, title, icon) ->
                            val isSelected = uiState.disguiseStyle == style
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .pressScaleTarget()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) primaryAccent else Color.Transparent)
                                    .springClickable(tick = true) { viewModel.setDisguiseStyle(style) }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (style == SecurityPreferences.DISGUISE_STYLE_CALCULATOR) {
                                    androidx.compose.foundation.Image(
                                        painter = painterResource(id = com.example.R.drawable.ic_samsung_calculator),
                                        contentDescription = null,
                                        modifier = Modifier.size(19.dp).clip(RoundedCornerShape(5.dp))
                                    )
                                } else {
                                    androidx.compose.foundation.Image(
                                        painter = painterResource(id = com.example.R.drawable.ic_notes_app),
                                        contentDescription = "Notes",
                                        modifier = Modifier.size(19.dp).clip(RoundedCornerShape(5.dp))
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = title,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (uiState.disguiseStyle == SecurityPreferences.DISGUISE_STYLE_CALCULATOR) {
                        // Calculator settings: Passcode / PIN & Device Lock
                        ListItem(
                            headlineContent = { Text("Secret Calculator Passcode", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                            supportingContent = {
                                Text(
                                    text = "Current: " + (if (isPasscodeRevealed) uiState.calculatorPasscode else "••••••••") + " • Type & press '='",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            leadingContent = {
                                IconButton(onClick = { isPasscodeRevealed = !isPasscodeRevealed }) {
                                    Icon(
                                        imageVector = if (isPasscodeRevealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = if (isPasscodeRevealed) "Hide passcode" else "Reveal passcode",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            trailingContent = {
                                TextButton(onClick = {
                                    newPasscodeText = uiState.calculatorPasscode
                                    isDialogPasscodeRevealed = false
                                    showSetPasscodeDialog = true
                                }) {
                                    Text("Change", fontSize = 13.sp)
                                }
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                        // '=' Button Long-Press Hold Duration Selector
                        ListItem(
                            headlineContent = { Text("Hold '=' Button to Unlock", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                            supportingContent = {
                                Text(
                                    if (uiState.isEqualsHoldUnlockEnabled)
                                        "Hold '=' for ${uiState.equalsHoldDurationSec}s in Calculator to reveal chat"
                                    else
                                        "Disabled (enter passcode and press '=' to unlock)",
                                    fontSize = 13.sp
                                )
                            },
                            leadingContent = { Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = primaryAccent) },
                            trailingContent = {
                                CherishSwitch(
                                    checked = uiState.isEqualsHoldUnlockEnabled,
                                    onCheckedChange = { viewModel.setEqualsHoldUnlockEnabled(it) }
                                )
                            }
                        )

                        AnimatedVisibility(visible = uiState.isEqualsHoldUnlockEnabled) {
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
                                    4 to "4s",
                                    5 to "5s"
                                ).forEach { (duration, label) ->
                                    val isSelected = uiState.equalsHoldDurationSec == duration
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .pressScaleTarget()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) primaryAccent else Color.Transparent)
                                            .springClickable(tick = true) { viewModel.setEqualsHoldDuration(duration) }
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
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        ListItem(
                            headlineContent = {
                                Text("Device Screen Lock After Unlock", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            },
                            supportingContent = {
                                Text("Require phone PIN/fingerprint before revealing chat", fontSize = 13.sp)
                            },
                            leadingContent = {
                                Icon(Icons.Rounded.Security, contentDescription = null, tint = primaryAccent)
                            },
                            trailingContent = {
                                CherishSwitch(
                                    checked = uiState.isRequirePhoneLockAfterHold,
                                    onCheckedChange = { viewModel.setRequirePhoneLockAfterHold(it) }
                                )
                            }
                        )
                    } else {
                        // Notes App settings: Trigger word and Hold duration
                        ListItem(
                            headlineContent = { Text("Search Bar Trigger Word", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("Type secret word in Notes search to reveal chat", fontSize = 13.sp) },
                            leadingContent = { Icon(Icons.Rounded.Password, contentDescription = null, tint = primaryAccent) },
                            trailingContent = {
                                CherishSwitch(
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
                                            imageVector = if (isPasscodeRevealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
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
                            leadingContent = { Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = primaryAccent) }
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
                                        .pressScaleTarget()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) primaryAccent else Color.Transparent)
                                        .springClickable(tick = true) { viewModel.setPlusHoldDuration(duration) }
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
                                Icon(Icons.Rounded.Security, contentDescription = null, tint = primaryAccent)
                            },
                            trailingContent = {
                                CherishSwitch(
                                    checked = uiState.isRequirePhoneLockAfterHold,
                                    onCheckedChange = { viewModel.setRequirePhoneLockAfterHold(it) }
                                )
                            }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CherishOutlinedButton(
                            onClick = { onQuickDisguise() },
                            modifier = Modifier.fillMaxWidth().height(38.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp))
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
                    leadingContent = { Icon(Icons.AutoMirrored.Rounded.ExitToApp, contentDescription = null, tint = HeartRed) },
                    trailingContent = {
                        CherishSwitch(
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
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(
                                    alpha = com.example.ui.security.EmergencyExitLook.background(uiState.sideEmergencyExitOpacity)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(
                                        alpha = com.example.ui.security.EmergencyExitLook.border(uiState.sideEmergencyExitOpacity)
                                    )
                                ),
                                modifier = Modifier
                                    .width(26.dp)
                                    .height(44.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    val arrowTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        alpha = com.example.ui.security.EmergencyExitLook.arrows(uiState.sideEmergencyExitOpacity)
                                    )
                                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, tint = arrowTint, modifier = Modifier.size(9.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ExitToApp,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = com.example.ui.security.EmergencyExitLook.icon(uiState.sideEmergencyExitOpacity)
                                        ),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = arrowTint, modifier = Modifier.size(9.dp))
                                }
                            }
                        }

                        CherishSlider(
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
                    leadingContent = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = primaryAccent) },
                    trailingContent = {
                        CherishSwitch(
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
                // How long the app can be away before it asks for the PIN again
                if (uiState.isAppLockEnabled) {
                    val lockPrefs = remember { com.example.security.SecurityPreferences.getInstance(context) }
                    var lockAfter by remember { mutableIntStateOf(lockPrefs.getAutoLockTimeoutSeconds()) }
                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                        Text(
                            text = "Lock again after leaving",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(0 to "Right away", 30 to "30 sec", 60 to "1 min", 300 to "5 min").forEach { (seconds, label) ->
                                val isSelected = lockAfter == seconds
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .pressScaleTarget()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) primaryAccent else Color.Transparent)
                                        .springClickable(tick = true) {
                                            lockAfter = seconds
                                            lockPrefs.setAutoLockTimeoutSeconds(seconds)
                                        }
                                        .padding(vertical = 8.dp)
                                        .testTag("app_lock_after_$seconds"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                // Biometrics Toggle
                ListItem(
                    headlineContent = { Text("Fingerprint / Face Unlock", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        // It unlocks the app lock, so it only matters while that's on
                        Text(
                            if (uiState.isAppLockEnabled) "Use device biometrics to unlock quickly" else "Turn on the app lock to use it",
                            fontSize = 13.sp
                        )
                    },
                    leadingContent = { Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = primaryAccent) },
                    trailingContent = {
                        CherishSwitch(
                            checked = uiState.isBiometricEnabled && uiState.isAppLockEnabled,
                            enabled = uiState.isAppLockEnabled,
                            onCheckedChange = { viewModel.setBiometric(it) }
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                // Screenshot Protection
                ListItem(
                    headlineContent = { Text("Screenshot Protection", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Block screenshots and screen recording in app", fontSize = 13.sp) },
                    leadingContent = { Icon(Icons.Rounded.Shield, contentDescription = null, tint = primaryAccent) },
                    trailingContent = {
                        CherishSwitch(
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

                // Digital Wellbeing & Screen Time Protection
                ListItem(
                    headlineContent = { Text("Digital Wellbeing Tracking", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(
                            if (uiState.isDigitalWellbeingBlocked) "Tracking blocked: Digital Wellbeing cannot monitor screen time or app activity"
                            else "Prevent Android Digital Wellbeing from tracking Notes/Cherish screen time",
                            fontSize = 13.sp
                        )
                    },
                    leadingContent = {
                        Icon(
                            if (uiState.isDigitalWellbeingBlocked) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = null,
                            tint = if (uiState.isDigitalWellbeingBlocked) primaryAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        TextButton(onClick = { viewModel.openDigitalWellbeingSettings(context) }) {
                            Text(
                                if (uiState.isDigitalWellbeingBlocked) "Blocked" else "Turn Off",
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.isDigitalWellbeingBlocked) primaryAccent else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                // --- Clean reinstall (resets the usage counters Android keeps per app) ---
                run {
                    val reinstallSchedule by viewModel.reinstallSchedule.collectAsState()
                    val lastReinstallAt by viewModel.lastReinstallAt.collectAsState()
                    val reinstallState by viewModel.reinstallState.collectAsState()
                    val partnerReinstallAt = uiState.partnerUser?.lastReinstallAt ?: 0L
                    val partnerName = uiState.partnerUser?.displayName?.ifBlank { "Partner" } ?: "Partner"
                    val agoText: (Long) -> String = { ts ->
                        if (ts <= 0L) "never" else {
                            val d = System.currentTimeMillis() - ts
                            when {
                                d < 60_000L -> "just now"
                                d < 3_600_000L -> "${d / 60_000L}m ago"
                                d < 86_400_000L -> "${d / 3_600_000L}h ago"
                                else -> "${d / 86_400_000L}d ago"
                            }
                        }
                    }
                    val periodMs = 7L * 86_400_000L // weekly
                    val myDone = reinstallSchedule != 0 && lastReinstallAt > 0L &&
                        System.currentTimeMillis() - lastReinstallAt < periodMs
                    val partnerDone = partnerReinstallAt > 0L &&
                        System.currentTimeMillis() - partnerReinstallAt < periodMs

                    ListItem(
                        headlineContent = { Text("Clean Reinstall", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Text(
                                "Reinstall to reset the app's data, battery and screen-time counters in Settings. Removes the app; you reinstall from Downloads and sign in again.",
                                fontSize = 13.sp
                            )
                        },
                        leadingContent = { Icon(Icons.Rounded.Refresh, contentDescription = null, tint = primaryAccent) }
                    )

                    // Schedule: Off / Weekly / Daily
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0 to "Off", 1 to "Weekly").forEach { (mode, label) ->
                            val selected = reinstallSchedule == mode
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .pressScaleTarget()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) primaryAccent else Color.Transparent)
                                    .springClickable(tick = true) { viewModel.setReinstallSchedule(mode) }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // This phone + partner status
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (reinstallSchedule == 0 || myDone) Icons.Rounded.CheckCircle else Icons.Rounded.Schedule,
                                    contentDescription = null,
                                    tint = if (reinstallSchedule != 0 && !myDone) Color(0xFFFFA000) else Color(0xFF4CAF50),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "You: " + (if (lastReinstallAt > 0L) agoText(lastReinstallAt) else "not yet") +
                                        (if (reinstallSchedule != 0) (if (myDone) " • done this week" else " • due") else ""),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (uiState.partnerUser != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (partnerDone) Icons.Rounded.CheckCircle else Icons.Rounded.Schedule,
                                        contentDescription = null,
                                        tint = if (partnerDone) Color(0xFF4CAF50) else Color(0xFFFFA000),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "$partnerName: " + (if (partnerReinstallAt > 0L) agoText(partnerReinstallAt) else "not yet"),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // Progress / error
                    when (val rs = reinstallState) {
                        is ProfileViewModel.ReinstallUi.Preparing -> {
                            Text(
                                "Preparing…",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                        is ProfileViewModel.ReinstallUi.Downloading -> {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                LinearProgressIndicator(
                                    progress = { rs.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = primaryAccent
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Downloading installer to Downloads… ${(rs.progress * 100).toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        is ProfileViewModel.ReinstallUi.Error -> {
                            Text(
                                rs.message,
                                fontSize = 12.sp,
                                color = Color(0xFFE53935),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                        else -> {}
                    }

                    // Reinstall now
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        val busy = reinstallState is ProfileViewModel.ReinstallUi.Preparing ||
                            reinstallState is ProfileViewModel.ReinstallUi.Downloading
                        CherishTonalButton(
                            onClick = { if (!busy) showReinstallConfirm = true },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (busy) "Working…" else "Reinstall now", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (showReinstallConfirm) {
                    val reinstallLogin = remember { viewModel.getLoginDetails() }
                    CherishAlertDialog(
                        onDismissRequest = { showReinstallConfirm = false },
                        title = { Text("Reinstall now?", fontWeight = FontWeight.Bold) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    "The installer is saved to your Downloads first. Then you uninstall the app, install it again from Downloads and sign in with the details below.",
                                    fontSize = 13.sp
                                )
                                ReinstallLoginCard(
                                    login = reinstallLogin,
                                    revealed = isReinstallLoginRevealed,
                                    onToggleReveal = { isReinstallLoginRevealed = !isReinstallLoginRevealed }
                                )
                            }
                        },
                        confirmButton = {
                            CherishButton(
                                onClick = {
                                    showReinstallConfirm = false
                                    viewModel.startReinstall(context)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("Download & Reinstall", fontWeight = FontWeight.Bold) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showReinstallConfirm = false }) { Text("Cancel") }
                        }
                    )
                }

                // Downloaded: what to do now, step by step, starting with the uninstall
                val reinstallStep by viewModel.reinstallState.collectAsState()
                (reinstallStep as? ProfileViewModel.ReinstallUi.Downloaded)?.let { done ->
                    val reinstallLogin = remember { viewModel.getLoginDetails() }
                    CherishAlertDialog(
                        onDismissRequest = { viewModel.dismissReinstallSteps() },
                        title = { Text("Download complete", fontWeight = FontWeight.Bold) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("The new installer is in your Downloads: ${done.fileName}", fontSize = 13.sp)
                                listOf(
                                    "Tap Uninstall now and confirm. The app is removed.",
                                    "Open Files (My Files on Samsung), go to Downloads and tap ${done.fileName} to install it.",
                                    "Open the app and sign in with the details below.",
                                    "Delete ${done.fileName} from Downloads."
                                ).forEachIndexed { index, step ->
                                    Row(verticalAlignment = Alignment.Top) {
                                        Text(
                                            "${index + 1}.",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(20.dp)
                                        )
                                        Text(step, fontSize = 13.sp)
                                    }
                                }
                                ReinstallLoginCard(
                                    login = reinstallLogin,
                                    revealed = isReinstallLoginRevealed,
                                    onToggleReveal = { isReinstallLoginRevealed = !isReinstallLoginRevealed }
                                )
                            }
                        },
                        confirmButton = {
                            CherishButton(
                                onClick = { viewModel.openUninstall(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("Uninstall now", fontWeight = FontWeight.Bold) }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.dismissReinstallSteps() }) { Text("Later") }
                        }
                    )
                }

            }

            } // Privacy

            // 4. Notification Settings Section (Separated Notification Bar & App Badge)
            if (page == SettingsPage.Notifications) {
            SettingsSection(
                title = "Notification Settings",
                icon = Icons.Rounded.Notifications
            ) {
                // --- SUBSECTION 1: NOTIFICATION BAR & SYSTEM ALERTS ---
                ListItem(
                    headlineContent = { Text("Notification Bar Alerts", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(
                            if (uiState.isNotificationsEnabled) "Show message alerts, drop-down banners, and lock screen previews"
                            else "Status bar and heads-up notifications are silenced",
                            fontSize = 13.sp
                        )
                    },
                    leadingContent = {
                        Icon(
                            if (uiState.isNotificationsEnabled) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                            contentDescription = null,
                            tint = if (uiState.isNotificationsEnabled) primaryAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        CherishSwitch(
                            checked = uiState.isNotificationsEnabled,
                            onCheckedChange = { viewModel.setNotificationsEnabled(context, it) },
                            modifier = Modifier.testTag("notification_bar_switch")
                        )
                    }
                )

                if (uiState.isNotificationsEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)

                    // Notification Content Privacy (Mask private notification)
                    ListItem(
                        headlineContent = { Text("Hide Notification Content", fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                        supportingContent = { Text("Disguise private messages on notification bar and lock screen", fontSize = 12.sp) },
                        leadingContent = { Icon(Icons.Rounded.Security, contentDescription = null, tint = primaryAccent, modifier = Modifier.size(20.dp)) },
                        trailingContent = {
                            CherishSwitch(
                                checked = uiState.isHideNotificationContent,
                                onCheckedChange = { viewModel.setHideNotificationContent(it) }
                            )
                        }
                    )

                    // What a masked notification says
                    val maskPrefs = remember { com.example.security.SecurityPreferences.getInstance(context) }
                    var maskedTitle by remember { mutableStateOf(maskPrefs.getMaskedNotificationTitle(uiState.disguiseStyle)) }
                    var maskedText by remember { mutableStateOf(maskPrefs.getMaskedNotificationText(uiState.disguiseStyle)) }
                    LaunchedEffect(uiState.disguiseStyle) {
                        maskedTitle = maskPrefs.getMaskedNotificationTitle(uiState.disguiseStyle)
                        maskedText = maskPrefs.getMaskedNotificationText(uiState.disguiseStyle)
                    }
                    var showMaskedEditor by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text("Custom Disguise Text", fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                        supportingContent = { Text("$maskedTitle \u00B7 $maskedText", fontSize = 12.sp, maxLines = 1) },
                        leadingContent = { Icon(Icons.Rounded.Edit, contentDescription = null, tint = primaryAccent, modifier = Modifier.size(20.dp)) },
                        modifier = Modifier
                            .clickable { showMaskedEditor = true }
                            .testTag("masked_notification_text")
                    )
                    if (showMaskedEditor) {
                        val isCalcDisguise = uiState.disguiseStyle == SecurityPreferences.DISGUISE_STYLE_CALCULATOR
                        var titleInput by remember { mutableStateOf(maskedTitle) }
                        var textInput by remember { mutableStateOf(maskedText) }
                        CherishAlertDialog(
                            onDismissRequest = { showMaskedEditor = false },
                            title = { Text(if (isCalcDisguise) "Disguised Notification (Calculator)" else "Disguised Notification (Notes)") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        "What hidden or disguised message notifications say on the status bar.",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedTextField(
                                        value = titleInput,
                                        onValueChange = { titleInput = it.take(40) },
                                        label = { Text(if (isCalcDisguise) "Title (e.g. Calculator)" else "Title (e.g. Notes)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = textInput,
                                        onValueChange = { textInput = it.take(80) },
                                        label = { Text(if (isCalcDisguise) "Text (e.g. Calculation history updated)" else "Text (e.g. Checklist item updated)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    // Preview
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                titleInput.ifBlank { if (isCalcDisguise) "Calculator" else com.example.security.SecurityPreferences.DEFAULT_MASKED_TITLE },
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                textInput.ifBlank { if (isCalcDisguise) "Calculation history updated" else com.example.security.SecurityPreferences.DEFAULT_MASKED_TEXT },
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    maskPrefs.setMaskedNotificationText(titleInput, textInput, uiState.disguiseStyle)
                                    maskedTitle = maskPrefs.getMaskedNotificationTitle(uiState.disguiseStyle)
                                    maskedText = maskPrefs.getMaskedNotificationText(uiState.disguiseStyle)
                                    showMaskedEditor = false
                                }) { Text("Save") }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    maskPrefs.setMaskedNotificationText("", "", uiState.disguiseStyle)
                                    maskedTitle = maskPrefs.getMaskedNotificationTitle(uiState.disguiseStyle)
                                    maskedText = maskPrefs.getMaskedNotificationText(uiState.disguiseStyle)
                                    showMaskedEditor = false
                                }) { Text("Reset") }
                            }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                // --- SUBSECTION 2: APP ICON BADGE (INDEPENDENT SETTING) ---
                ListItem(
                    headlineContent = { Text("App Icon Badge", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(
                            if (uiState.isBadgeNotificationEnabled) "Display unread indicator dot or count badge directly on home screen app icon"
                            else "App icon badge is hidden",
                            fontSize = 13.sp
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.MarkChatUnread,
                            contentDescription = null,
                            tint = if (uiState.isBadgeNotificationEnabled) primaryAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        CherishSwitch(
                            checked = uiState.isBadgeNotificationEnabled,
                            onCheckedChange = { viewModel.setBadgeNotificationEnabled(context, it) },
                            modifier = Modifier.testTag("app_badge_switch")
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                // --- SUBSECTION 3: HEARTBEAT TRIGGER ("THINKING OF YOU") ---
                ListItem(
                    headlineContent = { Text("Heartbeat Trigger (\"Thinking of You\")", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(
                            if (uiState.isHeartbeatTriggerEnabled) "Receive real-time tactile heartbeat pulses (thump-thump) when your partner sends a heartbeat"
                            else "Heartbeat triggers and remote pulse alerts are turned off",
                            fontSize = 13.sp
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = if (uiState.isHeartbeatTriggerEnabled) HeartRed else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        CherishSwitch(
                            checked = uiState.isHeartbeatTriggerEnabled,
                            onCheckedChange = { viewModel.setHeartbeatTriggerEnabled(it) },
                            modifier = Modifier.testTag("heartbeat_trigger_switch")
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                // --- SUBSECTION 4: GET EVERY MESSAGE & CALL (this phone's battery and start-up switches; nothing runs in the background) ---
                ListItem(
                    headlineContent = { Text("Get Every Message & Call", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(
                            if (deliveryMissing == 0) {
                                "Messages and calls arrive reliably when Notes is closed. All background switches are configured on this phone."
                            } else {
                                "Ensure background switches are on so messages and calls reach this phone immediately when closed. " +
                                    (if (deliveryMissing == 1) "1 switch is" else "$deliveryMissing switches are") + " needed: tap Set up."
                            },
                            fontSize = 13.sp
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = primaryAccent)
                    },
                    trailingContent = {
                        CherishTonalButton(
                            onClick = { showDeliverySetup = true },
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                        ) {
                            Text(if (deliveryMissing == 0) "Check" else "Set up", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    modifier = Modifier.testTag("notifications_when_closed")
                )

                if (partner != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)
                    val partnerNameDisplay = partner.displayName.ifBlank { "Partner" }
                    val partnerNotifPerm = partner.notificationPermission ?: true
                    val partnerNotifStatus = partner.notificationStatus ?: "OPEN"
                    val isPermBlocked = !partnerNotifPerm
                    val isAlertClosed = partnerNotifStatus.uppercase() in listOf("CLOSED", "OFF", "MUTED")
                    val isSafeDisguised = partnerNotifStatus.uppercase() in listOf("SAFE", "HIDDEN")

                    // Partner's Complete Notification Settings Status Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (isPermBlocked) "⚠️" else if (isAlertClosed) "🔕" else if (isSafeDisguised) "🛡️" else "🔔",
                                        fontSize = 18.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "$partnerNameDisplay's Notification Status",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isPermBlocked || isAlertClosed) Red500.copy(alpha = 0.15f) else Emerald500.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = if (isPermBlocked) "Blocked" else if (isAlertClosed) "Muted" else if (isSafeDisguised) "Disguised" else "Active",
                                        color = if (isPermBlocked || isAlertClosed) Red500 else Emerald500,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            if (isPermBlocked) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "System notification permission is OFF on their device. Ringtone and message alerts are silenced.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(8.dp))

                            // Rows of all notification types
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.Notifications, contentDescription = null, tint = if (partnerNotifPerm) primaryAccent else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("System Notification Bar", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                                }
                                Text(if (partnerNotifPerm) "Allowed ✅" else "Blocked ❌", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (partnerNotifPerm) Emerald500 else Red500)
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.Security, contentDescription = null, tint = if (!isAlertClosed) primaryAccent else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Message Content Privacy", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                                }
                                Text(if (isSafeDisguised) "Disguised (Notes) 🛡️" else if (isAlertClosed) "Muted / Off 🔕" else "Full Preview 🔔", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (!isAlertClosed) Emerald500 else Red500)
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.PhoneInTalk, contentDescription = null, tint = if (!isPermBlocked && !isAlertClosed) primaryAccent else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Incoming Call Ringing", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                                }
                                Text(if (!isPermBlocked && !isAlertClosed) "Ringing Active 📞" else "Silenced 🔕", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (!isPermBlocked && !isAlertClosed) Emerald500 else Red500)
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = if (!isAlertClosed) HeartRed else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Heartbeat Tactile Alerts", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                                }
                                Text(if (!isAlertClosed) "Active (Pulse) ❤️" else "Muted 🤍", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (!isAlertClosed) Emerald500 else Red500)
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.MarkChatUnread, contentDescription = null, tint = if (!isPermBlocked) primaryAccent else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("App Icon Badge", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                                }
                                Text(if (!isPermBlocked) "Active 🔴" else "Disabled", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (!isPermBlocked) Emerald500 else Red500)
                            }

                        }
                    }
                }

            }

            } // Notifications

            // 4. Voice Calls Settings Section
            if (page == SettingsPage.Calls) {
            SettingsSection(
                title = "Voice Calls",
                icon = Icons.Rounded.PhoneInTalk
            ) {
                // Incoming Call Ringing
                ListItem(
                    headlineContent = { Text("Call Notification Ringing", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(
                            if (uiState.isCallRingingEnabled) "Play ringtone sound when your partner calls you"
                            else "Silent incoming calls (vibrate or silent without ringtone audio)",
                            fontSize = 13.sp
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.PhoneInTalk,
                            contentDescription = null,
                            tint = if (uiState.isCallRingingEnabled) primaryAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        CherishSwitch(
                            checked = uiState.isCallRingingEnabled,
                            onCheckedChange = { viewModel.setCallRingingEnabled(it) },
                            modifier = Modifier.testTag("call_ringing_switch_voice")
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                // Incoming Call Screen: the phone's permissions for the full call screen (only from here, when tapped)
                ListItem(
                    headlineContent = { Text("Incoming Call Screen", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(
                            if (isCallScreenAllowed) "The call screen comes up when your partner calls, also over other apps and on the lock screen"
                            else "Only a call notification comes up when your partner calls: tap Allow for the full call screen",
                            fontSize = 13.sp
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.PhonelinkRing,
                            contentDescription = null,
                            tint = if (isCallScreenAllowed) primaryAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        if (!isCallScreenAllowed) {
                            CherishTonalButton(
                                onClick = { com.example.notifications.BackgroundDelivery.openCallScreenSettings(context) },
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                            ) {
                                Text("Allow", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = "Allowed", tint = Emerald500)
                        }
                    },
                    modifier = Modifier.testTag("incoming_call_screen")
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                // Get Every Call When Closed (Reliable Call Delivery)
                ListItem(
                    headlineContent = { Text("Get Every Call When Closed", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(
                            if (deliveryMissing == 0) {
                                "Incoming calls ring reliably even when Notes is closed. All background switches are configured."
                            } else {
                                "Ensure background switches are on so incoming calls ring immediately when closed. Tap Set up."
                            },
                            fontSize = 13.sp
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = primaryAccent)
                    },
                    trailingContent = {
                        CherishTonalButton(
                            onClick = { showDeliverySetup = true },
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                        ) {
                            Text(if (deliveryMissing == 0) "Check" else "Set up", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    modifier = Modifier.testTag("calls_when_closed")
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                // Samsung & Vivo OEM Background Delivery Guide
                if (com.example.notifications.BackgroundDelivery.isSamsungOrVivo()) {
                    val isSamsung = com.example.notifications.BackgroundDelivery.isSamsung()
                    val title = if (isSamsung) "Samsung Device Care & Battery" else "Vivo iManager & Auto-Start"
                    val desc = if (isSamsung) {
                        if (isBatteryOptimizationIgnored) "Battery is Unrestricted. Ensure app is added to 'Never sleeping apps' in Device Care."
                        else "Samsung Device Care puts closed apps to sleep. Tap to set Unrestricted Battery & Never-sleeping apps."
                    } else {
                        "Vivo iManager kills background apps. Enable Auto-start and allow High background power so calls ring."
                    }
                    ListItem(
                        headlineContent = { Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(desc, fontSize = 13.sp) },
                        leadingContent = {
                            Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null, tint = primaryAccent)
                        },
                        trailingContent = {
                            CherishTonalButton(
                                onClick = { showSamsungVivoPrompt = true },
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                            ) {
                                Text("Guide", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        },
                        modifier = Modifier.testTag("samsung_vivo_background_delivery")
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)
                }

                // AI Noise Suppression Mode
                ListItem(
                    headlineContent = { Text("AI Noise Suppression", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(
                            if (uiState.callAiNoiseMode == "whisper") "Night / Whisper Mode: Boosts soft whispering and pillow-talk gain"
                            else "Balanced / Street: Suppresses wind, traffic, and background room noise",
                            fontSize = 13.sp
                        )
                    },
                    leadingContent = { Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = primaryAccent) }
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
                        "balanced" to "Balanced / Street 🏙️",
                        "whisper" to "Night / Whisper 🌙"
                    ).forEach { (mode, label) ->
                        val isSelected = uiState.callAiNoiseMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .pressScaleTarget()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) primaryAccent else Color.Transparent)
                                .springClickable(tick = true) { viewModel.setCallAiNoiseMode(mode) }
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

                // Low-Data Calling Mode
                ListItem(
                    headlineContent = { Text("Low-Data Calling", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(
                            "Reduces audio bitrate on weak mobile data to preserve smooth call stability",
                            fontSize = 13.sp
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.NetworkCheck,
                            contentDescription = null,
                            tint = if (uiState.isCallLowDataMode) primaryAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        CherishSwitch(
                            checked = uiState.isCallLowDataMode,
                            onCheckedChange = { viewModel.setCallLowDataMode(it) }
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                // Auto-Call Recording
                ListItem(
                    headlineContent = { Text("Auto-Call Recording", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(
                            "Automatically save audio recordings of your calls into Shared Gallery memories",
                            fontSize = 13.sp
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.Mic,
                            contentDescription = null,
                            tint = if (uiState.isCallAutoRecordEnabled) primaryAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        CherishSwitch(
                            checked = uiState.isCallAutoRecordEnabled,
                            onCheckedChange = { viewModel.setCallAutoRecordEnabled(it) }
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                // Incoming Call Privacy / Masking Style
                ListItem(
                    headlineContent = { Text("Incoming Call Privacy", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(
                            if (uiState.callMaskingStyle == "discreet") "Discreet: Lock screen disguises incoming call as a system alert"
                            else "Normal: Displays partner's name and photo on incoming call screen",
                            fontSize = 13.sp
                        )
                    },
                    leadingContent = { Icon(Icons.Rounded.Security, contentDescription = null, tint = primaryAccent) }
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
                        "normal" to "Normal (Photo & Name)",
                        "discreet" to "Discreet (Disguised)"
                    ).forEach { (style, label) ->
                        val isSelected = uiState.callMaskingStyle == style
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .pressScaleTarget()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) primaryAccent else Color.Transparent)
                                .springClickable(tick = true) { viewModel.setCallMaskingStyle(style) }
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

                // Vibration Feedback: On Connect
                ListItem(
                    headlineContent = { Text("Vibrate When Connected", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Short gentle haptic pulse when partner answers the call", fontSize = 13.sp) },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.Vibration,
                            contentDescription = null,
                            tint = if (uiState.isCallVibrateConnected) primaryAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        CherishSwitch(
                            checked = uiState.isCallVibrateConnected,
                            onCheckedChange = { viewModel.setCallVibrateConnected(it) }
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                // Vibration Feedback: On Mute Toggle
                ListItem(
                    headlineContent = { Text("Vibrate on Mute Toggle", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Haptic confirmation when tapping microphone mute button", fontSize = 13.sp) },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.Vibration,
                            contentDescription = null,
                            tint = if (uiState.isCallVibrateMute) primaryAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        CherishSwitch(
                            checked = uiState.isCallVibrateMute,
                            onCheckedChange = { viewModel.setCallVibrateMute(it) }
                        )
                    }
                )
            }

            } // Calls

            // 5. Appearance & Experience Section
            if (page == SettingsPage.Chats) {
            SettingsSection(
                title = "Appearance & Experience",
                icon = Icons.Rounded.Palette
            ) {
                ListItem(
                    headlineContent = { Text("App Theme", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("System, Day, or Dark mode", fontSize = 13.sp) },
                    leadingContent = { Icon(Icons.Rounded.BrightnessMedium, contentDescription = null, tint = primaryAccent) }
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
                        2 to "Dark",
                        // Pure black for AMOLED screens; applies to Notes too
                        3 to "Black"
                    ).forEach { (mode, label) ->
                        val isSelected = uiState.themeMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .pressScaleTarget()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) primaryAccent else Color.Transparent)
                                .springClickable(tick = true) { viewModel.setThemeMode(mode) }
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
                    leadingContent = { Icon(Icons.Rounded.Wallpaper, contentDescription = null, tint = primaryAccent) }
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
                        2 to "Theme 2",
                        3 to "Custom"
                    ).forEach { (theme, label) ->
                        val isSelected = uiState.chatBgTheme == theme
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .pressScaleTarget()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) primaryAccent else Color.Transparent)
                                .springClickable(tick = true) {
                                    if (theme == 3 && uiState.customWallpaperPath == null) {
                                        val prefs = com.example.security.SecurityPreferences.getInstance(context)
                                        prefs.isExternalPickerActive = true
                                        prefs.ignoreNextPause = true
                                        customWallpaperPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    } else {
                                        viewModel.setChatBgTheme(theme)
                                    }
                                }
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

                if (uiState.chatBgTheme == 3 || uiState.customWallpaperPath != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = primaryAccent.copy(alpha = 0.12f),
                            modifier = Modifier
                                .weight(1f)
                                .pressScaleTarget()
                                .clip(RoundedCornerShape(10.dp))
                                .springClickable(tick = true) {
                                    val prefs = com.example.security.SecurityPreferences.getInstance(context)
                                    prefs.isExternalPickerActive = true
                                    prefs.ignoreNextPause = true
                                    customWallpaperPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PhotoLibrary,
                                    contentDescription = null,
                                    tint = primaryAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (uiState.customWallpaperPath != null) "Change Custom Wallpaper" else "Choose From Gallery",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryAccent
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                // How the chat's messages read: their text size and font, with a preview
                ChatTextSettings(
                    textSize = uiState.chatTextSize,
                    font = uiState.chatFont,
                    accent = primaryAccent,
                    onTextSizeChange = { viewModel.setChatTextSize(it) },
                    onFontChange = { viewModel.setChatFont(it) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                ListItem(
                    headlineContent = { Text("Photo Thumbnail Size", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Grid sizing in chat photo gallery", fontSize = 13.sp) },
                    leadingContent = { Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = primaryAccent) }
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
                                .pressScaleTarget()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) primaryAccent else Color.Transparent)
                                .springClickable(tick = true) { viewModel.setImageGallerySize(key) }
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
                    headlineContent = { Text("Double-Tap Zoom in Viewer", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Instant 2.8x zoom on photo double-tap", fontSize = 13.sp) },
                    trailingContent = {
                        CherishSwitch(
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
                        CherishSwitch(
                            checked = uiState.isHapticEnabled,
                            onCheckedChange = { viewModel.setHapticEnabled(it) }
                        )
                    }
                )
                // Which ones (each only while Haptic Feedback is on)
                val hapticPrefs = remember { com.example.security.SecurityPreferences.getInstance(context) }
                var hapticSend by remember { mutableStateOf(hapticPrefs.isHapticOnSend()) }
                var hapticReceive by remember { mutableStateOf(hapticPrefs.isHapticOnReceive()) }
                var hapticHeartbeat by remember { mutableStateOf(hapticPrefs.isHeartbeatOnLongPress()) }
                listOf(
                    Triple("Soft tick when sending", hapticSend) { on: Boolean -> hapticSend = on; hapticPrefs.setHapticOnSend(on) },
                    Triple("Gentle buzz on new messages", hapticReceive) { on: Boolean -> hapticReceive = on; hapticPrefs.setHapticOnReceive(on) },
                    Triple("Heartbeat on long-press", hapticHeartbeat) { on: Boolean -> hapticHeartbeat = on; hapticPrefs.setHeartbeatOnLongPress(on) }
                ).forEach { (label, checked, onChange) ->
                    ListItem(
                        headlineContent = { Text(label, fontSize = 14.sp) },
                        modifier = Modifier.padding(start = 16.dp),
                        trailingContent = {
                            CherishSwitch(
                                checked = checked && uiState.isHapticEnabled,
                                enabled = uiState.isHapticEnabled,
                                onCheckedChange = onChange
                            )
                        }
                    )
                }
            }
            // Chat Experience Mode Selector
            SettingsSection(title = "Chat Experience", icon = Icons.Rounded.ChatBubble) {
                val currentMode = uiState.chatExperienceMode

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // ❤️ Normal Mode option
                    Surface(
                        shape = RoundedCornerShape(16.dp),
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
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                            CherishRadioButton(
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
                        shape = RoundedCornerShape(16.dp),
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
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                            CherishRadioButton(
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
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                                        imageVector = if (uiState.isChatSoundsEnabled) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
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
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                            CherishSwitch(
                                checked = uiState.isChatSoundsEnabled,
                                onCheckedChange = { viewModel.setChatSoundsEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = RoseGoldPrimary
                                )
                            )
                        }
                    }

                    // How loud the app's sounds play (a sample plays when you let go)
                    val soundPrefs = remember { com.example.security.SecurityPreferences.getInstance(context) }
                    var soundVolume by remember { mutableFloatStateOf(soundPrefs.getAppSoundVolume().toFloat()) }
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(
                                imageVector = if (soundVolume <= 0f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = null,
                                tint = primaryAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Sound volume", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("${soundVolume.toInt()}%", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        CherishSlider(
                            value = soundVolume,
                            onValueChange = { soundVolume = it },
                            onValueChangeFinished = {
                                soundPrefs.setAppSoundVolume(soundVolume.toInt())
                                com.example.audio.ChatSoundEffectsPlayer.getInstance(context)
                                    .playSound(com.example.audio.ChatSoundEffectsPlayer.SoundType.SENT)
                            },
                            valueRange = 0f..100f,
                            enabled = uiState.isChatSoundsEnabled,
                            modifier = Modifier.testTag("app_sound_volume")
                        )
                    }
                }
            }

            } // Chats

            // 5. Check-After & Shared Presence Section
            if (page == SettingsPage.Account) {
            SettingsSection(
                title = "Check-After & Presence",
                icon = Icons.Rounded.HourglassTop
            ) {
                val myActiveCheckAfter = user?.hasActiveCheckAfter() == true
                val partnerActiveCheckAfter = partner?.hasActiveCheckAfter() == true

                if (myActiveCheckAfter) {
                    Column(modifier = Modifier.padding(16.dp)) {
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
                            CherishTonalButton(
                                onClick = { viewModel.extendCheckAfter(30 * 60 * 1000L) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("+30m", fontSize = 12.sp)
                            }
                            CherishTonalButton(
                                onClick = { viewModel.extendCheckAfter(60 * 60 * 1000L) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("+1h", fontSize = 12.sp)
                            }
                            CherishOutlinedButton(
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
                                Text("Cancel", fontSize = 12.sp, color = Red700)
                            }
                        }
                    }
                } else if (partnerActiveCheckAfter) {
                    Column(modifier = Modifier.padding(16.dp)) {
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
                            color = if (isExpired) (if (isDark) darkTone(Color(0xFF86EFAC)) else Green800) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        CherishButton(
                            onClick = { showCheckAfterSheet = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryAccent),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Icon(Icons.Rounded.HourglassTop, contentDescription = null, modifier = Modifier.size(16.dp))
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
                        CherishSwitch(
                            checked = uiState.isCheckAfterReminderEnabled,
                            onCheckedChange = { viewModel.setCheckAfterReminderEnabled(it) }
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                ListItem(
                    headlineContent = { Text("Partner Location & Time Zone", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        val currentTz = partner?.timeZone ?: "Auto-detected"
                        val displayCity = when {
                            currentTz.contains("Sydney", ignoreCase = true) -> "Sydney (AEST/AEDT, UTC+10/11) 🇦🇺"
                            currentTz.contains("Melbourne", ignoreCase = true) -> "Melbourne (AEST/AEDT, UTC+10/11) 🇦🇺"
                            currentTz.contains("Brisbane", ignoreCase = true) -> "Brisbane (AEST, UTC+10) 🇦🇺"
                            currentTz.contains("Perth", ignoreCase = true) -> "Perth (AWST, UTC+8) 🇦🇺"
                            currentTz.contains("Adelaide", ignoreCase = true) -> "Adelaide (ACST/ACDT, UTC+9:30/10:30) 🇦🇺"
                            currentTz.contains("Darwin", ignoreCase = true) -> "Darwin (ACST, UTC+9:30) 🇦🇺"
                            currentTz.contains("Hobart", ignoreCase = true) -> "Hobart (AEST/AEDT, UTC+10/11) 🇦🇺"
                            currentTz.startsWith("Australia/", ignoreCase = true) -> currentTz.substringAfter("Australia/") + " 🇦🇺"
                            else -> currentTz
                        }
                        Text("Auto-synced: $displayCity", fontSize = 13.sp)
                    },
                    leadingContent = { Icon(Icons.Rounded.Schedule, contentDescription = null, tint = primaryAccent) }
                )
            }

            } // Account

            // 6. Security Tools & Vault Section
            if (page == SettingsPage.Privacy) {
            val secPrefs = remember { com.example.security.SecurityPreferences.getInstance(context) }
            val securityScore = secPrefs.getSecurityAuditScore()

            SettingsSection(
                title = "Security & Tools",
                icon = Icons.Rounded.VerifiedUser
            ) {
                ListItem(
                    headlineContent = { Text("Privacy & Security Audit", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(
                            text = "${securityScore.passedCount}/${securityScore.totalCount} protections active • Tap to audit",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = primaryAccent)
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (securityScore.passedCount >= 6) Emerald500.copy(alpha = 0.15f) else Amber500.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${securityScore.passedCount}/${securityScore.totalCount} Active 🛡️",
                                    color = if (securityScore.passedCount >= 6) Color(0xFF059669) else Color(0xFFD97706),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    modifier = Modifier.clickable { onNavigateToPrivacyAudit() }.testTag("privacy_security_audit_item")
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                ListItem(
                    headlineContent = { Text("Authorized Devices & Sessions", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("View active logins, manage pairing & remote logout", fontSize = 13.sp) },
                    leadingContent = {
                        Icon(Icons.Rounded.Devices, contentDescription = null, tint = primaryAccent)
                    },
                    trailingContent = {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    modifier = Modifier.clickable { onNavigateToDeviceSessions() }
                )
            }
            } // Privacy

            // 7. Storage: the cache, and Backup & Recovery
            if (page == SettingsPage.Storage) {
            SettingsSection(
                title = "Storage & Cache",
                icon = Icons.Rounded.CleaningServices
            ) {
                ListItem(
                    headlineContent = { Text("Storage & Cache Manager", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Clear temporary media cache without deleting chats", fontSize = 13.sp) },
                    leadingContent = {
                        Icon(Icons.Rounded.CleaningServices, contentDescription = null, tint = primaryAccent)
                    },
                    trailingContent = {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    modifier = Modifier.clickable { onNavigateToStorageManager() }
                )
            }

            SettingsSection(
                title = "Backup & Recovery",
                icon = Icons.Rounded.CloudDone
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                            color = Green50
                        ) {
                            Text(
                                text = "Always Active",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Green800,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                            .clip(RoundedCornerShape(12.dp))
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
                        Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(15.dp), tint = primaryAccent)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Google Drive Transfer & Details", fontSize = 12.sp, color = primaryAccent, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            } // Storage

            // 8. Updates & Logout Actions, under the main list
            if (page == null) {
            CherishButton(
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
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = "Check for Updates",
                    tint = primaryAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Check for Updates", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            Text(
                text = "Installed: ${com.example.BuildConfig.VERSION_NAME} (build ${com.example.BuildConfig.VERSION_CODE})",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )


            CherishButton(
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
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Out", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            } // Main list

            Spacer(modifier = Modifier.height(24.dp))
        }
        } // AnimatedContent
    }

    

    // Avatar Options Dialog (Camera, Gallery, Remove)
    if (showAvatarOptionsDialog) {
        CherishAlertDialog(
            onDismissRequest = { showAvatarOptionsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.CameraAlt,
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
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                                        CherishToast.show("Profile photo removed")
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
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

    // Get every message and call: opened from Notifications or from Calls, so it lives outside the pages
    if (showDeliverySetup) {
        com.example.ui.components.DeliverySetupSheet(onDismiss = {
            showDeliverySetup = false
            deliveryMissing = com.example.notifications.BackgroundDelivery.missingCount(context)
        })
    }

    if (showSamsungVivoPrompt) {
        val primaryAccent = MaterialTheme.colorScheme.primary
        val isSamsung = com.example.notifications.BackgroundDelivery.isSamsung()
        val appName = try {
            context.applicationInfo.loadLabel(context.packageManager).toString()
        } catch (_: Exception) {
            "Calculator"
        }
        CherishAlertDialog(
            onDismissRequest = {
                showSamsungVivoPrompt = false
                com.example.notifications.BackgroundDelivery.setSeenSamsungVivoPrompt(context, true)
            },
            title = {
                Text(
                    if (isSamsung) "Samsung Device Care Setup" else "Vivo iManager Setup",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (isSamsung) {
                            "Samsung's Device Care aggressively pauses background sync and silences incoming calls when $appName is closed.\n\nTo ensure you receive calls and messages:"
                        } else {
                            "Vivo's iManager / Speed Up kills background processes and blocks incoming calls when $appName is closed.\n\nTo ensure you receive calls and messages:"
                        },
                        fontSize = 13.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isSamsung) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        if (isBatteryOptimizationIgnored) Icons.Rounded.CheckCircle else Icons.Rounded.BatteryChargingFull,
                                        contentDescription = null,
                                        tint = if (isBatteryOptimizationIgnored) Emerald500 else primaryAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("1. Set Battery to Unrestricted", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Prevents Samsung from freezing background sync", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    CherishTonalButton(
                                        onClick = { com.example.notifications.BackgroundDelivery.requestUnrestricted(context) },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                    ) {
                                        Text(if (isBatteryOptimizationIgnored) "Done" else "Allow", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.SettingsPower,
                                        contentDescription = null,
                                        tint = primaryAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("2. Add to 'Never sleeping apps'", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text("In Device Care > Battery > Background usage limits", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    CherishTonalButton(
                                        onClick = { com.example.notifications.BackgroundDelivery.openSamsungDeviceCare(context) },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                    ) {
                                        Text("Open", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.PlayCircleOutline,
                                        contentDescription = null,
                                        tint = primaryAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("1. Enable Auto-start in iManager", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text("App manager > Autostart: toggle $appName ON", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    CherishTonalButton(
                                        onClick = { com.example.notifications.BackgroundDelivery.openVivoAutoStart(context) },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                    ) {
                                        Text("Open", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        if (isBatteryOptimizationIgnored) Icons.Rounded.CheckCircle else Icons.Rounded.BatteryChargingFull,
                                        contentDescription = null,
                                        tint = if (isBatteryOptimizationIgnored) Emerald500 else primaryAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("2. High Background Power", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Battery > Background power consumption: allow $appName", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    CherishTonalButton(
                                        onClick = {
                                            com.example.notifications.BackgroundDelivery.openVivoBackgroundPower(context)
                                            com.example.notifications.BackgroundDelivery.requestUnrestricted(context)
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                    ) {
                                        Text("Allow", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                CherishButton(
                    onClick = {
                        showSamsungVivoPrompt = false
                        com.example.notifications.BackgroundDelivery.setSeenSamsungVivoPrompt(context, true)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Got It", fontWeight = FontWeight.Bold)
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

        CherishAlertDialog(
            onDismissRequest = {
                if (!uiState.isUpdating) showEditProfileDialog = false
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
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
                                .pressScaleTarget()
                                .clip(CircleShape)
                                .springClickable {
                                    showAvatarOptionsDialog = true
                                }
                        )
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .pressScaleTarget()
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .springClickable { showAvatarOptionsDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.CameraAlt,
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
                            Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = {
                            if (nameInput.isNotBlank()) {
                                IconButton(onClick = { nameInput = "" }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
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
                            Icon(Icons.Rounded.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                                CherishFilterChip(
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
                CherishButton(
                    onClick = {
                        val finalName = nameInput.trim().ifBlank { user?.displayName ?: "Me" }
                        val finalStatus = statusInput.trim().ifBlank { "Loving every moment with you ✨" }
                        viewModel.updateProfile(
                            displayName = finalName,
                            status = finalStatus,
                            newPhotoUri = null,
                            removePhoto = false
                        ) {
                            CherishToast.show("Profile updated successfully ✨")
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
        CherishAlertDialog(
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
                CherishButton(
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
        CherishAlertDialog(
            onDismissRequest = { showSetPasscodeDialog = false },
            title = {
                Text(
                    if (uiState.disguiseStyle == SecurityPreferences.DISGUISE_STYLE_CALCULATOR)
                        "Set Calculator Passcode"
                    else "Set Secret Trigger Word"
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (uiState.disguiseStyle == SecurityPreferences.DISGUISE_STYLE_CALCULATOR)
                            "Enter the secret passcode or PIN. In the Calculator, typing this and tapping '=' (or holding '=') will reveal Cherish."
                        else "Set your private trigger word. Typing this into the Notes search bar will immediately open Secret Chat without showing the word.",
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
                        label = {
                            Text(
                                if (uiState.disguiseStyle == SecurityPreferences.DISGUISE_STYLE_CALCULATOR)
                                    "Passcode / PIN (e.g. 1234 or love)"
                                else "Secret Trigger Word (e.g. love)"
                            )
                        },
                        visualTransformation = if (isDialogPasscodeRevealed) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isDialogPasscodeRevealed = !isDialogPasscodeRevealed }) {
                                Icon(
                                    imageVector = if (isDialogPasscodeRevealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
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
                CherishButton(
                    onClick = {
                        if (newPasscodeText.isNotBlank()) {
                            if (uiState.disguiseStyle == SecurityPreferences.DISGUISE_STYLE_CALCULATOR) {
                                viewModel.setCalculatorPasscode(newPasscodeText)
                            } else {
                                viewModel.setDisguisePasscode(newPasscodeText)
                            }
                            showSetPasscodeDialog = false
                        }
                    },
                    enabled = newPasscodeText.length >= 2
                ) {
                    Text(if (uiState.disguiseStyle == SecurityPreferences.DISGUISE_STYLE_CALCULATOR) "Save Passcode" else "Save Trigger Word")
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
        var partnerEmailInput by remember {
            mutableStateOf(
                user?.partnerEmail?.removeSuffix("@cherish.app")
                    ?: partner?.displayName
                    ?: ""
            )
        }
        var coupleKeyInput by remember {
            mutableStateOf(
                uiState.coupleKey.removePrefix("COUPLE_").removePrefix("couple_")
            )
        }

        CherishAlertDialog(
            onDismissRequest = { showPairDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("User Login Settings", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Update your partner's account and shared couple passcode. Your name is your display name, in Edit Profile.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                CherishButton(
                    onClick = {
                        viewModel.updateLoginCredentials(partnerEmailInput, coupleKeyInput) { success ->
                            CherishToast.show(if (success) "Login settings updated successfully! ✨" else "Update failed")
                        }
                        showPairDialog = false
                    },
                    shape = RoundedCornerShape(12.dp)
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
        },
        onRequestSpace = { target, note ->
            val asked = viewModel.requestSpace(target, note)
            CherishToast.show(if (asked) "Asked your partner. It starts when they accept." else "Couldn't send the request. Try again.", long = true)
        }
    )
}

/** The sign-in details to type in again after a Clean Reinstall (hidden until revealed). */
@Composable
private fun ReinstallLoginCard(
    login: ProfileViewModel.ReinstallLogin,
    revealed: Boolean,
    onToggleReveal: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Your sign-in details", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = onToggleReveal, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (revealed) "Hide" else "Reveal",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (login.userName.isNotBlank()) {
                Text("Your name: " + (if (revealed) login.userName else "••••••••"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            if (login.partnerName.isNotBlank()) {
                Text("Partner name: " + (if (revealed) login.partnerName else "••••••••"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            Text("Secret key: " + (if (revealed) login.coupleKey else "••••••••"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Your app-lock PIN resets on reinstall; set it again after.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}






