package com.example.ui.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.components.pressScaleTarget
import com.example.ui.components.springClickable
import kotlinx.coroutines.launch

/**
 * Tabbed media board panel providing dedicated Emoji, GIF, and Stickers tabs with a floating
 * bottom switcher bar (similar to WhatsApp and Telegram keyboards).
 */
@Composable
fun MediaBoardPanel(
    activeTab: Int,
    onTabSelected: (Int) -> Unit,
    isDark: Boolean,
    swipeDownModifier: Modifier,
    emojiTabs: List<String>,
    emojiCategories: List<List<String>>,
    emojiPagerState: PagerState,
    onEmojiPicked: (String) -> Unit,
    onSendGif: ((GiphyGif) -> Unit)?,
    onOpenGifSearchSheet: () -> Unit,
    onStickerPicked: (String) -> Unit,
    onStickerLongClicked: (String) -> Unit,
    onBackspace: () -> Unit,
    onSwitchToKeyboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Drag handle pill at top of emoji board (swipe down to dismiss)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(swipeDownModifier)
                    .padding(top = 6.dp, bottom = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1))
                )
            }

            // Tab Content Area with bottom clearance for the floating switcher bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 54.dp)
            ) {
                when (activeTab) {
                    0 -> EmojiBoardContent(
                        isDark = isDark,
                        emojiTabs = emojiTabs,
                        emojiCategories = emojiCategories,
                        emojiPagerState = emojiPagerState,
                        swipeDownModifier = swipeDownModifier,
                        onEmojiPicked = onEmojiPicked
                    )
                    1 -> GifBoardContent(
                        isDark = isDark,
                        onPickGif = { gif -> onSendGif?.invoke(gif) },
                        onOpenSearchSheet = onOpenGifSearchSheet
                    )
                    2 -> StickersBoardContent(
                        isDark = isDark,
                        onStickerPicked = onStickerPicked,
                        onStickerLongClicked = onStickerLongClicked
                    )
                }
            }
        }

        // Floating Bottom Switcher Bar (Emoji, GIF, Stickers, Backspace, Keyboard)
        MediaBoardBottomBar(
            activeTab = activeTab,
            onTabSelected = onTabSelected,
            onBackspace = onBackspace,
            onSwitchToKeyboard = onSwitchToKeyboard,
            isDark = isDark,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
        )
    }
}

/**
 * Tab 1: Emoji board with top category tabs and horizontal swiping pager.
 */
@Composable
private fun EmojiBoardContent(
    isDark: Boolean,
    emojiTabs: List<String>,
    emojiCategories: List<List<String>>,
    emojiPagerState: PagerState,
    swipeDownModifier: Modifier,
    onEmojiPicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    Column(modifier = modifier.fillMaxSize()) {
        // Category Tab Row (purely emoji categories)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(swipeDownModifier)
                .background(if (isDark) Color(0x66080B14) else Color.White.copy(alpha = 0.90f))
                .border(BorderStroke(0.6.dp, if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFE2E8F0)))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            emojiTabs.forEachIndexed { index, tabEmoji ->
                val isSelected = emojiPagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else Color.Transparent
                        )
                        .clickable {
                            scope.launch {
                                emojiPagerState.animateScrollToPage(index)
                            }
                        }
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tabEmoji,
                        fontSize = if (isSelected) 18.sp else 15.sp
                    )
                }
            }
        }

        // HorizontalPager of Emojis
        HorizontalPager(
            state = emojiPagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { pageIndex ->
            val currentEmojis = emojiCategories.getOrElse(pageIndex) { emptyList() }
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(currentEmojis, key = { "${pageIndex}_$it" }) { emoji ->
                    val displayEmoji = remember(emoji) { withFairSkinTone(emoji) }
                    Box(
                        modifier = Modifier
                            .pressScaleTarget()
                            .clip(RoundedCornerShape(8.dp))
                            .springClickable {
                                onEmojiPicked(displayEmoji)
                            }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayEmoji,
                            fontSize = 24.sp,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.fitToCell()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tab 2: Dedicated GIF section with quick couple suggestion chips, trending feed, and search sheet launcher.
 */
@Composable
private fun GifBoardContent(
    isDark: Boolean,
    onPickGif: (GiphyGif) -> Unit,
    onOpenSearchSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSuggestion by rememberSaveable { mutableStateOf<String?>(null) }
    val gifs = remember { mutableStateListOf<GiphyGif>() }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val gridState = rememberLazyStaggeredGridState()

    val suggestions = remember {
        listOf("Trending", "❤️ Love", "😘 Kiss", "🤗 Hug", "🥺 Miss you", "🌙 Night", "☀️ Morning", "😂 Funny", "🥰 Cute", "🔥 Flirty")
    }

    LaunchedEffect(selectedSuggestion) {
        if (!Giphy.isAvailable) return@LaunchedEffect
        loading = true
        failed = false
        val query = if (selectedSuggestion == null || selectedSuggestion == "Trending") {
            null
        } else {
            selectedSuggestion?.substringAfter(' ')?.trim()
        }
        val result = if (query == null) Giphy.trending(0) else Giphy.search(query, 0)
        gifs.clear()
        loading = false
        if (result != null) {
            gifs.addAll(result.distinctBy { it.id })
            if (gifs.isNotEmpty()) {
                gridState.scrollToItem(0)
            }
        } else {
            failed = true
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        // Search & category filter row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Search Button (launches bottom sheet with full keyboard search)
            Surface(
                shape = CircleShape,
                color = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                border = BorderStroke(0.8.dp, if (isDark) Color(0x22FFFFFF) else Color(0x1F000000)),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onOpenSearchSheet() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search GIFs",
                        tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Search",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                    )
                }
            }

            // Quick suggestion chips
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                suggestions.forEach { suggestion ->
                    val isSelected = (selectedSuggestion == null && suggestion == "Trending") || selectedSuggestion == suggestion
                    val bg = if (isSelected) MaterialTheme.colorScheme.primary else if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                    val textCol = if (isSelected) Color.White else if (isDark) Color(0xFFE2E8F0) else Color(0xFF334155)
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(bg)
                            .clickable {
                                selectedSuggestion = if (suggestion == "Trending") null else suggestion
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = suggestion,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = textCol
                        )
                    }
                }
            }
        }

        // GIF Grid
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (!Giphy.isAvailable) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🎬 GIPHY Unavailable",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Color(0xFF1E293B)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "GIF search requires an active connection and GIPHY API key.",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                    )
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    state = gridState,
                    verticalItemSpacing = 6.dp,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(gifs, key = { it.id }) { gif ->
                        AsyncImage(
                            model = gif.previewUrl,
                            contentDescription = "GIF",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(gif.width.toFloat() / gif.height.toFloat().coerceAtLeast(1f))
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                                .clickable { onPickGif(gif) }
                        )
                    }
                }

                if (loading && gifs.isEmpty()) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(28.dp),
                        strokeWidth = 2.5.dp
                    )
                } else if (failed && gifs.isEmpty()) {
                    Text(
                        text = "Couldn't load GIFs. Tap a category or search to retry.",
                        fontSize = 12.sp,
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Attribution
        Text(
            text = "Powered by GIPHY",
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 2.dp)
        )
    }
}

