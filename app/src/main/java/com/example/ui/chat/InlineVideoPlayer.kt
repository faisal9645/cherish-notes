package com.example.ui.chat

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.ui.security.SecretWindowGuard
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.ui.theme.DarkAubergine
import com.example.ui.theme.RoseGoldPrimary
import com.example.ui.theme.Graphite700
import com.example.ui.theme.Graphite800
import com.example.ui.theme.Graphite850
import com.example.ui.theme.Pink500
import com.example.ui.components.CherishToast
import com.example.ui.components.CherishButton
import com.example.ui.components.CherishTonalButton
import com.example.ui.components.pressScaleTarget
import com.example.ui.components.springClickable
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object YouTubeHelper {
    private val YOUTUBE_REGEX = Regex(
        """(?:https?://)?(?:www\.|m\.)?(?:youtube\.com/(?:watch\?(?:.*&)?v=|shorts/|embed/|live/|v/)|youtu\.be/)([a-zA-Z0-9_-]{11})""",
        RegexOption.IGNORE_CASE
    )

    data class YouTubeMetadata(
        val title: String,
        val authorName: String? = null
    )

    private val metadataCache = java.util.concurrent.ConcurrentHashMap<String, YouTubeMetadata>()

    fun getCachedMetadata(context: Context, videoId: String): YouTubeMetadata? {
        metadataCache[videoId]?.let { return it }
        val prefs = context.getSharedPreferences("cherish_yt_meta", Context.MODE_PRIVATE)
        val title = prefs.getString("${videoId}_title", null) ?: return null
        val author = prefs.getString("${videoId}_author", null)
        val meta = YouTubeMetadata(title, author)
        metadataCache[videoId] = meta
        return meta
    }

    suspend fun fetchMetadata(context: Context, videoId: String): YouTubeMetadata? = withContext(Dispatchers.IO) {
        getCachedMetadata(context, videoId)?.let { return@withContext it }
        try {
            val endpoint = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "Mozilla/5.0")
                instanceFollowRedirects = true
            }
            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                val title = json.optString("title").trim()
                val author = json.optString("author_name").trim().takeIf { it.isNotBlank() }
                if (title.isNotBlank()) {
                    val meta = YouTubeMetadata(title = title, authorName = author)
                    metadataCache[videoId] = meta
                    val prefs = context.getSharedPreferences("cherish_yt_meta", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString("${videoId}_title", title)
                        .putString("${videoId}_author", author)
                        .apply()
                    return@withContext meta
                }
            }
            conn.disconnect()
        } catch (_: Exception) {}
        null
    }

    fun extractVideoId(text: String): String? {
        val match = YOUTUBE_REGEX.find(text)
        return match?.groups?.get(1)?.value
    }

    fun getThumbnailUrl(videoId: String): String {
        return "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
    }

    fun getEmbedUrl(videoId: String): String {
        return "https://www.youtube.com/embed/$videoId?origin=https://www.youtube.com&autoplay=1&enablejsapi=1&fs=1&rel=0&playsinline=1&modestbranding=1"
    }

    fun getEmbedHtml(videoId: String): String {
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
                        src="https://www.youtube.com/embed/$videoId?origin=https://www.youtube-nocookie.com&autoplay=1&enablejsapi=1&fs=1&rel=0&playsinline=1&modestbranding=1"
                        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                        allowfullscreen>
                    </iframe>
                </div>
                <script>
                    function onYouTubeIframeAPIReady() {
                        new YT.Player('ytplayer', {
                            events: {
                                onStateChange: function(event) {
                                    if (window.CherishPlayback) {
                                        CherishPlayback.onPlaying(event.data === YT.PlayerState.PLAYING);
                                    }
                                },
                                onError: function() {
                                    if (window.CherishPlayback) CherishPlayback.onPlaying(false);
                                }
                            }
                        });
                    }
                </script>
                <script src="https://www.youtube.com/iframe_api"></script>
            </body>
            </html>
        """.trimIndent()
    }

    fun openInYouTube(context: Context, videoId: String) {
        try {
            val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId"))
            appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(appIntent)
        } catch (_: Exception) {
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId"))
                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(webIntent)
            } catch (_: Exception) {}
        }
    }
}

object GenericLinkHelper {
    private val URL_REGEX = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)

    fun extractUrl(text: String): String? {
        val url = URL_REGEX.find(text)?.value ?: return null
        if (YouTubeHelper.extractVideoId(url) != null) return null
        return url
    }

    fun extractDomain(url: String): String {
        return runCatching {
            Uri.parse(url).host?.removePrefix("www.") ?: url
        }.getOrDefault(url)
    }
}

/**
 * Embedded Hardware-Accelerated WebView player with Google Account / Cookie support
 * so users can watch YouTube inline and use their same account without leaving Cherish.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeWebView(
    videoId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val embedHtml = remember(videoId) { YouTubeHelper.getEmbedHtml(videoId) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var hasError by remember { mutableStateOf(false) }
    var youtubePlaying by remember(videoId) { mutableStateOf(false) }
    val playbackAlive = remember(videoId) { java.util.concurrent.atomic.AtomicBoolean(true) }
    ReportChatMediaActivity("youtube", active = youtubePlaying && !hasError)

    DisposableEffect(videoId) {
        onDispose {
            playbackAlive.set(false)
            webViewRef?.apply {
                removeJavascriptInterface("CherishPlayback")
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
                    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                    addJavascriptInterface(object {
                        @android.webkit.JavascriptInterface
                        fun onPlaying(playing: Boolean) {
                            mainHandler.post {
                                if (playbackAlive.get()) youtubePlaying = playing
                            }
                        }
                    }, "CherishPlayback")
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
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

                        // Standard modern desktop Chrome user-agent to bypass mobile embedded restrictions
                        userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                    }

                    // Enable cookie persistence for YouTube & Google login inside the app
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    webChromeClient = object : WebChromeClient() {
                        override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
                            super.onShowCustomView(view, callback)
                        }

                        override fun onHideCustomView() {
                            super.onHideCustomView()
                        }

                        override fun onPermissionRequest(request: PermissionRequest?) {
                            try {
                                request?.grant(request.resources)
                            } catch (_: Exception) {}
                        }

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
                                url.contains("ytimg.com")) {
                                return false // keep embed and its dependencies inside webview
                            }
                            // If user clicked "Watch on YouTube", channel link, or external intent
                            try {
                                val intent = if (url.startsWith("intent://")) {
                                    Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                                } else {
                                    Intent(Intent.ACTION_VIEW, uri)
                                }
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                view?.context?.startActivity(intent)
                                return true
                            } catch (_: Exception) {
                                return false
                            }
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
            update = { webView ->
                // ensure state remains active
            }
        )

        // Friendly 1-tap fallback overlay when the video owner disabled third-party embeds
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
                        imageVector = Icons.Rounded.Info,
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
                    CherishButton(
                        onClick = { YouTubeHelper.openInYouTube(context, videoId) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Watch in YouTube App", fontSize = 12.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

/** A small round button for the YouTube card's header; its touch area is exactly what's drawn. */
@Composable
private fun CardHeaderButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 18.dp
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(32.dp)
            .pressScaleTarget()
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .springClickable(
                onClickLabel = contentDescription,
                role = androidx.compose.ui.semantics.Role.Button,
                onClick = onClick
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Telegram/WhatsApp-style Inline YouTube Video Player Card
 */
@Composable
fun InlineYouTubeCard(
    videoId: String,
    onOpenTheater: (String) -> Unit,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit = {},
    openedByPartner: Boolean = false
) {
    val context = LocalContext.current
    var isPlayingInline by remember { mutableStateOf(false) }
    var metadata by remember(videoId) {
        mutableStateOf(YouTubeHelper.getCachedMetadata(context, videoId))
    }

    LaunchedEffect(videoId) {
        if (metadata == null) {
            val fetched = YouTubeHelper.fetchMetadata(context, videoId)
            if (fetched != null) {
                metadata = fetched
            }
        }
    }

    // Nothing keeps playing behind Notes once the chat hides
    val isDisguised by com.example.security.SecurityPreferences.getInstance(context).isDisguiseActive.collectAsState()
    LaunchedEffect(isDisguised) { if (isDisguised) isPlayingInline = false }

    // The play button pulses a few times when the card appears, then rests (every YouTube card
    // pulsing forever kept the screen redrawing)
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        repeat(3) {
            pulse.animateTo(1.08f, tween(1400, easing = FastOutSlowInEasing))
            pulse.animateTo(1f, tween(1400, easing = FastOutSlowInEasing))
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Graphite800,
        border = BorderStroke(
            width = 1.dp,
            color = Graphite700
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))) {
            // Header Bar & Title (Telegram style: Provider badge + Title + Action buttons)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Graphite850)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Red),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = metadata?.authorName?.let { "$it • YouTube" } ?: "YouTube",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Reuse the existing gap so the receipt never shifts the header actions.
                    Box(modifier = Modifier.width(8.dp), contentAlignment = Alignment.Center) {
                        if (openedByPartner) {
                            Canvas(
                                modifier = Modifier.size(6.dp)
                                    .semantics { contentDescription = "YouTube opened by partner" }
                            ) {
                                drawCircle(Color(0xFF4ADE80))
                            }
                        }
                    }

                    // Same-size round buttons, each tappable only where it's drawn
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Expand full screen icon on top with other icons
                        CardHeaderButton(
                            icon = Icons.Rounded.Fullscreen,
                            contentDescription = "Expand Fullscreen Theater",
                            iconSize = 20.dp,
                            modifier = Modifier.testTag("youtube_expand_fullscreen"),
                            onClick = {
                                isPlayingInline = false
                                onPlay()
                                onOpenTheater(videoId)
                            }
                        )

                        // Open the video in the YouTube app (or the browser without it)
                        CardHeaderButton(
                            icon = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = "Open in YouTube app",
                            iconSize = 16.dp,
                            modifier = Modifier.testTag("youtube_open_in_app"),
                            onClick = {
                                isPlayingInline = false
                                onPlay()
                                YouTubeHelper.openInYouTube(context, videoId)
                            }
                        )
                        CardHeaderButton(
                            icon = Icons.Rounded.BookmarkBorder,
                            contentDescription = "Save to Watch Later",
                            modifier = Modifier.testTag("youtube_watch_later"),
                            onClick = {
                                val app = context.applicationContext as com.example.CherishApplication
                                app.coupleFeaturesRepository.addWatchLaterVideo(
                                    videoId,
                                    metadata?.title ?: "Saved from chat"
                                )
                                CherishToast.show("Saved to Watch Later list")
                            }
                        )
                        if (isPlayingInline) {
                            CardHeaderButton(
                                icon = Icons.Rounded.Close,
                                contentDescription = "Close player",
                                iconSize = 16.dp,
                                modifier = Modifier.testTag("youtube_close_inline"),
                                onClick = { isPlayingInline = false }
                            )
                        }
                    }
                }

                val title = metadata?.title
                if (!title.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Player View / Thumbnail View (Full width 16:9 aspect ratio)
            if (isPlayingInline) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                ) {
                    YouTubeWebView(
                        videoId = videoId,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                // A tap plays it inline; "Theater" opens full view
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clickable { onPlay(); isPlayingInline = true },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = YouTubeHelper.getThumbnailUrl(videoId),
                        contentDescription = "YouTube Thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Dark gradient scrim
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.40f))
                    )

                    // Pristine circular play button with smooth pulse and zero corner glitch
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(64.dp)
                    ) {
                        // Soft outer pulse ring (strictly anti-aliased Canvas drawCircle - zero corner glitch)
                        Canvas(modifier = Modifier.size(64.dp)) {
                            drawCircle(
                                color = Color.Red.copy(alpha = 0.25f),
                                radius = (size.minDimension / 2f) * pulse.value
                            )
                        }

                        // Clean circular play button
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color.Red)
                                .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Play inline",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // Floating Bottom Action Bar
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "▶ Tap to play inline",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CherishTonalButton(
                                onClick = {
                                    // Watched together instead: the party's theater opens, this card stops
                                    val cherishApp = context.applicationContext as? com.example.CherishApplication
                                    cherishApp?.coupleFeaturesRepository?.startWatchParty(
                                        videoId,
                                        metadata?.title ?: "Shared video",
                                        ""
                                    )
                                    isPlayingInline = false
                                    com.example.ui.watchparty.WatchPartyUi.openTheater()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp).testTag("watch_party_sync_btn"),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Pink500,
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Sync 🍿", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            CherishTonalButton(
                                onClick = { onPlay(); onOpenTheater(videoId) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(28.dp)
                                    .testTag("youtube_play_theater"),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.25f),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Rounded.Fullscreen, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Theater", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * In-App Fullscreen Video Theater Modal
 * Rotates automatically to Landscape Full View for a complete cinema experience.
 */
@Composable
fun InlineVideoTheaterModal(
    videoId: String,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current
    val app = context.applicationContext as? com.example.CherishApplication
    val activity = remember(context) {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is Activity) return@remember ctx
            ctx = ctx.baseContext
        }
        null
    }

    DisposableEffect(activity) {
        app?.securityPreferences?.isTheaterModeActive = true
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = originalOrientation
            app?.securityPreferences?.isTheaterModeActive = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        val view = androidx.compose.ui.platform.LocalView.current
        SideEffect {
            val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
            if (window != null) {
                window.statusBarColor = android.graphics.Color.BLACK
                window.navigationBarColor = android.graphics.Color.BLACK
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
                window.setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        Surface(
            color = Color.Black,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // Full screen landscape video player: occupies full available screen
                YouTubeWebView(
                    videoId = videoId,
                    modifier = Modifier.fillMaxSize()
                )

                // Top Floating Bar (Landscape Mode): just the buttons, on the right
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .safeDrawingPadding()
                        .padding(horizontal = 32.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                    // Open in the YouTube app: the theater closes first, so leaving the app hides it
                    // as usual
                    IconButton(
                        onClick = {
                            onDismiss()
                            YouTubeHelper.openInYouTube(context, videoId)
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color.Black.copy(alpha = 0.75f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                            .testTag("theater_open_in_youtube")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = "Open in YouTube app",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // High-Visibility Floating Close Button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color.Black.copy(alpha = 0.75f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close Theater View",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    }
                }

                SecretWindowGuard()
            }
        }
    }
}

/**
 * Rich Web Link Preview Card for generic web URLs (WhatsApp / Telegram style)
 */
@Composable
fun LinkPreviewCard(
    url: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val domain = remember(url) { GenericLinkHelper.extractDomain(url) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.1f)),
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Link,
                contentDescription = null,
                tint = textColor.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = domain,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Text(
                    text = url,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = textColor.copy(alpha = 0.7f)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = "Open link",
                tint = textColor.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}




