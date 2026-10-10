package com.example.ui.watchparty

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.CherishApplication
import com.example.data.model.WatchPartySession
import com.example.data.repository.CoupleFeaturesRepository
import com.example.notifications.ThinkingOfYou
import com.example.ui.chat.WatchPartySetupDialog
import com.example.ui.chat.YouTubeHelper
import com.example.ui.components.AvatarView
import com.example.ui.security.EmergencyExitHandle
import com.example.ui.theme.OnlineGreen
import com.example.data.model.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** The quick reactions under the video. */
private val ReactionEmojis = listOf("❤️", "🍿", "😂", "🥺", "💋")

/** What may float up from a reaction (anything else shows as a heart). */
private val FloatableEmojis = ReactionEmojis.toSet() + setOf("💓", "💗")

/**
 * The Watch Party on this phone, above every screen of the app: full screen (the theater), a bar
 * at the top of the chat, or a small window on other screens. The video lives here, so it keeps
 * playing, in step, while it moves between them. It's gone behind Notes, behind the lock and while
 * the app is away, and comes back at the very second the party is at.
 */
@Composable
fun WatchPartyHost(app: CherishApplication, allowed: Boolean) {
    val repo = app.coupleFeaturesRepository
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val liveParty by repo.watchPartyFlow.collectAsState()
    val me by app.authRepository.currentUserState.collectAsState()
    val partnerUser by app.authRepository.partnerUserState.collectAsState()
    val myId = me?.id.orEmpty()
    val partnerId = partnerUser?.id ?: me?.partnerId
    val partnerName = partnerUser?.displayName?.ifBlank { null } ?: "Your love"

    val party = rememberSteadyParty(liveParty?.takeIf { it.isActive })
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val onScreen = lifecycleState.isAtLeast(Lifecycle.State.STARTED)

    val mode = WatchPartyUi.mode
    val docked = mode == WatchPartyMode.Mini && WatchPartyUi.chatOnScreen && !WatchPartyUi.chatBlocked
    val floating = mode == WatchPartyMode.Mini && !WatchPartyUi.chatOnScreen
    val shown = allowed && onScreen && party != null
    val showPlayer = shown && (mode == WatchPartyMode.Theater || docked || floating)
    SideEffect { WatchPartyUi.playerVisible = showPlayer }

    LaunchedEffect(mode) {
        if (mode != WatchPartyMode.Theater) {
            var ctx = context
            while (ctx is android.content.ContextWrapper && ctx !is android.app.Activity) {
                ctx = ctx.baseContext
            }
            (ctx as? android.app.Activity)?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    var pickVideo by remember { mutableStateOf(false) }

    // Behind Notes or the lock nothing of it shows; it comes back small, not full screen
    LaunchedEffect(allowed) {
        if (!allowed) {
            pickVideo = false
            if (WatchPartyUi.mode == WatchPartyMode.Theater) WatchPartyUi.minimize()
        }
    }
    // The party ended (my partner ended it, or it went idle): it closes here too
    LaunchedEffect(party == null) {
        if (party == null && WatchPartyUi.mode != WatchPartyMode.Hidden) {
            WatchPartyUi.close()
            if (allowed) Toast.makeText(context, "Movie Date has ended", Toast.LENGTH_SHORT).show()
        }
    }

    // While it's on screen here, my partner's phone knows I'm watching
    LaunchedEffect(showPlayer, party?.startedAt) {
        if (!showPlayer) return@LaunchedEffect
        while (true) {
            repo.markWatchingParty(true)
            delay(60_000)
        }
    }
    var joinedAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(showPlayer) { if (showPlayer) joinedAt = System.currentTimeMillis() }
    // Whether my partner's watching goes stale with time, so it's looked at again now and then
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(showPlayer) {
        if (!showPlayer) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(10_000)
        }
    }
    val partnerWatching = party?.isWatchedBy(partnerId, now) == true

    val player = remember(party?.videoId) { WatchPartyPlayer() }
    if (showPlayer && party != null) KeepInSync(party, player, repo, myId)

    // Reactions, heartbeats and what my partner did, over the video
    val bursts = remember { mutableStateListOf<Burst>() }
    var heartNote by remember { mutableStateOf<HeartNote?>(null) }
    var chip by remember { mutableStateOf<EventChip?>(null) }
    val sender = remember(repo) { ReactionSender(repo, scope) }
    LaunchedEffect(heartNote) {
        if (heartNote != null) {
            delay(2_800)
            heartNote = null
        }
    }
    LaunchedEffect(chip) {
        if (chip != null) {
            delay(2_400)
            chip = null
        }
    }
    LaunchedEffect(party?.reactionId) {
        val p = party ?: return@LaunchedEffect
        if (!showPlayer || p.reactionId.isEmpty() || p.reactionBy == myId) return@LaunchedEffect
        if (p.reactionAt < joinedAt - 2_000 || System.currentTimeMillis() - p.reactionAt > 15_000) return@LaunchedEffect
        val emoji = p.reactionEmoji.takeIf { it in FloatableEmojis } ?: "❤️"
        repeat(p.reactionCount.coerceIn(1, 8)) {
            bursts.spawn(emoji)
            delay(110)
        }
    }
    LaunchedEffect(party?.lastHeartburstAt) {
        val p = party ?: return@LaunchedEffect
        if (!showPlayer || p.lastHeartburstAt == 0L || p.lastHeartburstBy == myId) return@LaunchedEffect
        if (p.lastHeartburstAt < joinedAt - 2_000 || System.currentTimeMillis() - p.lastHeartburstAt > 15_000) return@LaunchedEffect
        ThinkingOfYou.playHeartbeat(context)
        heartNote = HeartNote(fromPartner = true, at = System.currentTimeMillis())
        repeat(4) {
            bursts.spawn("💗")
            delay(150)
        }
    }
    // Play voice snippets from the partner
    LaunchedEffect(party?.voiceSnippetId) {
        val p = party ?: return@LaunchedEffect
        if (!showPlayer || p.voiceSnippetId.isEmpty() || p.voiceSnippetBy == myId) return@LaunchedEffect
        
        // Lower video volume
        player.setVolume(20)
        
        val playerHelper = app.voicePlayerHelper
        playerHelper.playAudio(p.voiceSnippetId, p.voiceSnippetData) {
            // Restore video volume when done
            player.setVolume(100)
        }
    }

    // A line when my partner joins or leaves, plays, pauses, skips, or picks another video
    var seenWatching by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(partnerWatching, showPlayer) {
        if (!showPlayer) {
            seenWatching = null
            return@LaunchedEffect
        }
        val before = seenWatching
        seenWatching = partnerWatching
        if (before != null && before != partnerWatching) {
            chip = EventChip(if (partnerWatching) "$partnerName joined 💕" else "$partnerName left")
        }
    }
    var seenState by remember { mutableStateOf<WatchPartySession?>(null) }
    LaunchedEffect(party?.updatedAt, showPlayer) {
        val p = party
        val before = seenState
        seenState = p
        if (!showPlayer || p == null || before == null) return@LaunchedEffect
        if (p.updatedBy != partnerId || p.updatedAt == before.updatedAt) return@LaunchedEffect
        chip = EventChip(
            when {
                p.videoId != before.videoId -> "$partnerName picked “${p.title}”"
                p.isPlaying != before.isPlaying -> if (p.isPlaying) "$partnerName pressed play ▶" else "$partnerName paused ⏸"
                else -> "$partnerName skipped to ${clock(p.positionSeconds)}"
            }
        )
    }

    fun positionNow(p: WatchPartySession) = if (player.isReady) player.currentTime else p.positionAt()
    fun togglePlay() {
        val p = party ?: return
        repo.updateWatchPartyPlayback(!p.isPlaying, positionNow(p))
    }
    fun seek(to: Float) {
        val p = party ?: return
        val end = player.duration
        repo.updateWatchPartyPlayback(p.isPlaying, if (end > 1f) to.coerceIn(0f, end - 0.5f) else to.coerceAtLeast(0f))
    }
    fun react(emoji: String) {
        bursts.spawn(emoji)
        sender.send(emoji)
    }
    fun heartbeat() {
        repo.sendWatchPartyHeartburst()
        heartNote = HeartNote(fromPartner = false, at = System.currentTimeMillis())
        repeat(3) { bursts.spawn("💓") }
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
    fun leave() {
        WatchPartyUi.close()
        repo.leaveWatchParty()
    }

    if (showPlayer && mode == WatchPartyMode.Theater) {
        // Back makes it small (it keeps playing), like closing the theater door behind you
        BackHandler { WatchPartyUi.minimize() }
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(Unit) { keyboard?.hide() }
    }

    // Where the video goes: the theater's place for it, the chat's bar, or the small window
    var hostOrigin by remember { mutableStateOf(Offset.Zero) }
    var hostSize by remember { mutableStateOf(IntSize.Zero) }
    var theaterSlot by remember { mutableStateOf<Rect?>(null) }
    var dockSlot by remember { mutableStateOf<Rect?>(null) }
    var floatSlot by remember { mutableStateOf<Rect?>(null) }
    val target = when {
        mode == WatchPartyMode.Theater -> theaterSlot
        docked -> dockSlot
        else -> floatSlot
    }?.translate(-hostOrigin)
    val videoRect = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    var placed by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    
    LaunchedEffect(controlsVisible, party?.isPlaying) {
        if (controlsVisible && party?.isPlaying == true) {
            delay(3500)
            controlsVisible = false
        }
    }

    LaunchedEffect(target, showPlayer) {
        if (!showPlayer) {
            placed = false
            return@LaunchedEffect
        }
        val t = target ?: return@LaunchedEffect
        val c = videoRect.value
        val far = abs(c.left - t.left) + abs(c.top - t.top) + abs(c.width - t.width) + abs(c.height - t.height) > 48f
        if (!placed || !far) {
            // Follows small moves (dragging, the chat settling) exactly; glides between places
            videoRect.snapTo(t)
            placed = true
        } else {
            videoRect.animateTo(t, spring(dampingRatio = 0.9f, stiffness = 420f))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                hostOrigin = it.positionInRoot()
                hostSize = it.size
            }
    ) {
        if (shown && party != null) {
            val dockBounds = WatchPartyUi.chatDockBounds
            val myBuffering = party.bufferingBy == myId
            val partnerBuffering = party.bufferingBy.isNotEmpty() && party.bufferingBy != myId
            val myAd = party.adBy == myId
            val partnerAd = party.adBy.isNotEmpty() && party.adBy != myId

            if (docked && dockBounds != null) {
                DockBar(
                    bounds = dockBounds.translate(-hostOrigin),
                    party = party,
                    partnerName = partnerName,
                    partnerWatching = partnerWatching,
                    myBuffering = myBuffering,
                    partnerBuffering = partnerBuffering,
                    myAd = myAd,
                    partnerAd = partnerAd,
                    onVideoSlot = { dockSlot = it },
                    onTogglePlay = { togglePlay() },
                    onReact = { react(it) },
                    onExpand = { WatchPartyUi.openTheater() },
                    onLeave = { leave() }
                )
            }
            if (floating) {
                FloatingMini(
                    hostSize = hostSize,
                    party = party,
                    myBuffering = myBuffering,
                    partnerBuffering = partnerBuffering,
                    myAd = myAd,
                    partnerAd = partnerAd,
                    partnerName = partnerName,
                    onVideoSlot = { floatSlot = it },
                    onTogglePlay = { togglePlay() },
                    onExpand = { WatchPartyUi.openTheater() },
                    onLeave = { leave() }
                )
            }
            AnimatedVisibility(
                visible = mode == WatchPartyMode.Theater,
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(180))
            ) {
                Theater(
                    party = party,
                    player = player,
                    partnerName = partnerName,
                    partnerWatching = partnerWatching,
                    partnerPhoto = partnerUser?.photoUrl,
                    myBuffering = myBuffering,
                    partnerBuffering = partnerBuffering,
                    myAd = myAd,
                    partnerAd = partnerAd,
                    onVideoSlot = { theaterSlot = it },
                    onMinimize = { WatchPartyUi.minimize() },
                    onChatWhileWatching = {
                        WatchPartyUi.minimize()
                        WatchPartyUi.openChat()
                    },
                    onLeave = { leave() },
                    onEndForBoth = {
                        WatchPartyUi.close()
                        repo.endWatchParty()
                    },
                    onPickVideo = { pickVideo = true },
                    onReact = { react(it) },
                    onTogglePlay = { togglePlay() },
                    onSeek = { seek(it) },
                    onSpeedChange = { repo.updateWatchPartySpeed(it) },
                    onSendVoice = { repo.sendWatchPartyVoiceSnippet(it) },
                    onSendWhisper = { repo.sendWatchPartyWhisper(it) },
                    controlsVisible = controlsVisible
                )
            }
            if (showPlayer && placed) {
                VideoLayer(
                    rect = videoRect.value,
                    party = party,
                    player = player,
                    compact = mode != WatchPartyMode.Theater,
                    bursts = bursts,
                    heartNote = heartNote,
                    chip = chip,
                    partnerName = partnerName,
                    partnerTyping = partnerUser?.typingInChat == true,
                    onSingleTap = { controlsVisible = !controlsVisible },
                    onDoubleTap = { heartbeat() },
                    onPickAnother = { pickVideo = true }
                )
            }
        }


        if (pickVideo && allowed) {
            WatchPartySetupDialog(
                onStart = { videoId, title ->
                    repo.startWatchParty(videoId, title, "")
                    pickVideo = false
                },
                onDismiss = { pickVideo = false }
            )
        }
    }
}

/** The party that's on, held a moment if it blinks out (an older read while a new one is saved). */
@Composable
private fun rememberSteadyParty(live: WatchPartySession?): WatchPartySession? {
    var steady by remember { mutableStateOf(live) }
    LaunchedEffect(live) {
        if (live == null) delay(1_500)
        steady = live
    }
    return live ?: steady
}

/**
 * Keeps this phone's video with the party: when it's ready, on every change either of us makes,
 * and every few seconds while playing (a phone that fell behind catches up). A tap on the video
 * itself (play, pause) or its end goes to the party once it settles.
 */
@Composable
private fun KeepInSync(party: WatchPartySession, player: WatchPartyPlayer, repo: CoupleFeaturesRepository, myId: String) {
    val latest by rememberUpdatedState(party)
    LaunchedEffect(player.isReady, party.videoId, party.isPlaying, party.positionSeconds, party.updatedAt) {
        if (!player.isReady) return@LaunchedEffect
        var pass = 0
        while (true) {
            val p = latest
            val end = player.duration
            var target = p.positionAt().coerceAtLeast(0f)
            if (end > 1f) target = target.coerceAtMost(end - 0.3f)
            val over = end > 1f && player.state == YtState.ENDED && target >= end - 1f
            val off = abs(player.currentTime - target)
            // Right away when anything changed; later only if it drifted. A phone still loading is
            // left to load (seeking it again only starts the loading over), and an ad isn't touched.
            val seek = !over && player.state != YtState.AD && when {
                pass == 0 -> off > 0.8f
                player.state == YtState.PLAYING -> off > 1.5f
                player.state == YtState.PAUSED -> off > 0.8f
                else -> false
            }
            val now = com.example.data.repository.ServerTime.now()
            val shouldPlay = p.isPlaying && p.playScheduledAt <= now
            if (seek) player.seekTo(target)
            if (player.playbackRate != p.playbackRate) player.setPlaybackRate(p.playbackRate)
            
            if (shouldPlay) {
                if (!over && player.state != YtState.PLAYING && player.state != YtState.BUFFERING && player.state != YtState.AD) player.play()
            } else if (player.state != YtState.AD && (seek || player.state == YtState.PLAYING || player.state == YtState.BUFFERING)) {
                // A seek starts a video that hasn't played yet, so it's paused again right after (but don't pause an ad)
                player.pause()
            }
            pass++
            // Paused, it's checked twice more while it settles, then left alone
            if (!shouldPlay && pass >= 3) {
                if (p.isPlaying && p.playScheduledAt > now) {
                    val waitTime = p.playScheduledAt - now
                    if (waitTime > 0) delay(waitTime)
                } else {
                    break
                }
            } else {
                delay(if (shouldPlay) 4_000 else 1_200)
            }
        }
    }
    val action = player.lastVideoAction
    LaunchedEffect(action) {
        action ?: return@LaunchedEffect
        delay(450) // a double tap toggles twice, and then nothing changed
        if (player.state != action.state) return@LaunchedEffect
        val p = latest
        when (action.state) {
            // Loading or an ad here is only said (my partner's phone shows it); the party itself
            // goes on, and this phone catches up once it plays. Pausing it for both left it paused.
            YtState.BUFFERING, YtState.AD ->
                repo.markWatchPartyWaiting(isBuffering = action.state == YtState.BUFFERING, isAd = action.state == YtState.AD)
            YtState.ENDED ->
                if (p.queue.isNotEmpty()) {
                    repo.dequeueWatchPartyVideo(p.videoId)
                } else if (p.isPlaying) {
                    repo.updateWatchPartyPlayback(false, player.currentTime)
                }
            else -> {
                val playing = action.state == YtState.PLAYING
                if (playing != p.isPlaying) {
                    repo.updateWatchPartyPlayback(playing, player.currentTime)
                } else if (p.bufferingBy == myId || p.adBy == myId) {
                    repo.markWatchPartyWaiting(isBuffering = false, isAd = false)
                }
            }
        }
    }
}

/** The video at [rect], with what floats over it. */
@Composable
private fun VideoLayer(
    rect: Rect,
    party: WatchPartySession,
    player: WatchPartyPlayer,
    compact: Boolean,
    bursts: SnapshotStateList<Burst>,
    heartNote: HeartNote?,
    chip: EventChip?,
    partnerName: String,
    partnerTyping: Boolean,
    onSingleTap: (() -> Unit)? = null,
    onDoubleTap: () -> Unit,
    onPickAnother: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as com.example.CherishApplication
    val myId = app.authRepository.getCurrentUserId()
    val density = LocalDensity.current
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val doubleTap = remember { { currentOnDoubleTap() } }
    Box(
        modifier = Modifier
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
            .clip(RoundedCornerShape(if (compact) 10.dp else 14.dp))
            .background(Color.Black)
            .observeTaps(onSingleTap = onSingleTap, onDoubleTap = doubleTap)
            .testTag("watch_party_video")
    ) {
        key(party.videoId) {
            WatchPartyVideo(
                player = player,
                videoId = party.videoId,
                startAt = party.positionAt(),
                autoplay = party.isPlaying,
                modifier = Modifier.fillMaxSize()
            )
        }
        BurstLayer(bursts = bursts, compact = compact, modifier = Modifier.fillMaxSize())

        if (!player.isReady && player.errorCode == 0) {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.85f),
                strokeWidth = 2.5.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(if (compact) 20.dp else 32.dp)
            )
        }

        var countdownText by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(party.playScheduledAt) {
            while (true) {
                val rem = party.playScheduledAt - com.example.data.repository.ServerTime.now()
                if (rem > 0 && party.isPlaying) {
                    val sec = (rem / 1000).toInt()
                    countdownText = if (sec > 0) "$sec" else "Go!"
                    delay(50)
                } else {
                    if (countdownText == "Go!") delay(500)
                    countdownText = null
                    break
                }
            }
        }
        
        AnimatedVisibility(
            visible = countdownText != null,
            enter = fadeIn(tween(150)) + scaleIn(initialScale = 1.5f),
            exit = fadeOut(tween(300)) + scaleOut(targetScale = 2f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text(
                text = countdownText.orEmpty(),
                color = Color.White,
                fontSize = if (compact) 48.sp else 72.sp,
                fontWeight = FontWeight.Bold,
                style = androidx.compose.ui.text.TextStyle(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.5f),
                        blurRadius = 8f
                    )
                )
            )
        }

        val shownChip = rememberLast(chip)
        AnimatedVisibility(
            visible = chip != null,
            enter = fadeIn(tween(180)) + slideInVertically { -it / 2 },
            exit = fadeOut(tween(220)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (compact) 4.dp else 10.dp)
        ) {
            OverlayPill(shownChip?.text.orEmpty(), compact)
        }

        val shownNote = rememberLast(heartNote)
        AnimatedVisibility(
            visible = heartNote != null,
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.85f),
            exit = fadeOut(tween(260)),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 8.dp)
        ) {
            val note = shownNote
            OverlayPill(
                text = when {
                    note == null -> ""
                    note.fromPartner -> "💓 $partnerName: Thinking of you in this scene ✨"
                    else -> "💓 Heartbeat sent"
                },
                compact = compact
            )
        }
        var activeWhisper by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(party.whisperId) {
            if (party.whisperId.isNotEmpty() && party.whisperBy != myId) {
                activeWhisper = party.whisperText
                delay(5000)
                activeWhisper = null
            }
        }
        
        AnimatedVisibility(
            visible = activeWhisper != null,
            enter = fadeIn(tween(300)) + slideInVertically { it / 2 },
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (compact) 16.dp else 32.dp)
        ) {
            Text(
                text = activeWhisper.orEmpty(),
                color = Color.White,
                fontSize = if (compact) 14.sp else 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        AnimatedVisibility(
            visible = partnerTyping,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = if (compact) 16.dp else 32.dp)
        ) {
            Text(
                text = "$partnerName is typing...",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = if (compact) 12.sp else 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        if (player.errorCode != 0) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.86f))
                    .padding(10.dp)
            ) {
                Text(
                    text = if (compact) "Can't play here" else "This video can't play inside the app",
                    color = Color.White,
                    fontSize = if (compact) 11.sp else 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                if (!compact) {
                    // Most refusals already moved on to YouTube's own page; what's left is said here
                    val reason = when (player.errorCode) {
                        100 -> "It's private or was removed."
                        2 -> "The link doesn't point to a video."
                        -1 -> "YouTube wants a sign-in for this one (an age limit, or its \"not a bot\" check)."
                        else -> "YouTube won't play it here."
                    }
                    Text(reason, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, textAlign = TextAlign.Center)
                    Row(modifier = Modifier.padding(top = 6.dp)) {
                        TextButton(onClick = onPickAnother) { Text("Pick another") }
                        if (player.errorCode == -1) {
                            // Shows YouTube's page as it is, with its own Sign in button
                            TextButton(onClick = { player.errorCode = 0 }) { Text("Sign in") }
                        }
                        TextButton(onClick = { YouTubeHelper.openInYouTube(context, party.videoId) }) { Text("Open YouTube") }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayPill(text: String, compact: Boolean) {
    Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.6f)) {
        Text(
            text = text,
            color = Color.White,
            fontSize = if (compact) 10.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = if (compact) 8.dp else 14.dp, vertical = if (compact) 3.dp else 7.dp)
        )
    }
}

/** The last non-null [value], so a line can fade out after it's cleared. */
@Composable
private fun <T : Any> rememberLast(value: T?): T? {
    val last = remember { mutableStateOf(value) }
    SideEffect { if (value != null) last.value = value }
    return value ?: last.value
}

/** A black box the video is laid over; reports where it is in the window. */
@Composable
private fun VideoSlot(modifier: Modifier, corner: Dp, onSlot: (Rect) -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(Color.Black)
            .onGloballyPositioned { c -> onSlot(Rect(c.positionInRoot(), c.size.toSize())) }
    )
}

