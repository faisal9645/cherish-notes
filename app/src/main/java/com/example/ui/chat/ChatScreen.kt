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
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.RoseGoldPrimary
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
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .background(Color.White)
                    .navigationBarsPadding()
            ) {
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
                    }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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
