package com.example.ui.chat

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.ui.security.EmergencyExitHandle
import kotlinx.coroutines.delay
import java.net.URLEncoder
import com.example.ui.components.CherishButton
import com.example.ui.components.CherishTonalButton

/**
 * Extracts the 11-character YouTube video ID from any link, embed, shorts, or raw ID.
 */
fun extractYouTubeVideoId(text: String?): String? {
    if (text.isNullOrBlank()) return null
    val trimmed = text.trim()
    val fromRegex = YouTubeHelper.extractVideoId(trimmed)
    if (fromRegex != null) return fromRegex
    return try {
        val uri = Uri.parse(trimmed)
        val vParam = uri.getQueryParameter("v")
        if (!vParam.isNullOrBlank() && vParam.length == 11) {
            vParam
        } else if (uri.host?.contains("youtu.be") == true) {
            uri.lastPathSegment?.take(11)?.takeIf { it.length == 11 }
        } else if (trimmed.contains("/shorts/")) {
            val idx = trimmed.indexOf("/shorts/") + 8
            trimmed.substring(idx).take(11).takeIf { it.length == 11 }
        } else if (trimmed.length == 11 && Regex("^[A-Za-z0-9_-]{11}$").matches(trimmed)) {
            trimmed
        } else null
    } catch (_: Exception) {
        null
    }
}

/**
 * JavaScript Bridge for observing SPA navigation on mobile YouTube.
 */
private class CherishBrowserBridge(
    private val onUrlChanged: (String, String) -> Unit
) {
    @JavascriptInterface
    fun onUrlChange(url: String, title: String) {
        onUrlChanged(url, title)
    }
}

/** Curated YouTube Video entry for immediate, zero-latency Watch Together selection. */
data class WatchPartyCuratedVideo(
    val videoId: String,
    val title: String,
    val description: String,
    val category: String,
    val badge: String = ""
)

