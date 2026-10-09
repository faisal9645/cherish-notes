package com.example.ui.chat

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.CherishApplication
import com.example.R
import com.example.data.model.Message
import com.example.data.model.MessageType
import com.example.ui.home.HeartbeatBlue
import com.example.ui.home.HeartbeatPink
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val OurWordsFont = FontFamily(Font(R.font.great_vibes_regular))

/**
 * "Our words": the starred messages as a little book, a cover and then one message per page, oldest
 * first. Like the gallery's Starred tab, messages from before today only show after Recover All.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OurWordsScreen(
    chatViewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onShowInChat: (messageId: String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as? CherishApplication
    val chatState by chatViewModel.uiState.collectAsState()
    val galleryItems by chatViewModel.galleryMediaMessages.collectAsState()
    val isAllRecovered by (app?.securityPreferences?.isAllGalleryRecovered?.collectAsState() ?: remember { mutableStateOf(false) })
    val showsOlder = isAllRecovered || chatState.showPreviousChats
    val currentUserId = chatState.currentUser?.id ?: "user_me"
    val partnerName = chatState.partnerUser?.displayName?.takeIf { it.isNotBlank() } ?: "My Love"
    val myName = chatState.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Me"

    // The gallery's load also fetches every starred message
    LaunchedEffect(Unit) { chatViewModel.loadAllGalleryMedia() }

    val words = remember(chatState.messages, galleryItems, showsOlder) {
        (chatState.messages + galleryItems)
            .asSequence()
            .distinctBy { it.id }
            .filter { it.isStarred && !it.isDeleted && it.getTypedType() == MessageType.TEXT && it.text.isNotBlank() }
            .filter { showsOlder || isToday(it.timestamp) }
            .sortedBy { it.timestamp }
            .toList()
    }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val paper = if (isDark) NightPaper else DayPaper

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Our Words", fontFamily = OurWordsFont, fontSize = 30.sp, color = HeartbeatPink)
                        if (words.isNotEmpty()) {
                            Text(
                                if (words.size == 1) "1 favourite message" else "${words.size} favourite messages",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            HeartbeatPink.copy(alpha = if (isDark) 0.06f else 0.05f),
                            Color.Transparent,
                            HeartbeatBlue.copy(alpha = if (isDark) 0.08f else 0.05f)
                        )
                    )
                )
        ) {
            if (words.isEmpty()) {
                OurWordsEmpty(showsOlder = showsOlder, paper = paper)
            } else {
                // Page 0 is the cover
                val pagerState = rememberPagerState(pageCount = { words.size + 1 })
                // The book opens gently
                val opening = remember { Animatable(0f) }
                LaunchedEffect(Unit) { opening.animateTo(1f, tween(420)) }
                Column(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(horizontal = 28.dp),
                        pageSpacing = 12.dp,
                        key = { page -> if (page == 0) "cover" else words[page - 1].id },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = opening.value
                                translationY = (1f - opening.value) * 24.dp.toPx()
                            }
                            .testTag("our_words_book")
                    ) { page ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 16.dp)
                                .pageTurn(pagerState, page)
                        ) {
                            if (page == 0) {
                                OurWordsCover(words = words, paper = paper)
                            } else {
                                val message = words[page - 1]
                                val isMine = message.senderId == currentUserId
                                OurWordsPage(
                                    message = message,
                                    signedBy = if (isMine) myName else message.senderName.ifBlank { partnerName },
                                    paper = paper
                                )
                            }
                        }
                    }

                    // Page count and what to do with the page
                    val page = pagerState.currentPage
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, bottom = 18.dp)
                    ) {
                        Text(
                            text = if (page == 0) "Swipe to open" else "$page / ${words.size}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        val message = words.getOrNull(page - 1)
                        if (message != null) {
                            TextButton(
                                onClick = { onShowInChat(message.id) },
                                modifier = Modifier.testTag("our_words_show_in_chat")
                            ) {
                                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Show in chat")
                            }
                            TextButton(
                                onClick = {
                                    chatViewModel.toggleStar(message.id)
                                    Toast.makeText(context, "Taken out of Our Words", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("our_words_unstar")
                            ) {
                                Icon(Icons.Outlined.StarOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Unstar")
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A page turning like a book's: it swings on its spine edge, with a little depth. */
private fun Modifier.pageTurn(pagerState: PagerState, page: Int): Modifier = graphicsLayer {
    val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
    val amount = offset.coerceIn(-1f, 1f)
    rotationY = amount * 28f
    transformOrigin = TransformOrigin(if (amount > 0f) 1f else 0f, 0.5f)
    cameraDistance = 14f * density
    val away = abs(amount)
    scaleX = 1f - away * 0.06f
    scaleY = 1f - away * 0.06f
    alpha = 1f - away * 0.35f
}

private class PaperColors(
    val top: Color,
    val bottom: Color,
    val line: Color,
    val margin: Color,
    val ink: Color,
    val faint: Color,
    val edge: Color
)

