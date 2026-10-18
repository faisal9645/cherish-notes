package com.example.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.SecurityPreferences
import com.example.ui.chat.ChatFont
import com.example.ui.chat.ChatTextStyle
import com.example.ui.chat.EmojiText
import com.example.ui.components.CherishSlider
import com.example.ui.components.pressScaleTarget
import com.example.ui.components.springClickable
import kotlin.math.roundToInt

/**
 * Settings > Appearance & Experience: the chat's text size (a slider) and font (the app's own,
 * Sweetie or Romantic), with a little chat below showing how messages will look.
 */
@Composable
internal fun ChatTextSettings(
    textSize: Int,
    font: Int,
    accent: Color,
    onTextSizeChange: (Int) -> Unit,
    onFontChange: (Int) -> Unit
) {
    // Follows the finger for the preview; saved when let go
    var size by remember(textSize) { mutableFloatStateOf(textSize.toFloat()) }
    val shownSize = size.roundToInt()
    val chosenFont = ChatFont.of(font)

    ListItem(
        headlineContent = { Text("Chat Text Size", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text("Messages and the message box", fontSize = 13.sp) },
        leadingContent = { Icon(Icons.Rounded.FormatSize, contentDescription = null, tint = accent) },
        trailingContent = {
            Text(
                text = if (shownSize == SecurityPreferences.DEFAULT_CHAT_TEXT_SIZE) "Default" else "$shownSize sp",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text("A", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CherishSlider(
            value = size,
            onValueChange = { size = it },
            onValueChangeFinished = { onTextSizeChange(size.roundToInt()) },
            valueRange = SecurityPreferences.MIN_CHAT_TEXT_SIZE.toFloat()..SecurityPreferences.MAX_CHAT_TEXT_SIZE.toFloat(),
            // One stop per sp
            steps = SecurityPreferences.MAX_CHAT_TEXT_SIZE - SecurityPreferences.MIN_CHAT_TEXT_SIZE - 1,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
                .testTag("chat_text_size")
        )
        Text("A", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Spacer(modifier = Modifier.height(6.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)

    ListItem(
        headlineContent = { Text("Chat Font Style", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text("How messages are written", fontSize = 13.sp) },
        leadingContent = { Icon(Icons.Rounded.FontDownload, contentDescription = null, tint = accent) }
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ChatFont.entries.forEach { option ->
            val isSelected = option == chosenFont
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .pressScaleTarget()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) accent else Color.Transparent)
                    .springClickable(tick = true) { onFontChange(option.ordinal) }
                    .padding(vertical = 6.dp)
                    .testTag("chat_font_${option.name}")
            ) {
                // Each name written in its own font
                Text(
                    text = option.title,
                    fontFamily = option.family,
                    fontSize = (15 * option.scale).sp,
                    fontWeight = if (option.family == null) FontWeight.SemiBold else null,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = option.caption,
                    fontSize = 10.sp,
                    color = if (isSelected) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    ChatTextPreview(style = ChatTextStyle(shownSize, chosenFont), accent = accent)
}

/** A message from my partner and my reply, as the chat will show them. */
@Composable
private fun ChatTextPreview(style: ChatTextStyle, accent: Color) {
    val textStyle = style.applyTo(MaterialTheme.typography.bodyMedium)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EmojiText(
            text = "Good morning, my love ☀️",
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
        EmojiText(
            text = "Morning! Missing you already 💕",
            style = textStyle,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.End)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp))
                .background(accent)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
