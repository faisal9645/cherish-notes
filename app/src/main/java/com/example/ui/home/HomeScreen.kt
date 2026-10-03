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
    onNavigateToLifetimeJourney: () -> Unit = {},
    onNavigateToCloudBackup: () -> Unit = {},
    onQuickDisguise: () -> Unit = {}
) {
    BackHandler {
        onQuickDisguise()
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
                delay(1000)
            }
        }
    }

    var showDrawNoteDialog by remember { mutableStateOf(false) }
    var drawnNote by remember { mutableStateOf<LoveJarNote?>(null) }
    var showAddLoveNoteDialog by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "heartbeat")
    val heartScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heart_pulse"
    )

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
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break

                    val delta = change.positionChange()
                    accX += delta.x
                    accY += delta.y

                    if (!directionLocked && (kotlin.math.abs(accX) > 10f || kotlin.math.abs(accY) > 10f)) {
                        isHorizontal = kotlin.math.abs(accX) > kotlin.math.abs(accY)
                        directionLocked = true
                    }

                    if (directionLocked && isHorizontal) {
                        change.consume()
                    }
                }

                if (directionLocked && isHorizontal) {
                    if (accX < -80f) {
                        onNavigateToChat()
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
                                .scale(heartScale)
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
                    IconButton(
                        onClick = onQuickDisguise,
                        modifier = Modifier.testTag("home_quick_disguise_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.EditNote,
                            contentDescription = "Quick Disguise as Notes",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                            label = { Text("Love & Us") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = if (isDark) MaterialTheme.colorScheme.primaryContainer else Color(0xFFEFF4FF),
                                unselectedIconColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                                unselectedTextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
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
                                                Text("${uiState.unreadCount}")
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
                                unselectedIconColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                                unselectedTextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
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
                                unselectedIconColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                                unselectedTextColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                            )
                        )
                    }
                }
            }
        },
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
            // 1. Consolidated Hero Couple & Partner Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToChat() }
                    .testTag("partner_profile_card"),
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarView(
                            photoUrl = partner?.photoUrl,
                            name = partnerName,
                            size = 64.dp,
                            isOnline = isOnline,
                            showOnlineBadge = !partnerHasCheckAfter
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = partnerName,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Private",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(3.dp))

                            if (partnerHasCheckAfter) {
                                val (remaining, isExpired) = remember(partnerCheckTicker, partnerCheckAfterTarget) {
                                    CheckAfterHelper.calculateRemaining(partnerCheckAfterTarget)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.HourglassTop,
                                        contentDescription = null,
                                        tint = if (isExpired) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isExpired) "✨ You can check now" else "Check after ${CheckAfterHelper.formatTargetTime(partnerCheckAfterTarget)} ($remaining)",
                                        fontSize = 12.sp,
                                        color = if (isExpired) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (isOnline) OnlineGreen else Color.Gray)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = viewModel.formatLastSeen(partner?.lastSeen ?: 0L, isOnline),
                                        fontSize = 12.sp,
                                        color = if (isOnline) OnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (isOnline) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = statusText,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Bottom Row: Milestone Days Together + Chat Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "❤️", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${uiState.daysTogether} Days Together",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.clickable { onNavigateToChat() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "Chat Now",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

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
                                text = uiState.lastMessage!!.text,
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
                                text = "${uiState.unreadCount}",
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

            // 6. Our Love Growth Card
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
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Our Love Growth",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "Level 3: Soulmates 💖",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "14 Days Unbroken Connection Streak. Next milestone at 20 days!",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { 0.7f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "💡", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Today's Habit: Hug for at least 20 continuous seconds to deepen connection.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
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
}