/** Full screen: the video, reactions, the shared controls, and the ways out. */
@Composable
private fun Theater(
    party: WatchPartySession,
    player: WatchPartyPlayer,
    partnerName: String,
    partnerWatching: Boolean,
    partnerPhoto: String?,
    myBuffering: Boolean,
    partnerBuffering: Boolean,
    myAd: Boolean,
    partnerAd: Boolean,
    onVideoSlot: (Rect) -> Unit,
    onMinimize: () -> Unit,
    onChatWhileWatching: () -> Unit,
    onLeave: () -> Unit,
    onEndForBoth: () -> Unit,
    onPickVideo: () -> Unit,
    onReact: (String) -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSendVoice: (String) -> Unit,
    onSendWhisper: (String) -> Unit,
    controlsVisible: Boolean
) {
    val ink = MaterialTheme.colorScheme.onBackground
    var showWhisperDialog by remember { mutableStateOf(false) }

    if (showWhisperDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showWhisperDialog = false },
            title = { Text("Send a whisper") },
            text = { 
                OutlinedTextField(
                    value = text, 
                    onValueChange = { text = it }, 
                    placeholder = { Text("A short message...") }
                )
            },
            confirmButton = {
                TextButton(onClick = { 
                    if (text.isNotBlank()) onSendWhisper(text)
                    showWhisperDialog = false 
                }) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = { showWhisperDialog = false }) { Text("Cancel") }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Keeps touches off the screen underneath
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .testTag("watch_party_theater")
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            val header = @Composable {
                TheaterHeader(party, partnerName, partnerWatching, partnerPhoto, myBuffering, partnerBuffering, myAd, partnerAd, onMinimize, { showWhisperDialog = true }, onPickVideo, onLeave)
            }
            if (maxWidth > maxHeight) {
                Box(modifier = Modifier.fillMaxSize()) {
                    VideoSlot(
                        modifier = Modifier.fillMaxSize(),
                        corner = 0.dp,
                        onSlot = onVideoSlot
                    )
                    androidx.compose.animation.AnimatedVisibility(
                        visible = controlsVisible,
                        enter = androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .widthIn(max = 340.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(8.dp)
                            ) {
                                header()
                                ReactionBar(onReact = onReact, onSendVoice = onSendVoice, buttonSize = 42.dp)
                                Spacer(modifier = Modifier.height(8.dp))
                                PlaybackDeck(party, player, onTogglePlay, onSeek, onSpeedChange)
                                TheaterActions(onChatWhileWatching, onEndForBoth)
                            }
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    header()
                    VideoSlot(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                        corner = 14.dp,
                        onSlot = onVideoSlot
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "Double-tap the video to send a heartbeat 💓",
                        fontSize = 12.sp,
                        color = ink.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    ReactionBar(onReact = onReact, onSendVoice = onSendVoice, buttonSize = 54.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    PlaybackDeck(party, player, onTogglePlay, onSeek, onSpeedChange)
                    TheaterActions(onChatWhileWatching, onEndForBoth)
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun TheaterHeader(
    party: WatchPartySession,
    partnerName: String,
    partnerWatching: Boolean,
    partnerPhoto: String?,
    myBuffering: Boolean,
    partnerBuffering: Boolean,
    myAd: Boolean,
    partnerAd: Boolean,
    onMinimize: () -> Unit,
    onWhisperClick: () -> Unit,
    onPickVideo: () -> Unit,
    onLeave: () -> Unit
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val accent = MaterialTheme.colorScheme.primary
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMinimize, modifier = Modifier.testTag("watch_party_minimize")) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Make small", tint = ink)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Movie Date 🍿", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ink)
                Text(
                    text = party.title,
                    fontSize = 12.sp,
                    color = ink.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onWhisperClick, modifier = Modifier.testTag("watch_party_whisper")) {
                Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "Whisper", tint = ink)
            }
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            IconButton(
                onClick = {
                    var act = ctx
                    while (act is android.content.ContextWrapper && act !is android.app.Activity) {
                        act = act.baseContext
                    }
                    (act as? android.app.Activity)?.requestedOrientation = if (isLandscape) {
                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    } else {
                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
                    }
                },
                modifier = Modifier.testTag("watch_party_fullscreen")
            ) {
                Icon(if (isLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, contentDescription = "Toggle Fullscreen", tint = ink)
            }
            IconButton(onClick = onPickVideo, modifier = Modifier.testTag("watch_party_pick")) {
                Icon(Icons.Default.VideoLibrary, contentDescription = "Pick another video", tint = accent)
            }
            IconButton(onClick = onLeave, modifier = Modifier.testTag("watch_party_leave")) {
                Icon(Icons.Default.Close, contentDescription = "Leave", tint = ink)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(start = 14.dp, top = 2.dp, bottom = 10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = if (party.isPlaying && !myBuffering && !partnerBuffering && !myAd && !partnerAd) accent else MaterialTheme.colorScheme.outline
            ) {
                Text(
                    text = when {
                        partnerAd -> "⏳ $partnerName is watching an ad…"
                        myAd -> "⏳ Ad playing…"
                        partnerBuffering -> "⏳ Waiting for $partnerName…"
                        myBuffering -> "⏳ Buffering…"
                        party.isPlaying -> "▶ Playing in sync"
                        else -> "⏸ Paused"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                )
            }
            AvatarView(photoUrl = partnerPhoto, name = partnerName, size = 22.dp, showOnlineBadge = false, showRing = false)
            Text(
                text = if (partnerWatching) "$partnerName is watching" else "Waiting for $partnerName…",
                fontSize = 12.sp,
                fontWeight = if (partnerWatching) FontWeight.SemiBold else FontWeight.Normal,
                color = if (partnerWatching) OnlineGreen else ink.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ReactionBar(onReact: (String) -> Unit, onSendVoice: (String) -> Unit, buttonSize: Dp) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        ReactionEmojis.forEach { emoji -> ReactionButton(emoji, buttonSize) { onReact(emoji) } }
        VoiceSnippetButton(buttonSize, onSendVoice)
    }
}

@Composable
private fun VoiceSnippetButton(size: Dp, onSendVoice: (String) -> Unit) {
    val context = LocalContext.current
    val helper = (context.applicationContext as com.example.CherishApplication).voiceRecorderHelper
    val isRecording by helper.isRecording.collectAsState()
    
    val scope = rememberCoroutineScope()
    val bounce = remember { Animatable(1f) }
    
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = bounce.value
                scaleY = bounce.value
            }
            .clip(CircleShape)
            .background(if (isRecording) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            scope.launch { bounce.animateTo(1.2f) }
                            helper.startRecording()
                            try { awaitRelease() } finally {
                                scope.launch { bounce.animateTo(1f) }
                                val (file, duration) = helper.stopRecording()
                                if (file != null && duration > 0) {
                                    val bytes = file.readBytes()
                                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                                    onSendVoice("data:audio/m4a;base64,$base64")
                                    file.delete()
                                }
                            }
                        } else {
                            android.widget.Toast.makeText(context, "Microphone permission needed", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
    ) {
        Icon(
            Icons.Default.Mic, 
            contentDescription = "Hold to talk", 
            tint = if (isRecording) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

@Composable
private fun ReactionButton(emoji: String, size: Dp, onClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    val bounce = remember { Animatable(1f) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = bounce.value
                scaleY = bounce.value
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable {
                scope.launch {
                    bounce.snapTo(0.8f)
                    bounce.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 600f))
                }
                onClick()
            }
            .testTag("watch_party_react")
    ) {
        Text(emoji, fontSize = (size.value * 0.46f).sp)
    }
}

/** The shared controls: where it is, play / pause and skipping, for both phones. */
@Composable
private fun PlaybackDeck(
    party: WatchPartySession,
    player: WatchPartyPlayer,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val position = if (player.isReady) player.currentTime else party.positionAt()
    val total = player.duration
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shownPosition = dragging ?: position
    val max = if (total > 1f) total else maxOf(shownPosition + 60f, 60f)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        Slider(
            value = shownPosition.coerceIn(0f, max),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let(onSeek)
                dragging = null
            },
            valueRange = 0f..max,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = accent.copy(alpha = 0.2f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("watch_party_progress_slider")
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(clock(shownPosition), fontSize = 12.sp, color = muted)
            Text(if (total > 1f) clock(total) else "--:--", fontSize = 12.sp, color = muted)
        }
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            IconButton(onClick = { onSeek((position - 10f).coerceAtLeast(0f)) }) {
                Icon(Icons.Default.Replay10, contentDescription = "Back 10 seconds", tint = muted)
            }
            Spacer(modifier = Modifier.width(24.dp))
            FilledIconButton(
                onClick = onTogglePlay,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent),
                modifier = Modifier
                    .size(60.dp)
                    .testTag("watch_party_play_pause_btn")
            ) {
                Icon(
                    imageVector = if (party.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (party.isPlaying) "Pause for both" else "Play for both",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(modifier = Modifier.width(24.dp))
            IconButton(onClick = { onSeek(position + 10f) }) {
                Icon(Icons.Default.Forward10, contentDescription = "Forward 10 seconds", tint = muted)
            }
            Spacer(modifier = Modifier.width(16.dp))
            var speedMenu by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { speedMenu = true }) {
                    Text("${party.playbackRate}x", color = muted, fontWeight = FontWeight.Bold)
                }
                androidx.compose.material3.DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                    listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { rate ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("${rate}x") },
                            onClick = { onSpeedChange(rate); speedMenu = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TheaterActions(onChatWhileWatching: () -> Unit, onEndForBoth: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        FilledTonalButton(
            onClick = onChatWhileWatching,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .weight(1f)
                .height(46.dp)
                .testTag("watch_party_chat_while_watching")
        ) {
            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Chat while watching", fontWeight = FontWeight.SemiBold)
        }
        TextButton(onClick = onEndForBoth, modifier = Modifier.testTag("watch_party_end")) {
            Text("End for both", color = MaterialTheme.colorScheme.error)
        }
    }
}

/** At the top of the chat ([bounds], in the host): the video, what's on, and quick controls. */
@Composable
private fun DockBar(
    bounds: Rect,
    party: WatchPartySession,
    partnerName: String,
    partnerWatching: Boolean,
    myBuffering: Boolean,
    partnerBuffering: Boolean,
    myAd: Boolean,
    partnerAd: Boolean,
    onVideoSlot: (Rect) -> Unit,
    onTogglePlay: () -> Unit,
    onReact: (String) -> Unit,
    onExpand: () -> Unit,
    onLeave: () -> Unit
) {
    val density = LocalDensity.current
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        shadowElevation = 6.dp,
        modifier = Modifier
            .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
            .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
            .testTag("watch_party_dock")
            .clickable { onExpand() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(7.dp)
        ) {
            VideoSlot(
                modifier = Modifier.fillMaxHeight().aspectRatio(16f / 9f, matchHeightConstraintsFirst = true),
                corner = 10.dp,
                onSlot = onVideoSlot
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
            ) {
                Text(
                    text = party.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = (when {
                        partnerAd -> "⏳ $partnerName is watching an ad…"
                        myAd -> "⏳ Ad playing…"
                        partnerBuffering -> "⏳ Waiting for $partnerName…"
                        myBuffering -> "⏳ Buffering…"
                        party.isPlaying -> "▶ Playing together"
                        else -> "⏸ Paused"
                    }) + (if (partnerWatching) " · 👀 $partnerName" else ""),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MiniButton(
                        icon = if (party.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        label = if (party.isPlaying) "Pause for both" else "Play for both",
                        tint = accent,
                        onClick = onTogglePlay
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable { onReact("❤️") }
                    ) { Text("❤️", fontSize = 17.sp) }
                    MiniButton(Icons.Default.OpenInFull, "Full screen", ink, onExpand)
                    MiniButton(Icons.Default.Close, "Leave", ink, onLeave)
                }
            }
        }
    }
}

/** On screens other than the chat: a small window that can be dragged out of the way. */
@Composable
private fun FloatingMini(
    hostSize: IntSize,
    party: WatchPartySession,
    myBuffering: Boolean,
    partnerBuffering: Boolean,
    myAd: Boolean,
    partnerAd: Boolean,
    partnerName: String,
    onVideoSlot: (Rect) -> Unit,
    onTogglePlay: () -> Unit,
    onExpand: () -> Unit,
    onLeave: () -> Unit
) {
    val density = LocalDensity.current
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    var dragged by remember { mutableStateOf<Offset?>(null) }
    val margin = with(density) { 12.dp.toPx() }
    val top = WindowInsets.statusBars.getTop(density) + margin
    val bottom = hostSize.height - WindowInsets.navigationBars.getBottom(density) - margin
    fun clamp(o: Offset) = Offset(
        o.x.coerceIn(margin, (hostSize.width - cardSize.width - margin).coerceAtLeast(margin)),
        o.y.coerceIn(top, (bottom - cardSize.height).coerceAtLeast(top))
    )
    // Starts low on the right, above the bottom bar
    val at = clamp(
        dragged ?: Offset(
            hostSize.width - cardSize.width - margin,
            bottom - cardSize.height - with(density) { 84.dp.toPx() }
        )
    )
    val latestAt by rememberUpdatedState(at)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        shadowElevation = 10.dp,
        modifier = Modifier
            .offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }
            .width(196.dp)
            .onSizeChanged { cardSize = it }
            .pointerInput(hostSize) {
                detectDragGestures { change, drag ->
                    change.consume()
                    dragged = clamp(latestAt + drag)
                }
            }
            .testTag("watch_party_floating")
            .clickable { onExpand() }
    ) {
        Column {
            VideoSlot(
                modifier = Modifier
                    .padding(start = 6.dp, end = 6.dp, top = 6.dp)
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                corner = 10.dp,
                onSlot = onVideoSlot
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 2.dp)
            ) {
                MiniButton(
                    icon = if (party.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    label = if (party.isPlaying) "Pause for both" else "Play for both",
                    tint = accent,
                    onClick = onTogglePlay
                )
                Text(
                    text = when {
                        partnerAd -> "$partnerName is watching an ad…"
                        myAd -> "Ad playing…"
                        partnerBuffering -> "Waiting for $partnerName…"
                        myBuffering -> "Buffering…"
                        else -> party.title
                    },
                    fontSize = 11.sp,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                MiniButton(Icons.Default.OpenInFull, "Full screen", ink, onExpand)
                MiniButton(Icons.Default.Close, "Leave", ink, onLeave)
            }
        }
    }
}

@Composable
private fun MiniButton(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** An emoji floating up over the video. */
private data class Burst(
    val id: Long,
    val emoji: String,
    val x: Float,
    val drift: Float,
    val sizeSp: Float,
    val rise: Float
)

private var lastBurstId = 0L

private fun MutableList<Burst>.spawn(emoji: String) {
    if (size >= 40) removeAt(0)
    add(
        Burst(
            id = ++lastBurstId,
            emoji = emoji,
            x = 0.15f + Random.nextFloat() * 0.7f,
            drift = Random.nextFloat() * 2f - 1f,
            sizeSp = 24f + Random.nextFloat() * 12f,
            rise = 0.7f + Random.nextFloat() * 0.3f
        )
    )
}

/** The heartbeat line over the video: mine going out, or my partner's arriving. */
private data class HeartNote(val fromPartner: Boolean, val at: Long)

/** A short line over the video about what my partner did ("Faisal paused ⏸"). */
private data class EventChip(val text: String, val at: Long = System.currentTimeMillis())

@Composable
private fun BurstLayer(bursts: SnapshotStateList<Burst>, compact: Boolean, modifier: Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        for (b in bursts) {
            key(b.id) {
                FloatingEmoji(b, w, h, compact) { bursts.remove(b) }
            }
        }
    }
}

@Composable
private fun FloatingEmoji(b: Burst, w: Float, h: Float, compact: Boolean, onDone: () -> Unit) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(if (compact) 1_800 else 2_600, easing = LinearEasing))
        onDone()
    }
    Text(
        text = b.emoji,
        fontSize = (if (compact) b.sizeSp * 0.62f else b.sizeSp).sp,
        modifier = Modifier.graphicsLayer {
            val p = t.value
            translationX = b.x * w - size.width / 2f +
                sin(p * 7f + b.drift * 3f) * w * 0.035f + b.drift * p * w * 0.1f
            translationY = h - size.height - p * h * b.rise
            alpha = minOf(p / 0.12f, (1f - p) / 0.3f, 1f).coerceIn(0f, 1f)
            val grow = 0.5f + 0.5f * minOf(p / 0.18f, 1f)
            scaleX = grow
            scaleY = grow
        }
    )
}

/** Sees double taps without taking the taps from the video under it (YouTube still gets them). */
private fun Modifier.observeTaps(onSingleTap: (() -> Unit)? = null, onDoubleTap: () -> Unit): Modifier = pointerInput(Unit) {
    var lastTapAt = 0L
    var lastTapPosition = Offset.Zero
    var singleTapJob: Job? = null
    val scope = CoroutineScope(Dispatchers.Main)
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var up: PointerInputChange? = null
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.size > 1) break
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.changedToUpIgnoreConsumed()) {
                up = change
                break
            }
            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
        }
        val tap = up ?: return@awaitEachGesture
        if (tap.uptimeMillis - down.uptimeMillis > 350) return@awaitEachGesture
        if (tap.uptimeMillis - lastTapAt < 320 && (tap.position - lastTapPosition).getDistance() < 64.dp.toPx()) {
            singleTapJob?.cancel()
            lastTapAt = 0L
            onDoubleTap()
        } else {
            lastTapAt = tap.uptimeMillis
            lastTapPosition = tap.position
            if (onSingleTap != null) {
                singleTapJob?.cancel()
                singleTapJob = scope.launch {
                    delay(320)
                    onSingleTap()
                }
            }
        }
    }
}

/** Sends reactions as they're tapped, about two a second at most: quicker taps go out together. */
private class ReactionSender(private val repo: CoupleFeaturesRepository, private val scope: CoroutineScope) {
    private val pending = LinkedHashMap<String, Int>()
    private var job: Job? = null

    fun send(emoji: String) {
        pending[emoji] = (pending[emoji] ?: 0) + 1
        if (job?.isActive == true) return
        job = scope.launch {
            while (pending.isNotEmpty()) {
                val emojiToSend = pending.keys.first()
                val count = pending.remove(emojiToSend) ?: 0
                repo.sendWatchPartyReaction(emojiToSend, count.coerceIn(1, 8))
                delay(500)
            }
        }
    }
}

/** 75.4 → "1:15", 3725 → "1:02:05". */
private fun clock(seconds: Float): String {
    val s = seconds.coerceAtLeast(0f).toInt()
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
}
