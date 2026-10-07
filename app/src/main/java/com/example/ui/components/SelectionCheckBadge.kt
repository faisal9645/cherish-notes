package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.RoseGoldPrimary

/**
 * Round multi-select marker for chat messages and shared gallery items; the check pops in with a
 * spring. [overMedia] adds a dark backing so the empty ring stays visible on top of photos.
 */
@Composable
fun SelectionCheckBadge(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    overMedia: Boolean = false,
    accentColor: Color = RoseGoldPrimary
) {
    val fill by animateColorAsState(
        targetValue = when {
            isSelected -> accentColor
            overMedia -> Color.Black.copy(alpha = 0.55f)
            else -> Color.Transparent
        },
        animationSpec = tween(160),
        label = "selection_fill"
    )
    val ring by animateColorAsState(
        targetValue = when {
            isSelected -> accentColor
            overMedia -> Color.White
            else -> MaterialTheme.colorScheme.outline
        },
        animationSpec = tween(160),
        label = "selection_ring"
    )
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(fill)
            .border(2.dp, ring, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = isSelected,
            enter = scaleIn(spring(dampingRatio = 0.45f, stiffness = 700f), initialScale = 0.2f) + fadeIn(tween(90)),
            exit = scaleOut(tween(120), targetScale = 0.4f) + fadeOut(tween(120))
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
