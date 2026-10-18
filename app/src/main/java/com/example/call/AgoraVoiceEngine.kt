package com.example.call

import android.content.Context
import android.util.Log
import io.agora.rtc2.`internal`.AudioRecordingConfiguration
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import kotlinx.coroutines.delay
import java.io.File

/**
 * The call's live audio, through Agora. One engine instance kept alive across calls:
 * created on first use, channels joined and left per call.
 *
 * Audio processing (echo cancellation, noise suppression, gain control) is Agora's built-in
 * set, plus its AI noise suppression in balanced mode so voices aren't over-suppressed. Nothing
 * of Android's own (AcousticEchoCanceler etc.) is added on top, so nothing is processed twice.
 */
class AgoraVoiceEngine(private val context: Context) {

    interface Listener {
        fun onJoined(uid: Int)
        fun onRemoteJoined(uid: Int)
        fun onRemoteLeft(uid: Int, dropped: Boolean)
        fun onConnectionStateChanged(state: Int, reason: Int)
        fun onTokenExpiring()
        fun onRouteChanged(route: AudioRoute)
        fun onLocalAudioStateChanged(state: Int, reason: Int)
        fun onNetworkQuality(uid: Int, txQuality: Int, rxQuality: Int)
        fun onRemoteVolumeIndication(volume: Int)
        fun onEngineError(code: Int)
    }

    private val engineLock = Any()
    @Volatile private var engine: RtcEngine? = null
    @Volatile private var currentAppId: String? = null
    @Volatile private var activeListener: Listener? = null
    @Volatile private var currentAudioMode: String = "balanced"
    @Volatile private var isLowDataMode: Boolean = false
    /** This call moved to the speech profile for a bad network (until the next call joins). */
    @Volatile private var downgradedForNetwork = false
    @Volatile private var currentVoiceEffect: VoiceChangerEffect = VoiceChangerEffect.NONE

