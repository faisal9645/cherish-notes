package com.example.call

import android.content.Context
import android.util.Log
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * The call's live audio, through Agora. One engine per call: created when the call starts,
 * released when it ends. Its callbacks arrive on Agora's own thread.
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

    private var engine: RtcEngine? = null
    /** Completes when the previous call's engine has been fully released. */
    private var released: CompletableDeferred<Unit>? = null

    /** Creates the engine for a call; false if Agora couldn't start on this phone. */
    suspend fun start(appId: String, listener: Listener): Boolean {
        if (engine != null) return true
        // A new call right after the last one: wait for its engine to be gone first
        released?.let { withTimeoutOrNull(RELEASE_WAIT_MS) { it.await() } }
        val handler = object : IRtcEngineEventHandler() {
            override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) = listener.onJoined(uid)
            override fun onRejoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) = listener.onJoined(uid)
            override fun onUserJoined(uid: Int, elapsed: Int) = listener.onRemoteJoined(uid)
            override fun onUserOffline(uid: Int, reason: Int) =
                listener.onRemoteLeft(uid, dropped = reason == Constants.USER_OFFLINE_DROPPED)
            override fun onConnectionStateChanged(state: Int, reason: Int) = listener.onConnectionStateChanged(state, reason)
            override fun onTokenPrivilegeWillExpire(token: String?) = listener.onTokenExpiring()
            override fun onRequestToken() = listener.onTokenExpiring()
            override fun onAudioRouteChanged(routing: Int) {
                routeFor(routing)?.let(listener::onRouteChanged)
            }
            override fun onLocalAudioStateChanged(state: Int, reason: Int) = listener.onLocalAudioStateChanged(state, reason)
            override fun onNetworkQuality(uid: Int, txQuality: Int, rxQuality: Int) =
                listener.onNetworkQuality(uid, txQuality, rxQuality)
            override fun onError(err: Int) = listener.onEngineError(err)
        }
        return try {
            val config = RtcEngineConfig().apply {
                mContext = context.applicationContext
                mAppId = appId
                mEventHandler = handler
                mChannelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                mAudioScenario = Constants.AUDIO_SCENARIO_DEFAULT
                mAreaCode = RtcEngineConfig.AreaCode.AREA_CODE_GLOB
            }
            val created = RtcEngine.create(config)

            // UAE / Dubai VoIP compatibility: UAE telecom operators (Etisalat & du) block standard UDP VoIP.
            // Enabling TCP Cloud Proxy (TLS over port 443) routes call media through
            // Agora's encrypted proxy nodes, allowing calls between Chennai & Dubai to connect seamlessly.
            val proxyRes = created.setCloudProxy(Constants.TRANSPORT_TYPE_TCP_PROXY)
            if (proxyRes != 0) {
                Log.w(TAG, "setCloudProxy(TRANSPORT_TYPE_TCP_PROXY) returned $proxyRes, falling back to AUTO_FALLBACK")
                created.setCloudProxy(Constants.PROXY_TYPE_TCP_AUTO_FALLBACK)
            } else {
                Log.i(TAG, "Agora Cloud Proxy (TCP TLS 443) enabled for UAE/Dubai calling")
            }

            created.enableAudio()
            // Clear wideband speech; Agora adapts its bitrate to the network
            created.setAudioProfile(Constants.AUDIO_PROFILE_SPEECH_STANDARD)
            // AI noise suppression, balanced (not the aggressive modes that make voices sound thin)
            created.setAINSMode(true, AINS_MODE_BALANCED)
            // A call starts on the earpiece, like a phone call; headsets take over when connected
            created.setDefaultAudioRoutetoSpeakerphone(false)
            engine = created
            true
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't start the call's audio", e)
            false
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
        val result = rtc.joinChannel(token, channel, uid, options)
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
        val res = rtc.startAudioRecording(file.absolutePath, Constants.AUDIO_RECORDING_QUALITY_MEDIUM)
        if (res != 0) Log.w(TAG, "startAudioRecording returned $res")
        return res == 0
    }

    fun stopRecording(): Boolean {
        val rtc = engine ?: return false
        val res = rtc.stopAudioRecording()
        if (res != 0) Log.w(TAG, "stopAudioRecording returned $res")
        return res == 0
    }

    /** Leaves the call and releases the engine (in the background: it can take a moment). */
    fun stop() {
        val rtc = engine ?: return
        engine = null
        try {
            rtc.leaveChannel()
        } catch (e: Exception) {
            Log.w(TAG, "leaveChannel failed", e)
        }
        val done = CompletableDeferred<Unit>()
        released = done
        try {
            RtcEngine.destroy { done.complete(Unit) }
        } catch (e: Exception) {
            Log.w(TAG, "Releasing the call's audio failed", e)
            done.complete(Unit)
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
        private const val RELEASE_WAIT_MS = 5_000L
    }
}
