package com.example.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/** How long a Love & Us tile takes to grow into its screen (and shrink back). */
const val PLACE_TRANSFORM_MS = 380

/** The tiles' shape: the growing screen keeps these corners until it fills the screen. */
val PlaceTileShape: Shape = RoundedCornerShape(16.dp)

/** Keys pairing each Love & Us tile with the screen it opens. */
object PlaceKeys {
    const val GALLERY = "place_gallery"
    const val MEMORIES = "place_memories"
    const val DATES = "place_dates"
    const val NOTES = "place_notes"
}

/**
 * Which tile was just tapped. The screen it opens takes it once, so the screen grows out of that tile
 * only when opened from it (a screen opened another way, e.g. from the chat, comes in as usual).
 */
object PlaceTransition {
    @Volatile private var opening: String? = null

    fun open(key: String) {
        opening = key
    }

    fun take(key: String): Boolean = (opening == key).also { if (it) opening = null }
}

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The navigation transition the current screen is part of. */
val LocalNavAnimatedScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * The container transform: the element with [key] on one screen and the one with the same key on the
 * next morph into each other (bounds and corners), their contents crossfading. [isTile] is the small
 * side (relaid out as it grows, so its icon and name don't balloon); the screen side is scaled, so it
 * shows as a miniature of itself growing out of the tile. Outside navigation it does nothing.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.containerTransform(key: String, shape: Shape, isTile: Boolean): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalNavAnimatedScope.current ?: return this
    return with(shared) {
        this@containerTransform.sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = visibility,
            enter = fadeIn(tween(PLACE_TRANSFORM_MS / 2, delayMillis = PLACE_TRANSFORM_MS / 5)),
            exit = fadeOut(tween(PLACE_TRANSFORM_MS / 3)),
            boundsTransform = BoundsTransform { _, _ -> tween(PLACE_TRANSFORM_MS, easing = FastOutSlowInEasing) },
            resizeMode = if (isTile) {
                SharedTransitionScope.ResizeMode.RemeasureToBounds
            } else {
                SharedTransitionScope.ResizeMode.ScaleToBounds(ContentScale.FillWidth, Alignment.TopCenter)
            },
            clipInOverlayDuringTransition = OverlayClip(shape)
        )
    }
}

/**
 * A screen a Love & Us tile opens. When it was opened from that tile, it grows out of it (and shrinks
 * back into it on Back); otherwise it's shown as is.
 */
@Composable
fun PlaceScreen(key: String, navScope: AnimatedVisibilityScope, content: @Composable () -> Unit) {
    val fromTile = remember { PlaceTransition.take(key) }
    CompositionLocalProvider(LocalNavAnimatedScope provides navScope) {
        val transform = if (fromTile) Modifier.containerTransform(key, PlaceTileShape, isTile = false) else Modifier
        Box(modifier = Modifier.fillMaxSize().then(transform)) {
            content()
        }
    }
}
