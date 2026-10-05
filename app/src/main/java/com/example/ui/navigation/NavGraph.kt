package com.example.ui.navigation
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import com.example.ui.theme.HeartRed
import kotlin.math.roundToInt
import com.example.CherishApplication
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.AuthViewModel
import com.example.ui.chat.ChatScreen
import com.example.ui.chat.ChatViewModel
import com.example.ui.backup.CloudBackupScreen
import com.example.ui.backup.GoogleDriveBackupViewModel
import com.example.ui.dates.ImportantDatesScreen
import com.example.ui.dates.ImportantDatesViewModel
import com.example.ui.disguise.NotesDisguiseScreen
import com.example.ui.disguise.NotesDisguiseViewModel
import com.example.ui.gallery.SharedGalleryScreen
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.journey.LifetimeJourneyScreen
import com.example.ui.journey.LifetimeJourneyViewModel
import com.example.ui.lock.AppLockScreen
import com.example.ui.memories.MemoriesScreen
import com.example.ui.memories.MemoriesViewModel
import com.example.ui.notes.SharedNotesScreen
import com.example.ui.notes.SharedNotesViewModel
import com.example.ui.profile.ProfileScreen
import com.example.ui.profile.ProfileViewModel

@Composable
fun CherishNavGraph(
    app: CherishApplication,
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    val isDisguiseActive by app.securityPreferences.isDisguiseActive.collectAsState()
    val hasRevealedSecretApp by app.securityPreferences.hasRevealedSecretAppInSession.collectAsState()
    val isAppLocked by app.securityPreferences.isAppLocked.collectAsState()
    val isUserLoggedIn = remember { app.authRepository.isUserLoggedIn() }

    // Stable start destination: computed once per process, survives recomposition
    // and Activity recreation so navigation state is never accidentally reset
    val startDestination = rememberSaveable {
        when {
            !isUserLoggedIn -> Screen.Auth.route
            else -> Screen.Chat.route
        }
    }

    // Whenever opening secret app, it should go to chat tab only
    LaunchedEffect(isDisguiseActive) {
        if (!isDisguiseActive && isUserLoggedIn) {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute != Screen.Chat.route) {
                val popped = navController.popBackStack(Screen.Chat.route, inclusive = false)
                if (!popped) {
                    navController.navigate(Screen.Chat.route) {
                        popUpTo(0) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    val appAlpha by animateFloatAsState(
        targetValue = if (isDisguiseActive) 0f else 1f,
        animationSpec = if (isDisguiseActive) snap() else tween(160, easing = FastOutSlowInEasing),
        label = "app_reveal_alpha"
    )
    val appScale by animateFloatAsState(
        targetValue = if (isDisguiseActive) 0.96f else 1f,
        animationSpec = if (isDisguiseActive) snap() else tween(160, easing = FastOutSlowInEasing),
        label = "app_reveal_scale"
    )

    val notesDisguiseViewModel = remember {
        NotesDisguiseViewModel(app.notesRepository)
    }

    // Share a single ChatViewModel instance across Chat and its sub-screens
    val sharedChatViewModel = remember {
        ChatViewModel(
            app.authRepository,
            app.chatRepository,
            app.mediaRepository,
            app.voiceRecorderHelper,
            app.voicePlayerHelper
        )
    }

    // Issue 12: All ViewModels hoisted here so they survive tab switching.
    // Creating VMs inside composable{} lambdas destroys and recreates them on
    // every navigation, causing Firestore re-requests and scroll position resets.
    val homeViewModel = remember {
        HomeViewModel(app.authRepository, app.chatRepository, app.coupleFeaturesRepository)
    }
    val memoriesViewModel = remember {
        MemoriesViewModel(app.coupleFeaturesRepository, app.mediaRepository)
    }
    val importantDatesViewModel = remember {
        ImportantDatesViewModel(app.coupleFeaturesRepository)
    }
    val notesViewModel = remember {
        SharedNotesViewModel(app.coupleFeaturesRepository)
    }
    val profileViewModel = remember {
        ProfileViewModel(
            app.authRepository,
            app.mediaRepository,
            app.securityPreferences,
            app.googleDriveBackupManager
        )
    }
    val lifetimeViewModel = remember {
        LifetimeJourneyViewModel(app.coupleFeaturesRepository)
    }
    val backupViewModel = remember {
        GoogleDriveBackupViewModel(app.googleDriveBackupManager)
    }

    val context = LocalContext.current
    val updateState by profileViewModel.updateState.collectAsState()

    LaunchedEffect(isDisguiseActive, hasRevealedSecretApp) {
        if (!isDisguiseActive && hasRevealedSecretApp) {
            profileViewModel.silentCheckForUpdates(context)
        } else {
            profileViewModel.dismissUpdateDialog()
        }
    }

    // Inside Cherish secret app (unlocked): Android system back button & edge gesture returns to Notes app for security
    BackHandler(enabled = !isDisguiseActive && hasRevealedSecretApp) {
        app.securityPreferences.reDisguise()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = appAlpha
                    scaleX = appScale
                    scaleY = appScale
                },
        enterTransition = {
            val fromOrder = getTabOrder(initialState.destination.route)
            val toOrder = getTabOrder(targetState.destination.route)
            if (fromOrder >= 0 && toOrder >= 0 && fromOrder != toOrder) {
                if (toOrder > fromOrder) {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(200))
                } else {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(200))
                }
            } else {
                fadeIn(animationSpec = tween(160, easing = FastOutSlowInEasing)) +
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(160, easing = FastOutSlowInEasing))
            }
        },
        exitTransition = {
            val fromOrder = getTabOrder(initialState.destination.route)
            val toOrder = getTabOrder(targetState.destination.route)
            if (fromOrder >= 0 && toOrder >= 0 && fromOrder != toOrder) {
                if (toOrder > fromOrder) {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(180))
                } else {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(180))
                }
            } else {
                fadeOut(animationSpec = tween(130, easing = FastOutLinearInEasing)) +
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(130, easing = FastOutLinearInEasing))
            }
        },
        popEnterTransition = {
            val fromOrder = getTabOrder(initialState.destination.route)
            val toOrder = getTabOrder(targetState.destination.route)
            if (fromOrder >= 0 && toOrder >= 0 && fromOrder != toOrder) {
                if (toOrder > fromOrder) {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(200))
                } else {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(200))
                }
            } else {
                fadeIn(animationSpec = tween(160, easing = FastOutSlowInEasing)) +
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(160, easing = FastOutSlowInEasing))
            }
        },
        popExitTransition = {
            val fromOrder = getTabOrder(initialState.destination.route)
            val toOrder = getTabOrder(targetState.destination.route)
            if (fromOrder >= 0 && toOrder >= 0 && fromOrder != toOrder) {
                if (toOrder > fromOrder) {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(180))
                } else {
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(180))
                }
            } else {
                fadeOut(animationSpec = tween(130, easing = FastOutLinearInEasing)) +
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(130, easing = FastOutLinearInEasing))
            }
        }
    ) {
        composable(Screen.Auth.route) {
            val authViewModel = remember { AuthViewModel(app.authRepository) }
            AuthScreen(
                viewModel = authViewModel,
                onAuthSuccess = {
                    navController.navigate(Screen.Chat.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToChat = {
                    val popped = navController.popBackStack(Screen.Chat.route, inclusive = false)
                    if (!popped) {
                        navController.navigate(Screen.Chat.route) { launchSingleTop = true }
                    }
                },
                onNavigateToMemories = { navController.navigate(Screen.Memories.route) { launchSingleTop = true } },
                onNavigateToDates = { navController.navigate(Screen.ImportantDates.route) { launchSingleTop = true } },
                onNavigateToNotes = { navController.navigate(Screen.SharedNotes.route) { launchSingleTop = true } },
                onNavigateToOpenWhen = { navController.navigate(Screen.OpenWhen.route) { launchSingleTop = true } },
                onNavigateToGallery = { navController.navigate(Screen.SharedGallery.route) { launchSingleTop = true } },
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) { launchSingleTop = true } },
                onNavigateToLifetimeJourney = { navController.navigate(Screen.LifetimeJourney.route) { launchSingleTop = true } },
                onNavigateToCloudBackup = { navController.navigate(Screen.CloudBackup.route) { launchSingleTop = true } },
                onQuickDisguise = { app.securityPreferences.reDisguise() }
            )
        }

        composable(Screen.Chat.route) {
            ChatScreen(
                viewModel = sharedChatViewModel,
                onNavigateBack = {
                    app.securityPreferences.reDisguise()
                },
                onNavigateToGallery = {
                    app.securityPreferences.ignoreChatNavigation = true
                    navController.navigate(Screen.SharedGallery.route)
                },
                onQuickDisguise = { app.securityPreferences.reDisguise() },
                onNavigateToHome = {
                    app.securityPreferences.ignoreChatNavigation = true
                    val popped = navController.popBackStack(Screen.Home.route, inclusive = false)
                    if (!popped) {
                        navController.navigate(Screen.Home.route) {
                            launchSingleTop = true
                        }
                    }
                },
                onNavigateToProfile = {
                    app.securityPreferences.ignoreChatNavigation = true
                    val popped = navController.popBackStack(Screen.Profile.route, inclusive = false)
                    if (!popped) {
                        navController.navigate(Screen.Profile.route) {
                            launchSingleTop = true
                        }
                    }
                },
                onLoggedOut = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Memories.route) {
            MemoriesScreen(
                viewModel = memoriesViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ImportantDates.route) {
            ImportantDatesScreen(
                viewModel = importantDatesViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SharedNotes.route) {
            SharedNotesScreen(
                viewModel = notesViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SharedGallery.route) {
            SharedGalleryScreen(
                chatViewModel = sharedChatViewModel,
                onNavigateBack = {
                    app.securityPreferences.ignoreChatNavigation = false
                    navController.popBackStack()
                },
                onNavigateToMessage = { messageId ->
                    sharedChatViewModel.navigateToMessageInChat(messageId)
                    app.securityPreferences.ignoreChatNavigation = false
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                viewModel = profileViewModel,
                onNavigateBack = {
                    app.securityPreferences.ignoreChatNavigation = false
                    app.securityPreferences.reDisguise()
                },
                onNavigateToChat = {
                    val popped = navController.popBackStack(Screen.Chat.route, inclusive = false)
                    if (!popped) {
                        navController.navigate(Screen.Chat.route) {
                            launchSingleTop = true
                        }
                    }
                },
                onNavigateToCloudBackup = {
                    navController.navigate(Screen.CloudBackup.route) { launchSingleTop = true }
                },
                onNavigateToPrivacyAudit = {
                    navController.navigate(Screen.PrivacyAudit.route) { launchSingleTop = true }
                },
                onNavigateToStorageManager = {
                    app.securityPreferences.ignoreChatNavigation = true
                    navController.navigate(Screen.StorageManager.route) { launchSingleTop = true }
                },
                onNavigateToDeviceSessions = {
                    app.securityPreferences.ignoreChatNavigation = true
                    navController.navigate(Screen.DeviceSessions.route) { launchSingleTop = true }
                },
                onNavigateToOpenWhen = {
                    navController.navigate(Screen.OpenWhen.route) { launchSingleTop = true }
                },
                onLoggedOut = {
                    app.securityPreferences.ignoreChatNavigation = false
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.LifetimeJourney.route) {
            LifetimeJourneyScreen(
                viewModel = lifetimeViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.CloudBackup.route) {
            CloudBackupScreen(
                viewModel = backupViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.PrivacyAudit.route) {
            com.example.ui.security.PrivacyAuditScreen(
                securityPreferences = app.securityPreferences,
                onNavigateBack = { app.securityPreferences.reDisguise() },
                onNavigateToBackup = { navController.navigate(Screen.CloudBackup.route) }
            )
        }

        composable(Screen.OpenWhen.route) {
            com.example.ui.home.OpenWhenScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.StorageManager.route) {
            com.example.ui.profile.StorageManagerScreen(
                onNavigateBack = {
                    app.securityPreferences.ignoreChatNavigation = false
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.DeviceSessions.route) {
            com.example.ui.profile.DeviceSessionsScreen(
                onNavigateBack = {
                    app.securityPreferences.ignoreChatNavigation = false
                    navController.popBackStack()
                }
            )
        }
    }

    // App Lock overlay if locked and not in disguise
    if (isAppLocked && !isDisguiseActive) {
        AppLockScreen(
            securityPreferences = app.securityPreferences,
            onUnlocked = {
                if (!app.authRepository.isUserLoggedIn()) {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                } else {
                    navController.navigate(Screen.Chat.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        )
    }

    // Real Notes Disguise screen - instant zero-latency snap on disguise, elegant smooth reveal on unlock
    AnimatedVisibility(
        visible = isDisguiseActive,
        enter = EnterTransition.None,
        exit = fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing)) +
               scaleOut(targetScale = 1.04f, animationSpec = tween(160, easing = FastOutSlowInEasing)),
        modifier = Modifier.fillMaxSize()
    ) {
        NotesDisguiseScreen(
            viewModel = notesDisguiseViewModel,
            securityPreferences = app.securityPreferences,
            onSecretGestureTriggered = {
                app.securityPreferences.revealSecretApp()
                if (isUserLoggedIn) {
                    val currentRoute = navController.currentBackStackEntry?.destination?.route
                    if (currentRoute != Screen.Chat.route) {
                        val popped = navController.popBackStack(Screen.Chat.route, inclusive = false)
                        if (!popped) {
                            navController.navigate(Screen.Chat.route) {
                                popUpTo(0) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
    // In-App OTA Updates Dialog: ONLY show inside Cherish app, NEVER inside Notes app
    if (updateState.showDialog && !isDisguiseActive && hasRevealedSecretApp) {
        AlertDialog(
            onDismissRequest = {
                if (!updateState.isDownloading) profileViewModel.dismissUpdateDialog()
            },
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
                            .size(70.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                updateState.isDownloading -> Icons.Default.CloudDownload
                                updateState.isReadyToInstall -> Icons.Default.CheckCircle
                                updateState.isUpdateAvailable -> Icons.Default.SystemUpdate
                                else -> Icons.Default.CheckCircle
                            },
                            contentDescription = "System Update",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = when {
                            updateState.isChecking -> "Checking for Updates..."
                            updateState.isDownloading -> "Downloading Update..."
                            updateState.isReadyToInstall -> "Update Ready to Install!"
                            updateState.isUpdateAvailable -> "Update Available! ??"
                            else -> "Cherish is Up to Date ?"
                        },
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Version ${if (updateState.isUpdateAvailable) updateState.latestVersion else updateState.currentVersion}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (updateState.isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                    } else if (updateState.isDownloading) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinearProgressIndicator(
                                progress = { updateState.downloadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val pct = (updateState.downloadProgress * 100).toInt()
                            val dlMb = updateState.downloadedBytes / (1024f * 1024f)
                            val totMb = updateState.totalBytes / (1024f * 1024f)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${pct}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                if (totMb > 0) {
                                    Text(
                                        String.format(java.util.Locale.US, "%.1f MB / %.1f MB", dlMb, totMb),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Downloading directly inside Cherish...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                                    text = updateState.releaseNotes.ifBlank { "Performance improvements and bug fixes." },
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 19.sp
                                )
                                if (updateState.errorMessage != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Note: ${updateState.errorMessage}",
                                        fontSize = 12.sp,
                                        color = Color(0xFFE53935)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                when {
                    updateState.isDownloading -> {
                    }
                    updateState.isReadyToInstall -> {
                        Button(
                            onClick = { profileViewModel.triggerInstall(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Install Now", fontWeight = FontWeight.Bold)
                        }
                    }
                    updateState.isUpdateAvailable -> {
                        Button(
                            onClick = { profileViewModel.downloadAndInstallUpdate(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download & Install", fontWeight = FontWeight.Bold)
                        }
                    }
                    !updateState.isChecking -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!updateState.downloadUrl.isNullOrBlank()) {
                                OutlinedButton(
                                    onClick = { profileViewModel.downloadAndInstallUpdate(context) },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Re-download Build", fontSize = 12.sp)
                                }
                            }
                            Button(
                                onClick = { profileViewModel.dismissUpdateDialog() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("OK", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            dismissButton = {
                if (!updateState.isDownloading) {
                    TextButton(onClick = { profileViewModel.dismissUpdateDialog() }) {
                        Text(if (updateState.isUpdateAvailable) "Later" else "Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Universal Side Floating Emergency Exit Toggle: available across ALL screens of Cherish app for emergency
    val isSideEmergencyExitEnabled by app.securityPreferences.isSideEmergencyExitEnabled.collectAsState()
    val sideEmergencyExitOpacity by app.securityPreferences.sideEmergencyExitOpacity.collectAsState()
    if (!isDisguiseActive && hasRevealedSecretApp && !isAppLocked && isSideEmergencyExitEnabled) {
        val exitOpacity = sideEmergencyExitOpacity.coerceIn(0.1f, 1.0f)
        val haptic = LocalHapticFeedback.current
        var dragOffsetY by remember { mutableFloatStateOf(0f) }
        var dragOffsetX by remember { mutableFloatStateOf(0f) }
        var isDockedOnLeft by remember { mutableStateOf(false) }
        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

        val handleShape = if (isDockedOnLeft) {
            RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 24.dp, bottomEnd = 24.dp)
        } else {
            RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 0.dp, bottomEnd = 0.dp)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(999f)
        ) {
            Surface(
                shape = handleShape,
                color = if (isDark) Color(0xFF1E2638).copy(alpha = exitOpacity)
                        else Color(0xFF1F2937).copy(alpha = exitOpacity),
                border = BorderStroke(
                    1.dp,
                    if (isDark) Color(0xFF2A364F).copy(alpha = (exitOpacity * 0.7f).coerceIn(0.1f, 0.9f))
                    else Color(0xFF111827).copy(alpha = (exitOpacity * 0.5f).coerceIn(0.1f, 0.8f))
                ),
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .align(if (isDockedOnLeft) Alignment.CenterStart else Alignment.CenterEnd)
                    .offset { IntOffset(dragOffsetX.roundToInt(), dragOffsetY.roundToInt()) }
                    .width(44.dp)
                    .height(88.dp)
                    .pointerInput(isDockedOnLeft) {
                        detectDragGestures(
                            onDragEnd = {
                                if (!isDockedOnLeft && dragOffsetX < -90f) {
                                    isDockedOnLeft = true
                                } else if (isDockedOnLeft && dragOffsetX > 90f) {
                                    isDockedOnLeft = false
                                }
                                dragOffsetX = 0f
                            },
                            onDragCancel = {
                                dragOffsetX = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetX += dragAmount.x
                                dragOffsetY = (dragOffsetY + dragAmount.y).coerceIn(-500f, 500f)
                                // Immediate switch when dragged sufficiently across the screen
                                if (!isDockedOnLeft && dragOffsetX < -180f) {
                                    isDockedOnLeft = true
                                    dragOffsetX = 0f
                                } else if (isDockedOnLeft && dragOffsetX > 180f) {
                                    isDockedOnLeft = false
                                    dragOffsetX = 0f
                                }
                            }
                        )
                    }
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        app.securityPreferences.reDisguise()
                        try {
                            navController.popBackStack(Screen.Chat.route, inclusive = false)
                        } catch (_: Exception) {}
                    }
                    .testTag("side_emergency_exit_toggle")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = if (isDockedOnLeft) 4.dp else 0.dp, end = if (!isDockedOnLeft) 4.dp else 0.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Emergency Exit to Notes",
                        tint = HeartRed.copy(alpha = exitOpacity.coerceAtLeast(0.55f)),
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                if (isDockedOnLeft) {
                                    scaleX = -1f
                                }
                            }
                    )
                }
            }
        }
    }

    } // End of Box
} // End of CherishNavGraph

private fun getTabOrder(route: String?): Int {
    return when (route) {
        Screen.Home.route -> 0
        Screen.Chat.route -> 1
        Screen.Profile.route -> 2
        else -> -1
    }
}

