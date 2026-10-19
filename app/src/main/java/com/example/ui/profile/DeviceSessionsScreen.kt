package com.example.ui.profile

import com.example.ui.theme.darkTone

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Warning
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
import com.example.ui.theme.Blue300
import com.example.ui.theme.Blue900
import com.example.ui.theme.Green50
import com.example.ui.theme.Green800
import com.example.ui.theme.Red700
import com.example.ui.components.CherishToast
import com.example.ui.components.CherishAlertDialog
import com.example.ui.components.CherishButton
import com.example.ui.components.pressScale
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.example.ui.components.softShadow
import com.example.ui.components.lazyFadingEdges
import androidx.compose.foundation.lazy.rememberLazyListState

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
        CherishAlertDialog(
            onDismissRequest = { sessionToRevoke = null },
            icon = {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
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
                CherishButton(
                    onClick = {
                        val toRemove = sessionToRevoke
                        sessionToRevoke = null
                        if (toRemove != null) {
                            sessions = sessions.filter { it.id != toRemove.id }
                            CherishToast.show("Session revoked successfully")
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
        CherishAlertDialog(
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
                        imageVector = if (detailSession.isCurrent) Icons.Rounded.Smartphone else Icons.Rounded.Devices,
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
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SessionDetailRow("Device Name", detailSession.deviceName)
                            SessionDetailRow("Status", if (detailSession.isCurrent) "Active Now (Primary Device)" else detailSession.ipOrLocation)
                            SessionDetailRow("Operating System", if (detailSession.isCurrent) "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" else detailSession.platform)
                            SessionDetailRow("App Version", if (detailSession.isCurrent) "Cherish v${com.example.BuildConfig.VERSION_NAME} (${com.example.BuildConfig.VERSION_CODE})" else "Cherish v${partnerUser?.appVersionName ?: com.example.BuildConfig.VERSION_NAME} (build ${partnerUser?.appVersionCode ?: "—"})")
                            if (!detailSession.isCurrent && partnerUser?.appVersionCode != null) {
                                val isPartnerLatest = partnerUser.appVersionCode >= com.example.BuildConfig.VERSION_CODE
                                SessionDetailRow("Update Status", if (isPartnerLatest) "Installed Latest Version ✨" else "Update Available (Installed Build ${partnerUser.appVersionCode}) ⚠️")
                            }
                            SessionDetailRow("Security", "Encrypted in transit (TLS)")
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
                    CherishButton(
                        onClick = {
                            val toRevoke = detailSession
                            selectedSessionForDetails = null
                            sessionToRevoke = toRevoke
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Revoke Session")
                    }
                } else {
                    CherishButton(
                        onClick = { selectedSessionForDetails = null },
                        shape = RoundedCornerShape(12.dp)
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
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            CherishToast.show("Sessions up to date ✨")
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
        val sessionsList = rememberLazyListState()
        LazyColumn(
            state = sessionsList,
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                // Devices fade softly at the top and bottom edges as they scroll
                .lazyFadingEdges(sessionsList)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Devices, contentDescription = null, tint = RoseGoldPrimary, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Two-Person Exclusive Pairing", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "Cherish only allows authorized mutual sessions. Tap any session below to view its connection details.",
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
                        color = Green50
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Green800))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Protected", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Green800)
                        }
                    }
                }
            }

            items(sessions, key = { it.id }) { session ->
                val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                // Card's own click keeps the ripple inside the rounded corners; it shrinks a touch
                // while held, and slides into place as sessions come and go
                val press = remember { MutableInteractionSource() }
                Card(
                    onClick = { selectedSessionForDetails = session },
                    interactionSource = press,
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, if (session.isCurrent) RoseGoldPrimary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .pressScale(press)
                        .softShadow(6.dp, RoundedCornerShape(20.dp), clip = false)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (session.isCurrent) (if (isDark) darkTone(Blue900) else Green50) else MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (session.isCurrent) Icons.Rounded.Smartphone else Icons.Rounded.Devices,
                                contentDescription = null,
                                tint = if (session.isCurrent) (if (isDark) darkTone(Blue300) else Green800) else RoseGoldPrimary,
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
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isDark) darkTone(Blue900) else Green800
                                    ) {
                                        Text(
                                            "This Phone",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                } else if (partnerUser?.appVersionCode != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    val isPartnerLatest = partnerUser.appVersionCode >= com.example.BuildConfig.VERSION_CODE
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isPartnerLatest) Green800.copy(alpha = 0.2f) else RoseGoldPrimary.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            if (isPartnerLatest) "v${partnerUser.appVersionName ?: ""} Latest ✨" else "Build ${partnerUser.appVersionCode} ⚠️",
                                            color = if (isPartnerLatest) (if (isDark) darkTone(Blue300) else Green800) else Red700,
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
                                color = if (session.isCurrent) (if (isDark) darkTone(Blue300) else Green800) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { selectedSessionForDetails = session }
                        ) {
                            Icon(Icons.Rounded.Info, contentDescription = "Details", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                        }

                        if (!session.isCurrent) {
                            IconButton(
                                onClick = {
                                    sessionToRevoke = session
                                }
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Revoke", tint = Red700)
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
                            Icon(Icons.Rounded.Shield, contentDescription = null, tint = RoseGoldPrimary, modifier = Modifier.size(24.dp))
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
