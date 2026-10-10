package com.example.ui.chat

import android.annotation.SuppressLint
import android.app.Activity
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import com.example.data.model.WatchPartySession
import kotlinx.coroutines.delay
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

/** The video id in a YouTube link, or the id itself if that's what was pasted. */
private fun youTubeIdOf(text: String): String? {
    return extractYouTubeVideoId(text)
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
 * JavaScript Bridge for synchronizing video playback (play, pause, seek, completion).
 */
private class CherishSyncBridge(
    private val onState: (Int) -> Unit
) {
    @JavascriptInterface
    fun onStateChange(state: Int) {
        onState(state)
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

                // 5. Embedded YouTube Mobile Browser
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
                    visible = detectedVideoId != null,
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

                            // Start Sync Button
                            Button(
                                onClick = {
                                    onStart(activeId, detectedVideoTitle.ifBlank { "Watch Party" })
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accent),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("watch_party_start")
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

/**
 * Generates embed HTML with the YouTube IFrame API and bidirectional postMessage bridge.
 */
private fun getSyncedEmbedHtml(videoId: String): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                * { box-sizing: border-box; margin: 0; padding: 0; }
                html, body {
                    width: 100%;
                    height: 100%;
                    overflow: hidden;
                    background-color: #000000;
                }
                .player-container {
                    position: relative;
                    width: 100%;
                    height: 100%;
                    background-color: #000000;
                }
                iframe {
                    position: absolute;
                    top: 0;
                    left: 0;
                    width: 100%;
                    height: 100%;
                    border: 0;
                }
            </style>
        </head>
        <body>
            <div class="player-container">
                <iframe 
                    id="ytplayer"
                    src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&enablejsapi=1&fs=1&rel=0&playsinline=1&modestbranding=1"
                    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                    allowfullscreen>
                </iframe>
            </div>
            <script>
                function postYtCommand(func, args) {
                    var p = document.getElementById('ytplayer');
                    if (p && p.contentWindow) {
                        p.contentWindow.postMessage(JSON.stringify({
                            event: 'command',
                            func: func,
                            args: args || []
                        }), '*');
                    }
                }
                function playVideo() {
                    postYtCommand('playVideo');
                }
                function pauseVideo() {
                    postYtCommand('pauseVideo');
                }
                function seekTo(seconds) {
                    postYtCommand('seekTo', [seconds, true]);
                }
                window.addEventListener('message', function(event) {
                    try {
                        var data = typeof event.data === 'string' ? JSON.parse(event.data) : event.data;
                        if (data && data.event === 'onStateChange' && window.CherishSyncBridge) {
                            window.CherishSyncBridge.onStateChange(data.info);
                        }
                    } catch(e) {}
                });
            </script>
        </body>
        </html>
    """.trimIndent()
}

/**
 * Synced YouTube Player that dynamically responds to real-time play, pause, and seek commands.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SyncedYouTubePlayer(
    videoId: String,
    isPlaying: Boolean,
    positionSeconds: Float,
    onPlayPauseToggle: (Boolean, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var hasError by remember { mutableStateOf(false) }
    val embedHtml = remember(videoId) { getSyncedEmbedHtml(videoId) }

    // Synchronize play/pause
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            webViewRef?.evaluateJavascript("playVideo();", null)
        } else {
            webViewRef?.evaluateJavascript("pauseVideo();", null)
        }
    }

    // Synchronize seek position
    var lastKnownPos by remember { mutableFloatStateOf(positionSeconds) }
    LaunchedEffect(positionSeconds) {
        if (kotlin.math.abs(positionSeconds - lastKnownPos) > 2.5f) {
            webViewRef?.evaluateJavascript("seekTo($positionSeconds);", null)
        }
        lastKnownPos = positionSeconds
    }

    DisposableEffect(videoId) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                loadUrl("about:blank")
                destroy()
            }
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp)),
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
                        allowFileAccess = true
                        allowContentAccess = true
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
                        CherishSyncBridge { state ->
                            mainHandler.post {
                                // 1: playing, 2: paused, 0: ended
                                if (state == 1 && !isPlaying) {
                                    onPlayPauseToggle(true, positionSeconds)
                                } else if (state == 2 && isPlaying) {
                                    onPlayPauseToggle(false, positionSeconds)
                                } else if (state == 0 && isPlaying) {
                                    onPlayPauseToggle(false, positionSeconds)
                                }
                            }
                        },
                        "CherishSyncBridge"
                    )

                    webChromeClient = object : WebChromeClient() {
                        override fun getDefaultVideoPoster(): Bitmap? {
                            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                            super.onReceivedError(view, errorCode, description, failingUrl)
                            if (failingUrl?.contains("youtube") == true) {
                                hasError = true
                            }
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val uri = request?.url ?: return false
                            val url = uri.toString()
                            if (url.contains("/embed/") ||
                                url.contains("youtube-nocookie.com") ||
                                url.contains("youtube.com") ||
                                url.contains("accounts.google.com") ||
                                url.contains("doubleclick.net") ||
                                url.contains("googlevideo.com") ||
                                url.contains("gstatic.com") ||
                                url.contains("ytimg.com")
                            ) {
                                return false
                            }
                            return false
                        }
                    }

                    loadDataWithBaseURL(
                        "https://www.youtube-nocookie.com",
                        embedHtml,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            },
            update = {}
        )

        if (hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.90f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFFA1A1AA),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Video owner restricted inline playback",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { YouTubeHelper.openInYouTube(context, videoId) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Watch in YouTube App", fontSize = 12.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * Synced Ambient Listening / Watch Party Modal
 *
 * Sync playback for YouTube videos with shared pause/play, seek synchronization,
 * and seamless in-app video browsing.
 */
@Composable
fun SyncedWatchPartyModal(
    session: WatchPartySession,
    onPlayPause: (Boolean, Float) -> Unit,
    onSeek: (Float) -> Unit,
    onEndSession: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current
    val app = context.applicationContext as? com.example.CherishApplication

    var currentProgress by remember(session.videoId, session.positionSeconds) { mutableFloatStateOf(session.positionSeconds) }
    var isSeeking by remember { mutableStateOf(false) }
    var showBrowsePicker by remember { mutableStateOf(false) }

    // Progress tick while playing
    LaunchedEffect(session.isPlaying, isSeeking) {
        if (session.isPlaying && !isSeeking) {
            while (true) {
                delay(1000)
                currentProgress += 1f
            }
        }
    }

    // In-party YouTube picker to change or browse videos together
    if (showBrowsePicker) {
        WatchPartyYouTubePickerModal(
            onStart = { newVid, newTitle ->
                app?.coupleFeaturesRepository?.startWatchParty(newVid, newTitle, "")
                showBrowsePicker = false
            },
            onDismiss = { showBrowsePicker = false }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        val accent = MaterialTheme.colorScheme.primary
        val ink = MaterialTheme.colorScheme.onBackground
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = accent.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Synced Watch Party",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ink
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (session.isPlaying) accent else MaterialTheme.colorScheme.outline
                                ) {
                                    Text(
                                        text = if (session.isPlaying) "SYNCED • PLAYING" else "PAUSED",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = session.title.ifBlank { "Watching together with Partner 💕" },
                                fontSize = 12.sp,
                                color = ink.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Browse YouTube inside to pick or switch videos
                        FilledTonalButton(
                            onClick = { showBrowsePicker = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = accent.copy(alpha = 0.12f),
                                contentColor = accent
                            )
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Browse YT", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(ink.copy(alpha = 0.08f))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = ink)
                        }
                    }
                }

                // Video / Ambient Player Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (session.videoId.isNotBlank()) {
                        key(session.videoId) {
                            SyncedYouTubePlayer(
                                videoId = session.videoId,
                                isPlaying = session.isPlaying,
                                positionSeconds = currentProgress,
                                onPlayPauseToggle = { newState, pos ->
                                    onPlayPause(newState, pos)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Headphones,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Synced Ambient Audio",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "Listening together in synchronized harmony ✨",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // Bottom Synced Control Deck
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        // Synced Progress Slider
                        val minutes = (currentProgress / 60).toInt()
                        val seconds = (currentProgress % 60).toInt()
                        val timeStr = String.format("%02d:%02d", minutes, seconds)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = timeStr,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Sync,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Both phones in sync",
                                    fontSize = 11.sp,
                                    color = accent,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Slider(
                            value = currentProgress,
                            onValueChange = {
                                isSeeking = true
                                currentProgress = it
                            },
                            onValueChangeFinished = {
                                isSeeking = false
                                onSeek(currentProgress)
                            },
                            valueRange = 0f..600f,
                            colors = SliderDefaults.colors(
                                thumbColor = accent,
                                activeTrackColor = accent,
                                inactiveTrackColor = accent.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("watch_party_progress_slider")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Controls Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Rewind 10s
                            IconButton(
                                onClick = {
                                    val newPos = (currentProgress - 10f).coerceAtLeast(0f)
                                    currentProgress = newPos
                                    onSeek(newPos)
                                }
                            ) {
                                Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            // Shared Play / Pause Primary Button
                            FilledIconButton(
                                onClick = {
                                    val nextPlayState = !session.isPlaying
                                    onPlayPause(nextPlayState, currentProgress)
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent),
                                modifier = Modifier.size(56.dp).testTag("watch_party_play_pause_btn")
                            ) {
                                Icon(
                                    imageVector = if (session.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (session.isPlaying) "Pause" else "Play",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            // Forward 10s
                            IconButton(
                                onClick = {
                                    val newPos = currentProgress + 10f
                                    currentProgress = newPos
                                    onSeek(newPos)
                                }
                            ) {
                                Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            // Leave / End Party
                            TextButton(
                                onClick = {
                                    onEndSession()
                                    onDismiss()
                                }
                            ) {
                                Text("End Party", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
