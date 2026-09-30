package com.example.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
    val isOnline = partner?.isEffectivelyOnline() ?: false
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
            detectHorizontalDragGestures(
                onDragStart = { _ -> dragAccumulator = 0f },
                onDragEnd = {
                    if (dragAccumulator < -80f) {
                        onNavigateToChat()
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
                            contentDescription = "Google Drive Backup & Restore",
                            tint = RoseGoldPrimary
                        )
                    }
                    IconButton(
                        onClick = onNavigateToLifetimeJourney,
                        modifier = Modifier.testTag("home_lifetime_journey_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AllInclusive,
                            contentDescription = "Our Lifetime Story",
                            tint = RoseGoldPrimary
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
                    IconButton(
                        onClick = onNavigateToProfile,
                        modifier = Modifier.testTag("home_profile_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = "Profile",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                ),
                windowInsets = WindowInsets.statusBars
            )
        },
        bottomBar = {
            Surface(
                color = Color.White,
                tonalElevation = 2.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    HorizontalDivider(color = Color(0xFFF0F0F2), thickness = 1.dp)
                    // Streamlined 3-tab navigation focused on strictly 2-person chat, daily growth, and ironclad security
                    NavigationBar(
                        containerColor = Color.Transparent,
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
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
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
                            label = { Text("Private Chat") }
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = onNavigateToProfile,
                            icon = { Icon(Icons.Outlined.Person, contentDescription = "Profile") },
                            label = { Text("Profile") }
                        )
                    }
                }
            }
        },
        containerColor = Color.White
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Strictly 2-Person Verified Badge
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SoftPinkSurfaceVariant,
                border = BorderStroke(1.dp, SoftBorderOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = RoseGoldPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Strictly 2-Person Private Channel",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkOnBackground
                        )
                        Text(
                            text = "$myEmail ❤️ $partnerEmail",
                            fontSize = 11.sp,
                            color = DarkOnSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Partner Showcase Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToChat() }
                    .testTag("partner_profile_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                border = BorderStroke(1.dp, Color(0xFFF0F0F2)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarView(
                        photoUrl = partner?.photoUrl,
                        name = partnerName,
                        size = 72.dp,
                        isOnline = isOnline,
                        showOnlineBadge = !partnerHasCheckAfter
                    )

                    Spacer(modifier = Modifier.width(18.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = partnerName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        if (partnerHasCheckAfter) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.HourglassTop,
                                    contentDescription = null,
                                    tint = RoseGoldPrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                val (remaining, isExpired) = remember(partnerCheckTicker, partnerCheckAfterTarget) {
                                    CheckAfterHelper.calculateRemaining(partnerCheckAfterTarget)
                                }
                                Text(
                                    text = if (isExpired) "✨ You can check now" else "Check after ${CheckAfterHelper.formatTargetTime(partnerCheckAfterTarget)} ($remaining)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isExpired) Color(0xFF2E7D32) else RoseGoldPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            // Online / Last seen indicator
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = viewModel.formatLastSeen(partner?.lastSeen ?: 0L, isOnline),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isOnline) OnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isOnline) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }


                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Relationship Milestone Banner (Days Together)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .appGradientShadow(RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(appHorizontalGradient())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "❤️",
                            fontSize = 24.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Days in Deep Love",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${uiState.daysTogether} Days Together",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.25f),
                        modifier = Modifier.clickable { onNavigateToChat() }
                    ) {
                        Text(
                            text = "Say I Love You →",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Lifetime Love & Age Journey Card (Tracking our love across every age & year of our lives)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToLifetimeJourney() }
                    .testTag("lifetime_journey_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFF0F0F2)),
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
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(SoftPinkSurfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AllInclusive,
                                    contentDescription = null,
                                    tint = RoseGoldPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Our Lifetime Love & Ages",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkOnBackground
                                )
                                Text(
                                    text = "Year by Year • Where we went & enjoyed",
                                    fontSize = 11.sp,
                                    color = DarkOnSurfaceVariant
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Open Journey",
                            tint = RoseGoldPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SoftPinkSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Ages 34 & 31 • Year 3 of Our Bond",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkOnBackground
                            )
                            Text(
                                text = "Open Diary →",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = RoseGoldPrimary
                            )
                        }
                    }
                }
            }

            // 1. Instant Tactile Love Nudges
            LoveNudgesBar(
                onSendNudge = { name, emoji, msg ->
                    viewModel.sendLoveNudge(name, emoji, msg)
                }
            )

            // 2. Daily Us — Question of the Day (Engaging Double-Blind Q&A to Grow Love Daily)
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

            // 3. Strictly 2-Person Private Conversation Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToChat() }
                    .testTag("open_chat_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                border = BorderStroke(1.dp, Color(0xFFF0F0F2)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ChatBubble,
                                contentDescription = null,
                                tint = RoseGoldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Strictly 2-Person Chat",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (uiState.lastMessage != null) {
                            Text(
                                text = viewModel.formatMessageTime(uiState.lastMessage!!.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (uiState.isPartnerTyping) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$partnerName is typing...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = RoseGoldPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else if (uiState.lastMessage != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = uiState.lastMessage!!.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            if (uiState.unreadCount > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .background(HeartRed, CircleShape)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
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
                    } else {
                        Text(
                            text = "No messages yet. Say something sweet to your love 💕",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .appGradientShadow(RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .background(appHorizontalGradient())
                            .clickable { onNavigateToChat() }
                            .testTag("home_enter_chat_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Open 2-Person Messenger",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // 4. Daily Love Growth & Daily Tips Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFF0F0F2)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
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
                                tint = RoseGoldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Our Love Growth",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = DarkOnBackground
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFFF0F5)
                        ) {
                            Text(
                                text = "Level 3: Soulmates 💖",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = RoseGoldPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "14 Days Unbroken Connection Streak. Next milestone at 20 days!",
                        fontSize = 12.sp,
                        color = DarkOnSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { 0.7f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = RoseGoldPrimary,
                        trackColor = SoftPinkSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SoftPinkSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "💡", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Today's Love Habit: Hug for at least 20 continuous seconds. It releases oxytocin and deepens bonding.",
                                fontSize = 12.sp,
                                color = DarkOnBackground,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // 5. Security & Shield Status
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToProfile() },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFF0F0F2)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Couple Privacy & Security",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = DarkOnBackground
                            )
                        }
                        Text(
                            text = "Manage →",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = RoseGoldPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "• Stealth Notes App Disguise (Passcode: 'love')\n• Screenshot Blocking (FLAG_SECURE Active)\n• Biometric / PIN Lock on Re-entry\n• Masked Lockscreen Notifications",
                        fontSize = 12.sp,
                        color = DarkOnSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
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
