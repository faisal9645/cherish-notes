package com.example.ui.components

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.example.ui.theme.darkSurface

/** The look every pop-up in the secret app shares: dialogs and bottom sheets. */
object CherishDialogDefaults {
    val shape: Shape = RoundedCornerShape(24.dp)

    /** White by day; a lifted navy (graphite in the Black theme) at night, so it stands off black. */
    val containerColor: Color
        @Composable get() = if (isDarkSurface()) darkSurface(navy = Color(0xFF111827), black = Color(0xFF1A1A1A))
        else Color.White

    val sheetShape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    val sheetContainerColor: Color
        @Composable get() = MaterialTheme.colorScheme.surface

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SheetDragHandle() = BottomSheetDefaults.DragHandle(width = 36.dp, height = 4.dp)
}

@Composable
private fun isDarkSurface(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/**
 * Material's AlertDialog in the Cherish style: rounder corners, a lifted surface with a hairline edge
 * at night, a firmer title, and (Android 12+, where the phone allows it) the screen behind softly
 * blurred instead of only dimmed. Same parameters as AlertDialog, so call sites only change the name.
 */
@Composable
fun CherishAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = CherishDialogDefaults.shape,
    containerColor: Color = CherishDialogDefaults.containerColor,
    iconContentColor: Color = MaterialTheme.colorScheme.primary,
    titleContentColor: Color = MaterialTheme.colorScheme.onSurface,
    textContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    tonalElevation: Dp = 0.dp,
    properties: DialogProperties = DialogProperties(),
) {
    val edge = if (isDarkSurface()) modifier.border(1.dp, Color.White.copy(alpha = 0.08f), shape) else modifier
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            BlurBehindDialog()
            confirmButton()
        },
        modifier = edge,
        dismissButton = dismissButton,
        icon = icon,
        title = styledTitle(title),
        text = text,
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties,
    )
}

/** Titles a size down from Material's 24 sp, semi-bold: calmer and more refined. */
private fun styledTitle(title: (@Composable () -> Unit)?): (@Composable () -> Unit)? {
    if (title == null) return null
    val styled: @Composable () -> Unit = {
        ProvideTextStyle(
            MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
        ) { title() }
    }
    return styled
}

/**
 * Blurs what's behind the dialog's window (Android 12+, when the phone has cross-window blur on;
 * battery saver turns it off). Placed inside the dialog's content, so it finds its window.
 */
@Composable
private fun BlurBehindDialog() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val view = LocalView.current
    val radiusPx = with(LocalDensity.current) { 18.dp.roundToPx() }
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null && window.windowManager.isCrossWindowBlurEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply {
                blurBehindRadius = radiusPx
                // The blur already sets the dialog apart: a lighter dim keeps the screen readable
                dimAmount = 0.28f
            }
        }
        onDispose { }
    }
}
