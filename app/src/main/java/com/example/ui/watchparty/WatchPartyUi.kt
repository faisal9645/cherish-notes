package com.example.ui.watchparty

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.example.CherishApplication
import com.example.data.model.WatchPartySession

/** How the Watch Party shows on this phone: not at all, full screen, or small while doing other things. */
enum class WatchPartyMode { Hidden, Theater, Mini }

/** The bar at the top of the chat while watching there. */
internal val DockHeight = 92.dp

/**
 * The Watch Party's place on this phone, shared by the screens and [WatchPartyHost]: whether it's
 * open full screen or small, and where the chat keeps a place for it at its top.
 */
object WatchPartyUi {
    var mode by mutableStateOf(WatchPartyMode.Hidden)
        private set

    /** Whether the video is on screen right now (the host says so). */
    var playerVisible by mutableStateOf(false)
        internal set

    /** The chat is on screen; [chatBlocked] when it's private or curtained, so nothing of it shows. */
    var chatOnScreen by mutableStateOf(false)
        internal set
    var chatBlocked by mutableStateOf(false)
        internal set

    /** The chat's place for the small player (in the window), while it keeps one. */
    var chatDockBounds by mutableStateOf<Rect?>(null)
        internal set

    /** The party whose invite was put away here ([inviteKey]), so it doesn't come back. */
    var dismissedInvite by mutableLongStateOf(0L)
        internal set

    /** Shows the chat; set by the nav graph. */
    var openChat: () -> Unit = {}

    fun openTheater() {
        mode = WatchPartyMode.Theater
    }

    fun minimize() {
        mode = WatchPartyMode.Mini
    }

    fun close() {
        mode = WatchPartyMode.Hidden
    }
}

/** One key per party (changing its video keeps it), for putting its invite away. */
internal val WatchPartySession.inviteKey: Long
    get() = startedAt.takeIf { it > 0L } ?: videoId.hashCode().toLong()

/**
 * At the top of the chat: the small player's place while watching there, or else the invite to
 * join a party that's on. [blocked] (private chat, the curtain) shows neither.
 */
@Composable
fun ChatWatchPartySlot(blocked: Boolean) {
    DisposableEffect(Unit) {
        WatchPartyUi.chatOnScreen = true
        onDispose {
            WatchPartyUi.chatOnScreen = false
            WatchPartyUi.chatDockBounds = null
        }
    }
    SideEffect { WatchPartyUi.chatBlocked = blocked }

    val docked = !blocked && WatchPartyUi.mode == WatchPartyMode.Mini && WatchPartyUi.playerVisible
    AnimatedVisibility(
        visible = docked,
        enter = expandVertically(tween(260, easing = FastOutSlowInEasing)) + fadeIn(tween(200)),
        exit = shrinkVertically(tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(160))
    ) {
        // Kept free for the bar the host draws here (the video lives in the host, so it keeps playing)
        DisposableEffect(Unit) { onDispose { WatchPartyUi.chatDockBounds = null } }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .height(DockHeight)
                .onGloballyPositioned { c ->
                    WatchPartyUi.chatDockBounds = Rect(c.positionInRoot(), c.size.toSize())
                }
        )
    }
    if (!blocked) {
        WatchPartyInvite(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
    }
}

/**
 * A glowing pill while a party is on and this phone isn't watching it:
 * 🍿 Faisal started "Cozy Lofi Rain" • [Join Together]. Joining opens it at the very second it's at.
 */
@Composable
fun WatchPartyInvite(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as CherishApplication }
    val party by app.coupleFeaturesRepository.watchPartyFlow.collectAsState()
    val me by app.authRepository.currentUserState.collectAsState()
    val partner by app.authRepository.partnerUserState.collectAsState()

    val live = party?.takeIf { it.isActive }
    // Shown on the way out too, after the party is gone
    val lastLive = remember { mutableStateOf<WatchPartySession?>(null) }
    SideEffect { if (live != null) lastLive.value = live }
    val visible = live != null &&
        WatchPartyUi.mode == WatchPartyMode.Hidden &&
        WatchPartyUi.dismissedInvite != live.inviteKey

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(260, easing = FastOutSlowInEasing)) + fadeIn(tween(220)),
        exit = shrinkVertically(tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(160)),
        modifier = modifier
    ) {
        val p = live ?: lastLive.value ?: return@AnimatedVisibility
        val partnerId = partner?.id ?: me?.partnerId
        val partnerName = partner?.displayName?.ifBlank { null } ?: "Your love"
        val line = when {
            p.startedBy == partnerId -> "$partnerName started “${p.title}”"
            p.isWatchedBy(partnerId) -> "$partnerName is watching “${p.title}”"
            else -> "Movie Date on: “${p.title}”"
        }
        val accent = MaterialTheme.colorScheme.primary
        val glow = rememberInfiniteTransition(label = "invite_glow")
        val pulse by glow.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "invite_pulse"
        )
        // The whole pill opens it full screen, not only its button
        Surface(
            onClick = { WatchPartyUi.openTheater() },
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            border = BorderStroke(1.5.dp, accent.copy(alpha = pulse)),
            shadowElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("watch_party_invite")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 5.dp, bottom = 5.dp)
            ) {
                Text("🍿", fontSize = 17.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = line,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = { WatchPartyUi.openTheater() },
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("watch_party_join")
                ) {
                    Text("Join Together", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(
                    onClick = { WatchPartyUi.dismissedInvite = p.inviteKey },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Not now",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