/**
 * Tab 3: Curated couple stickers, big expressive reaction stickers, and romantic phrase cards.
 * Tapping sends instantly like WhatsApp / Telegram stickers; long-pressing inserts into text.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickersBoardContent(
    isDark: Boolean,
    onStickerPicked: (String) -> Unit,
    onStickerLongClicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategoryIndex by rememberSaveable { mutableIntStateOf(0) }
    val categories = remember {
        listOf("💖 Love", "🥰 Cute", "💬 Sayings", "🧸 Moods")
    }

    val loveStickers = remember {
        listOf(
            "💖", "❤️", "😘", "💋", "🥰", "😍", "🫂", "🥺", "👩‍❤️‍💋‍👨", "👩‍❤️‍👨", "💍", "🌹",
            "💌", "🧸", "🤍", "✨", "💓", "💞", "💘", "💒", "🕊️", "💐", "🍫", "🍓"
        )
    }
    val lovePhrases = remember {
        listOf(
            "I love you ❤️", "Miss you so much 🥺", "Sending you kisses 💋",
            "You are my world 🌍", "Thinking of you 💭", "Hug me tight 🫂"
        )
    }

    val cuteStickers = remember {
        listOf(
            "🧸", "🫂", "🥰", "🤭", "🫣", "🤤", "😻", "😽", "🫶", "🫰",
            "🫠", "🥺", "🎀", "🐣", "🌸", "🌷", "✨", "💫", "🍯", "🧁"
        )
    }
    val cutePhrases = remember {
        listOf(
            "Can't wait to see you 🥰", "Need your cuddles 🧸", "My favorite human 🫶",
            "So proud of you 🌟", "You make me smile 😊", "Sweet dreams baby 🌙"
        )
    }

    val sayingPhrases = remember {
        listOf(
            "I love you more than words ❤️", "Forever & always 💍", "Good morning my sunshine ☀️",
            "You + Me = Forever ♾️", "My whole heart 🫀", "Come home soon 🏡",
            "Coffee date? ☕", "Movie night tonight? 🍿", "You look so gorgeous 😍",
            "Always thinking about you 💕", "Forever yours 🤍", "My happily ever after ✨"
        )
    }

    val moodStickers = remember {
        listOf(
            "😉", "😏", "🤭", "🫦", "👅", "🔥", "💋", "🥂", "🍾", "🛀",
            "🕯️", "🏩", "🔐", "👑", "💅", "💃"
        )
    }
    val moodPhrases = remember {
        listOf(
            "Flirty mood 🔥", "Dinner date tonight? 🍽️", "Call me when free 📞",
            "Thinking bad thoughts 😏", "Yours only 🔐", "All mine 💖"
        )
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        // Sticker categories tab row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            categories.forEachIndexed { index, label ->
                val isSelected = selectedCategoryIndex == index
                val bg = if (isSelected) MaterialTheme.colorScheme.primary else if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                val textCol = if (isSelected) Color.White else if (isDark) Color(0xFFE2E8F0) else Color(0xFF334155)
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(bg)
                        .clickable { selectedCategoryIndex = index }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = textCol
                    )
                }
            }
        }

        // Helpful hint
        Text(
            text = "Tap to send sticker instantly • Long-press to insert",
            fontSize = 10.sp,
            color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp)
        )

        // Sticker items grid
        val (currentEmojis, currentPhrases) = when (selectedCategoryIndex) {
            0 -> loveStickers to lovePhrases
            1 -> cuteStickers to cutePhrases
            2 -> emptyList<String>() to sayingPhrases
            else -> moodStickers to moodPhrases
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(if (selectedCategoryIndex == 2) 2 else 4),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Big expressive sticker emojis (Cherish BigEmoji engine animates them in chat)
            if (currentEmojis.isNotEmpty()) {
                items(currentEmojis, key = { "emoji_$it" }) { sticker ->
                    val displaySticker = remember(sticker) { withFairSkinTone(sticker) }
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isDark) Color(0x331E293B) else Color(0x33F8FAFC))
                            .border(
                                BorderStroke(
                                    0.8.dp,
                                    if (isDark) Color(0x22FFFFFF) else Color(0x1A000000)
                                ),
                                RoundedCornerShape(14.dp)
                            )
                            .combinedClickable(
                                onClick = { onStickerPicked(displaySticker) },
                                onLongClick = { onStickerLongClicked(displaySticker) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displaySticker,
                            fontSize = 34.sp,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.fitToCell()
                        )
                    }
                }
            }

            // Romantic couple quote sticker badges
            items(currentPhrases, key = { "phrase_$it" }) { phrase ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isDark) Color(0xFF1E293B) else Color.White,
                    border = BorderStroke(
                        1.dp,
                        if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color(0xFFFFD1DC)
                    ),
                    shadowElevation = if (isDark) 0.dp else 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .combinedClickable(
                            onClick = { onStickerPicked(phrase) },
                            onLongClick = { onStickerLongClicked(phrase) }
                        )
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = phrase,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Floating button bar at the bottom of the keyboard/board to switch between
 * Emoji, GIF, and Stickers tabs, plus quick backspace and keyboard return buttons.
 */
