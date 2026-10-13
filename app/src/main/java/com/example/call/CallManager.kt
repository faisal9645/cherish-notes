package com.example.call

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.net.Uri
import android.util.Log
import com.example.CherishApplication
import com.example.audio.VoicePlayerHelper
import com.google.firebase.storage.FirebaseStorage
import io.agora.rtc2.Constants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The one call on this phone: placing it, ringing, answering, the live audio and hanging up.
 * It lives with the app process (not with a screen), so rotating, leaving the call screen or the
 * app going to the background never ends a call. Everything here runs on the main thread.
 *
 * The backend decides what a call is (connected, ended, recorded); this phone follows its call
 * record. The recording indicator only ever shows what the backend says Agora confirmed.
 */
object CallManager {
    private const val TAG = "CallManager"
    private const val RING_TIMEOUT_MS = 45_000L
    private const val RECONNECT_GIVE_UP_MS = 60_000L
    private const val PEER_LOST_GIVE_UP_MS = 45_000L
    private const val ENDED_SCREEN_MS = 2_500L
    private const val CALLER_UID = 1001
    private const val CALLEE_UID = 1002

    private lateinit var app: CherishApplication
    private val repo by lazy { CallRepository(app) }
    private val engine by lazy { AgoraVoiceEngine(app) }
    private val audio by lazy { CallAudioController(app) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(CallUiState())
    val state: StateFlow<CallUiState> = _state.asStateFlow()

    /** The call screen is on screen (the chat behind it must not switch to Notes for that). */
    @Volatile
    var isCallScreenVisible: Boolean = false

    // The current call
    private var coupleId: String? = null
    private var myUserId: String? = null
    private var callId: String? = null
    private var isOutgoing = false
    private var myUid = 0
    private var peerUid = 0
    private var localJoined = false
    private var remoteJoined = false
    private var userMuted = false
    private var onHold = false
    private var invite: CallInvite? = null
    private var callerNameHint: String? = null

    private var docJob: Job? = null
    private var ringTimeoutJob: Job? = null
    private var reconnectJob: Job? = null
    private var peerLostJob: Job? = null
    private var joinedReportJob: Job? = null
    private var endResetJob: Job? = null
    private var ringback: ToneGenerator? = null
    private var watchingIncoming = false
    private var activeRecordFile: File? = null
    private var uploadJob: Job? = null

    /** Calls already rung (or declined) on this phone, so a late push or listener can't ring again. */
    private val seenCalls = LinkedHashSet<String>()

    fun init(application: CherishApplication) {
        app = application
        CallNotifications.createChannels(application)
    }

    fun hasActiveCall(): Boolean {
        val phase = _state.value.phase
        return phase != CallPhase.IDLE && phase != CallPhase.ENDED
    }

    private fun post(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    // ---------- Placing a call ----------

    /** Calls the partner. Call it from the visible call screen, with the microphone allowed. */
    fun startOutgoing() = post {
        if (hasActiveCall()) return@post
        val couple = currentCoupleId()
        val me = app.authRepository.getCurrentUserId().ifBlank { null }
        if (couple == null || me == null) {
            showEnded(CallEndReason.NOT_PAIRED)
            return@post
        }
        val id = CallRepository.newCallId()
        beginCall(couple, me, id, outgoing = true)
        _state.value = freshState(id, outgoing = true).copy(phase = CallPhase.STARTING)
        CallService.start(app)
        audio.start(audioListener)
        scope.launch {
            val response = try {
                repo.start(couple, me, id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: CallRepository.CallActionException) {
                if (callId == id) finish(reasonFor(e), serverReason = null, errorMessage = e.message)
                return@launch
            }
            if (callId != id) return@launch
            if (_state.value.phase != CallPhase.STARTING) {
                // Hung up while it was being set up: end the call the server just made, so the
                // partner's phone doesn't ring for nothing
                if (response.status == CallRecord.STATE_RINGING) endOnServer(couple, me, id, "hangup")
                return@launch
            }
            when (response.status) {
                CallRecord.STATE_RINGING, CallRecord.STATE_CONNECTING, CallRecord.STATE_CONNECTED -> {
                    val join = response.join
                    if (join == null) {
                        finish(CallEndReason.FAILED, serverReason = "error")
                        return@launch
                    }
                    if (response.status == CallRecord.STATE_RINGING) {
                        setPhase(CallPhase.OUTGOING_RINGING)
                        startRingback()
                        ringTimeoutJob = scope.launch {
                            delay(RING_TIMEOUT_MS)
                            if (callId == id && _state.value.phase == CallPhase.OUTGOING_RINGING) {
                                finish(CallEndReason.NO_ANSWER, serverReason = "no_answer")
                            }
                        }
                    }
                    observeCallRecord()
                    joinAudio(join)
                }
                CallRecord.STATE_BUSY -> {
                    playTone(ToneGenerator.TONE_SUP_BUSY, 2_500)
                    finish(CallEndReason.BUSY, serverReason = null)
                }
                CallRecord.STATE_UNAVAILABLE -> finish(CallEndReason.UNAVAILABLE, serverReason = null)
                "incoming" -> {
                    // We both called at the same moment: pick up theirs instead
                    val theirs = response.callId
                    if (theirs.isNullOrBlank()) {
                        finish(CallEndReason.FAILED, serverReason = null)
                        return@launch
                    }
                    callId = theirs
                    isOutgoing = false
                    seenCalls += theirs
                    _state.update { it.copy(callId = theirs, isOutgoing = false, phase = CallPhase.INCOMING_RINGING) }
                    observeCallRecord()
                    accept()
                }
                else -> finish(CallEndReason.FAILED, serverReason = null)
            }
        }
    }

    // ---------- Incoming calls ----------

    /** A call push arrived (any thread). */
    fun onPushInvite(data: Map<String, String>) {
        val callId = data["callId"].orEmpty()
        val coupleId = data["coupleId"].orEmpty()
        val callerId = data["callerId"].orEmpty()
        if (callId.isBlank() || coupleId.isBlank() || callerId.isBlank()) return
        onIncomingInvite(
            CallInvite(
                callId = callId,
                coupleId = coupleId,
                callerId = callerId,
                callerName = data["callerName"].orEmpty(),
                ringUntil = data["ringUntil"]?.toLongOrNull() ?: (System.currentTimeMillis() + RING_TIMEOUT_MS)
            )
        )
    }

    /** The caller hung up before anyone answered, or nobody answered in time (any thread). */
    fun onPushCancel(data: Map<String, String>) = post {
        val cancelledId = data["callId"].orEmpty()
        if (cancelledId.isBlank()) return@post
        val reason = data["reason"].orEmpty()
        val wasRingingHere = callId == cancelledId && _state.value.phase == CallPhase.INCOMING_RINGING
        // A late invite for this call must not ring afterwards
        markSeen(cancelledId)
        if (wasRingingHere) {
            CallNotifications.showMissed(app, cancelledId, peerName(callerNameHint))
            finish(if (reason == CallRecord.STATE_MISSED) CallEndReason.MISSED else CallEndReason.CANCELLED, serverReason = null)
        } else if (callId != cancelledId) {
            // Rang while the app wasn't running: make sure it stops, and say it was missed
            CallNotifications.cancelIncoming(app)
            if (reason == CallRecord.STATE_MISSED || reason == CallRecord.STATE_CANCELLED) {
                CallNotifications.showMissed(app, cancelledId, peerName(null))
            }
        }
    }

    fun onIncomingInvite(invite: CallInvite) = post {
        val now = System.currentTimeMillis()
        if (invite.ringUntil in 1 until now) return@post
        val me = app.authRepository.getCurrentUserId()
        val myCouple = currentCoupleId()
        if (me.isBlank() || myCouple == null || invite.coupleId != myCouple || invite.callerId == me) return@post
        if (hasActiveCall()) return@post
        if (!markSeen(invite.callId)) return@post
        ringFor(invite, me)
        // With the app open, the call screen comes up right away (the notification still rings)
        if (CherishApplication.isAppInForeground) {
            try {
                app.startActivity(CallActivity.incomingIntent(app, invite).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't open the call screen", e)
            }
        }
    }

    private fun ringFor(invite: CallInvite, me: String) {
        beginCall(invite.coupleId, me, invite.callId, outgoing = false)
        this.invite = invite
        callerNameHint = invite.callerName
        _state.value = freshState(invite.callId, outgoing = false).copy(phase = CallPhase.INCOMING_RINGING)
        CallNotifications.showIncoming(app, invite)
        observeCallRecord()
        val ringLeft = (invite.ringUntil - System.currentTimeMillis()).coerceIn(5_000L, 60_000L)
        val id = invite.callId
        ringTimeoutJob = scope.launch {
            delay(ringLeft + 3_000L)
            if (callId == id && _state.value.phase == CallPhase.INCOMING_RINGING) {
                CallNotifications.showMissed(app, id, peerName(callerNameHint))
                finish(CallEndReason.MISSED, serverReason = null)
            }
        }
    }

    /**
     * Answers the ringing call. [fromNotification] restores it when the app was started just for
     * the answer (the process didn't know about the call yet).
     */
    fun accept(fromNotification: CallInvite? = null) = post {
        if (_state.value.phase != CallPhase.INCOMING_RINGING && fromNotification != null && !hasActiveCall()) {
            val me = app.authRepository.getCurrentUserId()
            if (me.isBlank() || fromNotification.coupleId != currentCoupleId()) return@post
            seenCalls += fromNotification.callId
            ringFor(fromNotification, me)
        }
        if (_state.value.phase != CallPhase.INCOMING_RINGING) return@post
        val couple = coupleId ?: return@post
        val me = myUserId ?: return@post
        val id = callId ?: return@post
        ringTimeoutJob?.cancel()
        CallNotifications.cancelIncoming(app)
        setPhase(CallPhase.CONNECTING)
        CallService.start(app)
        audio.start(audioListener)
        scope.launch {
            val response = try {
                repo.answer(couple, me, id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: CallRepository.CallActionException) {
                if (callId == id) finish(reasonFor(e), serverReason = "error", errorMessage = e.message)
                return@launch
            }
            if (callId != id) return@launch
            if (_state.value.phase != CallPhase.CONNECTING) {
                // Hung up while answering: the server has it answered now, so end it there too
                if (response.status == CallRecord.STATE_CONNECTING || response.status == CallRecord.STATE_CONNECTED) {
                    endOnServer(couple, me, id, "hangup")
                }
                return@launch
            }
            when (response.status) {
                CallRecord.STATE_CONNECTING, CallRecord.STATE_CONNECTED -> {
                    val join = response.join
                    if (join == null) finish(CallEndReason.FAILED, serverReason = "error") else joinAudio(join)
                }
                CallRecord.STATE_MISSED -> finish(CallEndReason.MISSED, serverReason = null)
                CallRecord.STATE_CANCELLED -> finish(CallEndReason.CANCELLED, serverReason = null)
                else -> finish(CallEndReason.FAILED, serverReason = null)
            }
        }
    }

    fun decline() = post {
        if (_state.value.phase == CallPhase.INCOMING_RINGING) finish(CallEndReason.DECLINED, serverReason = "declined")
    }

    /** Decline on the ringing notification; [done] when it's safe for the receiver to finish. */
    fun declineFromNotification(context: Context, invite: CallInvite?, done: () -> Unit) = post {
        CallNotifications.cancelIncoming(context)
        if (invite == null) {
            done()
            return@post
        }
        markSeen(invite.callId)
        if (callId == invite.callId && _state.value.phase == CallPhase.INCOMING_RINGING) {
            finish(CallEndReason.DECLINED, serverReason = "declined")
            done()
            return@post
        }
        // The app was started just for this: tell the server directly
        val me = app.authRepository.getCurrentUserId()
        if (me.isBlank()) {
            done()
            return@post
        }
        scope.launch {
            try {
                withTimeoutOrNull(9_000L) { repo.end(invite.coupleId, me, invite.callId, "declined") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Declining failed", e)
            } finally {
                done()
            }
        }
    }

    /** The microphone wasn't allowed: a call can't go ahead without it. */
    fun onMicrophoneDenied(outgoing: Boolean) = post {
        if (outgoing && !hasActiveCall()) {
            showEnded(CallEndReason.PERMISSION_DENIED)
        } else if (_state.value.phase == CallPhase.INCOMING_RINGING) {
            finish(CallEndReason.PERMISSION_DENIED, serverReason = "permission")
        }
    }

    /** Ends the call (or declines a ringing one). */
    fun hangUp() = post {
        when (_state.value.phase) {
            CallPhase.IDLE, CallPhase.ENDED -> Unit
            CallPhase.INCOMING_RINGING -> finish(CallEndReason.DECLINED, serverReason = "declined")
            else -> finish(CallEndReason.HANGUP, serverReason = "hangup")
        }
    }

    /** Manually dismisses the ended screen without waiting for the auto-dismiss timer. */
    fun dismiss() = post {
        if (_state.value.phase == CallPhase.ENDED) {
            endResetJob?.cancel()
            reset()
        }
    }

    // ---------- In-call controls ----------

    fun toggleMute() = post {
        if (!_state.value.isLive && _state.value.phase != CallPhase.OUTGOING_RINGING) return@post
        userMuted = !userMuted
        engine.setMuted(userMuted || onHold)
        _state.update { it.copy(isMuted = userMuted) }
    }

    fun setRoute(route: AudioRoute) = post {
        if (engine.setRoute(route)) _state.update { it.copy(route = route) }
        updateScreenOffAtEar()
    }

    /** Speaker on, or back to the headset (if one is connected) or the earpiece. */
    fun toggleSpeaker() = post {
        val current = _state.value
        val next = when {
            current.route != AudioRoute.SPEAKER -> AudioRoute.SPEAKER
            current.bluetoothAvailable -> AudioRoute.BLUETOOTH
            current.wiredAvailable -> AudioRoute.WIRED
            else -> AudioRoute.EARPIECE
        }
        if (engine.setRoute(next)) _state.update { it.copy(route = next) }
        updateScreenOffAtEar()
    }

    // ---------- While the app is open: ringing without a push ----------

    /**
     * Watches the couple's current call while the app runs, so a call rings even if its push is
     * late. Also ends a call if this phone logs out during it.
     */
    fun watchForIncomingCalls() = post {
        if (watchingIncoming) return@post
        watchingIncoming = true
        scope.launch {
            app.authRepository.currentUserState
                .map { user -> cleanCoupleId(user?.coupleId) to user?.id?.trim()?.ifBlank { null } }
                .distinctUntilChanged()
                .collectLatest { (couple, me) ->
                    if (couple == null || me == null) {
                        if (hasActiveCall()) finish(CallEndReason.HANGUP, serverReason = "hangup")
                        return@collectLatest
                    }
                    if (!repo.ensureMember(couple)) return@collectLatest
                    repo.observeControl(couple).collect { control ->
                        if (control != null && control.state == CallRecord.STATE_RINGING &&
                            control.calleeId == me && control.ringExpiresAt > System.currentTimeMillis()
                        ) {
                            onIncomingInvite(
                                CallInvite(
                                    callId = control.callId,
                                    coupleId = couple,
                                    callerId = control.callerId,
                                    callerName = app.authRepository.partnerUserState.value?.displayName.orEmpty(),
                                    ringUntil = control.ringExpiresAt
                                )
                            )
                        }
                    }
                }
        }
    }

    // ---------- Internals ----------

    private fun beginCall(couple: String, me: String, id: String, outgoing: Boolean) {
        endResetJob?.cancel()
        cancelCallJobs()
        coupleId = couple
        myUserId = me
        callId = id
        isOutgoing = outgoing
        myUid = if (outgoing) CALLER_UID else CALLEE_UID
        peerUid = if (outgoing) CALLEE_UID else CALLER_UID
        localJoined = false
        remoteJoined = false
        userMuted = false
        onHold = false
        invite = null
        callerNameHint = null
    }

    private fun freshState(id: String, outgoing: Boolean) = CallUiState(
        callId = id,
        isOutgoing = outgoing,
        peerName = peerName(callerNameHint),
        peerPhotoUrl = peerPhoto(),
        route = AudioRoute.EARPIECE,
        bluetoothAvailable = audio.hasBluetoothHeadset(),
        wiredAvailable = audio.hasWiredHeadset()
    )

    private fun setPhase(phase: CallPhase) {
        _state.update { it.copy(phase = phase) }
        updateScreenOffAtEar()
    }

    private suspend fun joinAudio(info: CallRepository.JoinInfo) {
        val id = callId
        // Voice notes stop, and stay off the call's audio while it lasts
        app.voicePlayerHelper.stop()
        VoicePlayerHelper.callInProgress = true
        myUid = info.uid
        peerUid = if (info.uid == CALLER_UID) CALLEE_UID else CALLER_UID
        if (!engine.start(info.appId, engineListener)) {
            if (callId == id) finish(CallEndReason.FAILED, serverReason = "error")
            return
        }
        // Ended while the audio was starting: don't join after all
        if (callId != id || !hasActiveCall()) {
            engine.stop()
            return
        }
        if (!engine.join(info.token, info.channel, info.uid)) finish(CallEndReason.FAILED, serverReason = "error")
    }

    private fun endOnServer(couple: String, me: String, id: String, reason: String) {
        scope.launch {
            try {
                repo.end(couple, me, id, reason)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't tell the server the call ended", e)
            }
        }
    }

    private fun observeCallRecord() {
        val couple = coupleId ?: return
        val id = callId ?: return
        docJob?.cancel()
        docJob = scope.launch {
            repo.observeCall(couple, id).collect { record -> onCallRecord(record) }
        }
    }

    private fun onCallRecord(record: CallRecord) {
        if (record.callId != callId) return
        _state.update { it.copy(recordingStatus = record.recordingStatus) }
        val phase = _state.value.phase
        when (record.state) {
            CallRecord.STATE_CONNECTING -> if (isOutgoing && phase == CallPhase.OUTGOING_RINGING) {
                // Answered: stop the ringback while the audio connects
                stopRingback()
                ringTimeoutJob?.cancel()
                setPhase(CallPhase.CONNECTING)
            }
            CallRecord.STATE_CONNECTED -> if (remoteJoined) markConnected()
            CallRecord.STATE_DECLINED ->
                finish(if (isOutgoing) CallEndReason.REMOTE_DECLINED else CallEndReason.DECLINED, serverReason = null)
            CallRecord.STATE_CANCELLED -> {
                if (!isOutgoing && phase == CallPhase.INCOMING_RINGING) {
                    CallNotifications.showMissed(app, record.callId, peerName(callerNameHint))
                }
                finish(CallEndReason.CANCELLED, serverReason = null)
            }
            CallRecord.STATE_MISSED -> {
                if (!isOutgoing && phase == CallPhase.INCOMING_RINGING) {
                    CallNotifications.showMissed(app, record.callId, peerName(callerNameHint))
                }
                finish(if (isOutgoing) CallEndReason.NO_ANSWER else CallEndReason.MISSED, serverReason = null)
            }
            CallRecord.STATE_ENDED ->
                finish(if (record.endedBy == myUserId) CallEndReason.HANGUP else CallEndReason.REMOTE_HANGUP, serverReason = null)
            CallRecord.STATE_UNAVAILABLE -> finish(CallEndReason.UNAVAILABLE, serverReason = null)
            CallRecord.STATE_BUSY -> finish(CallEndReason.BUSY, serverReason = null)
            CallRecord.STATE_FAILED -> finish(CallEndReason.FAILED, serverReason = null)
        }
    }

    /** Both phones are in the call's audio: the call is on. */
    private fun markConnected() {
        if (!localJoined || !remoteJoined) return
        val phase = _state.value.phase
        if (phase == CallPhase.IDLE || phase == CallPhase.ENDED || phase == CallPhase.INCOMING_RINGING) return
        stopRingback()
        ringTimeoutJob?.cancel()
        reconnectJob?.cancel()
        _state.update {
            it.copy(
                phase = CallPhase.CONNECTED,
                connectedAtElapsed = if (it.connectedAtElapsed > 0L) it.connectedAtElapsed else SystemClock.elapsedRealtime(),
                peerReconnecting = false,
                recordingStatus = RecordingStatus.RECORDING
            )
        }
        updateScreenOffAtEar()

        // Start in-app automatic call recording
        val currentCallId = callId
        if (activeRecordFile == null && currentCallId != null) {
            val recordDir = File(app.cacheDir, "callrec").apply { mkdirs() }
            val file = File(recordDir, "rec_${currentCallId}.mp4")
            if (engine.startRecording(file)) {
                activeRecordFile = file
                Log.i(TAG, "In-app call recording started: ${file.absolutePath}")
            }
        }
    }

    /** Tells the backend this phone is in the call's audio, until it confirms the call connected. */
    private fun reportJoined() {
        val couple = coupleId ?: return
        val me = myUserId ?: return
        val id = callId ?: return
        if (joinedReportJob?.isActive == true) return
        joinedReportJob = scope.launch {
            repeat(5) {
                val response = try {
                    repo.joined(couple, me, id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (callId != id) return@launch
                // Connected (and the recording started), or the call is over
                if (response != null && response.status != CallRecord.STATE_CONNECTING && response.status != CallRecord.STATE_RINGING) {
                    return@launch
                }
                delay(3_000L)
            }
        }
    }

    private fun renewToken() {
        val couple = coupleId ?: return
        val me = myUserId ?: return
        val id = callId ?: return
        scope.launch {
            val response = try {
                repo.renewToken(couple, me, id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't renew the call token", e)
                null
            }
            if (callId == id) response?.join?.token?.let(engine::renewToken)
        }
    }

    private fun setHold(hold: Boolean) {
        if (onHold == hold) return
        onHold = hold
        engine.setOnHold(hold, userMuted)
        _state.update { it.copy(isOnHold = hold) }
    }

    private fun updateScreenOffAtEar() {
        val current = _state.value
        val live = current.phase == CallPhase.CONNECTED || current.phase == CallPhase.CONNECTING ||
            current.phase == CallPhase.RECONNECTING || current.phase == CallPhase.OUTGOING_RINGING
        audio.setScreenOffAtEar(live && current.route == AudioRoute.EARPIECE)
    }

    /**
     * Ends the call on this phone: audio, notifications, service, then tells the backend (unless
     * it's the backend that said so). The screen shows why for a moment, then everything resets.
     */
    private fun finish(reason: CallEndReason, serverReason: String?, errorMessage: String? = null) {
        val phase = _state.value.phase
        if (phase == CallPhase.IDLE || phase == CallPhase.ENDED) return
        val endedCouple = coupleId
        val endedCall = callId
        val me = myUserId
        val recordingFile = activeRecordFile
        activeRecordFile = null
        engine.stopRecording()
        val durationSec = if (_state.value.connectedAtElapsed > 0L) {
            ((SystemClock.elapsedRealtime() - _state.value.connectedAtElapsed) / 1000).toInt()
        } else 0
        if (durationSec >= 2 && recordingFile != null && recordingFile.exists() && endedCouple != null && endedCall != null && !me.isNullOrBlank()) {
            uploadRecording(endedCouple, endedCall, me, recordingFile)
        }

        cancelCallJobs()
        stopRingback()
        engine.stop()
        audio.stop()
        VoicePlayerHelper.callInProgress = false
        CallNotifications.cancelIncoming(app)
        CallService.stop(app)
        _state.update {
            it.copy(
                phase = CallPhase.ENDED,
                endReason = reason,
                errorMessage = errorMessage,
                isOnHold = false,
                isWeakNetwork = false,
                peerReconnecting = false
            )
        }
        if (serverReason != null && endedCouple != null && endedCall != null && !me.isNullOrBlank()) {
            scope.launch {
                try {
                    repo.end(endedCouple, me, endedCall, serverReason)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The backend's sweeper ends it if this never arrives
                    Log.w(TAG, "Couldn't tell the server the call ended", e)
                }
            }
        }
        val dismissDelay = if (reason == CallEndReason.HANGUP || reason == CallEndReason.REMOTE_HANGUP) {
            ENDED_SCREEN_MS
        } else {
            4_000L
        }
        endResetJob = scope.launch {
            delay(dismissDelay)
            if (_state.value.phase == CallPhase.ENDED && callId == endedCall) reset()
        }
    }

    /** A call that never got going (not paired, no couple): just the ended screen. */
    private fun showEnded(reason: CallEndReason, errorMessage: String? = null) {
        _state.value = CallUiState(
            phase = CallPhase.ENDED,
            endReason = reason,
            errorMessage = errorMessage,
            isOutgoing = true,
            peerName = peerName(null),
            peerPhotoUrl = peerPhoto()
        )
        endResetJob?.cancel()
        endResetJob = scope.launch {
            delay(4_000L)
            if (_state.value.phase == CallPhase.ENDED) reset()
        }
    }

    private fun reset() {
        cancelCallJobs()
        activeRecordFile?.delete()
        activeRecordFile = null
        coupleId = null
        myUserId = null
        callId = null
        invite = null
        callerNameHint = null
        localJoined = false
        remoteJoined = false
        _state.value = CallUiState()
    }

    private fun uploadRecording(
        couple: String,
        call: String,
        me: String,
        file: File
    ) {
        uploadJob?.cancel()
        uploadJob = scope.launch(Dispatchers.IO) {
            try {
                if (file.length() < 1024) {
                    file.delete()
                    return@launch
                }
                val storage = FirebaseStorage.getInstance()
                val path = "couples/$couple/call-recordings/$call/recording.mp4"
                storage.reference.child(path).putFile(Uri.fromFile(file)).await()
                file.delete()
                repo.finalizeClientRecording(couple, me, call)
                Log.i(TAG, "In-app call recording uploaded and finalized: $path")
            } catch (e: Exception) {
                Log.w(TAG, "In-app call recording upload failed", e)
            }
        }
    }

    private fun cancelCallJobs() {
        docJob?.cancel()
        ringTimeoutJob?.cancel()
        reconnectJob?.cancel()
        peerLostJob?.cancel()
        joinedReportJob?.cancel()
        docJob = null
        ringTimeoutJob = null
        reconnectJob = null
        peerLostJob = null
        joinedReportJob = null
    }

    /** False when [id] was already rung or decided on this phone. */
    private fun markSeen(id: String): Boolean {
        val added = seenCalls.add(id)
        if (seenCalls.size > 100) seenCalls.remove(seenCalls.first())
        return added
    }

    private fun currentCoupleId(): String? =
        cleanCoupleId(app.authRepository.currentUserState.value?.coupleId)
            ?: cleanCoupleId(app.authRepository.getSavedCoupleId())

    private fun cleanCoupleId(raw: String?): String? =
        raw?.trim()?.ifBlank { null }?.takeIf { it != "couple_default" }

    /** The partner's name, unless calls must not show who it is. */
    private fun peerName(fallback: String?): String? {
        if (!CallPrivacy.showIdentity(app)) return null
        return app.authRepository.partnerUserState.value?.displayName?.trim()?.ifBlank { null }
            ?: fallback?.trim()?.ifBlank { null }
            ?: "Your partner"
    }

    private fun peerPhoto(): String? =
        if (CallPrivacy.showIdentity(app)) app.authRepository.partnerUserState.value?.photoUrl?.ifBlank { null } else null

    private fun reasonFor(e: CallRepository.CallActionException): CallEndReason = when (e.kind) {
        CallRepository.CallActionException.Kind.NOT_ALLOWED -> CallEndReason.NOT_ALLOWED
        CallRepository.CallActionException.Kind.NOT_PAIRED -> CallEndReason.NOT_PAIRED
        CallRepository.CallActionException.Kind.NETWORK -> CallEndReason.NETWORK
        else -> CallEndReason.FAILED
    }

    private fun startRingback() {
        stopRingback()
        ringback = try {
            // On the call's own stream, at the phone's call volume (never louder)
            ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70).also { it.startTone(ToneGenerator.TONE_SUP_RINGTONE) }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Ringback tone unavailable", e)
            null
        }
    }

    private fun stopRingback() {
        ringback?.let {
            try {
                it.stopTone()
                it.release()
            } catch (_: Exception) {}
        }
        ringback = null
    }

    private fun playTone(tone: Int, durationMs: Int) {
        try {
            val generator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70)
            generator.startTone(tone, durationMs)
            mainHandler.postDelayed({ generator.release() }, durationMs + 200L)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Tone unavailable", e)
        }
    }

    private val audioListener = object : CallAudioController.Listener {
        override fun onInterrupted(interrupted: Boolean) = post { setHold(interrupted) }

        override fun onHeadsetsChanged(bluetooth: Boolean, wired: Boolean) = post {
            _state.update { it.copy(bluetoothAvailable = bluetooth, wiredAvailable = wired) }
        }
    }

    private val engineListener = object : AgoraVoiceEngine.Listener {
        override fun onJoined(uid: Int) = post {
            localJoined = true
            // The callee joins after answering: the caller is already in
            if (!isOutgoing) reportJoined()
            markConnected()
        }

        override fun onRemoteJoined(uid: Int) = post {
            // Agora's recorder joins the channel too; only the partner counts
            if (uid != peerUid) return@post
            remoteJoined = true
            peerLostJob?.cancel()
            _state.update { it.copy(peerReconnecting = false) }
            markConnected()
            reportJoined()
        }

        override fun onRemoteLeft(uid: Int, dropped: Boolean) = post {
            if (uid != peerUid) return@post
            remoteJoined = false
            if (!dropped) {
                finish(CallEndReason.REMOTE_HANGUP, serverReason = "hangup")
                return@post
            }
            // Their connection dropped: wait a while for them to come back
            _state.update { it.copy(peerReconnecting = true) }
            peerLostJob?.cancel()
            peerLostJob = scope.launch {
                delay(PEER_LOST_GIVE_UP_MS)
                if (!remoteJoined) finish(CallEndReason.PEER_LOST, serverReason = "peer_lost")
            }
        }

        override fun onConnectionStateChanged(state: Int, reason: Int) = post {
            val phase = _state.value.phase
            when (state) {
                Constants.CONNECTION_STATE_RECONNECTING -> {
                    if (phase == CallPhase.CONNECTED || phase == CallPhase.CONNECTING) {
                        setPhase(CallPhase.RECONNECTING)
                        reconnectJob?.cancel()
                        reconnectJob = scope.launch {
                            delay(RECONNECT_GIVE_UP_MS)
                            if (_state.value.phase == CallPhase.RECONNECTING) finish(CallEndReason.NETWORK, serverReason = "network")
                        }
                    } else {
                        _state.update { it.copy(isWeakNetwork = true) }
                    }
                }
                Constants.CONNECTION_STATE_CONNECTED -> {
                    reconnectJob?.cancel()
                    if (phase == CallPhase.RECONNECTING) {
                        if (remoteJoined) markConnected() else setPhase(CallPhase.CONNECTING)
                    }
                }
                Constants.CONNECTION_STATE_FAILED -> {
                    val tokenProblem = reason == Constants.CONNECTION_CHANGED_INVALID_TOKEN ||
                        reason == Constants.CONNECTION_CHANGED_TOKEN_EXPIRED
                    finish(if (tokenProblem) CallEndReason.FAILED else CallEndReason.NETWORK, serverReason = "error")
                }
                Constants.CONNECTION_STATE_DISCONNECTED -> {
                    if (reason == Constants.CONNECTION_CHANGED_BANNED_BY_SERVER ||
                        reason == Constants.CONNECTION_CHANGED_REJECTED_BY_SERVER ||
                        reason == Constants.CONNECTION_CHANGED_SAME_UID_LOGIN
                    ) {
                        finish(CallEndReason.FAILED, serverReason = "error")
                    }
                }
            }
        }

        override fun onTokenExpiring() = post { renewToken() }

        override fun onRouteChanged(route: AudioRoute) = post {
            _state.update { it.copy(route = route) }
            updateScreenOffAtEar()
        }

        override fun onLocalAudioStateChanged(state: Int, reason: Int) = post {
            when {
                state == Constants.LOCAL_AUDIO_STREAM_STATE_FAILED &&
                    reason == Constants.LOCAL_AUDIO_STREAM_REASON_DEVICE_NO_PERMISSION ->
                    finish(CallEndReason.PERMISSION_DENIED, serverReason = "permission")
                reason == Constants.LOCAL_AUDIO_STREAM_REASON_INTERRUPTED -> setHold(true)
                onHold && reason == Constants.LOCAL_AUDIO_STREAM_REASON_OK &&
                    (state == Constants.LOCAL_AUDIO_STREAM_STATE_RECORDING || state == Constants.LOCAL_AUDIO_STREAM_STATE_ENCODING) &&
                    !audio.isPhoneCallActive() -> setHold(false)
            }
        }

        override fun onNetworkQuality(uid: Int, txQuality: Int, rxQuality: Int) = post {
            // uid 0 is this phone's own link
            if (uid != 0) return@post
            val weak = txQuality in Constants.QUALITY_POOR..Constants.QUALITY_DOWN ||
                rxQuality in Constants.QUALITY_POOR..Constants.QUALITY_DOWN
            if (_state.value.isWeakNetwork != weak) _state.update { it.copy(isWeakNetwork = weak) }
        }

        override fun onEngineError(code: Int) = post {
            if (code == Constants.ERR_TOKEN_EXPIRED || code == Constants.ERR_INVALID_TOKEN) renewToken()
        }
    }
}
