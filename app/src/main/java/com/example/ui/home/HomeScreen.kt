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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import com.example.ui.chat.CheckAfterHelper
import kotlinx.coroutines.delay
import com.example.ui.theme.*

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
    BackHandler {
        onQuickDisguise()
    }

    val chatState = chatViewModel?.uiState?.collectAsState()?.value
    var showHeartbeatTouch by remember { mutableStateOf(false) }
    // The partner started Heartbeat Touch: open it so it can be felt together
    LaunchedEffect(chatState?.isPartnerHeartTouching) {
        if (chatState?.isPartnerHeartTouching == true) showHeartbeatTouch = true
    }

    val uiState by viewModel.uiState.collectAsState()
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
                            imageVector = Icons.Default.Favorite,
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
                            imageVector = Icons.Outlined.CloudSync,
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
            val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            val barBg = if (isDark) TrueDarkSurface else Color.White
            Surface(
                color = barBg,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(barBg)
                        .navigationBarsPadding()
                ) {
                    HorizontalDivider(color = if (isDark) TrueDarkOutline else Color(0xFFE2E8F0), thickness = 0.8.dp)
                    // Streamlined 3-tab navigation focused on strictly 2-person chat, daily growth, and ironclad security
                    NavigationBar(
                        containerColor = barBg,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets(0.dp)
                    ) {
                        NavigationBarItem(
                            selected = true,
                            onClick = { },
                            icon = { Icon(Icons.Filled.Favorite, contentDescription = "Us & Growth") },
                            label = { 
                                val streak = uiState.currentUser?.heartbeatStreak ?: 0
                                if (streak > 0) {
                                    Text("Love & Us 🔥$streak") 
                                } else {
                                    Text("Love & Us") 
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = if (isDark) MaterialTheme.colorScheme.primaryContainer else Color(0xFFEFF4FF),
                                unselectedIconColor = if (isDark) darkTone(Color(0xFF94A3B8)) else Color(0xFF64748B),
                                unselectedTextColor = if (isDark) darkTone(Color(0xFF94A3B8)) else Color(0xFF64748B)
                            )
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = onNavigateToChat,
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (uiState.unreadCount > 0) {
                                            Badge(containerColor = HeartRed) {
                                                Text(if (uiState.unreadCount > 99) "99+" else uiState.unreadCount.toString())
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Outlined.ChatBubble, contentDescription = "Chat")
                                }
                            },
                            label = { Text("Private Chat") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = if (isDark) MaterialTheme.colorScheme.primaryContainer else Color(0xFFEFF4FF),
                                unselectedIconColor = if (isDark) darkTone(Color(0xFF94A3B8)) else Color(0xFF64748B),
                                unselectedTextColor = if (isDark) darkTone(Color(0xFF94A3B8)) else Color(0xFF64748B)
                            )
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = onNavigateToProfile,
                            icon = { Icon(Icons.Outlined.Person, contentDescription = "Profile") },
                            label = { Text("Profile") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = if (isDark) MaterialTheme.colorScheme.primaryContainer else Color(0xFFEFF4FF),
                                unselectedIconColor = if (isDark) darkTone(Color(0xFF94A3B8)) else Color(0xFF64748B),
                                unselectedTextColor = if (isDark) darkTone(Color(0xFF94A3B8)) else Color(0xFF64748B)
                            )
                        )
                    }
                }
            }
        },
        // Heartbeat: at the bottom centre, big enough to reach any time
        floatingActionButton = {
            if (chatViewModel != null) {
                HeartbeatButton(
                    isPartnerTouching = chatState?.isPartnerHeartTouching == true,
                    onOpenHeartbeatTouch = { showHeartbeatTouch = true },
                    onSendThinkingOfYou = { chatViewModel.sendThinkingOfYou() }
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
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
            BothOfUsCard(
                me = LovePerson(
                    id = myId,
                    name = currentUser?.displayName?.ifBlank { null } ?: "Me",
                    photoUrl = currentUser?.photoUrl,
                    birthday = birthdays[myId],
                    isOnline = true,
                    lastSeen = 0L,
                    batteryLevel = currentUser?.batteryLevel,
                    isCharging = currentUser?.isCharging == true,
                    email = currentUser?.email
                ),
                partner = LovePerson(
                    id = partnerId,
                    name = partnerName,
                    photoUrl = partner?.photoUrl,
                    birthday = birthdays[partnerId],
                    isOnline = isOnline,
                    lastSeen = partner?.lastSeen ?: 0L,
                    status = partnerQuietStatus,
                    batteryLevel = partner?.batteryLevel,
                    isCharging = partner?.isCharging == true,
                    email = partner?.email?.ifBlank { null } ?: currentUser?.partnerEmail
                ),
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

            // 2. Secret Chat Quick Preview Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToChat() }
                    .testTag("open_chat_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                            imageVector = Icons.Outlined.ChatBubble,
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
                                .padding(horizontal = 7.dp, vertical = 3.dp)
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

            // 3. Lifetime Story Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToLifetimeJourney() }
                    .testTag("lifetime_journey_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
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
                                imageVector = Icons.Default.AllInclusive,
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
                        imageVector = Icons.Default.ChevronRight,
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
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Event,
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
                            imageVector = if (ourDates.isEmpty()) Icons.Default.Add else Icons.Default.ChevronRight,
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
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
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
                                imageVector = Icons.Filled.MarkEmailRead,
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
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 4. Instant Tactile Love Nudges
            LoveNudgesBar(
                onSendNudge = { name, emoji, msg ->
                    viewModel.sendLoveNudge(name, emoji, msg)
                }
            )

            // 5. Daily Us — Question of the Day
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

            // 6. Our month: last month as a little story (with the numbers, emoji, photos, streak)
            com.example.ui.recap.OurMonthCard(
                myId = myId,
                myName = currentUser?.displayName?.ifBlank { null } ?: "Me",
                partnerName = partnerName
            )

            // 7. Our Love Growth: real numbers (days together, Daily Us and Heartbeat Touch streaks)
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

    // Heartbeat Touch (moved here from the chat's top bar)
    if (showHeartbeatTouch && chatViewModel != null) {
        com.example.ui.chat.HeartbeatTouchDialog(
            partnerName = partnerName,
            isPartnerTouching = chatState?.isPartnerHeartTouching == true,
            isPartnerOnline = chatState?.isPartnerOnline == true,
            onTouchChanged = { chatViewModel.setHeartbeatTouch(it) },
            onSyncHeartbeatStreak = { chatViewModel.syncHeartbeatStreak() },
            onDismiss = { showHeartbeatTouch = false }
        )
    }
}