@Composable
private fun MediaBoardBottomBar(
    activeTab: Int,
    onTabSelected: (Int) -> Unit,
    onBackspace: () -> Unit,
    onSwitchToKeyboard: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = if (isDark) Color(0xF00F172A) else Color(0xF5FFFFFF),
            border = BorderStroke(1.dp, if (isDark) Color(0x33FFFFFF) else Color(0x22000000)),
            shadowElevation = 8.dp,
            modifier = Modifier.height(46.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // The 3 tabs: Emoji, GIF, Stickers
                val tabs = listOf(
                    Triple(0, "😊", "Emoji"),
                    Triple(1, "🎬", "GIF"),
                    Triple(2, "💟", "Stickers")
                )
                tabs.forEach { (index, icon, label) ->
                    val isSelected = activeTab == index
                    val tabBg by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        animationSpec = tween(180),
                        label = "tab_bg_$index"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) Color.White else if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                        animationSpec = tween(180),
                        label = "tab_text_$index"
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(tabBg)
                            .clickable { onTabSelected(index) }
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = icon, fontSize = 13.sp)
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = contentColor
                            )
                        }
                    }
                }

                // Vertical Divider
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .size(width = 1.dp, height = 20.dp)
                        .background(if (isDark) Color(0x22FFFFFF) else Color(0x1F000000))
                )

                // Backspace button
                IconButton(
                    onClick = onBackspace,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Backspace,
                        contentDescription = "Backspace",
                        tint = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Keyboard toggle button
                IconButton(
                    onClick = onSwitchToKeyboard,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Keyboard,
                        contentDescription = "Keyboard",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Shrinks an emoji or sticker character drawn wider than its cell until it fits.
 */
private fun Modifier.fitToCell(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
    val width = placeable.width.coerceAtMost(constraints.maxWidth)
    val scale = width.toFloat() / placeable.width.coerceAtLeast(1)
    layout(width, placeable.height) {
        if (scale >= 1f) {
            placeable.place(0, 0)
        } else {
            placeable.placeWithLayer((width - placeable.width) / 2, 0) {
                scaleX = scale
                scaleY = scale
            }
        }
    }
}