    private val eventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            activeListener?.onJoined(uid)
        }

        override fun onRejoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            activeListener?.onJoined(uid)
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            activeListener?.onRemoteJoined(uid)
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            activeListener?.onRemoteLeft(uid, dropped = reason == Constants.USER_OFFLINE_DROPPED)
        }

        override fun onConnectionStateChanged(state: Int, reason: Int) {
            activeListener?.onConnectionStateChanged(state, reason)
        }

        override fun onTokenPrivilegeWillExpire(token: String?) {
            activeListener?.onTokenExpiring()
        }

        override fun onRequestToken() {
            activeListener?.onTokenExpiring()
        }

        override fun onAudioRouteChanged(routing: Int) {
            routeFor(routing)?.let { activeListener?.onRouteChanged(it) }
        }

        override fun onLocalAudioStateChanged(state: Int, reason: Int) {
            activeListener?.onLocalAudioStateChanged(state, reason)
        }

        override fun onNetworkQuality(uid: Int, txQuality: Int, rxQuality: Int) {
            activeListener?.onNetworkQuality(uid, txQuality, rxQuality)
            // This phone's own link (uid 0) gone bad: the lighter speech profile, for the rest of
            // the call. Switching back and forth would break up the audio each time the network wobbles.
            val bad = txQuality in Constants.QUALITY_BAD..Constants.QUALITY_DOWN ||
                rxQuality in Constants.QUALITY_BAD..Constants.QUALITY_DOWN
            if (uid == 0 && bad && !isLowDataMode && !downgradedForNetwork) {
                downgradedForNetwork = true
                try {
                    engine?.setAudioProfile(Constants.AUDIO_PROFILE_SPEECH_STANDARD)
                    Log.i(TAG, "Weak network: speech audio profile for the rest of the call")
                } catch (_: Throwable) {}
            }
        }

        override fun onAudioVolumeIndication(speakers: Array<out AudioVolumeInfo>?, totalVolume: Int) {
            if (speakers == null) return
            var remoteMax = 0
            for (speaker in speakers) {
                // In Agora, uid == 0 is local microphone; uid != 0 is remote partner audio
                if (speaker.uid != 0 && speaker.volume > remoteMax) {
                    remoteMax = speaker.volume
                }
            }
            activeListener?.onRemoteVolumeIndication(remoteMax)
        }

        override fun onError(err: Int) {
            Log.e(TAG, "Agora RTC internal error: $err")
            activeListener?.onEngineError(err)
        }
    }

    suspend fun start(appId: String, listener: Listener): Boolean {
        val effectiveAppId = appId.trim().ifBlank { AgoraTokenBuilder.DEFAULT_APP_ID }
        activeListener = listener

        synchronized(engineLock) {
            val existing = engine
            if (existing != null && currentAppId == effectiveAppId) {
                // Engine is already alive and configured. Leave previous channel if any and reuse.
                try {
                    existing.leaveChannel()
                } catch (_: Throwable) {}
                try {
                    existing.enableAudio()
                    existing.setDefaultAudioRoutetoSpeakerphone(false)
                } catch (e: Throwable) {
                    Log.w(TAG, "Error refreshing existing audio engine: ${e.message}")
                }
                return true
            }

            // If appId changed or engine is invalid, clean up old instance
            if (existing != null) {
                try { existing.leaveChannel() } catch (_: Throwable) {}
                try { RtcEngine.destroy() } catch (_: Throwable) {}
                engine = null
                currentAppId = null
            }
        }

        // Initialize RtcEngine (with a brief delay retry if native teardown from prior run is concluding)
        var created: RtcEngine? = null
        for (attempt in 1..2) {
            created = createRtcEngineInstance(effectiveAppId)
            if (created != null) break
            if (attempt < 2) {
                Log.w(TAG, "RtcEngine creation attempt $attempt failed, retrying after 200ms...")
                delay(200)
            }
        }

        if (created == null) {
            Log.e(TAG, "Failed to initialize Agora RtcEngine for appId: $effectiveAppId")
            return false
        }

        // Configure engine features for speech
        applyEngineConfigurations(created)

        synchronized(engineLock) {
            engine = created
            currentAppId = effectiveAppId
        }
        return true
    }

    private fun createRtcEngineInstance(effectiveAppId: String): RtcEngine? {
        val appContext = context.applicationContext
        // 1. Try modern RtcEngineConfig without mChannelProfile (avoids video module validation in voice-rtc-basic)
        val config = RtcEngineConfig().apply {
            mContext = appContext
            mAppId = effectiveAppId
            mEventHandler = eventHandler
            mAudioScenario = Constants.AUDIO_SCENARIO_GAME_STREAMING
        }

        var rtc: RtcEngine? = try {
            RtcEngine.create(config)
        } catch (e: Throwable) {
            Log.w(TAG, "RtcEngine.create(config) threw: ${e.message}")
            null
        }

        // 2. Fallback to direct 3-argument create if config-based creation returned null or threw
        if (rtc == null) {
            rtc = try {
                RtcEngine.create(appContext, effectiveAppId, eventHandler)
            } catch (e: Throwable) {
                Log.e(TAG, "RtcEngine.create(appContext, appId, handler) threw: ${e.message}")
                null
            }
        }

        return rtc
    }

    private fun applyEngineConfigurations(rtc: RtcEngine) {
        // Set communication profile and game streaming scenario on the created engine instance
        // GAME_STREAMING allows crystal clear media audio without taking over telephony mode or causing other apps (WhatsApp, etc.) to show 'on another call'
        try {
            rtc.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
            rtc.setAudioScenario(Constants.AUDIO_SCENARIO_GAME_STREAMING)
        } catch (e: Throwable) {
            Log.w(TAG, "setChannelProfile/setAudioScenario failed: ${e.message}")
        }

        // Direct UDP connection for reliable, ultra-low latency audio worldwide
        try {
            rtc.setCloudProxy(Constants.PROXY_TYPE_NONE_PROXY)
        } catch (e: Throwable) {
            Log.w(TAG, "setCloudProxy(NONE_PROXY) failed: ${e.message}")
        }

        try {
            rtc.enableAudio()
        } catch (e: Throwable) {
            Log.w(TAG, "enableAudio failed: ${e.message}")
        }

        // Audio profile: SPEECH_STANDARD if Low-Data mode is active; MUSIC_STANDARD (48 kHz) for HD voice
        try {
            val profile = if (isLowDataMode) Constants.AUDIO_PROFILE_SPEECH_STANDARD else Constants.AUDIO_PROFILE_MUSIC_STANDARD
            rtc.setAudioProfile(profile)
        } catch (e: Throwable) {
            Log.w(TAG, "setAudioProfile failed: ${e.message}")
        }

        // Apply AI noise suppression and signal volume based on selected mode
        applyAudioMode(rtc, currentAudioMode)

        // Enable live voice amplitude detection (every 150ms) for UI concentric ripples
        try {
            rtc.enableAudioVolumeIndication(150, 3, true)
        } catch (e: Throwable) {
            Log.w(TAG, "enableAudioVolumeIndication failed: ${e.message}")
        }

        // A call starts on the earpiece, like a phone call; headsets take over when connected
        try {
            rtc.setDefaultAudioRoutetoSpeakerphone(false)
        } catch (e: Throwable) {
            Log.w(TAG, "setDefaultAudioRoutetoSpeakerphone failed: ${e.message}")
        }

        // Apply voice changer effect
        applyVoiceChanger(rtc, currentVoiceEffect)
    }

    private fun applyAudioMode(rtc: RtcEngine, mode: String) {
        try {
            if (mode.equals("whisper", ignoreCase = true)) {
                // Night / Whisper Mode: Lowers threshold suppression and boosts soft input gain (200),
                // making pillow-talk and whispering loud and clear without clipping
                rtc.setAINSMode(false, AINS_MODE_BALANCED)
                rtc.adjustRecordingSignalVolume(200)
                rtc.adjustPlaybackSignalVolume(150)
                Log.i(TAG, "Applied Night/Whisper audio profile (gain 200, relaxed AINS)")
            } else {
                // Balanced / Street: Suppresses traffic, wind, and cafe noise
                rtc.setAINSMode(true, AINS_MODE_BALANCED)
                rtc.adjustRecordingSignalVolume(120)
                rtc.adjustPlaybackSignalVolume(150)
                Log.i(TAG, "Applied Balanced/Street audio profile (gain 120, active AINS)")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "applyAudioMode failed: ${e.message}")
        }
    }

    fun setAudioMode(mode: String) {
        currentAudioMode = mode
        val rtc = engine ?: return
        applyAudioMode(rtc, mode)
    }

    fun setLowDataMode(enabled: Boolean) {
        isLowDataMode = enabled
        val rtc = engine ?: return
        try {
            val profile = if (enabled) Constants.AUDIO_PROFILE_SPEECH_STANDARD else Constants.AUDIO_PROFILE_MUSIC_STANDARD
            rtc.setAudioProfile(profile)
            Log.i(TAG, "Low data mode set to $enabled (profile=$profile)")
        } catch (e: Throwable) {
            Log.w(TAG, "setLowDataMode failed: ${e.message}")
        }
    }

    /** Sets the in-call real-time voice changer effect. */
    fun setVoiceChanger(effect: VoiceChangerEffect) {
        currentVoiceEffect = effect
        val rtc = engine ?: return
        applyVoiceChanger(rtc, effect)
    }

    private fun applyVoiceChanger(rtc: RtcEngine, effect: VoiceChangerEffect) {
        try {
            if (effect == VoiceChangerEffect.NONE) {
                try { rtc.setAudioEffectPreset(Constants.AUDIO_EFFECT_OFF) } catch (_: Throwable) {}
                try { rtc.setVoiceBeautifierPreset(Constants.VOICE_BEAUTIFIER_OFF) } catch (_: Throwable) {}
                try { rtc.setLocalVoicePitch(1.0) } catch (_: Throwable) {}
                Log.i(TAG, "Voice changer reset to natural voice (NONE)")
            } else {
                if (effect.agoraPreset != 0) {
                    try {
                        val res = rtc.setAudioEffectPreset(effect.agoraPreset)
                        Log.i(TAG, "Applied audioEffectPreset ${effect.agoraPreset}, result: $res")
                    } catch (e: Throwable) {
                        Log.w(TAG, "setAudioEffectPreset failed: ${e.message}")
                    }
                } else {
                    try { rtc.setAudioEffectPreset(Constants.AUDIO_EFFECT_OFF) } catch (_: Throwable) {}
                }
                try {
                    val pitchRes = rtc.setLocalVoicePitch(effect.pitchMultiplier)
                    Log.i(TAG, "Applied localVoicePitch ${effect.pitchMultiplier}, result: $pitchRes")
                } catch (e: Throwable) {
                    Log.w(TAG, "setLocalVoicePitch failed: ${e.message}")
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "applyVoiceChanger failed: ${e.message}", e)
        }
    }

    fun prewarm(appId: String) {
        val effectiveAppId = appId.trim().ifBlank { AgoraTokenBuilder.DEFAULT_APP_ID }
        synchronized(engineLock) {
            if (engine != null && currentAppId == effectiveAppId) return
        }
        val rtc = createRtcEngineInstance(effectiveAppId) ?: return
        applyEngineConfigurations(rtc)
        synchronized(engineLock) {
            engine = rtc
            currentAppId = effectiveAppId
        }
        Log.i(TAG, "Agora RTC engine pre-warmed in background for incoming call instant connection")
    }

    /** Joins the call's channel as [uid], talking and listening. */
    fun join(token: String, channel: String, uid: Int): Boolean {
        val rtc = engine ?: return false
        // A new call starts on the profile its settings chose (see setLowDataMode)
        downgradedForNetwork = false
        val options = ChannelMediaOptions().apply {
            channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
            clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            publishMicrophoneTrack = true
            autoSubscribeAudio = true
        }
        val effectiveToken = if (token.isNotBlank()) token else {
            AgoraTokenBuilder.buildToken(channelName = channel, uid = uid)
        }
        var result = rtc.joinChannel(effectiveToken, channel, uid, options)
        if (result != 0) {
            Log.w(TAG, "joinChannel returned $result with primary token, trying with empty token fallback")
            val fallbackToken = ""
            result = rtc.joinChannel(fallbackToken, channel, uid, options)
        }
        if (result != 0) Log.w(TAG, "joinChannel returned $result")
        return result == 0
    }

    fun renewToken(token: String) {
        engine?.renewToken(token)
    }

    /** Stops sending this phone's voice (the microphone stays ready, so unmuting is instant). */
    fun setMuted(muted: Boolean) {
        engine?.muteLocalAudioStream(muted)
    }

    /** While a phone call has the line: nothing sent, nothing played. */
    fun setOnHold(onHold: Boolean, userMuted: Boolean) {
        val rtc = engine ?: return
        rtc.muteAllRemoteAudioStreams(onHold)
        rtc.muteLocalAudioStream(onHold || userMuted)
    }

    /** Sends the call's sound to [route]; false if it couldn't. */
    fun setRoute(route: AudioRoute): Boolean {
        val rtc = engine ?: return false
        val code = when (route) {
            AudioRoute.EARPIECE -> Constants.AUDIO_ROUTE_EARPIECE
            AudioRoute.SPEAKER -> Constants.AUDIO_ROUTE_SPEAKERPHONE
            AudioRoute.BLUETOOTH -> Constants.AUDIO_ROUTE_BLUETOOTH_DEVICE_HFP
            AudioRoute.WIRED -> Constants.AUDIO_ROUTE_HEADSET
        }
        when (route) {
            AudioRoute.SPEAKER -> rtc.setEnableSpeakerphone(true)
            AudioRoute.EARPIECE -> rtc.setEnableSpeakerphone(false)
            else -> Unit
        }
        if (rtc.setRouteInCommunicationMode(code) == 0) return true
        // Older behaviour: only speaker on/off
        return when (route) {
            AudioRoute.SPEAKER -> rtc.setEnableSpeakerphone(true) == 0
            AudioRoute.EARPIECE -> rtc.setEnableSpeakerphone(false) == 0
            else -> false
        }
    }

    var currentRecordedFile: File? = null
        private set

    fun startRecording(file: File): Boolean {
        val rtc = engine ?: return false
        currentRecordedFile = null
        // Agora voice-rtc-basic natively supports linear PCM/WAV recording without external codecs.
        // Always record to WAV format to guarantee real audio bytes are captured and saved.
        val targetWav = if (!file.name.endsWith(".wav", ignoreCase = true)) {
            File(file.parentFile, file.nameWithoutExtension + ".wav")
        } else file

        try {
            targetWav.parentFile?.mkdirs()
            if (targetWav.exists()) targetWav.delete()
        } catch (_: Exception) {}

        var res = try {
            val config = AudioRecordingConfiguration().apply {
                filePath = targetWav.absolutePath
                sampleRate = 32000
                quality = Constants.AUDIO_RECORDING_QUALITY_MEDIUM
                fileRecordOption = Constants.AUDIO_FILE_RECORDING_MIXED
            }
            rtc.startAudioRecording(config)
        } catch (e: Throwable) {
            Log.w(TAG, "startAudioRecording(config) threw: ${e.message}")
            -1
        }

        if (res != 0) {
            Log.w(TAG, "startAudioRecording(config) returned $res, trying direct legacy call")
            res = rtc.startAudioRecording(targetWav.absolutePath, Constants.AUDIO_RECORDING_QUALITY_MEDIUM)
        }

        if (res == 0) {
            currentRecordedFile = targetWav
            Log.i(TAG, "startAudioRecording started successfully with WAV: ${targetWav.absolutePath}")
            return true
        }

        // Secondary fallback to original file if WAV failed for any reason
        if (targetWav != file) {
            try { if (file.exists()) file.delete() } catch (_: Exception) {}
            res = try {
                val config = AudioRecordingConfiguration().apply {
                    filePath = file.absolutePath
                    sampleRate = 32000
                    quality = Constants.AUDIO_RECORDING_QUALITY_MEDIUM
                    fileRecordOption = Constants.AUDIO_FILE_RECORDING_MIXED
                }
                rtc.startAudioRecording(config)
            } catch (_: Throwable) {
                rtc.startAudioRecording(file.absolutePath, Constants.AUDIO_RECORDING_QUALITY_MEDIUM)
            }
            if (res == 0) {
                currentRecordedFile = file
                Log.i(TAG, "startAudioRecording succeeded with fallback: ${file.absolutePath}")
                return true
            }
        }
        Log.w(TAG, "startAudioRecording failed ($res) for ${targetWav.absolutePath}")
        return false
    }

    fun stopRecording(): Boolean {
        val rtc = engine ?: return false
        val res = rtc.stopAudioRecording()
        if (res != 0) Log.w(TAG, "stopAudioRecording returned $res")
        return res == 0
    }

    /** Leaves the current call channel and stops recording without destroying the native engine. */
    fun stop() {
        activeListener = null
        currentVoiceEffect = VoiceChangerEffect.NONE
        val rtc = engine ?: return
        try {
            applyVoiceChanger(rtc, VoiceChangerEffect.NONE)
        } catch (_: Throwable) {}
        try {
            rtc.stopAudioRecording()
        } catch (e: Throwable) {
            Log.w(TAG, "stopAudioRecording failed", e)
        }
        try {
            rtc.leaveChannel()
        } catch (e: Throwable) {
            Log.w(TAG, "leaveChannel failed", e)
        }
    }

    /** Releases the native Agora engine completely (e.g. app termination or unpair). */
    fun destroy() {
        synchronized(engineLock) {
            activeListener = null
            val rtc = engine
            engine = null
            currentAppId = null
            try {
                rtc?.stopAudioRecording()
            } catch (_: Throwable) {}
            try {
                rtc?.leaveChannel()
            } catch (_: Throwable) {}
            try {
                RtcEngine.destroy()
            } catch (e: Throwable) {
                Log.w(TAG, "RtcEngine.destroy failed", e)
            }
        }
    }

    private fun routeFor(routing: Int): AudioRoute? = when (routing) {
        Constants.AUDIO_ROUTE_EARPIECE -> AudioRoute.EARPIECE
        Constants.AUDIO_ROUTE_SPEAKERPHONE, Constants.AUDIO_ROUTE_LOUDSPEAKER -> AudioRoute.SPEAKER
        Constants.AUDIO_ROUTE_BLUETOOTH_DEVICE_HFP -> AudioRoute.BLUETOOTH
        Constants.AUDIO_ROUTE_HEADSET, Constants.AUDIO_ROUTE_HEADSETNOMIC,
        Constants.AUDIO_ROUTE_USB_HEADSET, Constants.AUDIO_ROUTE_USBDEVICE -> AudioRoute.WIRED
        else -> null
    }

    companion object {
        private const val TAG = "AgoraVoiceEngine"
        private const val AINS_MODE_BALANCED = 0
    }
}
