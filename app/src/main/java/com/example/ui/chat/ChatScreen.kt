package com.example.ui.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.Message
import com.example.data.model.MessageType
import com.example.ui.components.AvatarView
import androidx.compose.foundation.BorderStroke
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToGallery: () -> Unit,
    onQuickDisguise: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    var composerText by remember { mutableStateOf("") }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var editDialogText by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf<Message?>(null) }
    var showMutualDeleteRequestDialog by remember { mutableStateOf(false) }
    var showChatMenu by remember { mutableStateOf(false) }

    val currentPlayingId by viewModel.voicePlayerHelper.currentlyPlayingId.collectAsState()
    val audioProgress by viewModel.voicePlayerHelper.playbackProgress.collectAsState()
    val isRecordingVoice by viewModel.voiceRecorderHelper.isRecording.collectAsState()
    val recordingDurationSec by viewModel.voiceRecorderHelper.recordingDurationSec.collectAsState()
    val recordingAmplitudes by viewModel.voiceRecorderHelper.amplitudes.collectAsState()

    // Activity result launchers for media selection
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.sendMediaFile(it, MessageType.IMAGE) }
    }

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.sendMediaFile(it, MessageType.DOCUMENT) }
    }

    // Mic permission launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startVoiceRecording()
        }
    }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // Clear disguised notifications upon entering chat
    LaunchedEffect(Unit) {
        com.example.notifications.NotificationHelper.clearNotifications(context)
    }

    // Partner info
    val partner = uiState.partnerUser
    val partnerName = partner?.displayName?.ifBlank { "My Partner" } ?: "My Partner"
    val isPartnerOnline = partner?.isOnline ?: false
    val currentUserId = viewModel.uiState.value.currentUser?.id ?: "user_me"

    // Filter messages for search query
    val displayedMessages = remember(uiState.messages, uiState.searchQuery) {
        if (uiState.searchQuery.isBlank()) {
            uiState.messages
        } else {
            uiState.messages.filter { it.text.contains(uiState.searchQuery, ignoreCase = true) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.isSearching) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search our chat...") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("chat_search_input")
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onNavigateToGallery() }
                        ) {
                            AvatarView(
                                photoUrl = partner?.photoUrl,
                                name = partnerName,
                                size = 40.dp,
                                isOnline = isPartnerOnline,
                                showOnlineBadge = true
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = partnerName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (uiState.isPartnerTyping) {
                                        "typing sweet words..."
                                    } else if (isPartnerOnline) {
                                        "Online"
                                    } else {
                                        "Offline"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (uiState.isPartnerTyping) RoseGoldPrimary else if (isPartnerOnline) OnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState.isSearching) {
                                viewModel.setSearching(false)
                            } else {
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (uiState.isSearching) {
                        IconButton(onClick = { viewModel.setSearching(false) }) {
                            Icon(Icons.Default.Close, contentDescription = "Close search")
                        }
                    } else {
                        IconButton(
                            onClick = { viewModel.toggleStealthCurtain() },
                            modifier = Modifier.testTag("chat_stealth_curtain_button")
                        ) {
                            Icon(
                                imageVector = if (uiState.isStealthCurtainActive) Icons.Default.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = "Emergency Stealth Curtain (Hide Previous Chat)",
                                tint = if (uiState.isStealthCurtainActive) RoseGoldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onQuickDisguise,
                            modifier = Modifier.testTag("chat_quick_disguise_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.EditNote,
                                contentDescription = "Quick Disguise as Notes",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { viewModel.setSearching(true) },
                            modifier = Modifier.testTag("chat_search_button")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search messages")
                        }
                        IconButton(
                            onClick = onNavigateToGallery,
                            modifier = Modifier.testTag("chat_gallery_button")
                        ) {
                            Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Couple Media Gallery")
                        }
                        Box {
                            IconButton(
                                onClick = { showChatMenu = true },
                                modifier = Modifier.testTag("chat_more_menu_button")
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showChatMenu,
                                onDismissRequest = { showChatMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (uiState.isStealthCurtainActive) "Unhide Previous Chat (Reveal)" else "Hide Previous Chat (Stealth)") },
                                    onClick = {
                                        showChatMenu = false
                                        viewModel.toggleStealthCurtain()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (uiState.isStealthCurtainActive) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            null,
                                            tint = RoseGoldPrimary
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear Chat (Both Must Accept)", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showChatMenu = false
                                        showMutualDeleteRequestDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.error)
                                    }
                                )
                            }
                        }
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
                        .imePadding()
                ) {
                    HorizontalDivider(color = Color(0xFFF0F0F2), thickness = 1.dp)
                    // Partner typing animated banner
                AnimatedVisibility(visible = uiState.isPartnerTyping) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BouncingDots()
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$partnerName is typing...",
                            fontSize = 12.sp,
                            color = RoseGoldPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Media upload progress bar
                if (uiState.isUploadingMedia) {
                    LinearProgressIndicator(
                        progress = { uiState.uploadProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = RoseGoldPrimary
                    )
                }

                MessageComposer(
                    text = composerText,
                    onTextChanged = {
                        composerText = it
                        viewModel.onTypingChanged(it.isNotBlank())
                    },
                    onSendText = {
                        viewModel.sendTextMessage(composerText)
                        composerText = ""
                        viewModel.onTypingChanged(false)
                    },
                    replyingTo = uiState.replyingToMessage,
                    onDismissReply = { viewModel.setReplyingTo(null) },
                    isRecordingVoice = isRecordingVoice,
                    recordingDurationSec = recordingDurationSec,
                    recordingAmplitudes = recordingAmplitudes,
                    onStartVoiceRecord = {
                        val hasMic = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasMic) {
                            viewModel.startVoiceRecording()
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onStopAndSendVoiceRecord = { viewModel.stopAndSendVoiceRecording() },
                    onCancelVoiceRecord = { viewModel.cancelVoiceRecording() },
                    onPickImage = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    onPickDocument = {
                        docPickerLauncher.launch("*/*")
                    },
                    placeholder = if (uiState.isStealthCurtainActive) "Add a note..." else "Message your love..."
                )
            }
        }
    },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            viewModel.toggleStealthCurtain()
                        }
                    )
                }
        ) {
            if (uiState.isStealthCurtainActive) {
                // Emergency Privacy Shield: Harmless daily notes / tasks view hiding all previous chat
                StealthDisguiseNotesView(
                    onRestore = { viewModel.toggleStealthCurtain() }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Mutual Consent Chat Deletion Active Request Banner
                    if (uiState.deletionRequest != null) {
                        item {
                            MutualConsentDeletionBanner(
                                request = uiState.deletionRequest!!,
                                isFromMe = (uiState.deletionRequest!!.requestedByUserId == currentUserId),
                                partnerName = partnerName,
                                onAccept = { viewModel.acceptMutualChatDeletion() },
                                onDecline = { viewModel.declineMutualChatDeletion() },
                                onCancel = { viewModel.cancelMutualChatDeletion() }
                            )
                        }
                    }

                    item {
                        Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = SoftPinkSurfaceVariant,
                            border = BorderStroke(1.dp, SoftBorderOutline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = RoseGoldPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Strictly 2-Person Private Channel",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = DarkOnBackground
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Messages, voice notes, and photos are strictly between you and $partnerName. No third parties can ever join or view this chat.",
                                    fontSize = 11.sp,
                                    color = DarkOnSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }

                var lastDateStr = ""

                items(displayedMessages, key = { it.id }) { message ->
                    val isFromMe = message.senderId == currentUserId
                    val msgDateStr = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(message.timestamp))

                    // Date Separator Pill
                    if (msgDateStr != lastDateStr) {
                        lastDateStr = msgDateStr
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = 1.dp
                            ) {
                                Text(
                                    text = formatDateSeparator(message.timestamp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Mark as read when seen
                    LaunchedEffect(message.id) {
                        if (!isFromMe && message.getTypedStatus() != com.example.data.model.MessageStatus.READ) {
                            viewModel.markMessageAsRead(message.id)
                        }
                    }

                    MessageBubble(
                        message = message,
                        isFromMe = isFromMe,
                        isPlayingAudio = currentPlayingId == message.id,
                        audioProgress = audioProgress,
                        onPlayAudio = {
                            message.mediaUrl?.let { url ->
                                viewModel.playAudio(message.id, url)
                            }
                        },
                        onImageClick = { url ->
                            viewModel.openFullScreenMedia(url, MessageType.IMAGE)
                        },
                        onLongClick = {
                            viewModel.setSelectedMessageForActions(message)
                        },
                        onReactionClick = { emoji ->
                            viewModel.toggleReaction(message.id, emoji)
                        }
                    )
                }
            }

            // Floating scroll to bottom button
            val showScrollButton by remember {
                derivedStateOf {
                    listState.firstVisibleItemIndex < (displayedMessages.size - 4).coerceAtLeast(0)
                }
            }

            AnimatedVisibility(
                visible = showScrollButton,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        scope.launch {
                            if (displayedMessages.isNotEmpty()) {
                                listState.animateScrollToItem(displayedMessages.size - 1)
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Scroll to bottom")
                }
            }
            } // Close else branch of if (uiState.isStealthCurtainActive)

            // Stealth mode floating status feedback badge
            AnimatedVisibility(
                visible = uiState.stealthToastMessage != null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF222228).copy(alpha = 0.95f),
                    shadowElevation = 8.dp
                ) {
                    Text(
                        text = uiState.stealthToastMessage ?: "",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    // Message Actions Bottom Sheet
    uiState.selectedMessageForActions?.let { msg ->
        MessageActionsSheet(
            message = msg,
            isFromMe = msg.senderId == currentUserId,
            onDismiss = { viewModel.setSelectedMessageForActions(null) },
            onReaction = { emoji -> viewModel.toggleReaction(msg.id, emoji) },
            onReply = { viewModel.setReplyingTo(msg) },
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Cherish Message", msg.text)
                clipboard.setPrimaryClip(clip)
            },
            onStar = { viewModel.toggleStar(msg.id) },
            onEdit = {
                editingMessage = msg
                editDialogText = msg.text
            },
            onDelete = {
                showDeleteConfirmDialog = msg
            }
        )
    }

    // Edit Message Dialog
    editingMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("Edit Message") },
            text = {
                OutlinedTextField(
                    value = editDialogText,
                    onValueChange = { editDialogText = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.editMessage(msg.id, editDialogText)
                        editingMessage = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    showDeleteConfirmDialog?.let { msg ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Delete message?") },
            text = { Text("This message will be removed from your private chat.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMessage(msg.id)
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Full-screen media viewer
    uiState.fullScreenMediaUrl?.let { url ->
        FullScreenMediaViewer(
            mediaUrl = url,
            onDismiss = { viewModel.closeFullScreenMedia() }
        )
    }

    // Mutual Consent Chat Clear Request Dialog
    if (showMutualDeleteRequestDialog) {
        AlertDialog(
            onDismissRequest = { showMutualDeleteRequestDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = RoseGoldPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mutual Consent Chat Clear", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = "Under our Mutual Protection rule, clearing chat history requires BOTH partners to accept so that precious memories are never erased accidentally or unilaterally.\n\nWould you like to send a deletion request to $partnerName?",
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.requestMutualChatDeletion("ALL_MESSAGES")
                        showMutualDeleteRequestDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Send Deletion Request")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMutualDeleteRequestDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun MutualConsentDeletionBanner(
    request: com.example.data.model.ChatDeletionRequest,
    isFromMe: Boolean,
    partnerName: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (isFromMe) Color(0xFFFFF8E1) else Color(0xFFFFEBEE)),
        border = BorderStroke(1.dp, if (isFromMe) Color(0xFFFFE082) else Color(0xFFFFCDD2))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isFromMe) Icons.Default.HourglassTop else Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = if (isFromMe) Color(0xFFF57F17) else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Mutual Consent Chat Deletion",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkOnBackground
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (isFromMe) {
                Text(
                    text = "You requested to clear this chat. Waiting for $partnerName to accept. Both of you must agree before messages can be deleted.",
                    fontSize = 12.sp,
                    color = DarkOnSurfaceVariant,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Cancel Request", fontSize = 12.sp)
                }
            } else {
                Text(
                    text = "$partnerName requested to clear the chat history. Under mutual protection, deletion will only happen if you accept.",
                    fontSize = 12.sp,
                    color = DarkOnBackground,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onAccept,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Accept & Clear Chat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onDecline,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Decline & Keep", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun StealthDisguiseNotesView(
    onRestore: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp)
            .clickable { onRestore() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Personal Checklist & Notes",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = DarkOnBackground
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF0F0F2)
            ) {
                Text(
                    text = "Saved",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        val dummyTasks = listOf(
            "Weekly Groceries: Sourdough bread, Oat milk, Avocados, Olive oil" to true,
            "Office: Review quarterly budget presentation by 3:00 PM" to false,
            "Home Chores: Clean kitchen, change air filter" to true,
            "Reminder: Pick up pharmacy prescription on the way home" to false,
            "Car: Check tire pressure and washer fluid next Saturday" to false
        )

        dummyTasks.forEach { (task, checked) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = null,
                    tint = if (checked) Color(0xFF4CAF50) else Color.LightGray,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = task,
                    fontSize = 13.sp,
                    color = if (checked) Color.Gray else DarkOnBackground
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(color = Color(0xFFF0F0F2))
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Meeting Notes Draft:",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = DarkOnBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Team sprint planning completed. Next milestone scheduled for the 15th. Follow up with the project manager regarding the final deliverables.",
            fontSize = 12.sp,
            color = DarkOnSurfaceVariant,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "• Double-tap anywhere to restore previous chat •",
                fontSize = 11.sp,
                color = Color.LightGray,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun BouncingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "bouncing_dots")
    val dot1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "d1"
    )
    val dot2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "d2"
    )
    val dot3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "d3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.offset(y = dot1.dp).size(6.dp).background(RoseGoldPrimary, CircleShape))
        Box(modifier = Modifier.offset(y = dot2.dp).size(6.dp).background(RoseGoldPrimary, CircleShape))
        Box(modifier = Modifier.offset(y = dot3.dp).size(6.dp).background(RoseGoldPrimary, CircleShape))
    }
}

fun formatDateSeparator(timestamp: Long): String {
    val date = Date(timestamp)
    val now = Date()
    val sdfDateOnly = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    val isToday = sdfDateOnly.format(date) == sdfDateOnly.format(now)
    val yesterday = Date(now.time - 24 * 3600 * 1000)
    val isYesterday = sdfDateOnly.format(date) == sdfDateOnly.format(yesterday)

    return when {
        isToday -> "Today"
        isYesterday -> "Yesterday"
        else -> SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(date)
    }
}