private val DayPaper = PaperColors(
    top = Color(0xFFFFFCF7),
    bottom = Color(0xFFFFF3EA),
    line = Color(0xFFB9C7E6).copy(alpha = 0.35f),
    margin = HeartbeatPink.copy(alpha = 0.28f),
    ink = Color(0xFF2F2A3A),
    faint = Color(0xFF7A7287),
    edge = Color(0xFFEFE2D6)
)

private val NightPaper = PaperColors(
    top = Color(0xFF141C45),
    bottom = Color(0xFF0D1333),
    line = Color.White.copy(alpha = 0.06f),
    margin = HeartbeatPink.copy(alpha = 0.32f),
    ink = Color(0xFFF1EDF9),
    faint = Color(0xFFA9B0D0),
    edge = Color(0xFF0E1638)
)

/** The paper itself: soft gradient, ruled lines and a pink margin, like a notebook page. */
@Composable
private fun PaperSheet(paper: PaperColors, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, paper.edge),
        modifier = modifier
            .fillMaxSize()
            .shadow(10.dp, shape, ambientColor = HeartbeatPink.copy(alpha = 0.25f), spotColor = HeartbeatBlue.copy(alpha = 0.25f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(paper.top, paper.bottom)))
                .drawBehind {
                    val gap = 32.dp.toPx()
                    var y = 84.dp.toPx()
                    while (y < size.height - 24.dp.toPx()) {
                        drawLine(paper.line, Offset(18.dp.toPx(), y), Offset(size.width - 18.dp.toPx(), y), strokeWidth = 1.dp.toPx())
                        y += gap
                    }
                    val marginX = 34.dp.toPx()
                    drawLine(paper.margin, Offset(marginX, 0f), Offset(marginX, size.height), strokeWidth = 1.5.dp.toPx())
                },
            content = content
        )
    }
}

@Composable
private fun OurWordsCover(words: List<Message>, paper: PaperColors) {
    val since = remember(words) { words.firstOrNull()?.let { CoverDate.format(Date(it.timestamp)) } }
    PaperSheet(paper = paper) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 44.dp, end = 24.dp, top = 24.dp, bottom = 24.dp)
        ) {
            Text("♥", fontSize = 40.sp, color = HeartbeatPink)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Our Words", fontFamily = OurWordsFont, fontSize = 52.sp, color = paper.ink, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                if (words.size == 1) "one little thing we said" else "${words.size} little things we said",
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontSize = 17.sp,
                color = paper.faint,
                textAlign = TextAlign.Center
            )
            if (since != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text("since $since", fontSize = 13.sp, color = paper.faint, textAlign = TextAlign.Center)
            }
            Spacer(modifier = Modifier.height(28.dp))
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 3.dp)
                    .background(Brush.horizontalGradient(listOf(HeartbeatPink, HeartbeatBlue)), CircleShape)
            )
        }
    }
}

@Composable
private fun OurWordsPage(message: Message, signedBy: String, paper: PaperColors) {
    val text = message.text.trim()
    // Short words big, long ones smaller (and they scroll on the page)
    val size = when {
        text.length <= 50 -> 26.sp
        text.length <= 120 -> 22.sp
        text.length <= 260 -> 19.sp
        else -> 16.sp
    }
    val reactions = remember(message.reactions) { message.reactions.values.distinct().take(4).joinToString(" ") }
    PaperSheet(paper = paper) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 48.dp, end = 26.dp, top = 22.dp, bottom = 22.dp)
        ) {
            Text("“", fontFamily = FontFamily.Serif, fontSize = 64.sp, color = HeartbeatPink.copy(alpha = 0.8f), lineHeight = 64.sp)
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = text,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = size,
                    lineHeight = size * 1.35f,
                    color = paper.ink
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                "— $signedBy",
                fontFamily = OurWordsFont,
                fontSize = 30.sp,
                color = HeartbeatPink,
                modifier = Modifier.align(Alignment.End)
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (reactions.isNotBlank()) {
                    Text(reactions, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    PageDate.format(Date(message.timestamp)),
                    fontSize = 12.sp,
                    color = paper.faint
                )
            }
        }
    }
}

@Composable
private fun OurWordsEmpty(showsOlder: Boolean, paper: PaperColors) {
    Box(modifier = Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        PaperSheet(paper = paper, modifier = Modifier.fillMaxHeight(0.7f)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 44.dp, end = 22.dp)
                    .testTag("our_words_empty")
            ) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = HeartbeatPink, modifier = Modifier.size(44.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("No words saved yet", fontFamily = OurWordsFont, fontSize = 34.sp, color = paper.ink, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Long-press a message you love and star it. It becomes a page in this book.",
                    fontSize = 14.sp,
                    color = paper.faint,
                    textAlign = TextAlign.Center
                )
                if (!showsOlder) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Older starred messages show here after Recover All in settings.",
                        fontSize = 12.sp,
                        color = paper.faint,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private val CoverDate = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
private val PageDate = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
