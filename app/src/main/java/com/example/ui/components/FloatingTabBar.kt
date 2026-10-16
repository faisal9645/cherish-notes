package com.example.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.HeartRed
import com.example.ui.theme.MistBlue
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.darkSurface
import com.example.ui.theme.darkTone

/** One tab of a [FloatingTabBar]: an outline icon, and the filled one it turns into when chosen. */
class FloatingTab(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val contentDescription: String,
    val badgeCount: Int = 0,
)

/**
 * A pill-shaped tab bar that floats above the bottom edge with a soft shadow, the screen running on
 * underneath it. The highlight slides to a tab the moment it's tapped (while the next screen comes
 * in), and the chosen tab's icon fills in.
 */
@Composable
fun FloatingTabBar(
    tabs: List<FloatingTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val barColor = if (isDark) Color.Black else Color.White
    val highlightColor = if (isDark) MaterialTheme.colorScheme.primaryContainer else MistBlue
    val selectedColor = MaterialTheme.colorScheme.primary
    val idleColor = if (isDark) darkTone(Slate400) else Slate500
    val badgeColor = HeartRed
    val shape = RoundedCornerShape(18.dp)
    val highlightShape = RoundedCornerShape(13.dp)
    // The tab the highlight is on: the tapped one straight away
    var shownIndex by remember(selectedIndex) { mutableIntStateOf(selectedIndex) }
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .softShadow(
                    elevation = 16.dp,
                    shape = shape,
                    ambientColor = if (isDark) Color.Black.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.10f),
                    spotColor = if (isDark) Color.Black.copy(alpha = 0.5f) else selectedColor.copy(alpha = 0.25f)
                )
                .clip(shape)
                .background(barColor)
                .then(if (isDark) Modifier.border(1.dp, Color(0xFF27272A), shape) else Modifier)
                .padding(6.dp)
        ) {
            val tabWidth = maxWidth / tabs.size
            val highlightX by animateDpAsState(
                targetValue = tabWidth * shownIndex,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
                label = "tab_highlight"
            )
            Box(
                modifier = Modifier
                    .offset(x = highlightX)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .clip(highlightShape)
                    .background(highlightColor)
            )
            Row(modifier = Modifier.fillMaxSize()) {
                tabs.forEachIndexed { index, tab ->
                    FloatingTabItem(
                        tab = tab,
                        selected = index == shownIndex,
                        selectedColor = selectedColor,
                        idleColor = idleColor,
                        badgeColor = badgeColor,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (index != shownIndex) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            shownIndex = index
                        }
                        onSelect(index)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FloatingTabItem(
    tab: FloatingTab,
    selected: Boolean,
    selectedColor: Color,
    idleColor: Color,
    badgeColor: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val color by animateColorAsState(if (selected) selectedColor else idleColor, tween(220), label = "tab_color")
    // The sliding highlight is the feedback; the tab also gives a little under the finger
    val press = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxHeight()
            .pressScale(press)
            .selectable(
                selected = selected,
                interactionSource = press,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
    ) {
        BadgedBox(
            badge = {
                if (tab.badgeCount > 0) {
                    Badge(containerColor = badgeColor) {
                        Text(if (tab.badgeCount > 99) "99+" else tab.badgeCount.toString())
                    }
                }
            }
        ) {
            Crossfade(targetState = selected, animationSpec = tween(200), label = "tab_icon") { isSelected ->
                Icon(
                    imageVector = if (isSelected) tab.selectedIcon else tab.icon,
                    contentDescription = tab.contentDescription,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = tab.label,
            color = color,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
