package com.example.ui.chat

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
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
import coil.compose.AsyncImage
import java.net.URLEncoder

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

/**
 * In-App YouTube Browser & Picker Modal.
 *
 * Instead of asking the couple to leave the app and paste links, this opens YouTube
 * directly inside Cherish with full search, mobile browsing, categories, and 1-tap syncing.
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
    var showWatchLater by remember { mutableStateOf(false) }
    
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var pageProgress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    var detectedVideoId by remember { mutableStateOf<String?>(null) }
    var detectedVideoTitle by remember { mutableStateOf("") }

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

    fun navigateOrSearch(input: String) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return
        val vid = extractYouTubeVideoId(trimmed)
        val targetUrl = when {
            vid != null -> "https://m.youtube.com/watch?v=$vid"
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> "https://m.youtube.com/results?search_query=" + URLEncoder.encode(trimmed, "UTF-8")
        }
        searchQuery = trimmed
        webViewRef?.loadUrl(targetUrl)
        keyboardController?.hide()
    }

    BackHandler {
        if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            onDismiss()
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
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = ink)
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFFF0000).copy(alpha = 0.15f),
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color(0xFFFF0000),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Browse YouTube 🍿",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ink
                                )
                            }
                            Text(
                                text = "Select any video to watch together in sync",
                                fontSize = 11.sp,
                                color = ink.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Web Navigation Controls
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
                                Icons.AutoMirrored.Filled.ArrowBack,
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
                                Icons.AutoMirrored.Filled.ArrowForward,
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
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = ink,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                searchQuery = ""
                                webViewRef?.loadUrl("https://m.youtube.com")
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Home,
                                contentDescription = "Home",
                                tint = accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // 2. Search & URL Bar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it.take(200) },
                            placeholder = {
                                Text(
                                    "Search songs, artists, or paste link...",
                                    fontSize = 13.sp,
                                    color = ink.copy(alpha = 0.5f)
                                )
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { navigateOrSearch(searchQuery) }),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("youtube_search_input")
                        )

                        if (searchQuery.isNotBlank()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = ink.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                                    ?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
                                    ?.coerceToText(context)?.toString()?.trim()
                                if (!clip.isNullOrBlank()) {
                                    navigateOrSearch(clip)
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentPaste,
                                contentDescription = "Paste",
                                tint = accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        FilledIconButton(
                            onClick = { navigateOrSearch(searchQuery) },
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Go",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // 3. Quick Category Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (clipboardVideoUrl != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accent.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
                            modifier = Modifier.clickable {
                                clipboardVideoUrl?.let { navigateOrSearch(it) }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, tint = accent, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Paste Copied Video", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = accent)
                            }
                        }
                    }

                    val categories = listOf(
                        "🔥 Trending" to "https://m.youtube.com/feed/trending",
                        "🎵 Music" to "trending songs",
                        "☕ Lofi Chill" to "lofi hip hop radio live",
                        "🎬 Movie Trailers" to "movie trailers",
                        "😂 Funny Clips" to "funny moments"
                    )

                    categories.forEach { (label, queryOrUrl) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.clickable { navigateOrSearch(queryOrUrl) }
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = ink,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    if (watchLaterList.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accent.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
                            modifier = Modifier.clickable { showWatchLater = !showWatchLater }
                        ) {
                            Text(
                                text = if (showWatchLater) "Hide Watch Later" else "⏳ Watch Later (${watchLaterList.size})",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = accent,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // 4. Loading Progress Bar
                if (isLoading && pageProgress in 1..99) {
                    LinearProgressIndicator(
                        progress = { pageProgress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp),
                        color = accent,
                        trackColor = Color.Transparent
                    )
                }

                // 5. Embedded YouTube Mobile Browser or Watch Later List
                if (showWatchLater) {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.background)
                    ) {
                        items(watchLaterList.size) { i ->
                            val v = watchLaterList[i]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        onStart(v.videoId, v.title)
                                        showWatchLater = false
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = YouTubeHelper.getThumbnailUrl(v.videoId),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(90.dp)
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = v.title.ifBlank { "Saved Video" },
                                        color = ink,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Saved from chat",
                                        color = ink.copy(alpha = 0.6f),
                                        fontSize = 12.sp
                                    )
                                }
                                IconButton(onClick = { app.coupleFeaturesRepository.removeWatchLaterVideo(v.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            HorizontalDivider(color = ink.copy(alpha = 0.05f))
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color.Black)
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
                                setBackgroundColor(android.graphics.Color.BLACK)
                                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    mediaPlaybackRequiresUserGesture = false
                                    loadsImagesAutomatically = true
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    javaScriptCanOpenWindowsAutomatically = true
                                    setSupportMultipleWindows(false)
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
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
                                        isLoading = newProgress < 100
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
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val url = request?.url?.toString() ?: return false
                                        // Keep standard navigation inside the WebView
                                        if (url.contains("youtube.com") ||
                                            url.contains("youtu.be") ||
                                            url.contains("google.com") ||
                                            url.contains("accounts.google.com")
                                        ) {
                                            return false
                                        }
                                        // Intercept external intent schemes to prevent app ejection
                                        if (url.startsWith("intent://") || url.startsWith("vnd.youtube:")) {
                                            val vid = extractYouTubeVideoId(url)
                                            if (vid != null) {
                                                view?.loadUrl("https://m.youtube.com/watch?v=$vid")
                                                return true
                                            }
                                        }
                                        return false
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
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
                                                setInterval(notify, 500);
                                                window.addEventListener('popstate', notify);
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

                    // Subtle hint pill when user hasn't clicked a video yet
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
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.TouchApp, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Tap any video to sync & start Watch Party 🍿",
                                    fontSize = 11.5.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // 6. Floating Synced Watch Party Dock (Appears when a video is active)
                AnimatedVisibility(
                    visible = detectedVideoId != null && !showWatchLater,
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
                            // Thumbnail
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

                            // Video Info
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

                            // Up Next & Start Buttons
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (activeParty?.isActive == true) {
                                    FilledIconButton(
                                        onClick = {
                                            app.coupleFeaturesRepository.enqueueWatchPartyVideo(activeId, detectedVideoTitle.ifBlank { "Watch Party" })
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
                                        Icon(Icons.Default.QueuePlayNext, contentDescription = "Add to Up Next")
                                    }
                                }

                                Button(
                                    onClick = {
                                        onStart(activeId, detectedVideoTitle.ifBlank { "Watch Party" })
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("watch_party_start").height(42.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Start",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
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
}

/**
 * Compatible bridge for starting a Watch Party.
 * Now opens YouTube directly inside the app instead of demanding a pasted link.
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
