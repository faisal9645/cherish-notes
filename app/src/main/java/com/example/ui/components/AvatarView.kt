package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.RoseGoldPrimary

@Composable
fun AvatarView(
    photoUrl: String?,
    name: String,
    size: Dp = 48.dp,
    isOnline: Boolean = false,
    showOnlineBadge: Boolean = true,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Glowing romantic ring around avatar
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        listOf(RoseGoldPrimary, MaterialTheme.colorScheme.secondary)
                    ),
                    shape = CircleShape
                )
                .padding(2.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!photoUrl.isNullOrEmpty()) {
                if (photoUrl.startsWith("data:image/")) {
                    val base64 = photoUrl.substringAfter("base64,")
                    val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "$name's avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val initial = name.trim().firstOrNull()?.uppercase() ?: "❤️"
                        Text(
                            text = initial,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = (size.value * 0.42f).sp,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                } else {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = "$name's avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                val initial = name.trim().firstOrNull()?.uppercase() ?: "❤️"
                Text(
                    text = initial,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = (size.value * 0.42f).sp,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        if (showOnlineBadge && isOnline) {
            Box(
                modifier = Modifier
                    .size((size.value * 0.28f).coerceAtLeast(10f).dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = (-1).dp, y = (-1).dp)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .background(OnlineGreen, CircleShape)
            )
        }
    }
}
