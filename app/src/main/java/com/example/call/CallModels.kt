package com.example.call

import android.content.Context
import androidx.compose.runtime.Immutable
import com.example.security.SecurityPreferences
import com.example.ui.chat.ChatExperienceMode

/** Where a call is, as this phone shows it. */
enum class CallPhase {
    IDLE,
    /** Asking the server to start the call. */
    STARTING,
    OUTGOING_RINGING,
    INCOMING_RINGING,
    /** Answered; joining the call's audio. */
    CONNECTING,
    CONNECTED,
    /** The network dropped; the call's audio is reconnecting. */
    RECONNECTING,
    ENDED
}

/** Why a call ended, for what the call screen says. */
enum class CallEndReason {
    HANGUP,
    REMOTE_HANGUP,
    DECLINED,
    REMOTE_DECLINED,
    BUSY,
    UNAVAILABLE,
    NO_ANSWER,
    CANCELLED,
    MISSED,
    FAILED,
    NETWORK,
    PEER_LOST,
    PERMISSION_DENIED,
    NOT_PAIRED,
    NOT_ALLOWED
}

/** The recording's state as the server reports it (never set by this phone). */
enum class RecordingStatus(val key: String) {
    WAITING("waiting"),
    STARTING("starting"),
    RECORDING("recording"),
    UPLOADING("uploading"),
    PROCESSING("processing"),
    READY("ready"),
    FAILED("failed"),
    DELETED("deleted"),
    NONE("none"),
    UNKNOWN("");

    companion object {
        fun fromKey(key: String?): RecordingStatus = entries.firstOrNull { it.key == key && key.isNotEmpty() } ?: UNKNOWN
    }
}

/** Where the call's sound goes. */
enum class AudioRoute { EARPIECE, SPEAKER, BLUETOOTH, WIRED }

/** A call as the server keeps it (couples/{coupleId}/calls/{callId}). */
@Immutable
data class CallRecord(
    val callId: String,
    val coupleId: String,
    val callerId: String,
    val calleeId: String,
    val state: String,
    val endReason: String?,
    /** Who ended it (the app user id), when someone did. */
    val endedBy: String?,
    val recordingStatus: RecordingStatus,
    /** The phones (app user ids) recording this call; each records it whole, both voices. */
    val recordingBy: List<String> = emptyList(),
    val recordingFailure: String?,
    val recordingPaths: List<String>,
    val recordingDurationSec: Int,
    val durationSec: Int,
    val endedAtMs: Long
) {
    val isActive: Boolean get() = state == STATE_RINGING || state == STATE_CONNECTING || state == STATE_CONNECTED

    companion object {
        const val STATE_RINGING = "ringing"
        const val STATE_CONNECTING = "connecting"
        const val STATE_CONNECTED = "connected"
        const val STATE_ENDED = "ended"
        const val STATE_DECLINED = "declined"
        const val STATE_CANCELLED = "cancelled"
        const val STATE_MISSED = "missed"
        const val STATE_BUSY = "busy"
        const val STATE_UNAVAILABLE = "unavailable"
        const val STATE_FAILED = "failed"
    }
}

/** An incoming call announced by a push or seen by the open app. */
@Immutable
data class CallInvite(
    val callId: String,
    val coupleId: String,
    val callerId: String,
    val callerName: String,
    val ringUntil: Long
)

/** Voice modifier effects available in real-time during live calls. */
enum class VoiceChangerEffect(
    val id: String,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val agoraPreset: Int,
    val pitchMultiplier: Double,
    // Moves the voice's resonances up too (0 leaves them), so a raised voice sounds like a woman's
    // rather than a sped-up one; where this Agora build can't, the pitch alone changes
    val formantRatio: Double = 0.0
) {
    NONE("none", "Normal", "Natural clear voice", "🎙️", 0, 1.0),
    LADY("lady", "Lady", "Soft, grown-up woman's voice", "👩", 0, 1.26, formantRatio = 0.3),
    GIRL("girl", "Girl", "Sweet young girl's voice", "👧", 0, 1.38, formantRatio = 0.45),
    CHIPMUNK("chipmunk", "Chipmunk", "High & cute pitch", "🐿️", 0x02010500 /* VOICE_CHANGER_EFFECT_GIRL */, 1.45),
    DEEP_MONSTER("deep_monster", "Monster", "Deep & powerful rumble", "👹", 0x02010700 /* VOICE_CHANGER_EFFECT_HULK */, 0.65),
    KID("kid", "Kid", "Playful bright tone", "🧒", 0x02010300 /* VOICE_CHANGER_EFFECT_BOY */, 1.25),
    BARITONE("baritone", "Baritone", "Smooth low resonance", "🧔", 0x02010100 /* VOICE_CHANGER_EFFECT_UNCLE */, 0.82),
    ROBOT("robot", "Robot", "Futuristic android effect", "🤖", 0x02010200 /* VOICE_CHANGER_EFFECT_OLDMAN */, 0.90),
    STUDIO("studio", "Studio Mic", "Warm acoustic radio tone", "🎧", 0x02020300 /* ROOM_ACOUSTICS_STUDIO */, 1.0),
    ETHEREAL("ethereal", "Ethereal Echo", "Dreamy cavern space", "✨", 0x02020700 /* ROOM_ACOUSTICS_ETHEREAL */, 1.0)
}

/** What the call screen shows. */
@Immutable
data class CallUiState(
    val phase: CallPhase = CallPhase.IDLE,
    val callId: String? = null,
    val isOutgoing: Boolean = false,
    /** The partner's name, or null when calls must not show who it is (Private Mode, hidden notifications). */
    val peerName: String? = null,
    val peerPhotoUrl: String? = null,
    /** SystemClock.elapsedRealtime() when the call connected, for the timer. */
    val connectedAtElapsed: Long = 0L,
    val isMuted: Boolean = false,
    val route: AudioRoute = AudioRoute.EARPIECE,
    val bluetoothAvailable: Boolean = false,
    val wiredAvailable: Boolean = false,
    val recordingStatus: RecordingStatus = RecordingStatus.WAITING,
    val endReason: CallEndReason? = null,
    val isWeakNetwork: Boolean = false,
    /** A phone (cellular) call or the system has taken the microphone for now. */
    val isOnHold: Boolean = false,
    val peerReconnecting: Boolean = false,
    val errorMessage: String? = null,
    val remoteVolume: Int = 0,
    val voiceEffect: VoiceChangerEffect = VoiceChangerEffect.NONE,
    val voiceChangerRemainingSeconds: Int = 300
) {
    val isLive: Boolean
        get() = phase == CallPhase.CONNECTING || phase == CallPhase.CONNECTED || phase == CallPhase.RECONNECTING
}

/** Whether calls may show who's calling, following the app's own privacy settings. */
object CallPrivacy {
    /**
     * Not with hidden notification content in Notes disguise (the same rule as message
     * notifications), and never in Private Mode, which shows no identity at all.
     */
    fun showIdentity(context: Context): Boolean {
        val prefs = SecurityPreferences.getInstance(context)
        val discreet = prefs.isHideNotificationContent() && prefs.isDisguiseModeEnabled()
        val privateMode = prefs.getChatExperienceMode() == ChatExperienceMode.PRIVATE
        return !discreet && !privateMode
    }
}
