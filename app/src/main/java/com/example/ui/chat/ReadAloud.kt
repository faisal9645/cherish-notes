package com.example.ui.chat

import android.content.Context
import android.speech.tts.TextToSpeech

/**
 * Reads a message aloud with the phone's own voice (the speaker under a message in private chat).
 * Tapping it again while it speaks stops it; hiding the app stops it too.
 */
internal object ReadAloud {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    fun toggle(context: Context, text: String) {
        val engine = tts
        when {
            engine == null -> {
                pending = text
                tts = TextToSpeech(context.applicationContext) { status ->
                    ready = status == TextToSpeech.SUCCESS
                    if (ready) pending?.let { speak(it) }
                    pending = null
                }
            }
            !ready -> pending = text
            engine.isSpeaking -> engine.stop()
            else -> speak(text)
        }
    }

    fun stop() {
        pending = null
        tts?.stop()
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "message")
    }
}
