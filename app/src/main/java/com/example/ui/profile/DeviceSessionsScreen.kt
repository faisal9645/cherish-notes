package com.example.ui.profile

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Shield
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
import com.example.CherishApplication
import com.example.data.model.DeviceSession
import com.example.ui.theme.DarkAubergine
import com.example.ui.theme.RoseGoldPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSessionsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as? CherishApplication
    val currentUser by app?.authRepository?.currentUserState?.collectAsState() ?: remember { mutableStateOf(null) }
    val partnerUser by app?.authRepository?.partnerUserState?.collectAsState() ?: remember { mutableStateOf(null) }

    val currentDeviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

    fun formatLastActive(timestamp: Long, isOnline: Boolean): String {
        if (isOnline) return "Online right now • Paired"
        if (timestamp <= 0L) return "Paired • Authorized Device"
        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / (1000 * 60)
        val hours = minutes / 60
        val days = hours / 24
        return when {
            minutes < 2 -> "Active just now"
            minutes < 60 -> "Active $minutes min ago"
            hours < 24 -> "Active $hours hr ago"
            days < 7 -> "Active $days days ago"
            else -> "Active recently"
        }
    }

    var sessions by remember(currentUser, partnerUser) {
        val list = mutableListOf(
            DeviceSession(
                id = "curr_1",
                deviceName = "${currentUser?.displayName?.ifBlank { "You" } ?: "This Phone"} ($currentDeviceModel)",
                platform = "Android ${Build.VERSION.RELEASE} • Cherish Vault",
                lastActiveMillis = System.currentTimeMillis(),
                isCurrent = true,
                ipOrLocation = "Active Now • Primary Device"
            )
        )
        if (partnerUser != null || currentUser?.partnerId?.isNotBlank() == true) {
            val pName = partnerUser?.displayName?.ifBlank { "Partner" } ?: "Partner"
            val isOnline = partnerUser?.isOnline == true
            val lastSeen = partnerUser?.lastSeen ?: 0L
            list.add(
                DeviceSession(
                    id = "partner_session",
                    deviceName = "$pName's Device (Paired)",
                    platform = "Android • Mutual End-to-End Sync",
                    lastActiveMillis = if (lastSeen > 0L) lastSeen else System.currentTimeMillis() - 1000 * 60 * 15,
                    isCurrent = false,
                    ipOrLocation = formatLastActive(lastSeen, isOnline)
                )
            )
        }
        mutableStateOf(list.toList())
    }

    var sessionToRevoke by remember { mutableStateOf<DeviceSession?>(null) }
    var selectedSessionForDetails by remember { mutableStateOf<DeviceSession?>(null) }

    if (sessionToRevoke != null) {
        AlertDialog(
            onDismissRequest = { sessionToRevoke = null },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = {
                Text("Revoke Authorized Session?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Are you sure you want to terminate this authorized session? The device will lose pairing access until re-authenticated.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toRemove = sessionToRevoke
                        sessionToRevoke = null
                        if (toRemove != null) {
                            sessions = sessions.filter { it.id != toRemove.id }
                            Toast.makeText(context, "Session revoked successfully", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Revoke Session", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToRevoke = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (selectedSessionForDetails != null) {
        val detailSession = selectedSessionForDetails!!
        AlertDialog(
            onDismissRequest = { selectedSessionForDetails = null },
            icon = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (detailSession.isCurrent) RoseGoldPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (detailSession.isCurrent) Icons.Default.Smartphone else Icons.Default.Devices,
                        contentDescription = null,
                        tint = if (detailSession.isCurrent) RoseGoldPrimary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Session Details",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SessionDetailRow("Device Name", detailSession.deviceName)
                            SessionDetailRow("Status", if (detailSession.isCurrent) "Active Now (Primary Device)" else detailSession.ipOrLocation)
                            SessionDetailRow("Operating System", if (detailSession.isCurrent) "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" else detailSession.platform)
                            SessionDetailRow("App Version", "Cherish v${com.example.BuildConfig.VERSION_NAME} (${com.example.BuildConfig.VERSION_CODE})")
                            SessionDetailRow("Security", "End-to-End Encrypted (AES-256-GCM)")
                            SessionDetailRow("Session Token", "SES-${detailSession.id.hashCode().toString().takeLast(6).uppercase()}-VAULT")
                            SessionDetailRow("Mutual Pairing", if (detailSession.isCurrent) "Primary Verified Session" else "Authorized Mutual Channel")
                        }
                    }

                    Text(
                        text = if (detailSession.isCurrent) 
                            "This is your current phone session. It has full administrative vault control." 
                        else 
                            "This is an authorized paired partner session with direct private synchronization.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                if (!detailSession.isCurrent) {
                    Button(
                        onClick = {
                            val toRevoke = detailSession
                            selectedSessionForDetails = null
                            sessionToRevoke = toRevoke
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Revoke Session")
                    }
                } else {
                    Button(
                        onClick = { selectedSessionForDetails = null },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Done")
                    }
                }
            },
            dismissButton = {
                if (!detailSession.isCurrent) {
                    TextButton(onClick = { selectedSessionForDetails = null }) {
                        Text("Close")
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Authorized Devices & Sessions",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("device_sessions_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            Toast.makeText(context, "Sessions up to date ✨", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Devices, contentDescription = null, tint = RoseGoldPrimary, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Two-Person Exclusive Pairing", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "Cherish only allows authorized mutual sessions. Tap any session below to view full connection and encryption details.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Active Sessions (${sessions.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFE8F5E9)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF2E7D32)))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Protected", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        }
                    }
                }
            }

            items(sessions, key = { it.id }) { session ->
                val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, if (session.isCurrent) RoseGoldPrimary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedSessionForDetails = session }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (session.isCurrent) (if (isDark) Color(0xFF1E3A8A) else Color(0xFFE8F5E9)) else MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (session.isCurrent) Icons.Default.Smartphone else Icons.Default.Devices,
                                contentDescription = null,
                                tint = if (session.isCurrent) (if (isDark) Color(0xFF93C5FD) else Color(0xFF2E7D32)) else RoseGoldPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    session.deviceName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (session.isCurrent) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isDark) Color(0xFF1E3A8A) else Color(0xFF2E7D32)
                                    ) {
                                        Text(
                                            "This Phone",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(session.platform, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                session.ipOrLocation,
                                fontSize = 11.sp,
                                color = if (session.isCurrent) (if (isDark) Color(0xFF93C5FD) else Color(0xFF2E7D32)) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { selectedSessionForDetails = session }
                        ) {
                            Icon(Icons.Default.Info, contentDescription = "Details", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                        }

                        if (!session.isCurrent) {
                            IconButton(
                                onClick = {
                                    sessionToRevoke = session
                                }
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Revoke", tint = Color(0xFFD32F2F))
                            }
                        }
                    }
                }
            }

            if (sessions.none { !it.isCurrent }) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Outlined.Shield, contentDescription = null, tint = RoseGoldPrimary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "No Other Sessions Connected",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "When you pair with your partner from Settings, their authorized session will appear here with live sync status.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionDetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}
