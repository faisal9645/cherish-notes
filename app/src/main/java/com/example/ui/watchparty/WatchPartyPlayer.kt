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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.util.Locale

/** A YouTube video id: 11 letters, digits, - or _. Anything else never reaches the page. */
private val VideoIdPattern = Regex("[A-Za-z0-9_-]{11}")

private const val DESKTOP_CHROME_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

/** For YouTube's own mobile page (m.youtube.com). */
private const val MOBILE_CHROME_UA =
    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

/**
 * The embedded player's errors that YouTube's own page can't help with either: 2 not a video, 100
 * private or removed. Every other refusal (5, 101, 150, 152, 153, and any new one) moves it there.
 */
private val NoWatchPageCodes = setOf(2, 100)

/** YouTube's player states. */
internal object YtState {
    const val UNSTARTED = -1
    const val ENDED = 0
    const val PLAYING = 1
    const val PAUSED = 2
    const val BUFFERING = 3
    const val AD = 4
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
    var playbackRate by mutableFloatStateOf(1f)
        internal set

    /**
     * YouTube's error: 2 bad id, 5 can't play here, 100 gone or private, 101 / 150 not allowed
     * outside YouTube; -1 not even YouTube's own page would play it (a sign-in it wants, say).
     */
    var errorCode by mutableIntStateOf(0)
    var lastVideoAction by mutableStateOf<VideoAction?>(null)

    /** The embedded player was refused, so it plays on YouTube's own mobile page instead. */
    var onWatchPage by mutableStateOf(false)
        private set

    /** Until when state changes are our own commands coming back, not a tap on the video. */
    private var commandsUntil = 0L

    fun play() = command("__party.play()", COMMAND_ECHO_MS)

    fun pause() = command("__party.pause()", COMMAND_ECHO_MS)

    /** A seek can buffer for a while, and starts a video that hasn't played yet. */
    fun seekTo(seconds: Float) {
        currentTime = seconds
        command("__party.seek(" + String.format(Locale.US, "%.2f", seconds) + ")", SEEK_ECHO_MS)
    }

    fun setPlaybackRate(rate: Float) {
        if (playbackRate == rate) return
        playbackRate = rate
        command("__party.rate(" + String.format(Locale.US, "%.2f", rate) + ")", COMMAND_ECHO_MS)
    }

    fun setVolume(volume: Int) {
        command("__party.volume($volume)", 0L)
    }

    /** Both pages (the embed, YouTube's own) answer the same commands as `__party`. */
    private fun command(js: String, echoMs: Long) {
        commandsUntil = maxOf(commandsUntil, SystemClock.uptimeMillis() + echoMs)
        webView?.evaluateJavascript("try{if(window.__party){$js;}}catch(e){}", null)
    }

    fun onError(code: Int) {
        if (!onWatchPage && code !in NoWatchPageCodes) switchToWatchPage() else errorCode = code
    }

    /** Plays it on YouTube's own page from here on (what the embed was refused, that still plays). */
    fun switchToWatchPage() {
        if (onWatchPage) return
        onWatchPage = true
        isReady = false
        errorCode = 0
        state = YtState.UNSTARTED
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
        val TAP_STATES = setOf(YtState.ENDED, YtState.PLAYING, YtState.PAUSED, YtState.BUFFERING, YtState.AD)
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
            // Playing after all (say, once signed in on YouTube's page): no error left showing
            player.errorCode = 0
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
        main.post { player.onError(code) }
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
    // The embed was refused: YouTube's own page, from about where the party is
    LaunchedEffect(player.onWatchPage) {
        if (!player.onWatchPage) return@LaunchedEffect
        val view = player.webView ?: return@LaunchedEffect
        view.settings.userAgentString = MOBILE_CHROME_UA
        val from = (if (player.currentTime > 0f) player.currentTime else startAt).toInt().coerceAtLeast(0)
        view.loadUrl("https://m.youtube.com/watch?v=$videoId&t=${from}s")
    }
    // Fall back to YouTube mobile watch page if the embed is blocked or doesn't start playing within 4.5 seconds
    LaunchedEffect(videoId, autoplay) {
        delay(4_500)
        if (page != null && !player.onWatchPage && autoplay && player.state != YtState.PLAYING) {
            player.switchToWatchPage()
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                player.webView = this
                setBackgroundColor(android.graphics.Color.BLACK)
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                // The screen stays on while watching
                keepScreenOn = true
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    allowFileAccess = true
                    allowContentAccess = true
                    loadsImagesAutomatically = true
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                    userAgentString = DESKTOP_CHROME_UA
                }
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)
                addJavascriptInterface(PlayerBridge(player), "Party")
                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                        try {
                            request?.grant(request.resources)
                        } catch (_: Exception) {}
                    }

