package com.example.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The secret app's own toast: a soft pill that drops in at the top instead of Android's plain
 * toast. [CherishToastHost] shows it under the app lock and the Notes screen, so a message never
 * appears over the disguise. Code inside a full-screen dialog (photo viewer, video-note recorder,
 * watch party) still uses Android's toast, since this pill would be hidden behind the dialog.
 */
object CherishToast {
    class Message internal constructor(val text: String, val long: Boolean) {
        internal val postedAt = SystemClock.elapsedRealtime()
    }

    private val _current = MutableStateFlow<Message?>(null)
    val current: StateFlow<Message?> = _current.asStateFlow()

    /** Shows [text] for 2 seconds, or 3.5 with [long] (Android's short and long toast times). */
    fun show(text: CharSequence, long: Boolean = false) {
        _current.value = Message(text.toString(), long)
    }

    internal fun dismiss(message: Message) {
        _current.compareAndSet(message, null)
    }
}

private const val SHORT_MS = 2_000L
private const val LONG_MS = 3_500L

/** A message posted while the app was hidden (or long ago) isn't shown late. */
private const val STALE_MS = 1_500L

/**
 * Where [CherishToast] messages appear: at the top, below the status bar. [visible] is false behind
 * the Notes disguise and the app lock, where messages are dropped instead of shown later.
 */
@Composable
fun CherishToastHost(visible: Boolean, modifier: Modifier = Modifier) {
    val message by CherishToast.current.collectAsState()
    // The last text stays while the pill slides away
    var shownText by remember { mutableStateOf("") }

    LaunchedEffect(message, visible) {
        val current = message ?: return@LaunchedEffect
        if (!visible || SystemClock.elapsedRealtime() - current.postedAt > STALE_MS) {
            CherishToast.dismiss(current)
            return@LaunchedEffect
        }
        shownText = current.text
        delay(if (current.long) LONG_MS else SHORT_MS)
        CherishToast.dismiss(current)
    }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = visible && message != null,
            enter = fadeIn(tween(160)) +
                slideInVertically(spring(dampingRatio = 0.75f, stiffness = 500f)) { -it } +
                scaleIn(spring(dampingRatio = 0.75f, stiffness = 500f), initialScale = 0.92f),
            exit = fadeOut(tween(180)) +
                slideOutVertically(tween(220)) { -it / 2 } +
                scaleOut(tween(220), targetScale = 0.96f),
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 8.dp, start = 24.dp, end = 24.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                // Near-black on light screens; a lifted graphite with a hairline edge on dark ones
                color = if (isDark) Color(0xFF26262C) else Color(0xFF1F1F25),
                border = if (isDark) BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)) else null,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .clickable { message?.let(CherishToast::dismiss) }
            ) {
                Text(
                    text = shownText,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    maxLines = 3,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        }
    }
}
