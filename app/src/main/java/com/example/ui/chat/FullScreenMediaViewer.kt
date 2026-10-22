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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.ui.security.SecretWindowGuard
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import com.example.ui.theme.Blue500
import com.example.ui.theme.Gray400
import com.example.ui.theme.Slate900
import com.example.ui.components.CherishAlertDialog
import com.example.ui.components.CherishButton
import com.example.ui.components.AVATAR_BREATH_SCALE
import com.example.ui.components.pressScaleTarget
import com.example.ui.components.rememberAvatarBreath
import com.example.ui.components.springClickable
import androidx.compose.material.icons.automirrored.rounded.Chat

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
    openFrom: TransformOrigin = TransformOrigin.Center,
    isCircularVideo: Boolean = false,
    circularMediaUrls: Set<String> = emptySet(),
    // When each one was sent, by its URL (see mediaTimesOf): shown under the count
    mediaTimes: Map<String, Long> = emptyMap(),
    // A profile photo: it breathes like their avatar in the chat's top bar
    breathing: Boolean = false,
    isProfilePhoto: Boolean = false,
    partnerName: String? = null,
    onOpenKissScreen: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showKissPadInViewer by remember { mutableStateOf(false) }
    var profileKisses by remember { mutableStateOf<List<KissBloom>>(emptyList()) }
    var kissFeedbackText by remember { mutableStateOf<String?>(null) }
    val isUserMale = remember { com.example.security.SecurityPreferences.getInstance(context).getUserGender() == com.example.security.SecurityPreferences.GENDER_MALE }
    val isPartnerMale = remember { com.example.security.SecurityPreferences.getInstance(context).getPartnerGender() == com.example.security.SecurityPreferences.GENDER_MALE }

    fun kissProfile(atFraction: Offset = Offset(0.5f, 0.45f)) {
        val bloom = KissBloom(java.util.UUID.randomUUID().toString(), atFraction.x, atFraction.y, fromMe = true, isMale = isUserMale)
        profileKisses = profileKisses + bloom
        context.vibrateKiss()
        val name = partnerName?.trim()?.substringBefore(' ') ?: "Partner"
        kissFeedbackText = "Kissed $name ${if (isUserMale) "👄" else "💋"}"
        scope.launch {
            try {
                (context.applicationContext as? CherishApplication)?.authRepository?.sendKiss(atFraction.x, atFraction.y)
            } catch (_: Exception) {}
            delay(2400)
            if (kissFeedbackText?.contains("Kissed") == true) {
                kissFeedbackText = null
            }
        }
    }

    // Opened from a photo in the chat or gallery: it grows out of that photo into full screen and
    // shrinks back into it when closed. Otherwise it zooms out of the point that was tapped
    // (openFrom, as a fraction of the screen).
    val openedTile = remember { MediaOpenOrigin.take() }
    val viewerCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    fun tileRectInViewer(): Rect? {
        val tile = openedTile?.takeIf { it.isAttached } ?: return null
        val viewer = viewerCoords[0]?.takeIf { it.isAttached } ?: return null
        return try {
            val topLeft = viewer.screenToLocal(tile.localToScreen(Offset.Zero))
            val bottomRight = viewer.screenToLocal(
                tile.localToScreen(Offset(tile.size.width.toFloat(), tile.size.height.toFloat()))
            )
            Rect(topLeft, bottomRight).takeIf { it.width > 1f && it.height > 1f }
        } catch (_: Exception) {
            null
        }
    }
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 340f))
    }
    var isClosing by remember { mutableStateOf(false) }
    val closeAnimated: () -> Unit = {
        if (!isClosing) {
            isClosing = true
            com.example.ui.components.stopAllVideoNotes()
            scope.launch {
                entrance.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
                onDismiss()
            }
        }
    }
    BackHandler(onBack = closeAnimated)
    DisposableEffect(Unit) {
        onDispose {
            com.example.ui.components.stopAllVideoNotes()
            // Back on the chat, its round notes play by themselves again as they come into view
            // (not when the viewer closed because the app went behind its disguise)
            if (!com.example.security.SecurityPreferences.getInstance(context).isDisguiseActive()) {
                com.example.ui.components.resumeVideoNotePreviews()
            }
        }
    }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val app = context.applicationContext as? CherishApplication
    // Settings > Double-Tap Zoom in Viewer
    val doubleTapZoom = remember { app?.securityPreferences?.isDoubleTapZoomEnabled() ?: true }
    DisposableEffect(Unit) {
        val prefs = app?.securityPreferences
        prefs?.isMediaViewerActive = true
        onDispose {
            prefs?.isMediaViewerActive = false
        }
    }

    val isDisguiseActive by (app?.securityPreferences?.isDisguiseActive ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()

    LaunchedEffect(isDisguiseActive) {
        if (isDisguiseActive) {
            onDismiss()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY || event == Lifecycle.Event.ON_STOP) {
                onDismiss()
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

    // Zoom & Pan state: Google Photos style smooth Animatable zoom with focal-point tracking
    val scaleAnim = remember(currentIndex) { Animatable(1f) }
    val offsetXAnim = remember(currentIndex) { Animatable(0f) }
    val offsetYAnim = remember(currentIndex) { Animatable(0f) }
    var swipeOffsetY by remember(currentIndex) { mutableFloatStateOf(0f) }
    var isChromeVisible by remember { mutableStateOf(true) }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var currentImageSize by remember(currentIndex) { mutableStateOf(Size.Zero) }

    val scale = scaleAnim.value
    val offset = Offset(offsetXAnim.value, offsetYAnim.value)
    val breath = rememberAvatarBreath(breathing)

    val filmstripListState = rememberLazyListState()

    fun calculateMaxBounds(scaleVal: Float, cSize: IntSize, imgSize: Size): Offset {
        if (scaleVal <= 1.001f || cSize.width <= 0 || cSize.height <= 0) {
            return Offset.Zero
        }
        val cw = cSize.width.toFloat()
        val ch = cSize.height.toFloat()

        val (contentW, contentH) = if (imgSize.width > 0f && imgSize.height > 0f && !imgSize.width.isNaN() && !imgSize.height.isNaN()) {
            val imgAspect = imgSize.width / imgSize.height
            val containerAspect = cw / ch
            if (imgAspect > containerAspect) {
                // Wide image: fills width, letterboxed vertically
                val fittedW = cw
                val fittedH = cw / imgAspect
                fittedW to fittedH
            } else {
                // Tall image: fills height, pillarboxed horizontally
                val fittedH = ch
                val fittedW = ch * imgAspect
                fittedW to fittedH
            }
        } else {
            cw to ch
        }

        val renderedW = contentW * scaleVal
        val renderedH = contentH * scaleVal

        val maxX = maxOf(0f, (renderedW - cw) / 2f)
        val maxY = maxOf(0f, (renderedH - ch) / 2f)

        return Offset(maxX, maxY)
    }

    // Sync pager and filmstrip
    LaunchedEffect(pagerState.currentPage) {
        currentIndex = pagerState.currentPage
        if (mediaList.isNotEmpty() && currentIndex in mediaList.indices) {
            filmstripListState.animateScrollToItem(currentIndex)
        }
        scaleAnim.snapTo(1f)
        offsetXAnim.snapTo(0f)
        offsetYAnim.snapTo(0f)
        swipeOffsetY = 0f
    }

    fun resetZoom(animated: Boolean = true) {
        if (animated) {
            scope.launch {
                launch { scaleAnim.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 380f)) }
                launch { offsetXAnim.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 380f)) }
                launch { offsetYAnim.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 380f)) }
            }
        } else {
            scope.launch {
                scaleAnim.snapTo(1f)
                offsetXAnim.snapTo(0f)
                offsetYAnim.snapTo(0f)
            }
        }
        swipeOffsetY = 0f
    }

    fun zoomIn() {
        scope.launch {
            val targetScale = (scaleAnim.value + 0.65f).coerceAtMost(5.0f)
            val bounds = calculateMaxBounds(targetScale, containerSize, currentImageSize)
            val targetOffsetX = offsetXAnim.value.coerceIn(-bounds.x, bounds.x)
            val targetOffsetY = offsetYAnim.value.coerceIn(-bounds.y, bounds.y)
            launch { scaleAnim.animateTo(targetScale, spring(dampingRatio = 0.85f, stiffness = 380f)) }
            launch { offsetXAnim.animateTo(targetOffsetX, spring(dampingRatio = 0.85f, stiffness = 380f)) }
            launch { offsetYAnim.animateTo(targetOffsetY, spring(dampingRatio = 0.85f, stiffness = 380f)) }
        }
    }

    fun zoomOut() {
        scope.launch {
            val targetScale = (scaleAnim.value - 0.65f).coerceAtLeast(1.0f)
            val bounds = calculateMaxBounds(targetScale, containerSize, currentImageSize)
            val targetOffsetX = if (targetScale <= 1.05f) 0f else offsetXAnim.value.coerceIn(-bounds.x, bounds.x)
            val targetOffsetY = if (targetScale <= 1.05f) 0f else offsetYAnim.value.coerceIn(-bounds.y, bounds.y)
            launch { scaleAnim.animateTo(targetScale, spring(dampingRatio = 0.85f, stiffness = 380f)) }
            launch { offsetXAnim.animateTo(targetOffsetX, spring(dampingRatio = 0.85f, stiffness = 380f)) }
            launch { offsetYAnim.animateTo(targetOffsetY, spring(dampingRatio = 0.85f, stiffness = 380f)) }
        }
    }

    fun toggleMaxMinZoom(tapPosition: Offset = Offset.Zero) {
        scope.launch {
            if (scaleAnim.value > 1.15f) {
                // Zoom out to minimum (fit screen)
                launch { scaleAnim.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 380f)) }
                launch { offsetXAnim.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 380f)) }
                launch { offsetYAnim.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 380f)) }
            } else {
                // Zoom in to 2.8x focused smoothly at tap position like Google Photos
                val targetScale = 2.8f
                val center = if (containerSize.width > 0 && containerSize.height > 0) {
                    Offset(containerSize.width / 2f, containerSize.height / 2f)
                } else {
                    Offset.Zero
                }
                val tapPoint = if (tapPosition != Offset.Zero && center != Offset.Zero) tapPosition else center
                val f = tapPoint - center
                // Keep the tapped position anchored under the tap
                val rawOffsetX = -f.x * (targetScale - 1f)
                val rawOffsetY = -f.y * (targetScale - 1f)
                val bounds = calculateMaxBounds(targetScale, containerSize, currentImageSize)
                val targetOffsetX = rawOffsetX.coerceIn(-bounds.x, bounds.x)
                val targetOffsetY = rawOffsetY.coerceIn(-bounds.y, bounds.y)

                launch { scaleAnim.animateTo(targetScale, spring(dampingRatio = 0.85f, stiffness = 380f)) }
                launch { offsetXAnim.animateTo(targetOffsetX, spring(dampingRatio = 0.85f, stiffness = 380f)) }
                launch { offsetYAnim.animateTo(targetOffsetY, spring(dampingRatio = 0.85f, stiffness = 380f)) }
            }
        }
    }

    // Save the photo or video on screen to the phone's gallery (the original file, unchanged)
    var isSaving by remember { mutableStateOf(false) }
    fun saveToGallery() {
        if (isSaving) return
        val url = currentUrl
        isSaving = true
        scope.launch {
            val saved = com.example.util.MediaSaver.saveToGallery(context, url)
            isSaving = false
            Toast.makeText(
                context,
                when {
                    !saved -> "Couldn't save. Check your connection and try again."
                    com.example.util.MediaSaver.looksLikeVideo(url) -> "Video saved to gallery"
                    else -> "Photo saved to gallery"
                },
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    // Android 9 and older need the storage permission first
    val storagePermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            saveToGallery()
        } else {
            Toast.makeText(context, "Storage permission is needed to save", Toast.LENGTH_SHORT).show()
        }
    }
    fun saveImageToGallery() {
        val allowed = !com.example.util.MediaSaver.needsStoragePermission ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (allowed) {
            saveToGallery()
        } else {
            storagePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
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

    // Parallax depth dismiss physics: quadratic scrim fade, scale down 1.0 -> 0.75, corner rounding 0dp -> 32dp
    val dismissProgress = remember(swipeOffsetY) {
        (swipeOffsetY.coerceAtLeast(0f) / 480f).coerceIn(0f, 1f)
    }
    val backgroundAlpha = remember(dismissProgress) {
        (1f - dismissProgress * dismissProgress).coerceIn(0.05f, 1f)
    }
    val dismissScale = remember(dismissProgress) {
        androidx.compose.ui.util.lerp(1.0f, 0.75f, dismissProgress)
    }
    val dismissCornerRadius = remember(dismissProgress) {
        androidx.compose.ui.unit.lerp(0.dp, 32.dp, dismissProgress)
    }

    Dialog(
        onDismissRequest = closeAnimated,
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
                // The black backdrop fades in with the zoom; no platform dim popping in first
                window.setDimAmount(0f)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onPlaced { viewerCoords[0] = it }
                // Growing out of a photo, the photo shows at once and only the black fades in
                .graphicsLayer {
                    alpha = if (openedTile != null) (entrance.value * 3f).coerceIn(0f, 1f) else entrance.value.coerceIn(0f, 1f)
                }
                .drawBehind {
                    val fade = if (openedTile != null) entrance.value.coerceIn(0f, 1f) else 1f
                    drawRect(Color.Black.copy(alpha = backgroundAlpha * fade))
                }
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
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val t = entrance.value
                        // Back into the photo only while still on it (after a swipe to another one,
                        // it zooms away instead)
                        val from = if (pagerState.currentPage == initialIdx) tileRectInViewer() else null
                        if (from != null && size.width > 0f && size.height > 0f) {
                            // At the start the photo (fitted to the screen) is exactly the tile's size,
                            // over the tile, cut to the tile's rounded shape
                            val s0 = maxOf(from.width / size.width, from.height / size.height)
                            val zoom = s0 + (1f - s0) * t
                            scaleX = zoom
                            scaleY = zoom
                            transformOrigin = TransformOrigin.Center
                            translationX = (from.center.x - size.width / 2f) * (1f - t)
                            translationY = (from.center.y - size.height / 2f) * (1f - t)
                            val open = t.coerceIn(0f, 1f)
                            if (open < 0.999f) {
                                val startW = from.width / s0
                                val startH = from.height / s0
                                shape = CenteredRoundRect(
                                    width = startW + (size.width - startW) * open,
                                    height = startH + (size.height - startH) * open,
                                    radius = 14.dp.toPx() / s0 * (1f - open)
                                )
                                clip = true
                            } else {
                                clip = false
                            }
                        } else {
                            val zoom = 0.18f + 0.82f * t
                            scaleX = zoom
                            scaleY = zoom
                            translationX = 0f
                            translationY = 0f
                            clip = false
                            transformOrigin = openFrom
                        }
                    }
            ) { page ->
                val pageUrl = mediaList.getOrNull(page) ?: currentUrl
                val isVideo = remember(pageUrl) {
                    pageUrl.contains(".mp4", ignoreCase = true) ||
                    pageUrl.contains(".mov", ignoreCase = true) ||
                    pageUrl.contains(".mkv", ignoreCase = true) ||
                    pageUrl.contains(".webm", ignoreCase = true) ||
                    pageUrl.contains("videonote", ignoreCase = true) ||
                    pageUrl.contains("video_note", ignoreCase = true) ||
                    pageUrl.contains("/videos/", ignoreCase = true) ||
                    pageUrl.contains("videos%2F", ignoreCase = true) ||
                    pageUrl.contains("video", ignoreCase = true) ||
                    (pageUrl.startsWith("content://") && pageUrl.contains("video", ignoreCase = true)) ||
                    (pageUrl.startsWith("data:video"))
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { containerSize = it }
                        .then(
                            if (page == pagerState.currentPage && !isVideo) {
                                Modifier.pointerInput(page) {
                                    var lastTapTime = 0L
                                    var lastTapOffset = Offset.Zero
                                    var tapJob: kotlinx.coroutines.Job? = null

                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        val startTime = System.currentTimeMillis()
                                        var isMultiTouch = false
                                        var previousDistance = 0f
                                        var lastCentroid = Offset.Zero
                                        var totalX = 0f
                                        var totalY = 0f
                                        var decidedDirection = false
                                        var isVerticalSwipeDismiss = false
                                        var overscrollX = 0f
                                        // The finger's speed: a quick flick down closes, like a long pull
                                        val velocityTracker = androidx.compose.ui.input.pointer.util.VelocityTracker()

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val activeChanges = event.changes.filter { it.pressed }
                                            if (activeChanges.isEmpty()) break

                                            if (activeChanges.size >= 2) {
                                                // Multi-finger pinch-to-zoom & focal panning
                                                isMultiTouch = true
                                                val p0 = activeChanges[0].position
                                                val p1 = activeChanges[1].position
                                                val currentDistance = hypot(p0.x - p1.x, p0.y - p1.y)
                                                val currentCentroid = (p0 + p1) / 2f

                                                if (previousDistance > 0f && currentDistance > 0f) {
                                                    val zoomDelta = currentDistance / previousDistance
                                                    val oldScale = scaleAnim.value

                                                    // Elastic zoom scale with resistance beyond [1.0, 5.0]
                                                    var newScale = oldScale * zoomDelta
                                                    if (newScale < 1.0f) {
                                                        val under = 1.0f - newScale
                                                        newScale = 1.0f - (under * 0.45f)
                                                        newScale = newScale.coerceAtLeast(0.7f)
                                                    } else if (newScale > 5.0f) {
                                                        val over = newScale - 5.0f
                                                        newScale = 5.0f + (over * 0.45f)
                                                        newScale = newScale.coerceAtMost(6.5f)
                                                    }

                                                    val effectiveDelta = if (oldScale > 0f) newScale / oldScale else 1f
                                                    val center = Offset(containerSize.width / 2f, containerSize.height / 2f)

                                                    // Precise focal point zoom formula: keep centroid anchored under fingers
                                                    val fOld = lastCentroid - center
                                                    val fNew = currentCentroid - center
                                                    val oldOffset = Offset(offsetXAnim.value, offsetYAnim.value)
                                                    val newOffset = fNew - (fOld - oldOffset) * effectiveDelta

                                                    scope.launch {
                                                        scaleAnim.snapTo(newScale)
                                                        offsetXAnim.snapTo(newOffset.x)
                                                        offsetYAnim.snapTo(newOffset.y)
                                                    }
                                                }
                                                previousDistance = currentDistance
                                                lastCentroid = currentCentroid
                                                activeChanges.forEach { it.consume() }

                                            } else if (activeChanges.size == 1 && !isMultiTouch) {
                                                // Single finger interaction
                                                val change = activeChanges[0]
                                                val delta = change.positionChange()
                                                totalX += delta.x
                                                totalY += delta.y
                                                velocityTracker.addPosition(change.uptimeMillis, change.position)

                                                if (scaleAnim.value > 1.05f) {
                                                    // Zoomed in: pan image with rubber-band edge resistance
                                                    val curScale = scaleAnim.value
                                                    val bounds = calculateMaxBounds(curScale, containerSize, currentImageSize)
                                                    val curX = offsetXAnim.value
                                                    val curY = offsetYAnim.value

                                                    val newX = if (bounds.x > 0f) {
                                                        if (curX > bounds.x && delta.x > 0f) curX + delta.x * 0.35f
                                                        else if (curX < -bounds.x && delta.x < 0f) curX + delta.x * 0.35f
                                                        else curX + delta.x
                                                    } else curX

                                                    val newY = if (bounds.y > 0f) {
                                                        if (curY > bounds.y && delta.y > 0f) curY + delta.y * 0.35f
                                                        else if (curY < -bounds.y && delta.y < 0f) curY + delta.y * 0.35f
                                                        else curY + delta.y
                                                    } else curY

                                                    // Track overscroll at horizontal bounds for paging
                                                    if (bounds.x > 0f) {
                                                        if (newX > bounds.x) overscrollX += (newX - bounds.x)
                                                        else if (newX < -bounds.x) overscrollX += (newX - (-bounds.x))
                                                        else overscrollX = 0f
                                                    }

                                                    scope.launch {
                                                        offsetXAnim.snapTo(newX)
                                                        offsetYAnim.snapTo(newY)
                                                    }
                                                    change.consume()
                                                } else {
                                                    // Normal 1.0x scale: Horizontal swipe for pager or vertical pull-down dismiss
                                                    if (!decidedDirection) {
                                                        if (abs(totalX) > 20f && abs(totalX) > abs(totalY)) {
                                                            decidedDirection = true
                                                            isVerticalSwipeDismiss = false
                                                        } else if (totalY > 30f && totalY > abs(totalX) * 1.5f) {
                                                            decidedDirection = true
                                                            isVerticalSwipeDismiss = true
                                                        }
                                                    }

                                                    if (decidedDirection && isVerticalSwipeDismiss) {
                                                        change.consume()
                                                        swipeOffsetY = totalY
                                                    }
                                                }
                                            }
                                        }

                                        // Gesture finished (fingers lifted)
                                        val elapsed = System.currentTimeMillis() - startTime
                                        val dist = hypot(totalX, totalY)

                                        if (isMultiTouch) {
                                            // End of pinch gesture: spring back smoothly if scale < 1.0x or > 5.0x, or offset out of bounds
                                            val curScale = scaleAnim.value
                                            val targetScale = curScale.coerceIn(1.0f, 5.0f)
                                            val bounds = calculateMaxBounds(targetScale, containerSize, currentImageSize)
                                            val targetX = if (targetScale <= 1.05f) 0f else offsetXAnim.value.coerceIn(-bounds.x, bounds.x)
                                            val targetY = if (targetScale <= 1.05f) 0f else offsetYAnim.value.coerceIn(-bounds.y, bounds.y)

                                            scope.launch {
                                                launch { scaleAnim.animateTo(targetScale, spring(dampingRatio = 0.82f, stiffness = 380f)) }
                                                launch { offsetXAnim.animateTo(targetX, spring(dampingRatio = 0.82f, stiffness = 380f)) }
                                                launch { offsetYAnim.animateTo(targetY, spring(dampingRatio = 0.82f, stiffness = 380f)) }
                                            }
                                        } else {
                                            // Tap / Double-tap detection
                                            if (dist < 30f && elapsed < 350L) {
                                                val now = System.currentTimeMillis()
                                                val tapPos = down.position
                                                if (now - lastTapTime < 320L && (tapPos - lastTapOffset).getDistance() < 100f) {
                                                    // Double tap: cancel pending single-tap and toggle zoom
                                                    tapJob?.cancel()
                                                    tapJob = null
                                                    lastTapTime = 0L
                                                    if (doubleTapZoom) {
                                                        toggleMaxMinZoom(tapPos)
                                                    }
                                                } else {
                                                    lastTapTime = now
                                                    lastTapOffset = tapPos
                                                    tapJob?.cancel()
                                                    tapJob = scope.launch {
                                                        kotlinx.coroutines.delay(260L)
                                                        isChromeVisible = !isChromeVisible
                                                    }
                                                }
                                            }

                                            // Single finger release when zoomed in: fling or spring back to bounds
                                            if (scaleAnim.value > 1.05f) {
                                                val curScale = scaleAnim.value
                                                val bounds = calculateMaxBounds(curScale, containerSize, currentImageSize)
                                                val velocity = velocityTracker.calculateVelocity()
                                                val targetX = offsetXAnim.value.coerceIn(-bounds.x, bounds.x)
                                                val targetY = offsetYAnim.value.coerceIn(-bounds.y, bounds.y)

                                                scope.launch {
                                                    if (offsetXAnim.value != targetX || offsetYAnim.value != targetY) {
                                                        launch {
                                                            offsetXAnim.animateTo(
                                                                targetValue = targetX,
                                                                initialVelocity = velocity.x,
                                                                animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
                                                            )
                                                        }
                                                        launch {
                                                            offsetYAnim.animateTo(
                                                                targetValue = targetY,
                                                                initialVelocity = velocity.y,
                                                                animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
                                                            )
                                                        }
                                                    } else if (hypot(velocity.x, velocity.y) > 120f) {
                                                        // Smooth momentum fling with decay
                                                        launch {
                                                            val decaySpec = exponentialDecay<Float>(frictionMultiplier = 1.3f)
                                                            var hitBoundary = false
                                                            try {
                                                                offsetXAnim.animateDecay(
                                                                    initialVelocity = velocity.x,
                                                                    animationSpec = decaySpec
                                                                ) {
                                                                    if (value < -bounds.x || value > bounds.x) {
                                                                        hitBoundary = true
                                                                        throw kotlinx.coroutines.CancellationException()
                                                                    }
                                                                }
                                                            } catch (_: kotlinx.coroutines.CancellationException) {}
                                                            if (hitBoundary || offsetXAnim.value !in -bounds.x..bounds.x) {
                                                                offsetXAnim.animateTo(
                                                                    targetValue = offsetXAnim.value.coerceIn(-bounds.x, bounds.x),
                                                                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
                                                                )
                                                            }
                                                        }
                                                        launch {
                                                            val decaySpec = exponentialDecay<Float>(frictionMultiplier = 1.3f)
                                                            var hitBoundary = false
                                                            try {
                                                                offsetYAnim.animateDecay(
                                                                    initialVelocity = velocity.y,
                                                                    animationSpec = decaySpec
                                                                ) {
                                                                    if (value < -bounds.y || value > bounds.y) {
                                                                        hitBoundary = true
                                                                        throw kotlinx.coroutines.CancellationException()
                                                                    }
                                                                }
                                                            } catch (_: kotlinx.coroutines.CancellationException) {}
                                                            if (hitBoundary || offsetYAnim.value !in -bounds.y..bounds.y) {
                                                                offsetYAnim.animateTo(
                                                                    targetValue = offsetYAnim.value.coerceIn(-bounds.y, bounds.y),
                                                                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // Snap back near 1.0x to immediately re-enable pager scrolling
                                            if (scaleAnim.value < 1.05f) {
                                                scope.launch {
                                                    scaleAnim.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 380f))
                                                    offsetXAnim.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 380f))
                                                    offsetYAnim.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 380f))
                                                }
                                            }

                                            if (isVerticalSwipeDismiss) {
                                                val velocityY = velocityTracker.calculateVelocity().y
                                                val flicked = velocityY >= 1100.dp.toPx() && swipeOffsetY > 24.dp.toPx()
                                                val from = swipeOffsetY
                                                if (from > 160f || flicked) {
                                                    val flyTo = size.height.toFloat() + from
                                                    scope.launch {
                                                        androidx.compose.animation.core.animate(
                                                            initialValue = from,
                                                            targetValue = flyTo,
                                                            initialVelocity = velocityY.coerceAtLeast(1500.dp.toPx()),
                                                            animationSpec = spring(dampingRatio = 1f, stiffness = 120f)
                                                        ) { value, _ -> swipeOffsetY = value }
                                                    }
                                                    closeAnimated()
                                                } else if (from != 0f) {
                                                    scope.launch {
                                                        androidx.compose.animation.core.animate(
                                                            initialValue = from,
                                                            targetValue = 0f,
                                                            initialVelocity = velocityY,
                                                            animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f)
                                                        ) { value, _ -> swipeOffsetY = value }
                                                    }
                                                }
                                            }

                                            // If user swiped past the boundary while zoomed in, glide to next/previous photo
                                            if (overscrollX < -120f && pagerState.currentPage < mediaList.lastIndex) {
                                                scope.launch {
                                                    resetZoom(animated = false)
                                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                }
                                            } else if (overscrollX > 120f && pagerState.currentPage > 0) {
                                                scope.launch {
                                                    resetZoom(animated = false)
                                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                }
                                            }
                                        }
                                    }
                                }
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val isVideoNote = (isVideo || isCircularVideo || pageUrl in circularMediaUrls) && (
                        (pageUrl == mediaUrl && isCircularVideo) ||
                        pageUrl in circularMediaUrls ||
                        pageUrl.contains("videonote", ignoreCase = true) ||
                        pageUrl.contains("video_note", ignoreCase = true)
                    )
                    if (isVideoNote) {
                        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                        val screenWidth = configuration.screenWidthDp.dp
                        val screenHeight = configuration.screenHeightDp.dp
                        // Maximize circular video player: make it big, filling almost the entire screen width
                        // with a small clean margin, while fitting comfortably vertically above bottom controls
                        val maxAllowedDiameter = minOf(screenWidth - 16.dp, screenHeight - 140.dp)
                        val playerDiameter = maxAllowedDiameter.coerceAtLeast(340.dp)
                        // No border: the note's own progress ring is its one ring
                        Box(
                            modifier = Modifier
                                .size(playerDiameter)
                                .clip(CircleShape)
                                .background(Slate900),
                            contentAlignment = Alignment.Center
                        ) {
                        val isCurrentPage = page == pagerState.currentPage
                        com.example.ui.components.CircularVideoNoteView(
                            videoUrl = pageUrl,
                            autoPlay = isCurrentPage,
                            shape = CircleShape,
                            cropToFill = true,
                            // While the big circle plays, the bar and buttons go with its own
                            // buttons; a tap on it brings them all back
                            onControlsVisibleChanged = { visible -> if (isCurrentPage) isChromeVisible = visible },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else if (isVideo) {
                    // A shared video: full width, whole picture shown
                    val isCurrentPage = page == pagerState.currentPage
                    com.example.ui.components.CircularVideoNoteView(
                        videoUrl = pageUrl,
                        autoPlay = isCurrentPage,
                        shape = androidx.compose.ui.graphics.RectangleShape,
                        cropToFill = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.8f)
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
                            onState = { state ->
                                if (state is coil.compose.AsyncImagePainter.State.Success) {
                                    val intrinsic = state.painter.intrinsicSize
                                    if (intrinsic.width > 0f && intrinsic.height > 0f && !intrinsic.width.isNaN() && !intrinsic.height.isNaN()) {
                                        currentImageSize = intrinsic
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(dismissCornerRadius))
                                .graphicsLayer {
                                    if (page == pagerState.currentPage) {
                                        // The breath settles as soon as the photo is zoomed or pulled down
                                        val calm = (1f - (scaleAnim.value - 1f).coerceAtLeast(0f) / 0.05f).coerceIn(0f, 1f) *
                                            (1f - dismissProgress)
                                        val breathScale = 1f + AVATAR_BREATH_SCALE * breath() * calm
                                        scaleX = scaleAnim.value * dismissScale * breathScale
                                        scaleY = scaleAnim.value * dismissScale * breathScale
                                        translationX = offsetXAnim.value
                                        translationY = offsetYAnim.value + swipeOffsetY
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
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
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
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = "Next photo",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // Kisses blooming on the profile photo
            if (isProfilePhoto || breathing) {
                KissStampLayer(
                    blooms = profileKisses,
                    partnerName = partnerName ?: "Partner",
                    onDone = { bloom -> profileKisses = profileKisses.filter { it.id != bloom.id } },
                    modifier = Modifier.fillMaxSize()
                )
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            // Leaves the buttons on the right their room; a long date shortens instead
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .pressScaleTarget()
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.50f))
                                    .springClickable { closeAnimated() }
                                    .testTag("full_screen_media_close"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Black.copy(alpha = 0.50f),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                // Which one of how many ("88 of 100"), and when it was sent
                                val sentAt = remember(currentUrl, mediaTimes) {
                                    mediaTimes[currentUrl]?.let { com.example.util.ChatTimeFormatter.formatDayAndTime(it) }
                                }
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                                    Text(
                                        text = if (mediaList.size > 1) "${currentIndex + 1} of ${mediaList.size}" else "Photo View",
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        modifier = Modifier.testTag("full_screen_media_count")
                                    )
                                    if (!sentAt.isNullOrEmpty()) {
                                        Text(
                                            text = sentAt,
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier = Modifier.testTag("full_screen_media_time")
                                        )
                                    }
                                }
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
                                        .pressScaleTarget()
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.50f))
                                        .springClickable {
                                            onShowInChat(currentUrl)
                                            onDismiss()
                                        }
                                        .testTag("full_screen_media_show_in_chat"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Chat,
                                        contentDescription = "Chat",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }


                            // Download / Save to Gallery
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .pressScaleTarget()
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.50f))
                                    .springClickable { saveImageToGallery() }
                                    .testTag("full_screen_media_download"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
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
                                        .pressScaleTarget()
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.50f))
                                        .springClickable { showDeleteConfirmDialog = true }
                                        .testTag("full_screen_media_delete"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "Delete",
                                        tint = Gray400,
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
                CherishAlertDialog(
                    onDismissRequest = { showDeleteConfirmDialog = false },
                    title = { Text("Delete photo?") },
                    text = { Text("This photo will be permanently deleted from the gallery and chat.") },
                    confirmButton = {
                        CherishButton(
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
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                            )
                        )
                        .navigationBarsPadding()
                        .padding(bottom = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Thumbnail filmstrip preview for fast photo browsing (starts from center of screen)
                    if (mediaList.size > 1) {
                        LazyRow(
                            state = filmstripListState,
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            itemsIndexed(mediaList) { idx, url ->
                                val isSelected = idx == currentIndex
                                val isVid = url.endsWith(".mp4", true) || url.contains("videonote", true)
                                val thumbData = remember(url) {
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
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.Black.copy(alpha = 0.65f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        if (isSelected) 2.5.dp else 1.dp,
                                        if (isSelected) RoseGoldPrimary else Color.White.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier
                                        .size(52.dp)
                                        .pressScaleTarget()
                                        .clip(RoundedCornerShape(12.dp))
                                        .springClickable {
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
                                                .data(thumbData)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "Photo thumbnail $idx",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        if (isVid) {
                                            Icon(
                                                imageVector = Icons.Rounded.PlayArrow,
                                                contentDescription = "Video",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isProfilePhoto || breathing) {
                        AnimatedVisibility(
                            visible = kissFeedbackText != null,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.Black.copy(alpha = 0.78f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0475F).copy(alpha = 0.6f)),
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Text(
                                    text = kissFeedbackText.orEmpty(),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            // Romantic Kiss Profile button
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = if (isUserMale) Color(0xFFC76270).copy(alpha = 0.88f) else Color(0xFFE0475F).copy(alpha = 0.88f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .pressScaleTarget()
                                    .springClickable { kissProfile() }
                                    .testTag("kiss_profile_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                                ) {
                                    Text(if (isUserMale) "👄" else "💋", fontSize = 17.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Kiss ${partnerName?.trim()?.substringBefore(' ') ?: "Profile"}",
                                        color = Color.White,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Full Kiss Screen button & indication
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = Color(0xFF6B1832).copy(alpha = 0.92f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0475F).copy(alpha = 0.65f)),
                                modifier = Modifier
                                    .pressScaleTarget()
                                    .springClickable {
                                        if (onOpenKissScreen != null) {
                                            closeAnimated()
                                            onOpenKissScreen()
                                        } else {
                                            showKissPadInViewer = true
                                        }
                                    }
                                    .testTag("kiss_screen_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                                ) {
                                    Text(if (isPartnerMale) "👄" else "💋", fontSize = 17.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Kiss Screen ${if (isPartnerMale) "👄" else "💋"}",
                                        color = Color.White,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Zoom Max & Min Controls Pill (elevated safely above native gesture bar)
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
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
                                    imageVector = Icons.Rounded.ZoomOut,
                                    contentDescription = "Zoom Out (Min Size)",
                                    tint = if (scale > 1.05f) Color.White else Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Current Zoom percentage & quick reset
                            Box(
                                modifier = Modifier
                                    .pressScaleTarget()
                                    .clip(RoundedCornerShape(12.dp))
                                    .springClickable { resetZoom() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
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
                                    imageVector = Icons.Rounded.ZoomIn,
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
                }
            }

            SecretWindowGuard(ownDialogOpen = showDeleteConfirmDialog)

            if (showKissPadInViewer) {
                val appAuth = (context.applicationContext as? CherishApplication)?.authRepository
                val pTouch by (appAuth?.partnerKissTouch ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState()
                KissPadDialog(
                    partnerName = partnerName ?: "Partner",
                    partnerTouch = pTouch,
                    onTouch = { x, y -> appAuth?.setKissTouch(x, y) },
                    onDismiss = { showKissPadInViewer = false },
                    userIsMale = isUserMale,
                    partnerIsMale = isPartnerMale
                )
            }
        }
    }
}

/**
 * The photo (a chat bubble or a gallery tile) that was just tapped to open, so the viewer can grow
 * out of it. The viewer takes it once; a tap from more than a moment ago doesn't count.
 */
object MediaOpenOrigin {
    private var tile: LayoutCoordinates? = null
    private var at = 0L

    fun record(coordinates: LayoutCoordinates?) {
        tile = coordinates
        at = android.os.SystemClock.uptimeMillis()
    }

    fun take(): LayoutCoordinates? {
        val picked = tile?.takeIf { android.os.SystemClock.uptimeMillis() - at < 1500 && it.isAttached }
        tile = null
        return picked
    }
}

/** Where a photo sits on screen; [open] tells the viewer, just before opening it. */
class MediaTileAnchor {
    var coordinates: LayoutCoordinates? = null
    fun open() = MediaOpenOrigin.record(coordinates)
}

fun Modifier.mediaTileAnchor(anchor: MediaTileAnchor): Modifier = onPlaced { anchor.coordinates = it }

/** A tappable photo that the viewer grows out of when it opens. */
fun Modifier.openableMedia(
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onPress: (suspend androidx.compose.foundation.gestures.PressGestureScope.(androidx.compose.ui.geometry.Offset) -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val anchor = remember { MediaTileAnchor() }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentOnDoubleClick by rememberUpdatedState(onDoubleClick)
    val currentOnPress by rememberUpdatedState(onPress)
    mediaTileAnchor(anchor).pointerInput(Unit) {
        detectTapGestures(
            onPress = { offset ->
                val press = currentOnPress
                if (press != null) {
                    press.invoke(this, offset)
                } else {
                    tryAwaitRelease()
                }
            },
            onTap = {
                anchor.open()
                currentOnClick()
            },
            onLongPress = {
                currentOnLongClick?.invoke()
            },
            onDoubleTap = {
                currentOnDoubleClick?.invoke()
            }
        )
    }
}

/** When each photo or video of [messages] was sent, by its URL: the viewer's date line. */
internal fun mediaTimesOf(messages: List<com.example.data.model.Message>): Map<String, Long> {
    val times = HashMap<String, Long>()
    messages.forEach { message ->
        message.getAllMediaUrls().forEach { url -> times.getOrPut(url) { message.timestamp } }
    }
    return times
}

/** A rounded rectangle [width] x [height] (px) in the middle of the layer. */
private class CenteredRoundRect(
    private val width: Float,
    private val height: Float,
    private val radius: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val left = (size.width - width) / 2f
        val top = (size.height - height) / 2f
        val r = radius.coerceAtLeast(0f)
        return Outline.Rounded(RoundRect(left, top, left + width, top + height, CornerRadius(r, r)))
    }
}
