package com.example.ui.chat

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** What the keyboard can hand the message box: GIFs, and its stickers and pictures. */
private val KEYBOARD_MEDIA_TYPES = arrayOf("image/gif", "image/webp", "image/png", "image/jpeg")

/**
 * Lets the keyboard's own GIF search (Gboard, Samsung Keyboard...) put a GIF straight into the
 * chat, like Telegram and WhatsApp: the text fields in [content] tell the keyboard they take
 * pictures, and one it sends is copied into the app (the keyboard's link to it doesn't last) and
 * handed to [onMedia] as a file of its own.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AcceptKeyboardMedia(
    enabled: Boolean,
    onMedia: (Uri) -> Unit,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        content()
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestOnMedia by rememberUpdatedState(onMedia)
    InterceptPlatformTextInput(
        interceptor = { request, nextHandler ->
            val withMedia = object : PlatformTextInputMethodRequest {
                override fun createInputConnection(outAttributes: EditorInfo): InputConnection {
                    val connection = request.createInputConnection(outAttributes)
                    EditorInfoCompat.setContentMimeTypes(outAttributes, KEYBOARD_MEDIA_TYPES)
                    @Suppress("DEPRECATION")
                    val wrapped = InputConnectionCompat.createWrapper(connection, outAttributes) { info, flags, _ ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 &&
                            (flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0
                        ) {
                            try {
                                info.requestPermission()
                            } catch (e: Exception) {
                                Log.w(TAG, "The keyboard's picture can't be read", e)
                                return@createWrapper false
                            }
                        }
                        scope.launch {
                            val file = withContext(Dispatchers.IO) { copyKeyboardMedia(context, info) }
                            try {
                                info.releasePermission()
                            } catch (_: Exception) {}
                            if (file != null) latestOnMedia(Uri.fromFile(file))
                        }
                        true
                    }
                    return wrapped
                }
            }
            nextHandler.startInputMethod(withMedia)
        },
        content = content
    )
}

/** The keyboard's picture, copied into the app's own storage to send (null when it couldn't be). */
private fun copyKeyboardMedia(context: Context, info: InputContentInfoCompat): File? = try {
    val mime = info.description.getMimeType(0)?.lowercase().orEmpty()
    val extension = when {
        "gif" in mime -> "gif"
        "webp" in mime -> "webp"
        "png" in mime -> "png"
        else -> "jpg"
    }
    val dir = File(context.cacheDir, "compressed_media").apply { mkdirs() }
    val target = File(dir, "kb_${UUID.randomUUID()}.$extension")
    context.contentResolver.openInputStream(info.contentUri)?.use { input ->
        target.outputStream().use { input.copyTo(it) }
    }
    target.takeIf { it.length() > 0L }
} catch (e: Exception) {
    Log.w(TAG, "Couldn't take the keyboard's picture", e)
    null
}

private const val TAG = "KeyboardMedia"
