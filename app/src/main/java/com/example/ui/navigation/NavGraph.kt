package com.example.ui.navigation

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
            fadeIn(animationSpec = tween(160, easing = FastOutSlowInEasing)) +
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(160, easing = FastOutSlowInEasing))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(130, easing = FastOutLinearInEasing)) +
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(130, easing = FastOutLinearInEasing))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(160, easing = FastOutSlowInEasing)) +
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(160, easing = FastOutSlowInEasing))
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(130, easing = FastOutLinearInEasing)) +
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(130, easing = FastOutLinearInEasing))
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
                onNavigateToChat = { navController.navigate(Screen.Chat.route) { launchSingleTop = true } },
                onNavigateToMemories = { navController.navigate(Screen.Memories.route) { launchSingleTop = true } },
                onNavigateToDates = { navController.navigate(Screen.ImportantDates.route) { launchSingleTop = true } },
                onNavigateToNotes = { navController.navigate(Screen.SharedNotes.route) { launchSingleTop = true } },
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
                onNavigateToProfile = {
                    app.securityPreferences.ignoreChatNavigation = true
                    navController.navigate(Screen.Profile.route)
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
                    navController.popBackStack()
                },
                onNavigateToCloudBackup = {
                    navController.navigate(Screen.CloudBackup.route) { launchSingleTop = true }
                },
                onNavigateToPrivacyAudit = {
                    navController.navigate(Screen.PrivacyAudit.route) { launchSingleTop = true }
                },
                onNavigateToStorageManager = {
                    navController.navigate(Screen.StorageManager.route) { launchSingleTop = true }
                },
                onNavigateToDeviceSessions = {
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
                onNavigateBack = { navController.popBackStack() },
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
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.DeviceSessions.route) {
            com.example.ui.profile.DeviceSessionsScreen(
                onNavigateBack = { navController.popBackStack() }
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
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
}
