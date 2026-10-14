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
        fun onEngineError(code: Int)
    }

    private val engineLock = Any()
    @Volatile private var engine: RtcEngine? = null
    @Volatile private var currentAppId: String? = null
    @Volatile private var activeListener: Listener? = null

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

        // Maximum mobile sound quality: 48 kHz full-band audio profile with boosted signal volume
        try {
            rtc.setAudioProfile(Constants.AUDIO_PROFILE_MUSIC_STANDARD)
        } catch (e: Throwable) {
            Log.w(TAG, "setAudioProfile failed: ${e.message}")
        }

        // Boost recording and playback volume signal to maximum level
        try {
            rtc.adjustRecordingSignalVolume(150)
            rtc.adjustPlaybackSignalVolume(150)
        } catch (e: Throwable) {
            Log.w(TAG, "adjustSignalVolume failed: ${e.message}")
        }

        // AI noise suppression, balanced (safely guarded against native library missing)
        try {
            rtc.setAINSMode(true, AINS_MODE_BALANCED)
        } catch (e: Throwable) {
            Log.w(TAG, "setAINSMode failed: ${e.message}")
        }

        // A call starts on the earpiece, like a phone call; headsets take over when connected
        try {
            rtc.setDefaultAudioRoutetoSpeakerphone(false)
        } catch (e: Throwable) {
            Log.w(TAG, "setDefaultAudioRoutetoSpeakerphone failed: ${e.message}")
        }
    }

    /** Joins the call's channel as [uid], talking and listening. */
    fun join(token: String, channel: String, uid: Int): Boolean {
        val rtc = engine ?: return false
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

    fun startRecording(file: File): Boolean {
        val rtc = engine ?: return false
        try {
            file.parentFile?.mkdirs()
            if (file.exists()) file.delete()
        } catch (_: Exception) {}

        var res = try {
            val config = AudioRecordingConfiguration().apply {
                filePath = file.absolutePath
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
            res = rtc.startAudioRecording(file.absolutePath, Constants.AUDIO_RECORDING_QUALITY_MEDIUM)
        }

        if (res != 0 && file.extension.lowercase() != "wav") {
            val wavFile = File(file.parentFile, file.nameWithoutExtension + ".wav")
            try { if (wavFile.exists()) wavFile.delete() } catch (_: Exception) {}
            res = try {
                val config = AudioRecordingConfiguration().apply {
                    filePath = wavFile.absolutePath
                    sampleRate = 32000
                    quality = Constants.AUDIO_RECORDING_QUALITY_MEDIUM
                    fileRecordOption = Constants.AUDIO_FILE_RECORDING_MIXED
                }
                rtc.startAudioRecording(config)
            } catch (_: Throwable) {
                rtc.startAudioRecording(wavFile.absolutePath, Constants.AUDIO_RECORDING_QUALITY_MEDIUM)
            }
            if (res == 0) {
                Log.i(TAG, "startAudioRecording succeeded with WAV: ${wavFile.absolutePath}")
                return true
            }
        }
        if (res != 0) Log.w(TAG, "startAudioRecording returned $res for ${file.absolutePath}")
        else Log.i(TAG, "startAudioRecording started successfully for ${file.absolutePath}")
        return res == 0
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
        val rtc = engine ?: return
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
