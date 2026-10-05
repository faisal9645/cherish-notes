package com.example.ui.chat

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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

object YouTubeHelper {
    private val YOUTUBE_REGEX = Regex(
        """(?:https?://)?(?:www\.|m\.)?(?:youtube\.com/(?:watch\?(?:.*&)?v=|shorts/|embed/|live/|v/)|youtu\.be/)([a-zA-Z0-9_-]{11})""",
        RegexOption.IGNORE_CASE
    )

    fun extractVideoId(text: String): String? {
        val match = YOUTUBE_REGEX.find(text)
        return match?.groups?.get(1)?.value
    }

    fun getThumbnailUrl(videoId: String): String {
        return "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
    }

    fun getEmbedUrl(videoId: String): String {
        return "https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&enablejsapi=1&fs=1&rel=0&playsinline=1&modestbranding=1"
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
                        src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&enablejsapi=1&fs=1&rel=0&playsinline=1&modestbranding=1"
                        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                        allowfullscreen>
                    </iframe>
                </div>
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

                        // Standard modern mobile Chrome user-agent for smooth YouTube playback
                        userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                    }

                    // Enable cookie persistence for YouTube & Google login inside the app
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    webChromeClient = object : WebChromeClient() {
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
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Watch in YouTube App", fontSize = 12.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * Telegram/WhatsApp-style Inline YouTube Video Player Card
 */
@Composable
fun InlineYouTubeCard(
    videoId: String,
    onOpenTheater: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlayingInline by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.onBackground,
        border = BorderStroke(1.dp, Color(0xFF27272A)),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF202024))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(Color(0xFFFF0000), RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "YouTube • Player",
                        color = MaterialTheme.colorScheme.surface,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { YouTubeHelper.openInYouTube(context, videoId) },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Watch in YouTube App",
                            tint = Color(0xFFA1A1AA),
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    IconButton(
                        onClick = { onOpenTheater(videoId) },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Theater Mode",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (isPlayingInline) {
                        TextButton(
                            onClick = { isPlayingInline = false },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("✕ Close", color = Color(0xFFA1A1AA), fontSize = 11.sp)
                        }
                    }
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clickable { isPlayingInline = true },
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
                            .background(Color.Black.copy(alpha = 0.35f))
                    )

                    // Glowing YouTube Red Play Button
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color(0xFFFF0000), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play Inline",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Floating Bottom Action Bar
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.70f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "▶ Tap to play inline",
                            color = MaterialTheme.colorScheme.surface,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { YouTubeHelper.openInYouTube(context, videoId) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFFFF0000).copy(alpha = 0.25f),
                                    contentColor = Color.White
                                )
                            ) {
                                Text("▶ App", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            FilledTonalButton(
                                onClick = { onOpenTheater(videoId) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                                    contentColor = Color.White
                                )
                            ) {
                                Text("⛶ Theater", fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
 * Keeps the user strictly within Cherish while enjoying shared videos in high resolution.
 */
@Composable
fun InlineVideoTheaterModal(
    videoId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color.Black,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top control bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Movie, contentDescription = null, tint = Color(0xFFFF0000), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cherish Theater",
                            color = MaterialTheme.colorScheme.surface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { YouTubeHelper.openInYouTube(context, videoId) },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFF27272A), CircleShape)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "Watch in YouTube App", tint = Color.White, modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFF27272A), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // Centered Player
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .aspectRatio(16f / 9f)
                        .padding(horizontal = 8.dp)
                ) {
                    YouTubeWebView(
                        videoId = videoId,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Bottom Hint
                Text(
                    text = "Watching together in Cherish • Tap ↗ to open in YouTube app",
                    color = Color(0xFFA1A1AA),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp)
                )
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Link,
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
                imageVector = Icons.Default.OpenInNew,
                contentDescription = "Open link",
                tint = textColor.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}




