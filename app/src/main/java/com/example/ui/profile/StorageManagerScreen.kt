package com.example.ui.profile

import com.example.ui.theme.darkTone

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StorageBreakdown
import com.example.ui.theme.DarkAubergine
import com.example.ui.theme.RoseGoldPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.example.ui.theme.Amber600
import com.example.ui.theme.Blue300
import com.example.ui.theme.Blue900
import com.example.ui.theme.Green50
import com.example.ui.theme.Green800
import com.example.ui.components.CherishToast
import com.example.ui.components.CherishButton
import com.example.ui.components.CherishOutlinedButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageManagerScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var cacheSizeBytes by remember { mutableLongStateOf(0L) }
    var photoSizeBytes by remember { mutableLongStateOf(0L) }
    var videoSizeBytes by remember { mutableLongStateOf(0L) }
    var voiceSizeBytes by remember { mutableLongStateOf(0L) }
    var isCleaning by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    suspend fun loadStorageStats() = withContext(Dispatchers.IO) {
        val breakdown = computeActualStorage(context)
        cacheSizeBytes = breakdown.cacheBytes
        photoSizeBytes = breakdown.photosBytes
        videoSizeBytes = breakdown.videosBytes
        voiceSizeBytes = breakdown.voiceBytes
        isLoading = false
    }

    LaunchedEffect(Unit) {
        loadStorageStats()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Storage & Cache Manager",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("storage_manager_back")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                loadStorageStats()
                                CherishToast.show("Storage refreshed ✨")
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Storage Overview Card
            val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            val totalBytes = photoSizeBytes + videoSizeBytes + voiceSizeBytes + cacheSizeBytes
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF7F6FB)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Total Space Used by Cherish",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    }
                    Text(
                        formatSize(totalBytes),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Dynamic Proportional Color Bar
                    if (totalBytes > 0L) {
                        val photoWeight = if (photoSizeBytes > 0) (photoSizeBytes.toFloat() / totalBytes).coerceAtLeast(0.04f) else 0f
                        val videoWeight = if (videoSizeBytes > 0) (videoSizeBytes.toFloat() / totalBytes).coerceAtLeast(0.04f) else 0f
                        val voiceWeight = if (voiceSizeBytes > 0) (voiceSizeBytes.toFloat() / totalBytes).coerceAtLeast(0.04f) else 0f
                        val cacheWeight = if (cacheSizeBytes > 0) (cacheSizeBytes.toFloat() / totalBytes).coerceAtLeast(0.04f) else 0f

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            if (photoWeight > 0f) Box(modifier = Modifier.weight(photoWeight).fillMaxHeight().background(RoseGoldPrimary))
                            if (videoWeight > 0f) Box(modifier = Modifier.weight(videoWeight).fillMaxHeight().background(Color(0xFF00ACC1)))
                            if (voiceWeight > 0f) Box(modifier = Modifier.weight(voiceWeight).fillMaxHeight().background(Amber600))
                            if (cacheWeight > 0f) Box(modifier = Modifier.weight(cacheWeight).fillMaxHeight().background(Color(0xFF9E9E9E)))
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StorageLegend(color = RoseGoldPrimary, label = "Photos", size = formatSize(photoSizeBytes))
                        StorageLegend(color = Color(0xFF00ACC1), label = "Videos", size = formatSize(videoSizeBytes))
                        StorageLegend(color = Amber600, label = "Voice", size = formatSize(voiceSizeBytes))
                        StorageLegend(color = Color(0xFF9E9E9E), label = "Cache", size = formatSize(cacheSizeBytes))
                    }
                }
            }

            // Quick Cleanup Actions
            Text("Manage & Optimize", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isDark) darkTone(Blue900) else Green50),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.CleaningServices, contentDescription = null, tint = if (isDark) darkTone(Blue300) else Green800, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Clear Cached Media", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Free compressed image caches & previews without deleting chats", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        CherishButton(
                            onClick = {
                                coroutineScope.launch {
                                    isCleaning = true
                                    val freed = performSafeCacheClean(context)
                                    loadStorageStats()
                                    isCleaning = false
                                    val freedText = if (freed > 0L) "Freed ${formatSize(freed)}! ✨" else "Cache is already clean ✨"
                                    CherishToast.show(freedText)
                                }
                            },
                            enabled = !isCleaning,
                            colors = ButtonDefaults.buttonColors(containerColor = RoseGoldPrimary),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            if (isCleaning) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cleaning...", fontSize = 12.sp)
                            } else {
                                Text("Clear", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.DownloadDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Clean Update Installers", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Remove saved update packages to reclaim storage", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        CherishOutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    val updatesDir = File(context.cacheDir, "updates")
                                    val freed = getDirSize(updatesDir)
                                    withContext(Dispatchers.IO) {
                                        deleteDirContents(updatesDir)
                                    }
                                    loadStorageStats()
                                    CherishToast.show(if (freed > 0L) "Freed ${formatSize(freed)} from updates" else "No installer files to clean")
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Clean", fontSize = 12.sp)
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Security, contentDescription = null, tint = RoseGoldPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Safe Storage Guarantee", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        "• All photos, notes, and voice clips are stored inside Cherish's private encrypted app sandbox.\n" +
                        "• Hidden with .nomedia so external Android gallery or file scanners cannot see them.\n" +
                        "• Clearing cache never touches your cloud sync, database or actual chat messages.\n" +
                        "• Keystores and private encryption keys remain 100% secure and untouched.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageLegend(color: Color, label: String, size: String) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(size, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun computeActualStorage(context: Context): StorageBreakdown {
    val cacheDir = context.cacheDir
    val filesDir = context.filesDir

    val compressedMediaSize = com.example.util.StorageOptimizer.getDirSize(File(cacheDir, "compressed_media"))
    val cameraSnapsSize = com.example.util.StorageOptimizer.getDirSize(File(cacheDir, "camera_snaps"))
    val avatarSnapsSize = com.example.util.StorageOptimizer.getDirSize(File(cacheDir, "avatar_snaps"))
    val avatarFilesSize = com.example.util.StorageOptimizer.getDirSize(File(filesDir, "avatars"))
    val photosDirSize = com.example.util.StorageOptimizer.getDirSize(File(filesDir, "photos"))
    val extPicturesSize = com.example.util.StorageOptimizer.getDirSize(File(context.getExternalFilesDir(null), "Pictures"))
    val totalPhotos = compressedMediaSize + cameraSnapsSize + avatarSnapsSize + avatarFilesSize + photosDirSize + extPicturesSize

    val voiceNotesSize = com.example.util.StorageOptimizer.getDirSize(File(cacheDir, "voice_notes"))
    val voiceCacheSize = com.example.util.StorageOptimizer.getDirSize(File(cacheDir, "voice_cache"))
    val totalVoice = voiceNotesSize + voiceCacheSize

    val videosCacheSize = com.example.util.StorageOptimizer.getDirSize(File(cacheDir, "videos")) + com.example.util.StorageOptimizer.getDirSize(File(cacheDir, "video_notes"))
    val videosFilesSize = com.example.util.StorageOptimizer.getDirSize(File(filesDir, "videos"))
    val extMoviesSize = com.example.util.StorageOptimizer.getDirSize(File(context.getExternalFilesDir(null), "Movies"))
    val totalVideos = videosCacheSize + videosFilesSize + extMoviesSize

    val totalAppCache = com.example.util.StorageOptimizer.getDirSize(cacheDir)
    val generalCache = (totalAppCache - compressedMediaSize - cameraSnapsSize - avatarSnapsSize - voiceNotesSize - voiceCacheSize - videosCacheSize).coerceAtLeast(0L)

    return StorageBreakdown(
        photosBytes = totalPhotos,
        videosBytes = totalVideos,
        voiceBytes = totalVoice,
        cacheBytes = generalCache
    )
}

private suspend fun performSafeCacheClean(context: Context): Long {
    return com.example.util.StorageOptimizer.performSafeCacheClean(context)
}

private fun getDirSize(dir: File?): Long {
    return com.example.util.StorageOptimizer.getDirSize(dir)
}

private fun deleteDirContents(dir: File?): Boolean {
    return com.example.util.StorageOptimizer.deleteDirContents(dir)
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> "%.1f GB".format(bytes.toDouble() / (1024 * 1024 * 1024))
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes.toDouble() / (1024 * 1024))
        bytes >= 1024 -> "%.1f KB".format(bytes.toDouble() / 1024)
        else -> "$bytes B"
    }
}



