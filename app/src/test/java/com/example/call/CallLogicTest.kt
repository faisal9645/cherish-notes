package com.example.call

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Message
import com.example.data.model.MessageType
import com.example.security.SecurityPreferences
import com.example.ui.chat.ChatExperienceMode
import com.example.ui.chat.formatRecordingLength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CallLogicTest {

    @Test
    fun `call ids are 20 letters and digits, as the backend requires`() {
        val ids = (1..200).map { CallRepository.newCallId() }
        ids.forEach { assertTrue(it, it.matches(Regex("^[A-Za-z0-9]{20}$"))) }
        assertEquals("ids don't repeat", ids.size, ids.toSet().size)
    }

    @Test
    fun `calls hide who it is in Private Mode and with hidden notifications in Notes disguise`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SecurityPreferences.getInstance(context)

        prefs.setChatExperienceMode(ChatExperienceMode.NORMAL)
        prefs.setDisguiseModeEnabled(true)
        prefs.setHideNotificationContent(true)
        assertFalse("hidden notification content hides the caller", CallPrivacy.showIdentity(context))

        prefs.setHideNotificationContent(false)
        assertTrue(CallPrivacy.showIdentity(context))

        prefs.setChatExperienceMode(ChatExperienceMode.PRIVATE)
        assertFalse("Private Mode never shows identity", CallPrivacy.showIdentity(context))

        prefs.setChatExperienceMode(ChatExperienceMode.NORMAL)
        prefs.setDisguiseModeEnabled(false)
        prefs.setHideNotificationContent(true)
        assertTrue("hidden content only applies with the disguise on", CallPrivacy.showIdentity(context))
    }

    @Test
    fun `recording status comes only from known server values`() {
        assertEquals(RecordingStatus.RECORDING, RecordingStatus.fromKey("recording"))
        assertEquals(RecordingStatus.READY, RecordingStatus.fromKey("ready"))
        assertEquals(RecordingStatus.UNKNOWN, RecordingStatus.fromKey(null))
        assertEquals(RecordingStatus.UNKNOWN, RecordingStatus.fromKey(""))
        assertEquals(RecordingStatus.UNKNOWN, RecordingStatus.fromKey("RECORDING"))
        assertEquals(RecordingStatus.UNKNOWN, RecordingStatus.fromKey("something else"))
    }

    @Test
    fun `every way a call ends says something`() {
        CallEndReason.entries.forEach { assertTrue(it.name, endText(it).isNotBlank()) }
        assertEquals("Call ended", endText(null))
        assertTrue(endText(CallEndReason.BUSY).startsWith("Busy"))
        assertEquals("No answer", endText(CallEndReason.NO_ANSWER))
    }

    @Test
    fun `durations read like a phone call`() {
        assertEquals("00:00", formatCallDuration(0))
        assertEquals("01:05", formatCallDuration(65))
        assertEquals("1:02:05", formatCallDuration(3725))
        assertEquals("0:00", formatRecordingLength(0))
        assertEquals("1:01", formatRecordingLength(61))
        assertEquals("1:00:00", formatRecordingLength(3600))
        assertEquals("0:00", formatRecordingLength(-5))
    }

    @Test
    fun `a call recording message is its own type, and unknown types still read as text`() {
        assertEquals(MessageType.CALL_RECORDING, Message(type = "CALL_RECORDING").getTypedType())
        assertEquals(MessageType.TEXT, Message(type = "SOMETHING_NEW").getTypedType())
        val recording = Message(
            id = "callrec_AbCdEfGhIjKlMnOpQrSt",
            type = MessageType.CALL_RECORDING.name,
            callId = "AbCdEfGhIjKlMnOpQrSt",
            recordingPath = "couples/couple_x/call-recordings/AbCdEfGhIjKlMnOpQrSt/recording.mp4"
        )
        assertTrue("no download link", recording.getAllMediaUrls().isEmpty())
    }

    @Test
    fun `CallUiState identifies live vs non-live phases correctly`() {
        val idle = CallUiState(phase = CallPhase.IDLE)
        assertFalse(idle.isLive)

        val connecting = CallUiState(phase = CallPhase.CONNECTING)
        assertTrue(connecting.isLive)

        val connected = CallUiState(phase = CallPhase.CONNECTED)
        assertTrue(connected.isLive)

        val reconnecting = CallUiState(phase = CallPhase.RECONNECTING)
        assertTrue(reconnecting.isLive)

        val ended = CallUiState(phase = CallPhase.ENDED, endReason = CallEndReason.HANGUP)
        assertFalse(ended.isLive)
    }

    @Test
    fun `CallUiState carries error messages and audio routes properly`() {
        val withError = CallUiState(
            phase = CallPhase.ENDED,
            endReason = CallEndReason.NETWORK,
            errorMessage = "Couldn't reach the server",
            route = AudioRoute.SPEAKER
        )
        assertEquals(CallEndReason.NETWORK, withError.endReason)
        assertEquals("Couldn't reach the server", withError.errorMessage)
        assertEquals(AudioRoute.SPEAKER, withError.route)
    }
}
