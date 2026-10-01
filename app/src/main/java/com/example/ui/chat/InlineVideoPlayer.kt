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
        """(?:https?://)?(?:www\.|m\.)?(?:youtube\.com/(?:watch\?v=|shorts/|embed/)|youtu\.be/)([a-zA-Z0-9_-]{11})""",
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
        return "https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&enablejsapi=1&fs=1&rel=0&modestbranding=1"
    }

    fun getEmbedHtml(videoId: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; background: #000; }
                    html, body { width: 100%; height: 100%; overflow: hidden; background: #000; display: flex; align-items: center; justify-content: center; }
                    iframe { width: 100%; height: 100%; border: none; }
                </style>
            </head>
            <body>
                <iframe 
                    src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&enablejsapi=1&fs=1&rel=0&playsinline=1"
                    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                    allowfullscreen>
                </iframe>
            </body>
            </html>
        """.trimIndent()
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
    val embedHtml = remember(videoId) { YouTubeHelper.getEmbedHtml(videoId) }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(android.graphics.Color.BLACK)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    allowFileAccess = false
                    loadsImagesAutomatically = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }

                // Enable cookie persistence for YouTube & Google login inside the app
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        return false // keep video navigation inside webview
                    }
                }

                loadDataWithBaseURL("https://www.youtube.com", embedHtml, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            // ensure state remains active
        }
    )
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
    var isPlayingInline by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF18181B),
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
                        text = "YouTube • Inline Player",
                        color = MaterialTheme.colorScheme.surface,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isPlayingInline) {
                        TextButton(
                            onClick = { isPlayingInline = false },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("✕ Close", color = Color(0xFFA1A1AA), fontSize = 11.sp)
                        }
                    } else {
                        IconButton(
                            onClick = { onOpenTheater(videoId) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Theater Mode",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Player View / Thumbnail View
            if (isPlayingInline) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
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
                        .height(180.dp)
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
                            .background(Color.Black.copy(alpha = 0.65f))
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

/**
 * In-App Fullscreen Video Theater Modal
 * Keeps the user strictly within Cherish while enjoying shared videos in high resolution.
 */
@Composable
fun InlineVideoTheaterModal(
    videoId: String,
    onDismiss: () -> Unit
) {
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

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF27272A), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
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
                    text = "Watching together in Cherish • No external app required 💕",
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



