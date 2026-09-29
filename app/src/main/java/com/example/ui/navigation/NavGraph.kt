package com.example.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

    if (isDisguiseActive) {
        NotesDisguiseScreen(
            securityPreferences = app.securityPreferences,
            onSecretGestureTriggered = {
                app.securityPreferences.revealSecretApp()
            }
        )
        return
    }

    val startDestination = when {
        isAppLocked -> Screen.AppLock.route
        !isUserLoggedIn -> Screen.Auth.route
        else -> Screen.Home.route
    }

    if (isAppLocked) {
        AppLockScreen(
            securityPreferences = app.securityPreferences,
            onUnlocked = {
                // When unlocked, continue to appropriate screen
                if (!app.authRepository.isUserLoggedIn()) {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                } else {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        )
        return
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier.fillMaxSize(),
        enterTransition = {
            fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(220, easing = FastOutSlowInEasing))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(180, easing = FastOutLinearInEasing)) +
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(180, easing = FastOutLinearInEasing))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(220, easing = FastOutSlowInEasing))
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(180, easing = FastOutLinearInEasing)) +
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(180, easing = FastOutLinearInEasing))
        }
    ) {
        composable(Screen.Auth.route) {
            val authViewModel = remember { AuthViewModel(app.authRepository) }
            AuthScreen(
                viewModel = authViewModel,
                onAuthSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            val homeViewModel = remember {
                HomeViewModel(app.authRepository, app.chatRepository, app.coupleFeaturesRepository)
            }
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToChat = { navController.navigate(Screen.Chat.route) },
                onNavigateToMemories = { navController.navigate(Screen.Memories.route) },
                onNavigateToDates = { navController.navigate(Screen.ImportantDates.route) },
                onNavigateToNotes = { navController.navigate(Screen.SharedNotes.route) },
                onNavigateToGallery = { navController.navigate(Screen.SharedGallery.route) },
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                onNavigateToLifetimeJourney = { navController.navigate(Screen.LifetimeJourney.route) },
                onNavigateToCloudBackup = { navController.navigate(Screen.CloudBackup.route) },
                onQuickDisguise = { app.securityPreferences.reDisguise() }
            )
        }

        composable(Screen.Chat.route) {
            val chatViewModel = remember {
                ChatViewModel(
                    app.authRepository,
                    app.chatRepository,
                    app.mediaRepository,
                    app.voiceRecorderHelper,
                    app.voicePlayerHelper
                )
            }
            ChatScreen(
                viewModel = chatViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToGallery = { navController.navigate(Screen.SharedGallery.route) },
                onQuickDisguise = { app.securityPreferences.reDisguise() }
            )
        }

        composable(Screen.Memories.route) {
            val memoriesViewModel = remember {
                MemoriesViewModel(app.coupleFeaturesRepository, app.mediaRepository)
            }
            MemoriesScreen(
                viewModel = memoriesViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ImportantDates.route) {
            val datesViewModel = remember {
                ImportantDatesViewModel(app.coupleFeaturesRepository)
            }
            ImportantDatesScreen(
                viewModel = datesViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SharedNotes.route) {
            val notesViewModel = remember {
                SharedNotesViewModel(app.coupleFeaturesRepository)
            }
            SharedNotesScreen(
                viewModel = notesViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SharedGallery.route) {
            val chatViewModel = remember {
                ChatViewModel(
                    app.authRepository,
                    app.chatRepository,
                    app.mediaRepository,
                    app.voiceRecorderHelper,
                    app.voicePlayerHelper
                )
            }
            SharedGalleryScreen(
                chatViewModel = chatViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Profile.route) {
            val profileViewModel = remember {
                ProfileViewModel(app.authRepository, app.mediaRepository, app.securityPreferences)
            }
            ProfileScreen(
                viewModel = profileViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCloudBackup = { navController.navigate(Screen.CloudBackup.route) },
                onLoggedOut = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.LifetimeJourney.route) {
            val lifetimeViewModel = remember {
                LifetimeJourneyViewModel(app.coupleFeaturesRepository)
            }
            LifetimeJourneyScreen(
                viewModel = lifetimeViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.CloudBackup.route) {
            val backupViewModel = remember {
                GoogleDriveBackupViewModel(app.googleDriveBackupManager)
            }
            CloudBackupScreen(
                viewModel = backupViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
