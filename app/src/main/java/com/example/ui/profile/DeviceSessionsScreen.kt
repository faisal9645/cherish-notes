package com.example.ui.profile

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Devices
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
    val currentDeviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

    var sessions by remember {
        mutableStateOf(
            listOf(
                DeviceSession(
                    id = "curr_1",
                    deviceName = "$currentDeviceModel (This Phone)",
                    platform = "Android ${Build.VERSION.RELEASE}",
                    lastActiveMillis = System.currentTimeMillis(),
                    isCurrent = true,
                    ipOrLocation = "Active Now • Primary Device"
                ),
                DeviceSession(
                    id = "prev_2",
                    deviceName = "Partner's Phone (Paired)",
                    platform = "Android 14",
                    lastActiveMillis = System.currentTimeMillis() - 1000 * 60 * 18,
                    isCurrent = false,
                    ipOrLocation = "Active 18 mins ago"
                )
            )
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
                    color = Color(0xFFF7F6FB),
                    border = BorderStroke(1.dp, Color(0xFFECEAF3)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Devices, contentDescription = null, tint = RoseGoldPrimary, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Two-Person Exclusive Pairing", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DarkAubergine)
                            Text(
                                "Cherish only allows authorized mutual sessions. If an unrecognized device appears, revoke it immediately.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Text("Active Sessions", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DarkAubergine)
            }

            items(sessions, key = { it.id }) { session ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, if (session.isCurrent) RoseGoldPrimary.copy(alpha = 0.4f) else Color(0xFFF0F0F2)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (session.isCurrent) Color(0xFFE8F5E9) else Color(0xFFF4F4F8)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Smartphone,
                                contentDescription = null,
                                tint = if (session.isCurrent) Color(0xFF2E7D32) else RoseGoldPrimary,
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
                                    color = DarkAubergine
                                )
                                if (session.isCurrent) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF2E7D32)
                                    ) {
                                        Text(
                                            "This Phone",
                                            color = MaterialTheme.colorScheme.surface,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(session.platform, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(session.ipOrLocation, fontSize = 11.sp, color = if (session.isCurrent) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        if (!session.isCurrent) {
                            IconButton(
                                onClick = {
                                    sessions = sessions.filter { it.id != session.id }
                                    Toast.makeText(context, "Remote session revoked", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Revoke", tint = Color(0xFFD32F2F))
                            }
                        }
                    }
                }
            }
        }
    }
}


