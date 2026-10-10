package com.example.ui.watchparty

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.util.Locale

/** A YouTube video id: 11 letters, digits, - or _. Anything else never reaches the page. */
private val VideoIdPattern = Regex("[A-Za-z0-9_-]{11}")

private const val MOBILE_CHROME_UA =
    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

/** YouTube's player states. */
internal object YtState {
    const val UNSTARTED = -1
    const val ENDED = 0
    const val PLAYING = 1
    const val PAUSED = 2
    const val BUFFERING = 3
}

/** A play, pause or the end that came from the video itself (a tap on it), not from the party. */
internal data class VideoAction(val state: Int, val time: Float, val at: Long)

/**
 * The YouTube player on this phone: what it's doing, and play / pause / seek. One per video. The
 * party drives it (see [WatchPartyHost]); taps on the video itself come back as [lastVideoAction].
 */
@Stable
internal class WatchPartyPlayer {
    var webView: WebView? = null
    var isReady by mutableStateOf(false)
    var state by mutableIntStateOf(YtState.UNSTARTED)
    var currentTime by mutableFloatStateOf(0f)
    var duration by mutableFloatStateOf(0f)

    /** YouTube's error: 2 bad id, 5 can't play here, 100 gone or private, 101 / 150 not allowed outside YouTube. */
    var errorCode by mutableIntStateOf(0)
    var lastVideoAction by mutableStateOf<VideoAction?>(null)

    /** Until when state changes are our own commands coming back, not a tap on the video. */
    private var commandsUntil = 0L

    fun play() = command("player.playVideo()", COMMAND_ECHO_MS)

    fun pause() = command("player.pauseVideo()", COMMAND_ECHO_MS)

    /** A seek can buffer for a while, and starts a video that hasn't played yet. */
    fun seekTo(seconds: Float) {
        currentTime = seconds
        command("player.seekTo(" + String.format(Locale.US, "%.2f", seconds) + ", true)", SEEK_ECHO_MS)
    }

    private fun command(js: String, echoMs: Long) {
        commandsUntil = maxOf(commandsUntil, SystemClock.uptimeMillis() + echoMs)
        webView?.evaluateJavascript("try{if(isReady){$js;}}catch(e){}", null)
    }

    fun onState(newState: Int, time: Float) {
        val previous = state
        state = newState
        if (time > 0f) currentTime = time
        if (newState != previous && newState in TAP_STATES && SystemClock.uptimeMillis() > commandsUntil) {
            lastVideoAction = VideoAction(newState, currentTime, SystemClock.uptimeMillis())
        }
    }

    private companion object {
        const val COMMAND_ECHO_MS = 1500L
        const val SEEK_ECHO_MS = 3000L
        val TAP_STATES = setOf(YtState.ENDED, YtState.PLAYING, YtState.PAUSED)
    }
}

/** The page's calls back into the app; they come on a background thread. */
private class PlayerBridge(private val player: WatchPartyPlayer) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onReady(duration: Double) {
        main.post {
            if (duration > 0) player.duration = duration.toFloat()
            player.isReady = true
        }
    }

    @JavascriptInterface
    fun onState(state: Int, time: Double, duration: Double) {
        main.post {
            if (duration > 0) player.duration = duration.toFloat()
            player.onState(state, time.toFloat())
        }
    }

    @JavascriptInterface
    fun onTime(time: Double, state: Int) {
        main.post {
            player.currentTime = time.toFloat()
            if (state != player.state) player.onState(state, time.toFloat())
        }
    }

    @JavascriptInterface
    fun onError(code: Int) {
        main.post { player.errorCode = code }
    }
}

/**
 * The video, in YouTube's own player without its controls (ours drive it, so a stray tap on
 * theirs can't put the phones out of step). It starts at [startAt], playing if [autoplay].
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WatchPartyVideo(
    player: WatchPartyPlayer,
    videoId: String,
    startAt: Float,
    autoplay: Boolean,
    modifier: Modifier = Modifier
) {
    // Loaded once for this video; from then on the party drives it through [player]
    val page = remember(videoId) {
        videoId.takeIf { VideoIdPattern.matches(it) }
            ?.let { playerPage(it, startAt.toInt().coerceAtLeast(0), autoplay) }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                player.webView = this
                setBackgroundColor(android.graphics.Color.BLACK)
                // The screen stays on while watching
                keepScreenOn = true
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.userAgentString = MOBILE_CHROME_UA
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                addJavascriptInterface(PlayerBridge(player), "Party")
                webChromeClient = object : WebChromeClient() {
                    // No grey play-button poster before the video shows
                    override fun getDefaultVideoPoster(): Bitmap =
                        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                }
                webViewClient = object : WebViewClient() {
                    // Stays on the player: links in it (the YouTube logo, end screens) don't take it away
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                        request?.isForMainFrame == true
                }
                if (page != null) {
                    loadDataWithBaseURL("https://www.youtube.com", page, "text/html", "UTF-8", null)
                } else {
                    player.errorCode = 2
                }
            }
        },
        onRelease = { webView ->
            if (player.webView === webView) player.webView = null
            player.isReady = false
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
    )
}

/** YouTube's IFrame player, reporting back to [PlayerBridge] as "Party". */
private fun playerPage(videoId: String, startSeconds: Int, autoplay: Boolean): String = """
<!DOCTYPE html>
<html><head>
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<style>
html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #000; overflow: hidden; }
#player { position: absolute; top: 0; left: 0; width: 100%; height: 100%; border: 0; }
</style>
</head><body>
<div id="player"></div>
<script>
var player = null, isReady = false;
var tag = document.createElement('script');
tag.src = 'https://www.youtube.com/iframe_api';
document.head.appendChild(tag);
function onYouTubeIframeAPIReady() {
  player = new YT.Player('player', {
    width: '100%', height: '100%', videoId: '$videoId',
    playerVars: {
      autoplay: ${if (autoplay) 1 else 0}, start: $startSeconds, playsinline: 1, controls: 0,
      disablekb: 1, fs: 0, rel: 0, modestbranding: 1, iv_load_policy: 3, enablejsapi: 1,
      origin: 'https://www.youtube.com'
    },
    events: {
      onReady: function () {
        isReady = true;
        try { Party.onReady(player.getDuration() || 0); } catch (e) {}
        setInterval(tick, 500);
      },
      onStateChange: function (e) {
        try { Party.onState(e.data, player.getCurrentTime() || 0, player.getDuration() || 0); } catch (err) {}
      },
      onError: function (e) {
        try { Party.onError(e.data); } catch (err) {}
      }
    }
  });
}
function tick() {
  try { Party.onTime(player.getCurrentTime() || 0, player.getPlayerState()); } catch (e) {}
}
</script>
</body></html>
"""
