package com.example.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.CherishApplication
import com.example.share.PendingShares
import com.example.ui.components.CherishToast

/**
 * Things shared to "Notes" from other apps while the app was Notes: they wait here, above the
 * message box, until they're sent to the partner or dropped. Nothing shows when there are none.
 */
@Composable
fun PendingSharesBar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as? CherishApplication ?: return
    LaunchedEffect(Unit) { PendingShares.load(app) }
    val shares by PendingShares.items.collectAsState()
    var isSending by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = shares.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AttachFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                val count = shares.size
                Text(
                    text = if (count == 1) "1 shared item waiting" else "$count shared items waiting",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    enabled = !isSending,
                    onClick = {
                        PendingShares.remove(app, shares.map { it.id }.toSet())
                        CherishToast.show("Dropped (still in Notes)")
                    }
                ) {
                    Text("Discard", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(
                    enabled = !isSending,
                    onClick = {
                        isSending = true
                        PendingShares.send(app, shares) { sent, failed ->
                            isSending = false
                            CherishToast.show(
                                when {
                                    failed > 0 -> "Couldn't send $failed. Try again."
                                    sent == 1 -> "Sent ✨"
                                    else -> "Sent $sent ✨"
                                }
                            )
                        }
                    }
                ) {
                    Text(if (isSending) "Sending…" else "Send", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
