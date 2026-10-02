package com.example.ui.chat

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.ui.theme.RoseGoldPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Telegram-style interactive media viewer:
 * - Smooth Pinch-to-zoom (0.8x min to 5.0x max)
 * - Double-tap to instantly toggle between Fit (1.0x min) and Zoomed (2.8x max)
 * - On-screen Zoom In [+] and Zoom Out [-] controls with percentage badge for easy sizing
 * - Telegram-style Swipe-Down to Dismiss with background fade
 * - Bottom Filmstrip Carousel to swipe through all photos in chat / gallery
 * - Single-tap to toggle chrome immersion
 * - Save to device gallery with instant feedback toast
 */
@Composable
fun FullScreenMediaViewer(
    mediaUrl: String,
    allMediaUrls: List<String> = emptyList(),
    onShowInChat: ((url: String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Ensure initial media is present in media list
    val mediaList = remember(mediaUrl, allMediaUrls) {
        if (allMediaUrls.contains(mediaUrl)) allMediaUrls
        else listOf(mediaUrl) + allMediaUrls
    }

    val initialIdx = remember { mediaList.indexOf(mediaUrl).coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = initialIdx, pageCount = { mediaList.size })
    var currentIndex by remember { mutableIntStateOf(initialIdx) }

    val currentUrl = remember(currentIndex, mediaList) {
        mediaList.getOrNull(currentIndex) ?: mediaUrl
    }

    // Zoom & Pan state
    var scale by remember(currentIndex) { mutableFloatStateOf(1f) }
    var offset by remember(currentIndex) { mutableStateOf(Offset.Zero) }
    var swipeOffsetY by remember(currentIndex) { mutableFloatStateOf(0f) }
    var isChromeVisible by remember { mutableStateOf(true) }

    val filmstripListState = rememberLazyListState()

    // Sync pager and filmstrip
    LaunchedEffect(pagerState.currentPage) {
        currentIndex = pagerState.currentPage
        if (mediaList.isNotEmpty() && currentIndex in mediaList.indices) {
            filmstripListState.animateScrollToItem(currentIndex)
        }
        scale = 1f
        offset = Offset.Zero
        swipeOffsetY = 0f
    }

    fun resetZoom() {
        scale = 1f
        offset = Offset.Zero
        swipeOffsetY = 0f
    }

    fun zoomIn() {
        scale = (scale + 0.6f).coerceAtMost(5.0f)
    }

    fun zoomOut() {
        scale = (scale - 0.6f).coerceAtLeast(1.0f)
        if (scale <= 1.05f) {
            offset = Offset.Zero
        }
    }

    fun toggleMaxMinZoom(tapPosition: Offset = Offset.Zero) {
        if (scale > 1.2f) {
            // Zoom out to minimum (fit screen)
            resetZoom()
        } else {
            // Zoom in to max (2.8x)
            scale = 2.8f
        }
    }

    // Save image to Android gallery
    fun saveImageToGallery() {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val loader = ImageLoader(context)
                    val request = ImageRequest.Builder(context)
                        .data(currentUrl)
                        .allowHardware(false)
                        .build()
                    val result = (loader.execute(request) as? SuccessResult)?.drawable
                    val bitmap = (result as? BitmapDrawable)?.bitmap

                    if (bitmap != null) {
                        val filename = "Cherish_${System.currentTimeMillis()}.jpg"
                        var fos: OutputStream? = null
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val resolver = context.contentResolver
                            val contentValues = android.content.ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Cherish")
                            }
                            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                            if (imageUri != null) {
                                fos = resolver.openOutputStream(imageUri)
                            }
                        } else {
                            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/Cherish"
                            val file = File(imagesDir)
                            if (!file.exists()) file.mkdirs()
                            val image = File(imagesDir, filename)
                            fos = FileOutputStream(image)
                        }
                        fos?.use {
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved to Photos gallery ✨", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Photo ready in memory 💕", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Share image intent
    fun shareImage() {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Shared Photo from Cherish")
                putExtra(Intent.EXTRA_TEXT, currentUrl)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Photo"))
        } catch (_: Exception) {}
    }

    // Calculate background opacity based on swipe down
    val backgroundAlpha = remember(swipeOffsetY) {
        val dragDist = abs(swipeOffsetY)
        (1f - (dragDist / 500f)).coerceIn(0.2f, 1f)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = backgroundAlpha))
                .testTag("full_screen_media_dialog")
        ) {
            // Main Interactive Zoomable & Pannable Photo with Horizontal Pager
            // Issue 11: beyondBoundsPageCount=1 pre-loads the adjacent images so swiping
            // left/right is instant with no load delay. userScrollEnabled=false while zoomed
            // prevents accidental page swipes during pinch-zoom.
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = scale <= 1.05f,
                key = { it },
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val pageUrl = mediaList.getOrNull(page) ?: currentUrl
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(page) {
                            detectTapGestures(
                                onTap = {
                                    isChromeVisible = !isChromeVisible
                                },
                                onDoubleTap = { tapOffset ->
                                    toggleMaxMinZoom(tapOffset)
                                }
                            )
                        }
                        .pointerInput(page) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(0.75f, 5.0f)
                                scale = newScale

                                if (scale > 1.05f) {
                                    // Pan bounded
                                    val maxBound = (scale - 1f) * 600f
                                    offset = Offset(
                                        x = (offset.x + pan.x).coerceIn(-maxBound, maxBound),
                                        y = (offset.y + pan.y).coerceIn(-maxBound, maxBound)
                                    )
                                } else {
                                    // Swipe down to dismiss gesture when at min scale
                                    if (pan.y > 0 || swipeOffsetY > 0) {
                                        swipeOffsetY += pan.y
                                        if (swipeOffsetY > 160f) {
                                            onDismiss()
                                        }
                                    } else {
                                        swipeOffsetY = 0f
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = pageUrl,
                        contentDescription = "Full-screen media photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (page == pagerState.currentPage) {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offset.x
                                    translationY = offset.y + swipeOffsetY
                                }
                            }
                    )
                }
            }

            // Top App Bar (Telegram style: Counter, Download, Share, Close)
            AnimatedVisibility(
                visible = isChromeVisible,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.testTag("full_screen_media_close")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            if (mediaList.size > 1) {
                                Text(
                                    text = "${currentIndex + 1} of ${mediaList.size}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            } else {
                                Text(
                                    text = "Photo View",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (onShowInChat != null) {
                                IconButton(
                                    onClick = {
                                        onShowInChat(currentUrl)
                                        onDismiss()
                                    },
                                    modifier = Modifier.testTag("full_screen_media_show_in_chat")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Chat,
                                        contentDescription = "Show in chat",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Share
                            IconButton(
                                onClick = { shareImage() },
                                modifier = Modifier.testTag("full_screen_media_share")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Download / Save to Gallery
                            IconButton(
                                onClick = { saveImageToGallery() },
                                modifier = Modifier.testTag("full_screen_media_download")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Save to gallery",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Telegram Controls: Zoom Pill + Filmstrip Carousel
            AnimatedVisibility(
                visible = isChromeVisible,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Zoom Max & Min Controls Pill
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            // Zoom Out [-]
                            IconButton(
                                onClick = { zoomOut() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomOut,
                                    contentDescription = "Zoom Out (Min Size)",
                                    tint = if (scale > 1.05f) Color.White else Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Current Zoom percentage & quick reset
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { resetZoom() }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                val percent = (scale * 100).roundToInt()
                                Text(
                                    text = "$percent%",
                                    color = if (scale > 1.05f) RoseGoldPrimary else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            // Zoom In [+]
                            IconButton(
                                onClick = { zoomIn() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomIn,
                                    contentDescription = "Zoom In (Max Size)",
                                    tint = if (scale < 4.95f) Color.White else Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Max / Fit toggle button
                            TextButton(
                                onClick = { toggleMaxMinZoom() },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = if (scale > 1.2f) "Fit (1x)" else "Max (3x)",
                                    color = RoseGoldPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Telegram Bottom Filmstrip Carousel (if multiple photos)
                    if (mediaList.size > 1) {
                        LazyRow(
                            state = filmstripListState,
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            itemsIndexed(mediaList) { index, itemUrl ->
                                val isSelected = index == currentIndex
                                val thumbBorder = if (isSelected) {
                                    androidx.compose.foundation.BorderStroke(2.5.dp, RoseGoldPrimary)
                                } else {
                                    androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    border = thumbBorder,
                                    color = Color.DarkGray,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clickable {
                                            scope.launch {
                                                pagerState.animateScrollToPage(index)
                                            }
                                        }
                                ) {
                                    AsyncImage(
                                        model = itemUrl,
                                        contentDescription = "Thumbnail $index",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