                    // No grey play-button poster before the video shows
                    override fun getDefaultVideoPoster(): Bitmap =
                        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val uri = request?.url ?: return false
                        val url = uri.toString()
                        val host = uri.host.orEmpty()
                        val isYouTubeOrGoogle = host.endsWith("youtube.com") || host.endsWith("google.com") || host.endsWith("youtube-nocookie.com")
                        if (url.contains("/embed/") || isYouTubeOrGoogle) {
                            if (player.onWatchPage && uri.path == "/watch" && uri.getQueryParameter("v") != null && uri.getQueryParameter("v") != videoId) {
                                return true
                            }
                            return false
                        }
                        return true
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        if (player.onWatchPage && view != null && url.orEmpty().contains("youtube.com/watch")) {
                            view.evaluateJavascript(watchPageScript(videoId, autoplay), null)
                        }
                    }
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
window.__party = {
  ready: false,
  play: function () {
    if (player && player.playVideo) {
      try { player.playVideo(); } catch (e) {}
    }
  },
  pause: function () { if (player && player.pauseVideo) player.pauseVideo(); },
  seek: function (t) { if (player && player.seekTo) player.seekTo(t, true); },
  rate: function (r) { if (player && player.setPlaybackRate) player.setPlaybackRate(r); },
  volume: function (v) { if (player && player.setVolume) player.setVolume(v); }
};
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
        window.__party.ready = true;
        try { Party.onReady(player.getDuration() || 0); } catch (e) {}
        if (${if (autoplay) "true" else "false"}) {
          try { player.playVideo(); } catch (e) {}
        }
        setInterval(tick, 500);
      },
      onStateChange: function (e) {
        var state = e.data;
        try {
          var data = player.getVideoData();
          var url = player.getVideoUrl ? player.getVideoUrl() : "";
          if (data && data.video_id && data.video_id !== '$videoId') state = 4;
          else if (url && url.indexOf('$videoId') === -1) state = 4;
        } catch(err) {}
        try { Party.onState(state, player.getCurrentTime() || 0, player.getDuration() || 0); } catch (err) {}
      },
      onError: function (e) {
        try { Party.onError(e.data); } catch (err) {}
      }
    }
  });
}
function tick() {
  if (!player || !player.getPlayerState) return;
  var state = player.getPlayerState();
  try {
    var data = player.getVideoData();
    var url = player.getVideoUrl ? player.getVideoUrl() : "";
    if (data && data.video_id && data.video_id !== '$videoId') state = 4;
    else if (url && url.indexOf('$videoId') === -1) state = 4;
  } catch(err) {}
  try { Party.onTime(player.getCurrentTime() || 0, state); } catch (e) {}
}
</script>
</body></html>
"""

/**
 * Runs on YouTube's own mobile page: shows only the video, full size, and answers the same
 * `__party` commands as the embed, reporting back to [PlayerBridge] as "Party".
 */
private fun watchPageScript(videoId: String, autoplay: Boolean): String = """
(function () {
  if (window.__partyInstalled) return;
  window.__partyInstalled = true;
  var VID = '$videoId';
  var css = document.createElement('style');
  css.textContent =
    'html, body { margin: 0 !important; padding: 0 !important; width: 100% !important; height: 100% !important; background: #000 !important; overflow: hidden !important; }' +
    '#player-container-id, .player-container, ytm-player, #movie_player, .html5-video-player {' +
    ' position: fixed !important; top: 0 !important; left: 0 !important; width: 100% !important; height: 100% !important;' +
    ' max-height: none !important; margin: 0 !important; transform: none !important; z-index: 10 !important; background: transparent !important; }' +
    'video {' +
    ' position: fixed !important; top: 0 !important; left: 0 !important; width: 100% !important; height: 100% !important;' +
    ' object-fit: contain !important; z-index: 100 !important; background: #000 !important; }' +
    'ytm-mobile-topbar-renderer, header, ytm-pivot-bar-renderer, ytm-companion-slot,' +
    ' .mobile-topbar-header, ytm-item-section-renderer, ytm-watch-metadata, .ytm-watch-metadata,' +
    ' ytm-engagement-panel-section-list-renderer, ytm-comment-section-renderer, .eom-button-row { display: none !important; }';
  (document.head || document.documentElement).appendChild(css);

  try {
    var consentBtn = document.querySelector('form[action*="consent"] button, button[aria-label*="Agree"], button[aria-label*="Accept"]');
    if (consentBtn) consentBtn.click();
  } catch(e) {}
  function v() { return document.querySelector('video'); }
  function ad() { return !!document.querySelector('.ad-showing, .ad-interrupting'); }
  function ours() { return location.href.indexOf(VID) !== -1; }
  function stateOf(el) {
    if (!el) return -1;
    if (ad() || !ours()) return 4;
    if (el.ended) return 0;
    if (el.paused) return 2;
    if (!el.paused && (el.currentTime > 0 || el.readyState >= 3)) return 1;
    if (el.readyState < 3) return 3;
    return 1;
  }
  function tryTriggerPlay() {
    var el = v();
    if (el) {
      try {
        el.muted = false;
        var p = el.play();
        if (p && p.catch) {
          p.catch(function(e) {
            try { el.muted = true; el.play(); } catch(err) {}
          });
        }
      } catch(e) {}
    }
    var selectors = [
      '.ytp-large-play-button',
      '.player-control-play-pause-icon',
      'button[aria-label*="Play"]',
      '.ytp-play-button',
      '#player-control-overlay'
    ];
    for (var i = 0; i < selectors.length; i++) {
      var btn = document.querySelector(selectors[i]);
      if (btn) {
        try { btn.click(); } catch(e) {}
      }
    }
    var player = document.getElementById('movie_player');
    if (player && typeof player.playVideo === 'function') {
      try { player.playVideo(); } catch(e) {}
    }
  }
  window.__party = {
    ready: true,
    play: function () {
      tryTriggerPlay();
    },
    pause: function () {
      var el = v();
      if (el) { try { el.pause(); } catch(e) {} }
      var player = document.getElementById('movie_player');
      if (player && typeof player.pauseVideo === 'function') {
        try { player.pauseVideo(); } catch(e) {}
      }
    },
    seek: function (t) {
      var el = v();
      if (el) { try { el.currentTime = t; } catch(e) {} }
      var player = document.getElementById('movie_player');
      if (player && typeof player.seekTo === 'function') {
        try { player.seekTo(t, true); } catch(e) {}
      }
    },
    rate: function (r) {
      var el = v();
      if (el) { try { el.playbackRate = r; } catch(e) {} }
      var player = document.getElementById('movie_player');
      if (player && typeof player.setPlaybackRate === 'function') {
        try { player.setPlaybackRate(r); } catch(e) {}
      }
    },
    volume: function (x) {
      var el = v();
      if (el) { try { el.volume = Math.max(0, Math.min(1, x / 100)); } catch(e) {} }
    }
  };
  function report() {
    var el = v();
    try { Party.onState(stateOf(el), el ? el.currentTime : 0, el && isFinite(el.duration) ? el.duration : 0); } catch (e) {}
  }
  var watched = null, away = 0;
  var reportedReady = false;

  function checkReady() {
    var el = v();
    var player = document.getElementById('movie_player');
    if ((el || player) && !reportedReady && ours()) {
      reportedReady = true;
      window.__party.ready = true;
      var dur = (el && isFinite(el.duration) && el.duration > 0) ? el.duration :
                (player && typeof player.getDuration === 'function' ? player.getDuration() : 0);
      try { Party.onReady(dur); } catch (e) {}
      if (${if (autoplay) "true" else "false"}) {
        tryTriggerPlay();
      }
    }
  }

  setInterval(function () {
    var el = v();
    if (el && el !== watched) {
      watched = el;
      ['playing', 'pause', 'waiting', 'ended', 'seeked', 'durationchange', 'loadedmetadata'].forEach(function (n) {
        el.addEventListener(n, report);
      });
    }
    checkReady();
    away = ours() ? 0 : away + 1;
    if (away > 4) location.replace('https://m.youtube.com/watch?v=' + VID);

    if (${if (autoplay) "true" else "false"}) {
      var cur = v();
      if (cur && cur.paused && ours() && !ad()) {
        tryTriggerPlay();
      }
    }

    if (el) {
      try { Party.onTime(el.currentTime || 0, stateOf(el)); } catch (e) {}
    }
  }, 400);

  setTimeout(function () {
    if (!reportedReady) {
      checkReady();
      if (!reportedReady) {
        try { Party.onError(-1); } catch (e) {}
      }
    }
  }, 25000);
})();
"""