/**
 * In-App YouTube Browser & Watch Party Picker Modal.
 *
 * Instantly displays curated couple video selections (Romantic, Lofi, Trending, Trailers, Comedy),
 * direct YouTube link detection & paste, search, and embedded mobile browsing.
 * Zero blank screens: curated cards load in 0ms, and browsing has smooth non-blocking progress.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WatchPartyYouTubePickerModal(
    onStart: (videoId: String, title: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onBackground

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val app = context.applicationContext as com.example.CherishApplication
    val watchLaterList by app.coupleFeaturesRepository.listenToWatchLater().collectAsState(initial = emptyList())
    val activeParty by app.coupleFeaturesRepository.watchPartyFlow.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("💖 Romantic Date") }
    var currentTab by remember { mutableIntStateOf(0) } // 0 = Curated Video Cards, 1 = Search & Web Browser

    var isLoading by remember { mutableStateOf(false) }
    var pageProgress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    var detectedVideoId by remember { mutableStateOf<String?>(null) }
    var detectedVideoTitle by remember { mutableStateOf("") }

    // Auto-timeout on isLoading so webview never holds a loading state for > 2.5 seconds
    LaunchedEffect(isLoading) {
        if (isLoading) {
            delay(2500)
            isLoading = false
        }
    }

    // Check clipboard for any YouTube link copied previously
    var clipboardVideoUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                ?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
                ?.coerceToText(context)?.toString()?.trim()
            if (!clip.isNullOrBlank() && extractYouTubeVideoId(clip) != null) {
                clipboardVideoUrl = clip
            }
        } catch (_: Exception) {}
    }

    // Extract any video id from the current search/input query
    val queryVideoId = remember(searchQuery) { extractYouTubeVideoId(searchQuery) }

    fun navigateOrSearch(input: String) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return
        searchQuery = trimmed
        val vid = extractYouTubeVideoId(trimmed)
        if (vid != null) {
            detectedVideoId = vid
            detectedVideoTitle = "Shared Video"
            onStart(vid, "Shared Video")
            return
        }
        val targetUrl = when {
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> "https://m.youtube.com/results?search_query=" + URLEncoder.encode(trimmed, "UTF-8")
        }
        currentTab = 1
        isLoading = true
        webViewRef?.loadUrl(targetUrl)
        keyboardController?.hide()
    }

    fun pasteFromClipboard() {
        try {
            val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                ?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
                ?.coerceToText(context)?.toString()?.trim()
            if (!clip.isNullOrBlank()) {
                val vid = extractYouTubeVideoId(clip)
                if (vid != null) {
                    searchQuery = clip
                    detectedVideoId = vid
                    detectedVideoTitle = "Shared Video"
                } else {
                    navigateOrSearch(clip)
                }
            }
        } catch (_: Exception) {}
    }

    BackHandler {
        if (currentTab == 1 && webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else if (currentTab == 1) {
            currentTab = 0
        } else {
            onDismiss()
        }
    }

    val curatedVideos = remember {
        listOf(
            // 💖 Romantic Date
            WatchPartyCuratedVideo("2Vv-BfVoq4g", "Ed Sheeran - Perfect", "Official Music Video • Romantic Classic", "💖 Romantic Date", "Music"),
            WatchPartyCuratedVideo("-BjZmE2gtdo", "Taylor Swift - Lover", "Official Music Video • Heartwarming Vibes", "💖 Romantic Date", "Music"),
            WatchPartyCuratedVideo("LjhCEhWiKXk", "Bruno Mars - Just the Way You Are", "Classic Romantic Hit", "💖 Romantic Date", "Pop"),
            WatchPartyCuratedVideo("S_6qQjC-V0s", "Acoustic Love Songs Guitar Relax", "Soft Cozy Romance Melodies", "💖 Romantic Date", "Acoustic"),
            WatchPartyCuratedVideo("6GvFpXn_2-A", "Best Romantic Movie Scenes Montage", "Heartwarming Cinematic Love Moments", "💖 Romantic Date", "Movie"),
            WatchPartyCuratedVideo("D-eA53UuTqk", "Calum Scott - You Are The Reason", "Emotional Ballad & Piano", "💖 Romantic Date", "Ballad"),
            WatchPartyCuratedVideo("rtOvBOTyX00", "Christina Perri - A Thousand Years", "Twilight Saga Love Theme", "💖 Romantic Date", "Hits"),

            // ☕ Cozy Lofi Beats
            WatchPartyCuratedVideo("jfKfPfyJRdk", "Lofi Hip Hop Radio - Beats to Relax", "Lofi Girl 24/7 Cozy Beats Stream", "☕ Cozy Lofi Beats", "Live"),
            WatchPartyCuratedVideo("1ZYbU8JGBdQ", "Sunset Waves Chill Guitar Lofi", "Peaceful Evening Ocean Vibes", "☕ Cozy Lofi Beats", "Chill"),
            WatchPartyCuratedVideo("L_LUpnjgPso", "Warm Fireplace & Cozy Jazz Coffee", "Warm Acoustic Relaxing Ambience", "☕ Cozy Lofi Beats", "Jazz"),
            WatchPartyCuratedVideo("tNq4K1E6W7E", "Studio Ghibli Relaxing Piano", "Peaceful Nostalgic Anime Melodies", "☕ Cozy Lofi Beats", "Piano"),
            WatchPartyCuratedVideo("5qap5aO4i9A", "Rainy Night Coffee Shop Ambience", "Gentle Rain & Soft Background Music", "☕ Cozy Lofi Beats", "Rain"),

            // 🔥 Trending Hits
            WatchPartyCuratedVideo("yKNxeF4KMsY", "Coldplay - Yellow", "Official HD Remastered Video", "🔥 Trending Hits", "Rock"),
            WatchPartyCuratedVideo("4NRXx6U8ABQ", "The Weeknd - Blinding Lights", "Official Music Video • Billboard #1", "🔥 Trending Hits", "Pop"),
            WatchPartyCuratedVideo("09R8_2nJtjg", "Maroon 5 - Sugar", "Surprise Wedding Video Hit", "🔥 Trending Hits", "Pop"),
            WatchPartyCuratedVideo("7wtfhZwyrcc", "Imagine Dragons - Believer", "High Energy Music Video", "🔥 Trending Hits", "Anthem"),
            WatchPartyCuratedVideo("fJ9rUzIMcZQ", "Queen - Bohemian Rhapsody", "Classic Rock Masterpiece", "🔥 Trending Hits", "Classic"),
            WatchPartyCuratedVideo("k2qgadSvNyU", "Dua Lipa - New Rules", "Official Pop Music Video", "🔥 Trending Hits", "Dance"),

            // 🎬 Movie Trailers
            WatchPartyCuratedVideo("d9MyW72ELq0", "Avatar: The Way of Water - Trailer", "20th Century Studios Visual Spectacle", "🎬 Movie Trailers", "Sci-Fi"),
            WatchPartyCuratedVideo("LEjhY15eCx0", "Inside Out 2 - Official Trailer", "Disney & Pixar Emotional Adventure", "🎬 Movie Trailers", "Animation"),
            WatchPartyCuratedVideo("73_1biulkYk", "Deadpool & Wolverine - Trailer", "Marvel Studios Blockbuster Action", "🎬 Movie Trailers", "Action"),
            WatchPartyCuratedVideo("zSWdZVtXT7E", "Interstellar - Official Trailer", "Christopher Nolan Sci-Fi Masterpiece", "🎬 Movie Trailers", "Drama"),
            WatchPartyCuratedVideo("qEVUtrk8_B4", "John Wick: Chapter 4 - Trailer", "High Octane Action & Cinematography", "🎬 Movie Trailers", "Thriller"),

            // 😂 Comedy & Fun
            WatchPartyCuratedVideo("hY7m5jjJ9mM", "Cute & Funny Animal Moments", "Wholesome Pets & Dogs Compilation", "😂 Comedy & Fun", "Pets"),
            WatchPartyCuratedVideo("T647CGsuOVU", "Try Not To Laugh Challenge", "Funniest Viral Internet Clips", "😂 Comedy & Fun", "Funny"),
            WatchPartyCuratedVideo("aY9_D3W180k", "Golden Retriever Puppies Playing", "Adorable Pups & Heartwarming Fun", "😂 Comedy & Fun", "Cute"),
            WatchPartyCuratedVideo("J---aiyznGQ", "Cat Funny Fails & Acrobatics", "Cute Cat Compilation", "😂 Comedy & Fun", "Cats")
        )
    }

    val categories = remember(watchLaterList.size) {
        listOf(
            "💖 Romantic Date",
            "☕ Cozy Lofi Beats",
            "🔥 Trending Hits",
            "🎬 Movie Trailers",
            "😂 Comedy & Fun"
        ) + if (watchLaterList.isNotEmpty()) listOf("⏳ Watch Later (${watchLaterList.size})") else emptyList()
    }

    val displayedVideos = remember(selectedCategory, searchQuery, curatedVideos) {
        if (searchQuery.isNotBlank() && queryVideoId == null) {
            curatedVideos.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.description.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true)
            }
        } else {
            curatedVideos.filter { it.category == selectedCategory }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                ) {
                    // 1. Top Header Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(ink.copy(alpha = 0.08f))
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = ink)
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Red.copy(alpha = 0.15f),
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Rounded.PlayArrow,
                                                contentDescription = null,
                                                tint = Color.Red,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Watch Together 🍿",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ink
                                    )
                                }
                                Text(
                                    text = "Select any video to enjoy in synchronized harmony",
                                    fontSize = 11.sp,
                                    color = ink.copy(alpha = 0.65f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Mode Navigation Controls (when in Browser tab)
                        if (currentTab == 1) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = { webViewRef?.goBack() },
                                    enabled = canGoBack,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Back",
                                        tint = if (canGoBack) ink else ink.copy(alpha = 0.25f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { webViewRef?.goForward() },
                                    enabled = canGoForward,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.ArrowForward,
                                        contentDescription = "Forward",
                                        tint = if (canGoForward) ink else ink.copy(alpha = 0.25f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { webViewRef?.reload() },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Refresh,
                                        contentDescription = "Refresh",
                                        tint = ink,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 2. Mode Tabs (Curated Videos vs YouTube Web Browser)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (currentTab == 0) accent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { currentTab = 0 }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.Movie,
                                    contentDescription = null,
                                    tint = if (currentTab == 0) MaterialTheme.colorScheme.onPrimary else ink,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "🍿 Featured & Picks",
                                    fontSize = 12.sp,
                                    fontWeight = if (currentTab == 0) FontWeight.Bold else FontWeight.Medium,
                                    color = if (currentTab == 0) MaterialTheme.colorScheme.onPrimary else ink
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (currentTab == 1) accent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    currentTab = 1
                                    if (webViewRef?.url.isNullOrBlank()) {
                                        webViewRef?.loadUrl("https://m.youtube.com")
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.Public,
                                    contentDescription = null,
                                    tint = if (currentTab == 1) MaterialTheme.colorScheme.onPrimary else ink,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "🌐 Browse YouTube",
                                    fontSize = 12.sp,
                                    fontWeight = if (currentTab == 1) FontWeight.Bold else FontWeight.Medium,
                                    color = if (currentTab == 1) MaterialTheme.colorScheme.onPrimary else ink
                                )
                            }
                        }
                    }

                    // 3. Search & URL Bar
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search",
                                tint = accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            androidx.compose.foundation.text.BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 8.dp),
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = ink,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { navigateOrSearch(searchQuery) }),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isBlank()) {
                                        Text(
                                            text = "Search YouTube or paste link...",
                                            color = ink.copy(alpha = 0.45f),
                                            fontSize = 13.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            if (searchQuery.isNotBlank()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Clear,
                                        contentDescription = "Clear",
                                        tint = ink.copy(alpha = 0.5f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = { pasteFromClipboard() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.ContentPaste,
                                    contentDescription = "Paste",
                                    tint = accent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            CherishTonalButton(
                                onClick = { navigateOrSearch(searchQuery) },
                                enabled = searchQuery.isNotBlank(),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text(
                                    text = if (queryVideoId != null) "Play" else "Go",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }


                    // 5. Loading progress indicator for webview (never blocks UI)
                    if (currentTab == 1 && isLoading && pageProgress in 1..99) {
                        LinearProgressIndicator(
                            progress = { pageProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp),
                            color = accent,
                            trackColor = Color.Transparent
                        )
                    }

                    // 6. MAIN CONTENT AREA
                    if (currentTab == 0) {
                        // TAB 0: CURATED VIDEO PICKER (INSTANT, ZERO BLANK SCREEN)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            // Category Filter Chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (clipboardVideoUrl != null && queryVideoId == null) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = accent.copy(alpha = 0.18f),
                                        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
                                        modifier = Modifier.clickable {
                                            clipboardVideoUrl?.let {
                                                searchQuery = it
                                                val vid = extractYouTubeVideoId(it)
                                                if (vid != null) {
                                                    detectedVideoId = vid
                                                    detectedVideoTitle = "Shared Video"
                                                }
                                            }
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Rounded.ContentPaste, contentDescription = null, tint = accent, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Paste Copied Video", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = accent)
                                        }
                                    }
                                }

                                categories.forEach { cat ->
                                    val isSelected = selectedCategory == cat
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = BorderStroke(1.dp, if (isSelected) accent else Color.Transparent),
                                        modifier = Modifier.clickable {
                                            selectedCategory = cat
                                        }
                                    ) {
                                        Text(
                                            text = cat,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) accent else ink,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            // If searching with text, offer web search jump button
                            if (searchQuery.isNotBlank() && queryVideoId == null) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = accent.copy(alpha = 0.1f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                        .clickable { navigateOrSearch(searchQuery) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.Search, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Search full YouTube for “$searchQuery” 🌐",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = accent,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }

                            // Video List
                            if (selectedCategory.startsWith("⏳ Watch Later")) {
                                if (watchLaterList.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No saved videos in Watch Later",
                                            fontSize = 13.sp,
                                            color = ink.copy(alpha = 0.6f)
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(watchLaterList, key = { it.id }) { v ->
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { onStart(v.videoId, v.title) }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    AsyncImage(
                                                        model = YouTubeHelper.getThumbnailUrl(v.videoId),
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .width(110.dp)
                                                            .aspectRatio(16f / 9f)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(Color.Black)
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = v.title.ifBlank { "Saved Video" },
                                                            color = ink,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = "Saved from chat",
                                                            color = ink.copy(alpha = 0.6f),
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                    IconButton(onClick = { app.coupleFeaturesRepository.removeWatchLaterVideo(v.id) }) {
                                                        Icon(Icons.Rounded.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(displayedVideos, key = { it.videoId }) { item ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onStart(item.videoId, item.title) }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(120.dp)
                                                        .aspectRatio(16f / 9f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color.Black)
                                                ) {
                                                    AsyncImage(
                                                        model = YouTubeHelper.getThumbnailUrl(item.videoId),
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                    // Duration / badge overlay
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = Color.Black.copy(alpha = 0.75f),
                                                        modifier = Modifier
                                                            .align(Alignment.BottomEnd)
                                                            .padding(4.dp)
                                                    ) {
                                                        Text(
                                                            text = item.badge,
                                                            fontSize = 9.sp,
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.width(10.dp))

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = item.title,
                                                        color = ink,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Spacer(modifier = Modifier.height(3.dp))
                                                    Text(
                                                        text = item.description,
                                                        color = ink.copy(alpha = 0.6f),
                                                        fontSize = 11.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        CherishTonalButton(
                                                            onClick = { onStart(item.videoId, item.title) },
                                                            shape = RoundedCornerShape(8.dp),
                                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 3.dp),
                                                            modifier = Modifier.height(28.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Rounded.PlayArrow,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(13.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = "Watch Together 🍿",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // TAB 1: EMBEDDED YOUTUBE BROWSER (CLEAN USER-AGENT, NO LOCKUP OVERLAYS)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        webViewRef = this
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                                        settings.apply {
                                            javaScriptEnabled = true
                                            domStorageEnabled = true
                                            databaseEnabled = true
                                            mediaPlaybackRequiresUserGesture = false
                                            allowFileAccess = true
                                            allowContentAccess = true
                                            loadsImagesAutomatically = true
                                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                            useWideViewPort = true
                                            loadWithOverviewMode = true
                                            javaScriptCanOpenWindowsAutomatically = true
                                            setSupportMultipleWindows(false)
                                            cacheMode = WebSettings.LOAD_DEFAULT
                                            // Strip WebView marker to allow regular mobile browser rendering
                                            val defaultUa = userAgentString ?: ""
                                            userAgentString = defaultUa.replace("; wv", "")
                                        }

                                        val cookieManager = CookieManager.getInstance()
                                        cookieManager.setAcceptCookie(true)
                                        cookieManager.setAcceptThirdPartyCookies(this, true)

                                        addJavascriptInterface(
                                            CherishBrowserBridge { url, title ->
                                                mainHandler.post {
                                                    val id = extractYouTubeVideoId(url)
                                                    if (id != null) {
                                                        detectedVideoId = id
                                                        if (title.isNotBlank()) {
                                                            detectedVideoTitle = title.replace(" - YouTube", "").trim()
                                                        }
                                                    }
                                                }
                                            },
                                            "CherishBrowserBridge"
                                        )

                                        webChromeClient = object : WebChromeClient() {
                                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                                pageProgress = newProgress
                                                if (newProgress >= 80) {
                                                    isLoading = false
                                                }
                                            }

                                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                                val currentUrl = view?.url
                                                val id = extractYouTubeVideoId(currentUrl)
                                                if (id != null && !title.isNullOrBlank()) {
                                                    detectedVideoId = id
                                                    detectedVideoTitle = title.replace(" - YouTube", "").trim()
                                                }
                                            }

                                            override fun getDefaultVideoPoster(): Bitmap? {
                                                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                                            }
                                        }

                                        webViewClient = object : WebViewClient() {
                                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                                super.onPageStarted(view, url, favicon)
                                                val id = extractYouTubeVideoId(url)
                                                if (id != null) {
                                                    detectedVideoId = id
                                                    val t = view?.title ?: ""
                                                    if (t.isNotBlank()) {
                                                        detectedVideoTitle = t.replace(" - YouTube", "").trim()
                                                    }
                                                }
                                            }

                                            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                                                super.onReceivedError(view, errorCode, description, failingUrl)
                                                isLoading = false
                                            }

                                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                                super.onReceivedError(view, request, error)
                                                if (request?.isForMainFrame == true) {
                                                    isLoading = false
                                                }
                                            }

                                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                                val url = request?.url?.toString() ?: return false
                                                val vid = extractYouTubeVideoId(url)
                                                if (vid != null) {
                                                    detectedVideoId = vid
                                                }
                                                // Keep standard navigation inside the WebView
                                                if (url.startsWith("http://") || url.startsWith("https://")) {
                                                    if (url.contains("youtube.com") ||
                                                        url.contains("youtu.be") ||
                                                        url.contains("google.com") ||
                                                        url.contains("accounts.google.com")
                                                    ) {
                                                        return false
                                                    }
                                                }
                                                // Intercept external intent schemes to prevent app ejection
                                                if (url.startsWith("intent://") || url.startsWith("vnd.youtube:") || url.startsWith("android-app:")) {
                                                    val v = extractYouTubeVideoId(url)
                                                    if (v != null) {
                                                        detectedVideoId = v
                                                        view?.loadUrl("https://m.youtube.com/watch?v=$v")
                                                        return true
                                                    }
                                                    try {
                                                        val parsed = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                                                        val fallback = parsed.getStringExtra("browser_fallback_url")
                                                        if (!fallback.isNullOrBlank()) {
                                                            view?.loadUrl(fallback)
                                                            return true
                                                        }
                                                        val dataUri = parsed.dataString
                                                        if (!dataUri.isNullOrBlank() && (dataUri.startsWith("http://") || dataUri.startsWith("https://"))) {
                                                            view?.loadUrl(dataUri)
                                                            return true
                                                        }
                                                    } catch (_: Exception) {}

                                                    if (url.startsWith("intent://")) {
                                                        val stripped = url.removePrefix("intent://").substringBefore("#Intent")
                                                        if (stripped.isNotBlank()) {
                                                            val target = if (stripped.startsWith("http://") || stripped.startsWith("https://")) stripped else "https://$stripped"
                                                            view?.loadUrl(target)
                                                            return true
                                                        }
                                                    }
                                                    return true
                                                }

                                                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                                    return true
                                                }

                                                return false
                                            }

                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                isLoading = false
                                                canGoBack = view?.canGoBack() == true
                                                canGoForward = view?.canGoForward() == true
                                                val id = extractYouTubeVideoId(url)
                                                if (id != null) {
                                                    detectedVideoId = id
                                                    val t = view?.title ?: ""
                                                    if (t.isNotBlank()) {
                                                        detectedVideoTitle = t.replace(" - YouTube", "").trim()
                                                    }
                                                }

                                                // Injected SPA observer for seamless in-page pushState navigation
                                                val jsObserver = """
                                                    (function() {
                                                        if (window._cherishObsInstalled) return;
                                                        window._cherishObsInstalled = true;
                                                        var lastUrl = location.href;
                                                        function notify() {
                                                            if (location.href !== lastUrl) {
                                                                lastUrl = location.href;
                                                                if (window.CherishBrowserBridge) {
                                                                    window.CherishBrowserBridge.onUrlChange(lastUrl, document.title || "");
                                                                }
                                                            }
                                                        }
                                                        setInterval(notify, 400);
                                                        window.addEventListener('popstate', notify);
                                                        document.addEventListener('click', function(e) {
                                                            var a = e.target.closest('a');
                                                            if (a && a.href && (a.href.indexOf('watch?v=') !== -1 || a.href.indexOf('/shorts/') !== -1)) {
                                                                if (window.CherishBrowserBridge) {
                                                                    window.CherishBrowserBridge.onUrlChange(a.href, a.innerText || document.title || '');
                                                                }
                                                            }
                                                        }, true);
                                                    })();
                                                """.trimIndent()
                                                view?.evaluateJavascript(jsObserver, null)
                                            }

                                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                                canGoBack = view?.canGoBack() == true
                                                canGoForward = view?.canGoForward() == true
                                                val id = extractYouTubeVideoId(url)
                                                if (id != null) {
                                                    detectedVideoId = id
                                                    val t = view?.title ?: ""
                                                    if (t.isNotBlank()) {
                                                        detectedVideoTitle = t.replace(" - YouTube", "").trim()
                                                    }
                                                }
                                            }
                                        }

                                        loadUrl("https://m.youtube.com")
                                    }
                                },
                                update = {}
                            )

                            // Subtle hint pill when user hasn't selected a video yet
                            if (detectedVideoId == null) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.75f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Tap any video to sync & start Watch Together 🍿",
                                            fontSize = 11.sp,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 7. Floating Synced Watch Party Dock (Appears when a video is active in web tab)
                    AnimatedVisibility(
                        visible = detectedVideoId != null && currentTab == 1,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        val activeId = detectedVideoId ?: ""
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp,
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = YouTubeHelper.getThumbnailUrl(activeId),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(68.dp)
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black)
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = accent.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "READY TO SYNC 🍿",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = accent,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = detectedVideoTitle.ifBlank { "Selected Video" },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Plays on both phones together",
                                        fontSize = 11.sp,
                                        color = ink.copy(alpha = 0.6f)
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (activeParty?.isActive == true) {
                                        FilledIconButton(
                                            onClick = {
                                                app.coupleFeaturesRepository.enqueueWatchPartyVideo(activeId, detectedVideoTitle.ifBlank { "Watch Together" })
                                                onDismiss()
                                                android.widget.Toast.makeText(context, "Added to Up Next queue", android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = IconButtonDefaults.filledIconButtonColors(
                                                containerColor = accent.copy(alpha = 0.15f),
                                                contentColor = accent
                                            ),
                                            modifier = Modifier.size(42.dp)
                                        ) {
                                            Icon(Icons.Rounded.QueuePlayNext, contentDescription = "Add to Up Next")
                                        }
                                    }

                                    CherishButton(
                                        onClick = {
                                            onStart(activeId, detectedVideoTitle.ifBlank { "Watch Together" })
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                        modifier = Modifier.testTag("watch_party_start").height(42.dp)
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Watch Together 🍿",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                EmergencyExitHandle(modifier = Modifier.zIndex(999f))
            }
        }
    }
}

/**
 * Compatible bridge for starting a Watch Party.
 * Opens YouTube Watch Party picker directly inside the app with zero blank screens.
 */
@Composable
fun WatchPartySetupDialog(
    onStart: (videoId: String, title: String) -> Unit,
    onDismiss: () -> Unit
) {
    WatchPartyYouTubePickerModal(
        onStart = onStart,
        onDismiss = onDismiss
    )
}

