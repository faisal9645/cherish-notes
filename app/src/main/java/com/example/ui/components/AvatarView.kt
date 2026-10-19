package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.example.ui.chat.areAnimationsDisabled
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.RoseGoldPrimary
import com.example.ui.theme.appHorizontalGradient

object AvatarCache {
    private val cache = android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(64)

    fun getOrDecode(dataUrl: String): androidx.compose.ui.graphics.ImageBitmap? {
        if (!dataUrl.startsWith("data:image/")) return null
        cache.get(dataUrl)?.let { return it }
        return try {
            val base64 = dataUrl.substringAfter("base64,")
            val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val imageBitmap = bitmap?.asImageBitmap()
            if (imageBitmap != null) {
                cache.put(dataUrl, imageBitmap)
            }
            imageBitmap
        } catch (_: Exception) {
            null
        }
    }
}

/** How much an avatar grows at the top of a breath. */
internal const val AVATAR_BREATH_SCALE = 0.042f

/**
 * An avatar's breath, 0..1, while [alive] (online or typing) and not with system animations off.
 * Read it only while drawing, so nothing recomposes for it.
 */
@Composable
internal fun rememberAvatarBreath(alive: Boolean): () -> Float {
    val context = androidx.compose.ui.platform.LocalContext.current
    val breathPhase = androidx.compose.runtime.remember { Animatable(0f) }
    LaunchedEffect(alive) {
        breathPhase.snapTo(0f)
        if (alive && !context.areAnimationsDisabled()) {
            breathPhase.animateTo(
                targetValue = (2.0 * Math.PI).toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(3500, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }
    // Organic 3.5-second sinusoidal breathing curve: smooth expansion & spring-damped relaxation
    return androidx.compose.runtime.remember(breathPhase) {
        {
            val phaseD = breathPhase.value.toDouble()
            val rawCos = (1.0 - kotlin.math.cos(phaseD)) / 2.0
            val dampened = rawCos * (1.0 - 0.06 * kotlin.math.sin(phaseD))
            dampened.toFloat().coerceIn(0f, 1f)
        }
    }
}

@Composable
fun AvatarView(
    photoUrl: String?,
    name: String,
    size: Dp = 48.dp,
    isOnline: Boolean = false,
    isTyping: Boolean = false,
    showOnlineBadge: Boolean = true,
    modifier: Modifier = Modifier,
    // The blue ring; off where the photo sits in a ring of its own (e.g. the couple card)
    showRing: Boolean = true
) {
    val decodedBitmap = androidx.compose.runtime.remember(photoUrl) {
        if (photoUrl?.startsWith("data:image/") == true) {
            AvatarCache.getOrDecode(photoUrl)
        } else {
            null
        }
    }

    val isAlive = isOnline || isTyping
    val breathProgress = rememberAvatarBreath(isAlive)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Glowing vibrant royal blue ring around avatar with organic breathing physics
        Box(
            modifier = (if (showRing) {
                Modifier
                    .size((size - 4.dp).coerceAtLeast(24.dp))
                    .graphicsLayer {
                        val breath = breathProgress()
                        scaleX = 1f + AVATAR_BREATH_SCALE * breath
                        scaleY = 1f + AVATAR_BREATH_SCALE * breath
                        alpha = if (isAlive) 0.72f + 0.28f * breath else 1f
                    }
                    .border(
                        width = 2.dp,
                        brush = appHorizontalGradient(),
                        shape = CircleShape
                    )
                    .padding(2.dp)
            } else {
                Modifier
                    .size(size)
                    .graphicsLayer {
                        val breath = breathProgress()
                        scaleX = 1f + AVATAR_BREATH_SCALE * breath
                        scaleY = 1f + AVATAR_BREATH_SCALE * breath
                    }
            })
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!photoUrl.isNullOrEmpty()) {
                if (photoUrl.startsWith("data:image/")) {
                    if (decodedBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = decodedBitmap,
                            contentDescription = "$name's avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val initial = name.trim().firstOrNull()?.uppercase() ?: "❤️"
                        Text(
                            text = initial,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = (size.value * 0.42f).sp,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                } else {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = "$name's avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                val initial = name.trim().firstOrNull()?.uppercase() ?: "❤️"
                Text(
                    text = initial,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = (size.value * 0.42f).sp,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        if (showOnlineBadge && isOnline) {
            val badgeSize = (size.value * 0.28f).coerceIn(10.dp.value, 13.dp.value).dp
            Box(
                modifier = Modifier
                    .size(badgeSize)
                    .graphicsLayer {
                        val badgeScale = 1f + 0.15f * breathProgress()
                        scaleX = badgeScale
                        scaleY = badgeScale
                    }
                    .align(Alignment.BottomEnd)
                    .offset(x = (-1.5).dp, y = (-1.5).dp)
                    .clip(CircleShape)
                    .background(OnlineGreen)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}
