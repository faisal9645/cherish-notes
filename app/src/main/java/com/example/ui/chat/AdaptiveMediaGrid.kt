package com.example.ui.chat

import com.example.ui.theme.darkTone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.DayBlueSecondary

/**
 * Adaptive Media Grid for Chat & Memories.
 * Dynamically adjusts column arrangements and dimensions based on
 * number of photos and user-selected thumbnail size (small, medium, large).
 */
/** Photo shapes (width / height) seen so far, so a card has its size straight away when shown again. */
private val photoRatios = android.util.LruCache<String, Float>(500)

/** The tallest and widest a single photo card gets; beyond that the photo is trimmed to fit. */
private const val MIN_PHOTO_RATIO = 0.72f
private const val MAX_PHOTO_RATIO = 1.9f

@Composable
fun AdaptiveMediaGrid(
    urls: List<String>,
    gallerySize: String = "large",
    onImageClick: (index: Int, url: String) -> Unit,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    previewUrls: List<String> = emptyList()
) {
    if (urls.isEmpty()) return
    // Tiles show the small preview when there is one; taps still hand out the full photo
    fun shown(index: Int): String = previewUrls.getOrNull(index)?.takeIf { it.isNotBlank() } ?: urls[index]

    val gridHeight: Dp = when (gallerySize.lowercase()) {
        "small" -> 130.dp
        "medium" -> 190.dp
        else -> 250.dp
    }

    val singleImageHeight: Dp = when (gallerySize.lowercase()) {
        "small" -> 140.dp
        "medium" -> 200.dp
        else -> 265.dp
    }

    val twoImagesHeight: Dp = when (gallerySize.lowercase()) {
        "small" -> 105.dp
        "medium" -> 150.dp
        else -> 190.dp
    }

    val cornerRadius = 14.dp
    val spacing = 4.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
    ) {
        when {
            // Case 1: Single Photo. The card takes the photo's own shape (very tall or very wide ones
            // are trimmed a little), so there are no empty bands above or below it.
            urls.size == 1 -> {
                val key = urls[0]
                var ratio by remember(key) { mutableFloatStateOf(photoRatios.get(key) ?: 0f) }
                MediaTile(
                    url = shown(0),
                    contentScale = ContentScale.Crop,
                    onAspectRatio = { loaded ->
                        if (loaded > 0f) {
                            photoRatios.put(key, loaded)
                            ratio = loaded
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        // Square until the photo says otherwise
                        .aspectRatio((if (ratio > 0f) ratio else 1f).coerceIn(MIN_PHOTO_RATIO, MAX_PHOTO_RATIO))
                        .clip(RoundedCornerShape(cornerRadius))
                        .openableMedia(onClick = { onImageClick(0, urls[0]) }, onLongClick = onLongClick, onDoubleClick = onDoubleClick)
                )
            }

            // Case 2: Exactly 2 Photos (side-by-side)
            urls.size == 2 -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(twoImagesHeight),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MediaTile(
                        url = shown(0),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .openableMedia(onClick = { onImageClick(0, urls[0]) }, onLongClick = onLongClick, onDoubleClick = onDoubleClick)
                    )
                    MediaTile(
                        url = shown(1),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .openableMedia(onClick = { onImageClick(1, urls[1]) }, onLongClick = onLongClick, onDoubleClick = onDoubleClick)
                    )
                }
            }

            // Case 3: Exactly 3 Photos (1 large left, 2 stacked right)
            urls.size == 3 -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(gridHeight),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    MediaTile(
                        url = shown(0),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                            .openableMedia(onClick = { onImageClick(0, urls[0]) }, onLongClick = onLongClick, onDoubleClick = onDoubleClick)
                    )
                    Column(
                        modifier = Modifier
                            .weight(0.8f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        MediaTile(
                            url = shown(1),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .openableMedia(onClick = { onImageClick(1, urls[1]) }, onLongClick = onLongClick, onDoubleClick = onDoubleClick)
                        )
                        MediaTile(
                            url = shown(2),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .openableMedia(onClick = { onImageClick(2, urls[2]) }, onLongClick = onLongClick, onDoubleClick = onDoubleClick)
                        )
                    }
                }
            }

            // Case 4: Exactly 4 Photos (2x2 balanced grid)
            urls.size == 4 -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(gridHeight),
                    verticalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        MediaTile(
                            url = shown(0),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .openableMedia(onClick = { onImageClick(0, urls[0]) }, onLongClick = onLongClick, onDoubleClick = onDoubleClick)
                        )
                        MediaTile(
                            url = shown(1),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .openableMedia(onClick = { onImageClick(1, urls[1]) }, onLongClick = onLongClick, onDoubleClick = onDoubleClick)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        MediaTile(
                            url = shown(2),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .openableMedia(onClick = { onImageClick(2, urls[2]) }, onLongClick = onLongClick, onDoubleClick = onDoubleClick)
                        )
                        MediaTile(
                            url = shown(3),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .openableMedia(onClick = { onImageClick(3, urls[3]) }, onLongClick = onLongClick, onDoubleClick = onDoubleClick)
                        )
                    }
                }
            }

            // Case 5: 5 or more Photos (2x2 grid with +N counter on the 4th tile)
            else -> {
                val remainingCount = urls.size - 3
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(gridHeight),
                    verticalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        MediaTile(
                            url = shown(0),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .openableMedia(onClick = { onImageClick(0, urls[0]) }, onLongClick = onLongClick, onDoubleClick = onDoubleClick)
                        )
                        MediaTile(
                            url = shown(1),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .openableMedia(onClick = { onImageClick(1, urls[1]) }, onLongClick = onLongClick, onDoubleClick = onDoubleClick)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        MediaTile(
                            url = shown(2),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .openableMedia(onClick = { onImageClick(2, urls[2]) }, onLongClick = onLongClick, onDoubleClick = onDoubleClick)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .openableMedia(onClick = { onImageClick(3, urls[3]) }, onLongClick = onLongClick, onDoubleClick = onDoubleClick),
                            contentAlignment = Alignment.Center
                        ) {
                            MediaTile(
                                url = shown(3),
                                modifier = Modifier.fillMaxSize()
                            )
                            // Dimmed overlay with +N badge
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+$remainingCount",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaTile(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    onAspectRatio: ((Float) -> Unit)? = null
) {
    val context = LocalContext.current
    val isDark = androidx.compose.material3.MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val tileBg = if (isDark) darkTone(Color(0xFF131824)) else Color(0xFFE2E8F0)
    // Shimmer while the photo loads; the placeholder icon only when there is nothing to show
    var isLoading by remember(url) { mutableStateOf(url.isNotBlank()) }
    var hasFailed by remember(url) { mutableStateOf(false) }
    Box(
        modifier = modifier.background(tileBg),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .shimmer(
                        baseColor = tileBg,
                        highlightColor = Color.White.copy(alpha = if (isDark) 0.07f else 0.55f)
                    )
            )
        } else if (hasFailed || url.isBlank()) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = "Photo placeholder",
                tint = darkTone(DayBlueSecondary).copy(alpha = 0.6f),
                modifier = Modifier.size(36.dp)
            )
        }
        if (url.isNotBlank()) {
            val modelData = remember(url) {
                when {
                    url.startsWith("data:image") -> {
                        try {
                            val base64 = url.substringAfter("base64,")
                            android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                        } catch (e: Exception) {
                            url
                        }
                    }
                    url.startsWith("/") -> java.io.File(url)
                    url.startsWith("file://") -> java.io.File(url.removePrefix("file://"))
                    url.startsWith("content://") -> android.net.Uri.parse(url)
                    else -> url
                }
            }
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(modelData)
                    .crossfade(200)
                    .build(),
                contentDescription = "Photo",
                contentScale = contentScale,
                onState = { state ->
                    isLoading = state is coil.compose.AsyncImagePainter.State.Loading
                    hasFailed = state is coil.compose.AsyncImagePainter.State.Error
                    if (state is coil.compose.AsyncImagePainter.State.Success && onAspectRatio != null) {
                        val size = state.painter.intrinsicSize
                        if (size.width > 0f && size.height > 0f) onAspectRatio(size.width / size.height)
                    }
                },
                modifier = if (contentScale == ContentScale.FillWidth) Modifier.fillMaxWidth().wrapContentHeight() else Modifier.fillMaxSize()
            )
        }
    }
}
