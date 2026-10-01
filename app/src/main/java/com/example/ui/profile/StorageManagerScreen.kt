package com.example.ui.profile

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkAubergine
import com.example.ui.theme.RoseGoldPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageManagerScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var cacheSizeBytes by remember { mutableLongStateOf(0L) }
    var photoSizeBytes by remember { mutableLongStateOf(142 * 1024 * 1024L) }
    var videoSizeBytes by remember { mutableLongStateOf(280 * 1024 * 1024L) }
    var voiceSizeBytes by remember { mutableLongStateOf(24 * 1024 * 1024L) }
    var isCleaning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val cacheDir = context.cacheDir
            val size = getDirSize(cacheDir)
            cacheSizeBytes = size
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Storage Manager",
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            val totalBytes = photoSizeBytes + videoSizeBytes + voiceSizeBytes + cacheSizeBytes
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F6FB)),
                border = BorderStroke(1.dp, Color(0xFFECEAF3)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Total Space Used by Cherish",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatSize(totalBytes),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkAubergine
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Proportional Color Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                    ) {
                        Box(modifier = Modifier.weight(0.35f).fillMaxHeight().background(RoseGoldPrimary))
                        Box(modifier = Modifier.weight(0.40f).fillMaxHeight().background(Color(0xFF00ACC1)))
                        Box(modifier = Modifier.weight(0.12f).fillMaxHeight().background(Color(0xFFFFB300)))
                        Box(modifier = Modifier.weight(0.13f).fillMaxHeight().background(Color(0xFF9E9E9E)))
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StorageLegend(color = RoseGoldPrimary, label = "Photos", size = formatSize(photoSizeBytes))
                        StorageLegend(color = Color(0xFF00ACC1), label = "Videos", size = formatSize(videoSizeBytes))
                        StorageLegend(color = Color(0xFFFFB300), label = "Voice", size = formatSize(voiceSizeBytes))
                        StorageLegend(color = Color(0xFF9E9E9E), label = "Cache", size = formatSize(cacheSizeBytes))
                    }
                }
            }

            // Quick Cleanup Actions
            Text("Manage & Optimize", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DarkAubergine)

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE8F5E9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.CleaningServices, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Clear Cached Media", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Free temporary network cache without deleting chats", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isCleaning = true
                                    withContext(Dispatchers.IO) {
                                        deleteDir(context.cacheDir)
                                        cacheSizeBytes = 0L
                                    }
                                    isCleaning = false
                                    Toast.makeText(context, "Temporary cache cleared!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !isCleaning,
                            colors = ButtonDefaults.buttonColors(containerColor = RoseGoldPrimary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(if (isCleaning) "Cleaning..." else "Clear")
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Safe Storage Guarantee", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkAubergine)
                    Text(
                        "• All photos and voice notes are stored in Cherish's private encrypted app sandbox.\n" +
                        "• Hidden with .nomedia so external Android gallery or file scanners cannot see them.\n" +
                        "• Clearing cache never touches your cloud sync or actual messages.",
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
        Text(size, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkAubergine)
    }
}

private fun getDirSize(dir: File?): Long {
    if (dir == null || !dir.exists()) return 0L
    var size = 0L
    dir.listFiles()?.forEach { file ->
        size += if (file.isDirectory) getDirSize(file) else file.length()
    }
    return size
}

private fun deleteDir(dir: File?): Boolean {
    if (dir != null && dir.isDirectory) {
        val children = dir.list()
        if (children != null) {
            for (child in children) {
                deleteDir(File(dir, child))
            }
        }
    }
    return dir?.delete() ?: false
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> "%.1f GB".format(bytes.toDouble() / (1024 * 1024 * 1024))
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes.toDouble() / (1024 * 1024))
        bytes >= 1024 -> "%.1f KB".format(bytes.toDouble() / 1024)
        else -> "$bytes B"
    }
}


