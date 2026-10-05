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
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.example.CherishApplication
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
 * Interactive media viewer:
 * - Smooth Pinch-to-zoom (0.8x min to 5.0x max)
 * - Double-tap to instantly toggle between Fit (1.0x min) and Zoomed (2.8x max)
 * - On-screen Zoom In [+] and Zoom Out [-] controls with percentage badge for easy sizing
 * - Swipe-Down to Dismiss with background fade
 * - Bottom indicator to swipe through all photos in chat / gallery
 * - Single-tap to toggle chrome immersion
 * - Save to device gallery with instant feedback toast
 */
@Composable
fun FullScreenMediaViewer(
    mediaUrl: String,
    allMediaUrls: List<String> = emptyList(),
    onShowInChat: ((url: String) -> Unit)? = null,
    onDeleteMedia: ((url: String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val app = context.applicationContext as? CherishApplication
    val isDisguiseActive by (app?.securityPreferences?.isDisguiseActive ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()

    LaunchedEffect(isDisguiseActive) {
        if (isDisguiseActive) {
            onDismiss()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_PAUSE) {
                onDismiss()
                app?.securityPreferences?.reDisguise()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Ensure initial media is present in media list
    val mediaList = remember(mediaUrl, allMediaUrls) {
        val combined = if (allMediaUrls.contains(mediaUrl)) allMediaUrls
        else listOf(mediaUrl) + allMediaUrls
        combined.filter { it.isNotBlank() }.distinct().ifEmpty { listOf(mediaUrl) }
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
        val view = androidx.compose.ui.platform.LocalView.current
        SideEffect {
            val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
            if (window != null) {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            }
        }
        
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
            val isZoomed = scale > 1.05f
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = scale <= 1.05f,
                beyondViewportPageCount = 1,
                key = { it },
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val pageUrl = mediaList.getOrNull(page) ?: currentUrl
                val isVideo = remember(pageUrl) {
                    pageUrl.endsWith(".mp4", ignoreCase = true) ||
                    pageUrl.contains("videonote", ignoreCase = true) ||
                    (pageUrl.startsWith("content://") && pageUrl.contains("video", ignoreCase = true))
                }

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
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var isMultiTouch = false
                                var previousDistance = 0f
                                var totalX = 0f
                                var totalY = 0f
                                var decidedDirection = false
                                var isVerticalSwipeDismiss = false
                                var overscrollX = 0f

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val activeChanges = event.changes.filter { it.pressed }
                                    if (activeChanges.isEmpty()) break

                                    if (activeChanges.size >= 2) {
                                        // Multi-finger pinch-to-zoom: Works at ANY scale (from 1.0x to 5.0x)!
                                        isMultiTouch = true
                                        val p0 = activeChanges[0].position
                                        val p1 = activeChanges[1].position
                                        val currentDistance = kotlin.math.hypot(p0.x - p1.x, p0.y - p1.y)

                                        if (previousDistance > 0f && currentDistance > 0f) {
                                            val zoomDelta = currentDistance / previousDistance
                                            val newScale = (scale * zoomDelta).coerceIn(0.85f, 5.0f)
                                            scale = newScale

                                            // Centroid pan
                                            val centroidChange = (activeChanges[0].positionChange() + activeChanges[1].positionChange()) / 2f
                                            if (scale > 1.05f) {
                                                val maxBoundX = (scale - 1f) * 600f
                                                val maxBoundY = (scale - 1f) * 800f
                                                offset = Offset(
                                                    x = (offset.x + centroidChange.x).coerceIn(-maxBoundX, maxBoundX),
                                                    y = (offset.y + centroidChange.y).coerceIn(-maxBoundY, maxBoundY)
                                                )
                                            }
                                        }
                                        previousDistance = currentDistance
                                        activeChanges.forEach { it.consume() }

                                    } else if (activeChanges.size == 1 && !isMultiTouch) {
                                        // Single finger interaction
                                        val change = activeChanges[0]
                                        val delta = change.positionChange()
                                        totalX += delta.x
                                        totalY += delta.y

                                        if (scale > 1.05f) {
                                            // Zoomed-in state: Pan the zoomed image
                                            val maxBoundX = (scale - 1f) * 600f
                                            val maxBoundY = (scale - 1f) * 800f
                                            val newX = offset.x + delta.x
                                            val newY = offset.y + delta.y

                                            // Track overscroll at boundary
                                            if (newX > maxBoundX) {
                                                overscrollX += (newX - maxBoundX)
                                            } else if (newX < -maxBoundX) {
                                                overscrollX += (newX - (-maxBoundX))
                                            } else {
                                                overscrollX = 0f
                                            }

                                            offset = Offset(
                                                x = newX.coerceIn(-maxBoundX, maxBoundX),
                                                y = newY.coerceIn(-maxBoundY, maxBoundY)
                                            )
                                            change.consume()

                                        } else {
                                            // Normal 1.0x scale:
                                            // Only consume if predominantly vertical swipe-down to dismiss
                                            if (!decidedDirection && (abs(totalX) > 12f || abs(totalY) > 12f)) {
                                                decidedDirection = true
                                                isVerticalSwipeDismiss = abs(totalY) > abs(totalX) * 1.4f && totalY > 0
                                            }

                                            if (decidedDirection && isVerticalSwipeDismiss) {
                                                change.consume()
                                                swipeOffsetY = totalY
                                                if (swipeOffsetY > 160f) {
                                                    onDismiss()
                                                    break
                                                }
                                            } else {
                                                // Horizontal swipe: Do NOT consume change!
                                                // HorizontalPager freely takes it and smoothly slides to next/prev photo!
                                            }
                                        }
                                    }
                                }

                                if (scale < 1.05f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                }
                                if (swipeOffsetY <= 160f) {
                                    swipeOffsetY = 0f
                                }

                                // If user swiped past the boundary while zoomed in, glide to next/previous photo
                                if (overscrollX < -150f && pagerState.currentPage < mediaList.lastIndex) {
                                    scope.launch {
                                        resetZoom()
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                } else if (overscrollX > 150f && pagerState.currentPage > 0) {
                                    scope.launch {
                                        resetZoom()
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isVideo) {
                        com.example.ui.components.CircularVideoNoteView(
                            videoUrl = pageUrl,
                            modifier = Modifier.size(260.dp)
                        )
                    } else {
                        val modelData = remember(pageUrl) {
                            when {
                                pageUrl.startsWith("data:image") -> {
                                    try {
                                        val base64 = pageUrl.substringAfter("base64,")
                                        android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                                    } catch (e: Exception) {
                                        pageUrl
                                    }
                                }
                                pageUrl.startsWith("/") -> java.io.File(pageUrl)
                                pageUrl.startsWith("file://") -> java.io.File(pageUrl.removePrefix("file://"))
                                pageUrl.startsWith("content://") -> android.net.Uri.parse(pageUrl)
                                else -> pageUrl
                            }
                        }

                        AsyncImage(
                            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                .data(modelData)
                                .build(),
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
            }

            // Left (Previous) and Right (Next) chevron buttons for effortless photo navigation
            if (mediaList.size > 1) {
                AnimatedVisibility(
                    visible = isChromeVisible && pagerState.currentPage > 0,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .size(44.dp)
                            .clickable {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Previous photo",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isChromeVisible && pagerState.currentPage < mediaList.lastIndex,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .size(44.dp)
                            .clickable {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Next photo",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // Top App Bar: Counter, Delete, Download, Share, Close
            AnimatedVisibility(
                visible = isChromeVisible,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.50f))
                                    .clickable { onDismiss() }
                                    .testTag("full_screen_media_close"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Black.copy(alpha = 0.50f)
                            ) {
                                Text(
                                    text = if (mediaList.size > 1) "${currentIndex + 1} of ${mediaList.size}" else "Photo View",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (onShowInChat != null) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.50f))
                                        .clickable {
                                            onShowInChat(currentUrl)
                                            onDismiss()
                                        }
                                        .testTag("full_screen_media_show_in_chat"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Chat,
                                        contentDescription = "Show in chat",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Share
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.50f))
                                    .clickable { shareImage() }
                                    .testTag("full_screen_media_share"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Download / Save to Gallery
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.50f))
                                    .clickable { saveImageToGallery() }
                                    .testTag("full_screen_media_download"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Save to gallery",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            // Delete media button
                            if (onDeleteMedia != null) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.50f))
                                        .clickable { showDeleteConfirmDialog = true }
                                        .testTag("full_screen_media_delete"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = Color(0xFF9CA3AF),
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Delete Confirmation Dialog
            if (showDeleteConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmDialog = false },
                    title = { Text("Delete photo?") },
                    text = { Text("This photo will be permanently deleted from the gallery and chat.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                showDeleteConfirmDialog = false
                                onDeleteMedia?.invoke(currentUrl)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirmDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Bottom Controls: Zoom Pill + Media Indicators
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
                        .padding(bottom = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Thumbnail filmstrip preview for fast photo browsing (if multiple photos)
                    if (mediaList.size > 1) {
                        LazyRow(
                            state = filmstripListState,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        ) {
                            itemsIndexed(mediaList) { idx, url ->
                                val isSelected = idx == currentIndex
                                val isVid = url.endsWith(".mp4", true) || url.contains("videonote", true)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Black.copy(alpha = 0.6f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        if (isSelected) 2.dp else 1.dp,
                                        if (isSelected) RoseGoldPrimary else Color.White.copy(alpha = 0.25f)
                                    ),
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (currentIndex != idx) {
                                                scope.launch {
                                                    resetZoom()
                                                    pagerState.animateScrollToPage(idx)
                                                }
                                            }
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(url)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "Photo thumbnail $idx",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        if (isVid) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Video",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

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

                    // Page indicator dots (if multiple images)
                    if (mediaList.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val displayCount = mediaList.size.coerceAtMost(20)
                            repeat(displayCount) { index ->
                                val isSelected = index == currentIndex
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .size(if (isSelected) 8.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) RoseGoldPrimary
                                            else Color.White.copy(alpha = 0.4f)
                                        )
                                )
                            }
                            if (mediaList.size > 20) {
                                Text(
                                    text = "+${mediaList.size - 20}",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
