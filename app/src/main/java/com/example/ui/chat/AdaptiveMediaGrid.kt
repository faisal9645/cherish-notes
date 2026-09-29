package com.example.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Telegram-style Adaptive Media Grid for Chat & Memories.
 * Dynamically adjusts column arrangements and dimensions based on
 * number of photos and user-selected thumbnail size (small, medium, large).
 */
@Composable
fun AdaptiveMediaGrid(
    urls: List<String>,
    gallerySize: String = "medium",
    onImageClick: (index: Int, url: String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (urls.isEmpty()) return

    val gridHeight: Dp = when (gallerySize.lowercase()) {
        "small" -> 160.dp
        "large" -> 300.dp
        else -> 225.dp
    }

    val singleImageHeight: Dp = when (gallerySize.lowercase()) {
        "small" -> 180.dp
        "large" -> 330.dp
        else -> 245.dp
    }

    val cornerRadius = 14.dp
    val spacing = 4.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
    ) {
        when {
            // Case 1: Single Photo
            urls.size == 1 -> {
                MediaTile(
                    url = urls[0],
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(singleImageHeight)
                        .clip(RoundedCornerShape(cornerRadius))
                        .clickable { onImageClick(0, urls[0]) }
                )
            }

            // Case 2: Exactly 2 Photos (side-by-side)
            urls.size == 2 -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(gridHeight),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    MediaTile(
                        url = urls[0],
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onImageClick(0, urls[0]) }
                    )
                    MediaTile(
                        url = urls[1],
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onImageClick(1, urls[1]) }
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
                        url = urls[0],
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                            .clickable { onImageClick(0, urls[0]) }
                    )
                    Column(
                        modifier = Modifier
                            .weight(0.8f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        MediaTile(
                            url = urls[1],
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clickable { onImageClick(1, urls[1]) }
                        )
                        MediaTile(
                            url = urls[2],
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clickable { onImageClick(2, urls[2]) }
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
                            url = urls[0],
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { onImageClick(0, urls[0]) }
                        )
                        MediaTile(
                            url = urls[1],
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { onImageClick(1, urls[1]) }
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        MediaTile(
                            url = urls[2],
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { onImageClick(2, urls[2]) }
                        )
                        MediaTile(
                            url = urls[3],
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { onImageClick(3, urls[3]) }
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
                            url = urls[0],
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { onImageClick(0, urls[0]) }
                        )
                        MediaTile(
                            url = urls[1],
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { onImageClick(1, urls[1]) }
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        MediaTile(
                            url = urls[2],
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { onImageClick(2, urls[2]) }
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { onImageClick(3, urls[3]) },
                            contentAlignment = Alignment.Center
                        ) {
                            MediaTile(
                                url = urls[3],
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
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .crossfade(200)
            .build(),
        contentDescription = "Photo",
        contentScale = contentScale,
        modifier = modifier
            .background(Color(0xFFEBEBEF))
    )
}
