package com.example.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LoveJarNote
import com.example.ui.components.AvatarView
import com.example.ui.components.FloatingTab
import com.example.ui.components.FloatingTabBar
import com.example.ui.chat.CheckAfterHelper
import kotlinx.coroutines.delay
import com.example.ui.theme.*
import com.example.ui.components.scrollFadingEdges

/** How often the battery is re-read while the Love & Us tab is on screen. */
private const val BATTERY_REFRESH_MS = 3 * 60 * 1000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToChat: () -> Unit,
    onNavigateToMemories: () -> Unit,
    onNavigateToDates: () -> Unit,
    onNavigateToNotes: () -> Unit,
    onNavigateToGallery: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToOpenWhen: () -> Unit = {},
    onNavigateToSynchronicity: () -> Unit = {},
    synchronicityViewModel: com.example.ui.synchronicity.SynchronicityViewModel? = null,
    onNavigateToLifetimeJourney: () -> Unit = {},
    onNavigateToCloudBackup: () -> Unit = {},
    onQuickDisguise: () -> Unit = {},
    // The chat's view model: Heartbeat Touch and "thinking of you" live on this tab
    chatViewModel: com.example.ui.chat.ChatViewModel? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val homeApp = remember { context.applicationContext as com.example.CherishApplication }
    val ourMovies by homeApp.coupleFeaturesRepository.moviesFlow.collectAsState()
    val ourBooks by homeApp.coupleFeaturesRepository.booksFlow.collectAsState()
    val watchParty by homeApp.coupleFeaturesRepository.watchPartyFlow.collectAsState()
    val isDisguiseActive by homeApp.securityPreferences.isDisguiseActive.collectAsState()
    BackHandler(enabled = !isDisguiseActive) {
        onQuickDisguise()
    }

    val chatState = chatViewModel?.uiState?.collectAsState()?.value
    val chatPartnerPresence = chatViewModel?.partnerPresence?.collectAsState()?.value
    val uiState by viewModel.uiState.collectAsState()
    val isPartnerTouching = chatPartnerPresence?.isHeartTouching == true ||
        chatState?.isPartnerHeartTouching == true ||
        uiState.isPartnerHeartTouching

    var showHeartbeatTouch by remember { mutableStateOf(false) }
    var userDismissedHeartbeatTouch by remember { mutableStateOf(false) }
    // The partner started Heartbeat Touch: open it so it can be felt together
    LaunchedEffect(isPartnerTouching) {
        if (!isPartnerTouching) {
            userDismissedHeartbeatTouch = false
        } else if (!userDismissedHeartbeatTouch) {
            showHeartbeatTouch = true
        }
    }
    val currentUser = uiState.currentUser
    val partner = uiState.partnerUser
    val partnerName = partner?.displayName?.ifBlank { null }
        ?: currentUser?.partnerEmail?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
        ?: "My Partner"
    val partnerEmail = partner?.email?.ifBlank { "partner@cherish.app" } ?: "partner@cherish.app"
    val myEmail = currentUser?.email?.ifBlank { "you@cherish.app" } ?: "you@cherish.app"
    val isOnline = uiState.isPartnerOnline
    val statusText = partner?.statusMessage ?: "Together forever & always 💕"

    val partnerHasCheckAfter = partner?.hasActiveCheckAfter() == true
    val partnerCheckAfterTarget = partner?.checkAfterTimeMillis ?: 0L
    var partnerCheckTicker by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(partnerCheckAfterTarget, partnerHasCheckAfter) {
        if (partnerHasCheckAfter) {
            while (true) {
                partnerCheckTicker = System.currentTimeMillis()
                delay(10_000L)
            }
        }
    }

    var showDrawNoteDialog by remember { mutableStateOf(false) }
    var drawnNote by remember { mutableStateOf<LoveJarNote?>(null) }
    var showAddLoveNoteDialog by remember { mutableStateOf(false) }
    var showAddMovieDialog by remember { mutableStateOf(false) }
    var showAddBookDialog by remember { mutableStateOf(false) }
    var showWatchPartySetup by remember { mutableStateOf(false) }

    // The title heart beats a few times when the tab opens, then rests
    val heartBeat = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(Unit) {
        repeat(4) {
            heartBeat.animateTo(1.15f, tween(800, easing = FastOutSlowInEasing))
            heartBeat.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
        }
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
                        if (accX < -70f) {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Cherish",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = HeartRed,
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer {
                                    scaleX = heartBeat.value
                                    scaleY = heartBeat.value
                                }
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToCloudBackup,
                        modifier = Modifier.testTag("home_cloud_backup_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudSync,
                            contentDescription = "Cloud Backup",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                windowInsets = WindowInsets.statusBars
            )
        },
        bottomBar = {
            // Streamlined 3-tab navigation focused on strictly 2-person chat, daily growth, and ironclad
            // security, in a bar floating above the bottom edge
            val streak = uiState.currentUser?.heartbeatStreak ?: 0
            FloatingTabBar(
                tabs = listOf(
                    FloatingTab(
                        label = if (streak > 0) "Love & Us 🔥$streak" else "Love & Us",
                        icon = Icons.Rounded.FavoriteBorder,
                        selectedIcon = Icons.Rounded.Favorite,
                        contentDescription = "Us & Growth"
                    ),
                    FloatingTab(
                        label = "Private Chat",
                        icon = Icons.Rounded.ChatBubbleOutline,
                        selectedIcon = Icons.Rounded.ChatBubble,
                        contentDescription = "Chat",
                        badgeCount = uiState.unreadCount
                    ),
                    FloatingTab(
                        label = "Profile",
                        icon = Icons.Rounded.PersonOutline,
                        selectedIcon = Icons.Rounded.Person,
                        contentDescription = "Profile"
                    )
                ),
                selectedIndex = 0,
                onSelect = { index ->
                    when (index) {
                        1 -> onNavigateToChat()
                        2 -> onNavigateToProfile()
                    }
                }
            )
        },
        // Heartbeat: at the bottom centre, big enough to reach any time
        floatingActionButton = {
            if (chatViewModel != null) {
                HeartbeatButton(
                    isPartnerTouching = isPartnerTouching,
                    onOpenHeartbeatTouch = {
                        userDismissedHeartbeatTouch = false
                        showHeartbeatTouch = true
                    },
                    onSendThinkingOfYou = { chatViewModel.sendThinkingOfYou() }
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        val loveScroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                // The cards run on under the floating tab bar; the bar's room is inside the scroll,
                // so the last card still scrolls clear of it
                .padding(top = paddingValues.calculateTopPadding())
                // They fade softly under the top bar, and toward the bottom edge round the tab bar
                .scrollFadingEdges(loveScroll, top = 18.dp, bottom = 32.dp)
                .verticalScroll(loveScroll)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .padding(bottom = paddingValues.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // A watch party is on and I'm not in it: join it right from here
            com.example.ui.watchparty.WatchPartyInvite()

            // 1. Both of us, side by side: photos with a heart between, online / last seen, battery,
            // ages, days of life (live) and birthdays
            val homeApp = context.applicationContext as com.example.CherishApplication
            val ourDates by homeApp.coupleFeaturesRepository.datesFlow.collectAsState()
            val birthdays by homeApp.authRepository.birthdays.collectAsState()
            val togetherSinceSetting by homeApp.authRepository.togetherSince.collectAsState()
            val myId = currentUser?.id.orEmpty()
            val partnerId = (partner?.id ?: currentUser?.partnerId).orEmpty()
            val homeLifecycle by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
            val homeDisguised by homeApp.securityPreferences.isDisguiseActive.collectAsState()
            // My battery for the card (and the partner's view of it): read now and every few
            // minutes, only while this tab is on screen; nothing listens to the battery otherwise
            val loveUsOnScreen = !homeDisguised && homeLifecycle.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
            LaunchedEffect(loveUsOnScreen) {
                // Today's question (a new one each 6 AM day) and our shared Love & Us data
                if (loveUsOnScreen) homeApp.coupleFeaturesRepository.rebuildLoveUs()
                if (!loveUsOnScreen || !homeApp.authRepository.isUserLoggedIn()) return@LaunchedEffect
                val battery = com.example.util.BatteryStatusHelper(context)
                while (true) {
                    val info = battery.getCurrentBattery()
                    homeApp.authRepository.updateBatteryStatus(info.level, info.isCharging)
                    homeApp.authRepository.updateNotificationStatus()
                    delay(BATTERY_REFRESH_MS)
                }
            }
            val partnerQuietStatus = if (partnerHasCheckAfter) {
                val (_, isExpired) = remember(partnerCheckTicker, partnerCheckAfterTarget) {
                    CheckAfterHelper.calculateRemaining(partnerCheckAfterTarget)
                }
                if (isExpired) "\u2728 You can check now"
                else "Quiet until ${CheckAfterHelper.formatTargetTime(partnerCheckAfterTarget)}"
            } else null
            val activeSleepSync by homeApp.coupleFeaturesRepository.sleepSyncFlow.collectAsState()

            val effectivePartnerTz = partner?.timeZone?.takeIf { it.isNotBlank() }
                ?: homeApp.securityPreferences.getPartnerCustomTimeZone()
                ?: java.util.TimeZone.getDefault().id
            val myTz = currentUser?.timeZone?.takeIf { it.isNotBlank() } ?: java.util.TimeZone.getDefault().id
            val partnerStatusText = when {
                partner?.statusMessage == "Asleep 🌙" -> "Asleep 🌙"
                !partnerQuietStatus.isNullOrBlank() -> partnerQuietStatus
                else -> null
            }
            val mePerson = LovePerson(
                    id = myId,
                    name = currentUser?.displayName?.ifBlank { null } ?: "Me",
                    photoUrl = currentUser?.photoUrl,
                    birthday = birthdays[myId],
                    isOnline = true,
                    lastSeen = 0L,
                    batteryLevel = currentUser?.batteryLevel,
                    isCharging = currentUser?.isCharging == true,
                    email = currentUser?.email,
                    timeZone = myTz,
                    notificationStatus = currentUser?.notificationStatus,
                    notificationPermission = currentUser?.notificationPermission ?: true,
                    isDisguiseEnabled = homeApp.securityPreferences.isDisguiseModeEnabled(),
                    disguiseStyle = homeApp.securityPreferences.getDisguiseStyle()
                )
            val partnerPerson = LovePerson(
                    id = partnerId,
                    name = partnerName,
                    photoUrl = partner?.photoUrl,
                    birthday = birthdays[partnerId],
                    isOnline = isOnline,
                    lastSeen = partner?.lastSeen ?: 0L,
                    status = partnerStatusText,
                    batteryLevel = partner?.batteryLevel,
                    isCharging = partner?.isCharging == true,
                    email = partner?.email?.ifBlank { null } ?: currentUser?.partnerEmail,
                    timeZone = effectivePartnerTz,
                    notificationStatus = partner?.notificationStatus,
                    notificationPermission = partner?.notificationPermission ?: true,
                    isDisguiseEnabled = partner?.isDisguiseEnabled ?: true,
                    disguiseStyle = partner?.disguiseStyle ?: "notes"
                )
            BothOfUsCard(
                me = mePerson,
                partner = partnerPerson,
                partnerNote = partner?.statusMessage,
                togetherSince = remember(ourDates, togetherSinceSetting) {
                    com.example.ui.dates.DateReminders.togetherSince(ourDates, togetherSinceSetting)
                },
                isVisible = !homeDisguised && homeLifecycle.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED),
                onSetBirthday = { userId, date -> homeApp.authRepository.setBirthday(userId, date) },
                onOpenChat = onNavigateToChat,
                onSetTogetherSince = { date -> homeApp.authRepository.setTogetherSince(date) },
                onOpenGallery = onNavigateToGallery,
                onOpenMemories = onNavigateToMemories,
                onOpenDates = onNavigateToDates,
                onOpenNotes = onNavigateToNotes
            )

            // Under the card: Watch Party (Join while one is on), and Wake up if I'm marked asleep
            val iAmAsleep = currentUser?.statusMessage == "Asleep 🌙"
            val partyIsOn = watchParty?.isActive == true
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (iAmAsleep) {
                    LoveActionButton(
                        emoji = "☀️",
                        label = "Wake up",
                        highlighted = true,
                        tag = "wake_up_button",
                        modifier = Modifier.weight(1f),
                        onClick = { homeApp.coupleFeaturesRepository.wakeUpFromSleep() }
                    )
                }
                val iAmWatching = com.example.ui.watchparty.WatchPartyUi.mode !=
                    com.example.ui.watchparty.WatchPartyMode.Hidden
                LoveActionButton(
                    emoji = if (partyIsOn) "🎧" else "🍿",
                    label = when {
                        partyIsOn && iAmWatching -> "Open Watch Together"
                        partyIsOn -> "Join Watch Together"
                        else -> "Watch Together"
                    },
                    highlighted = partyIsOn,
                    live = partyIsOn,
                    tag = if (partyIsOn) "join_watch_party_button" else "ambient_watch_party_button",
                    modifier = Modifier.weight(1f),
                    // Starting one means picking a video first; while one is on, this joins it
                    onClick = {
                        if (partyIsOn) {
                            homeApp.coupleFeaturesRepository.markWatchingParty(true)
                            com.example.ui.watchparty.WatchPartyUi.openTheater()
                        } else {
                            showWatchPartySetup = true
                        }
                    }
                )
            }

            // Instant Tactile Love Nudges (directly below Watch Together button)
            LoveNudgesBar(
                onSendNudge = { name, emoji, msg ->
                    viewModel.sendLoveNudge(name, emoji, msg)
                }
            )

            // 2. Secret Chat Quick Preview Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToChat() }
                    .testTag("open_chat_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (com.example.ui.theme.isAppInDark()) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChatBubble,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Secret Chat",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (uiState.lastMessage != null) {
                                Text(
                                    text = viewModel.formatMessageTime(uiState.lastMessage!!.timestamp),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        if (uiState.isPartnerTyping) {
                            Text(
                                text = "$partnerName is typing...",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else if (uiState.lastMessage != null) {
                            Text(
                                text = lastMessagePreview(uiState.lastMessage!!),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = "Send a sweet message to your love 💕",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (uiState.unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(HeartRed, CircleShape)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (uiState.unreadCount > 99) "99+" else uiState.unreadCount.toString(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Free Daily Voice Call Minutes Card
            val freeCallMinutesRemaining by homeApp.securityPreferences.dailyFreeCallMinutesRemaining.collectAsState()
            val monthlyFreeCallMinutesRemaining by homeApp.securityPreferences.monthlyFreeCallMinutesRemaining.collectAsState()
            val isMonthlyLimitReached = monthlyFreeCallMinutesRemaining <= 0
            val hasActiveCall = com.example.call.CallManager.hasActiveCall()
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToChat() }
                    .testTag("free_call_minutes_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (com.example.ui.theme.isAppInDark()) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background((if (isMonthlyLimitReached) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (hasActiveCall) Icons.Rounded.PhoneInTalk else Icons.Rounded.Call,
                                contentDescription = "Daily Call Minutes",
                                tint = if (isMonthlyLimitReached) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Free Voice Calls",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isMonthlyLimitReached) "Limit Reached" else "$freeCallMinutesRemaining mins left",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isMonthlyLimitReached || freeCallMinutesRemaining <= 10) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isMonthlyLimitReached) {
                                    "10,000 mins used this month • Re-enables 1st of next month"
                                } else if (hasActiveCall) {
                                    "Active call in progress • Free daily limit"
                                } else {
                                    "Resets daily at 6 AM • 60 mins/day"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val callProgress = if (isMonthlyLimitReached) 0f else (freeCallMinutesRemaining / 60f).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { callProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = if (isMonthlyLimitReached) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = (if (isMonthlyLimitReached) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary).copy(alpha = 0.15f)
                    )
                }
            }

            // 3. Lifetime Story Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToLifetimeJourney() }
                    .testTag("lifetime_journey_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (com.example.ui.theme.isAppInDark()) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AllInclusive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Our Lifetime Story",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Track our love across every age & milestone",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Our dates: what's coming up (both birthdays included), and the way to add birthdays and our days
            val nextDates = remember(ourDates, birthdays, myId, partnerId, partnerName) {
                val titles = buildMap {
                    if (myId.isNotBlank()) put(myId, "Your birthday")
                    if (partnerId.isNotBlank()) put(partnerId, "$partnerName's birthday")
                }
                com.example.ui.dates.DateReminders.upcoming(
                    com.example.ui.dates.DateReminders.withBirthdays(ourDates, birthdays, titles), 366
                ).take(3)
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToDates() }
                    .testTag("our_dates_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (com.example.ui.theme.isAppInDark()) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Event,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Our Dates",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (ourDates.isEmpty()) "Add birthdays, anniversaries, our meetings..."
                                else "Birthdays, anniversaries and our days",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = if (ourDates.isEmpty()) Icons.Rounded.Add else Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (nextDates.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            nextDates.forEach { com.example.ui.dates.UpcomingDateRow(it) }
                        }
                    }
                }
            }

            // Open When... Envelopes Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToOpenWhen() }
                    .testTag("open_when_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (com.example.ui.theme.isAppInDark()) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MarkEmailRead,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Open When... Envelopes 💌",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Sealed letters locked until the right moment",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 4. Daily Us — Question of the Day
            DailyQuestionCard(
                dailyQuestion = uiState.dailyQuestion,
                partnerName = partnerName,
                onSubmitAnswer = { answer ->
                    viewModel.submitDailyAnswer(answer)
                },
                onToggleLike = {
                    viewModel.toggleLikeDailyAnswer()
                }
            )

            // Love Synchronicity: the repeating numbers we noticed (11:11, 444...), recorded on purpose
            if (synchronicityViewModel != null) {
                com.example.ui.synchronicity.SynchronicityCard(
                    viewModel = synchronicityViewModel,
                    partnerName = partnerName,
                    isOnScreen = loveUsOnScreen,
                    onOpenHistory = onNavigateToSynchronicity
                )
            }

            // 6. Our movies (who watched each first and second) and our books (who's reading what)
            val people = remember(myId, partnerId, partnerName, mePerson, partnerPerson) {
                CouplePeople(
                    myId = myId,
                    partnerId = partnerId,
                    myName = mePerson.name,
                    partnerName = partnerName,
                    herId = herUserId(mePerson, partnerPerson)
                )
            }
            CoupleMoviesCard(
                movies = ourMovies,
                people = people,
                onToggleWatched = { id -> homeApp.coupleFeaturesRepository.toggleMovieWatched(id) },
                onDeleteMovie = { id -> homeApp.coupleFeaturesRepository.deleteMovie(id) },
                onAddNew = { showAddMovieDialog = true }
            )
            CoupleBooksCard(
                books = ourBooks,
                people = people,
                onUpdateMyPage = { id, page -> homeApp.coupleFeaturesRepository.updateMyBookPage(id, page) },
                onDeleteBook = { id -> homeApp.coupleFeaturesRepository.deleteBook(id) },
                onAddNew = { showAddBookDialog = true }
            )

            // 7. Our month: last month as a little story (with the numbers, emoji, photos, streak)
            com.example.ui.recap.OurMonthCard(
                myId = myId,
                myName = currentUser?.displayName?.ifBlank { null } ?: "Me",
                partnerName = partnerName
            )

            // 8. Our Love Growth: real numbers (days together, Daily Us and Heartbeat Touch streaks)
            LoveGrowthCard(
                daysTogether = com.example.ui.chat.LoveDates.daysTogether(
                    com.example.ui.dates.DateReminders.togetherSince(ourDates, togetherSinceSetting)
                ),
                dailyStreak = uiState.dailyQuestion.streakDays,
                heartbeatStreak = currentUser?.heartbeatStreak ?: 0
            )

            // Room to scroll the last card above the heartbeat button
            Spacer(modifier = Modifier.height(88.dp))
        }
    }

    // Dialogs for Love Jar
    if (showDrawNoteDialog) {
        DrawLoveNoteDialog(
            note = drawnNote,
            onDismiss = { showDrawNoteDialog = false },
            onDrawAnother = {
                val notes = uiState.loveJarNotes
                drawnNote = if (notes.isNotEmpty()) notes.random() else null
            },
            onAddNote = {
                showDrawNoteDialog = false
                showAddLoveNoteDialog = true
            }
        )
    }

    if (showAddLoveNoteDialog) {
        AddLoveNoteDialog(
            onDismiss = { showAddLoveNoteDialog = false },
            onAdd = { text, emoji ->
                viewModel.addLoveJarNote(text, emoji)
            }
        )
    }

    if (showAddMovieDialog) {
        AddMovieDialog(
            onDismiss = { showAddMovieDialog = false },
            onAdd = { title, genre, emoji, watchedTogether, notes ->
                homeApp.coupleFeaturesRepository.addMovie(title, genre, emoji, watchedTogether, notes)
            }
        )
    }

    if (showAddBookDialog) {
        AddBookDialog(
            onDismiss = { showAddBookDialog = false },
            onAdd = { title, author, pages, genre, emoji, notes ->
                homeApp.coupleFeaturesRepository.addBook(title, author, pages, genre, emoji, notes)
            }
        )
    }

    // Heartbeat Touch (moved here from the chat's top bar)
    if (showHeartbeatTouch && chatViewModel != null) {
        com.example.ui.chat.HeartbeatTouchDialog(
            partnerName = partnerName,
            isPartnerTouching = isPartnerTouching,
            isPartnerOnline = chatPartnerPresence?.isOnline == true || chatState?.isPartnerOnline == true || isOnline,
            onTouchChanged = { chatViewModel.setHeartbeatTouch(it) },
            onSyncHeartbeatStreak = { chatViewModel.syncHeartbeatStreak() },
            onDismiss = {
                showHeartbeatTouch = false
                userDismissedHeartbeatTouch = true
            }
        )
    }

    if (showWatchPartySetup) {
        com.example.ui.chat.WatchPartySetupDialog(
            onStart = { videoId, title ->
                homeApp.coupleFeaturesRepository.startWatchParty(videoId, title, "")
                showWatchPartySetup = false
                com.example.ui.watchparty.WatchPartyUi.openTheater()
            },
            onDismiss = { showWatchPartySetup = false }
        )
    }
}

/** A button in the row under the couple card; [live] adds a small dot (something's on now). */
@Composable
private fun LoveActionButton(
    emoji: String,
    label: String,
    highlighted: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (highlighted) accent else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, accent.copy(alpha = if (highlighted) 0f else 0.25f)),
        modifier = modifier.testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (live) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(com.example.ui.theme.OnlineGreen)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(emoji, fontSize = 15.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1
            )
        }
    }
}
