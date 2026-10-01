package com.example.ui.backup

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBackupScreen(
    viewModel: GoogleDriveBackupViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Google Drive Backup & Restore",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Safe Transfer to Any New Mobile",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("backup_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                windowInsets = WindowInsets.statusBars
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp)
        ) {
            // 1. Where Is It Stored in Google Drive? (Discretion Guarantee Card)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = SoftPinkSurfaceVariant),
                    border = BorderStroke(1.dp, SoftBorderOutline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(RoseGoldPrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = RoseGoldPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Where is it stored in Google Drive?",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkOnBackground
                                )
                                Text(
                                    text = "Location: Hidden AppData Space",
                                    fontSize = 11.sp,
                                    color = RoseGoldPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "• Hidden from Prying Eyes: Stored in your Google Drive's hidden Application Data folder.\n" +
                                    "• Invisible in Regular Drive: When you or anyone opens your Google Drive app or drive.google.com, it will NOT appear in recent files, folders, or search results.\n" +
                                    "• Web Verification: Only visible to you in Google Drive on desktop under: Settings (⚙️) → Manage Apps → Cherish.",
                            fontSize = 12.sp,
                            color = DarkOnBackground,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // 2. Backup Status & Account Info
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, Color(0xFFF0F0F2)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Connected Google Account",
                                fontSize = 12.sp,
                                color = DarkOnSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFE8F5E9)
                            ) {
                                Text(
                                    text = "Active & Authorized",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = uiState.driveAccountEmail,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkOnBackground
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color(0xFFF0F0F2))
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = "Last Cloud Backup", fontSize = 11.sp, color = DarkOnSurfaceVariant)
                                Text(text = uiState.lastBackupDate, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DarkOnBackground)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = "Items Protected", fontSize = 11.sp, color = DarkOnSurfaceVariant)
                                Text(text = "${uiState.totalItemsBackedUp} Items", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DarkOnBackground)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Status: ${uiState.statusMessage}",
                            fontSize = 11.sp,
                            color = DarkOnSurfaceVariant,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }

            // 3. Primary Actions: Backup Now & Restore
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { viewModel.backupNow() },
                        enabled = !uiState.isBackingUp && !uiState.isRestoring,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("backup_now_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (uiState.isBackingUp) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Backing up to Google Drive...")
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Backup Everything to Google Drive Now", fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = { showRestoreConfirmDialog = true },
                        enabled = !uiState.isBackingUp && !uiState.isRestoring,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("restore_now_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (uiState.isRestoring) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Restoring from Google Drive...")
                        } else {
                            Icon(Icons.Default.CloudDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Restore to This Mobile Device", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 4. Step-by-Step New Mobile Transfer Guide
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, Color(0xFFF0F0F2)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = RoseGoldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "How to Transfer to a New Mobile",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = DarkOnBackground
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val steps = listOf(
                            "Step 1" to "Tap 'Backup Everything Now' above to ensure your latest chats, gallery, and lifetime memories are saved in your Google Drive.",
                            "Step 2" to "On your new mobile phone, install the app and log in with your account (${uiState.driveAccountEmail}).",
                            "Step 3" to "Open this Google Drive Backup page and tap 'Restore to This Mobile Device'. Everything is seamlessly restored onto your new phone!"
                        )

                        steps.forEach { (step, text) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SoftPinkSurfaceVariant
                                ) {
                                    Text(
                                        text = step,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RoseGoldPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = text,
                                    fontSize = 12.sp,
                                    color = DarkOnBackground,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialog before restore
    if (showRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmDialog = false },
            title = { Text("Restore From Google Drive?", fontWeight = FontWeight.Bold) },
            text = {
                Text("This will download and merge your chat messages, photo gallery, and lifetime memories from your Google Drive backup onto this mobile device.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirmDialog = false
                        viewModel.restoreNow()
                    }
                ) {
                    Text("Restore Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}


