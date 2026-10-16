package com.example.call

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.CherishApplication
import com.example.ui.theme.CherishTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The call screen. Its own task (not the chat's), so it can show over the lock screen without
 * the chat, never shows in recent apps, and closing it returns to wherever the user was. The
 * call itself lives in [CallManager]; this screen only shows it and passes on taps.
 */
class CallActivity : ComponentActivity() {

    private val app: CherishApplication
        get() = application as CherishApplication

    private var pendingAction: String? = null
    private var pendingInvite: CallInvite? = null
    /** Shown until this screen has seen its call start (or end), so it doesn't close too early. */
    private var waitingForCall = true
    private val showRecordingNotice = mutableStateOf(false)

    /** Permissions to ask for next (asked from the screen's composition, like the main screen does). */
    private val permissionRequest = mutableStateOf<Array<String>?>(null)

    private fun onPermissionsResult() {
        val action = pendingAction
        val invite = pendingInvite
        pendingAction = null
        pendingInvite = null
        if (hasMicrophone()) run(action, invite) else CallManager.onMicrophoneDenied(action == ACTION_OUTGOING)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showOverLockScreen()
        // The volume keys change the call's volume while this is on screen
        volumeControlStream = AudioManager.STREAM_VOICE_CALL
        // Same rule as the app's other screens (Settings > Screenshot Protection); not in recents previews
        if (app.securityPreferences.isScreenshotProtectionEnabled()) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)
        showRecordingNotice.value = false

        setContent {
            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                onPermissionsResult()
            }
            val request by permissionRequest
            LaunchedEffect(request) {
                val wanted = request ?: return@LaunchedEffect
                permissionRequest.value = null
                permissionLauncher.launch(wanted)
            }
            val themeMode by app.securityPreferences.themeMode.collectAsState()
            val state by CallManager.state.collectAsState()
            val showNotice by showRecordingNotice
            CherishTheme(themeMode = themeMode) {
                CallScreen(
                    state = state,
                    showRecordingNotice = showNotice,
                    onAnswer = { answer(pendingOrCurrentInvite()) },
                    onDecline = { CallManager.decline() },
                    onHangUp = { CallManager.hangUp() },
                    onToggleMute = { CallManager.toggleMute() },
                    onToggleSpeaker = { CallManager.toggleSpeaker() },
                    onSelectRoute = { CallManager.setRoute(it) },
                    onMinimize = { moveTaskToBack(true) },
                    onRetry = {
                        withPermissions(ACTION_OUTGOING, null)
                    },
                    onDismiss = {
                        CallManager.dismiss()
                        dismissAndReturnToCherish()
                    }
                )
            }
        }

        if (savedInstanceState == null) handleIntent(intent)

        // Closes once the call is over (after its "ended" moment), or if there never was one
        lifecycleScope.launch {
            CallManager.state.collect { state ->
                if (state.phase != CallPhase.IDLE) waitingForCall = false
                if (state.phase == CallPhase.IDLE && !waitingForCall) dismissAndReturnToCherish()
                keepScreenOn(state.phase != CallPhase.IDLE && state.phase != CallPhase.ENDED)
            }
        }
        lifecycleScope.launch {
            delay(NOTHING_TO_SHOW_MS)
            if (CallManager.state.value.phase == CallPhase.IDLE && pendingAction == null) dismissAndReturnToCherish()
        }
    }

    private fun dismissAndReturnToCherish() {
        app.securityPreferences.lastCallEndedAt = android.os.SystemClock.elapsedRealtime()
        app.securityPreferences.revealSecretApp()
        try {
            val intent = Intent(this, com.example.MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("open_chat", true)
                putExtra("from_call_ended", true)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.w("CallActivity", "Failed to bring MainActivity to front", e)
        }
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // A ringing call keeps ringing while this screen shows it (its notification stays up)
        CallManager.isCallScreenVisible = true
    }

    override fun onStop() {
        super.onStop()
        CallManager.isCallScreenVisible = false
        val current = CallManager.state.value
        val inv = pendingOrCurrentInvite() ?: CallManager.currentInvite
        if (current.phase == CallPhase.INCOMING_RINGING && inv != null) {
            // Left the call screen while it rings (or the screen went off, also in a pocket): its
            // notification rings from here
            CallNotifications.showIncoming(this, inv)
        }
        val power = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isSpeaking = current.phase == CallPhase.CONNECTED || current.phase == CallPhase.RECONNECTING || current.phase == CallPhase.CONNECTING
        if (isSpeaking && power != null && !power.isInteractive) {
            Log.i("CallActivity", "Lock button pressed on CallActivity while speaking -> hanging up")
            CallManager.hangUp()
        }
    }

    private fun handleIntent(intent: Intent) {
        val invite = inviteFrom(intent)
        when (intent.action) {
            ACTION_OUTGOING -> withPermissions(ACTION_OUTGOING, null)
            ACTION_ANSWER -> answer(invite)
            ACTION_INCOMING -> if (invite != null && !CallManager.hasActiveCall()) CallManager.onIncomingInvite(invite)
            // Back to a call that has ended meanwhile: nothing to show
            ACTION_SHOW -> if (CallManager.state.value.phase == CallPhase.IDLE) dismissAndReturnToCherish()
            else -> Unit
        }
    }

    private fun pendingOrCurrentInvite(): CallInvite? = inviteFrom(intent)

    /** A volume key while it rings: quiet, like a phone call (the call stays on screen to answer). */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val isVolumeKey = keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (isVolumeKey && CallManager.state.value.phase == CallPhase.INCOMING_RINGING) {
            val invite = pendingOrCurrentInvite() ?: CallManager.currentInvite
            if (invite != null) {
                CallNotifications.silence(this, invite)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Answering from the lock screen with hidden notifications (or a Discreet incoming call) asks
     * to unlock first, so nobody else holding the phone can take the call.
     */
    private fun answer(invite: CallInvite?) {
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val discreet = app.securityPreferences.getCallMaskingStyle().equals("discreet", ignoreCase = true)
        if (keyguard != null && keyguard.isKeyguardLocked && discreet &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ) {
            keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = withPermissions(ACTION_ANSWER, invite)
            })
            return
        }
        withPermissions(ACTION_ANSWER, invite)
    }

    /** The microphone is needed to talk; Bluetooth headsets and the call notification are asked for too. */
    private fun withPermissions(action: String, invite: CallInvite?) {
        val wanted = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isEmpty()) {
            run(action, invite)
            return
        }
        pendingAction = action
        pendingInvite = invite
        permissionRequest.value = wanted.toTypedArray()
    }

    private fun run(action: String?, invite: CallInvite?) {
        if (action != null) {
            app.securityPreferences.setSeenCallRecordingNotice()
            showRecordingNotice.value = false
        }
        when (action) {
            ACTION_OUTGOING -> CallManager.startOutgoing()
            ACTION_ANSWER -> CallManager.accept(invite)
        }
    }

    private fun hasMicrophone(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
    }

    private fun keepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        private const val ACTION_OUTGOING = "com.cherish.notes.call.OUTGOING"
        private const val ACTION_INCOMING = "com.cherish.notes.call.INCOMING"
        private const val ACTION_ANSWER = "com.cherish.notes.call.ANSWER"
        private const val ACTION_SHOW = "com.cherish.notes.call.SHOW"
        private const val EXTRA_CALL_ID = "call_id"
        private const val EXTRA_COUPLE_ID = "couple_id"
        private const val EXTRA_CALLER_ID = "caller_id"
        private const val EXTRA_CALLER_NAME = "caller_name"
        private const val EXTRA_RING_UNTIL = "ring_until"
        private const val NOTHING_TO_SHOW_MS = 4_000L

        /** Calls the partner (from the chat). Shows the current call instead if one is on. */
        fun startOutgoing(context: Context) {
            val app = context.applicationContext as? CherishApplication
            if (app?.securityPreferences?.isMonthlyCallLimitReached() == true && !CallManager.hasActiveCall()) {
                android.widget.Toast.makeText(
                    context,
                    "Monthly 10,000 min limit reached. Calls will re-enable next month.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                return
            }
            val intent = if (CallManager.hasActiveCall()) showIntent(context) else {
                Intent(context, CallActivity::class.java).setAction(ACTION_OUTGOING)
            }
            // Opening the call screen isn't the user leaving the chat
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            context.startActivity(intent)
        }

        fun incomingIntent(context: Context, invite: CallInvite): Intent =
            putInvite(Intent(context, CallActivity::class.java).setAction(ACTION_INCOMING), invite)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION)

        fun answerIntent(context: Context, invite: CallInvite): Intent =
            putInvite(Intent(context, CallActivity::class.java).setAction(ACTION_ANSWER), invite)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION)

        fun showIntent(context: Context): Intent =
            Intent(context, CallActivity::class.java).setAction(ACTION_SHOW)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION)

        fun putInvite(intent: Intent, invite: CallInvite): Intent = intent
            .putExtra(EXTRA_CALL_ID, invite.callId)
            .putExtra(EXTRA_COUPLE_ID, invite.coupleId)
            .putExtra(EXTRA_CALLER_ID, invite.callerId)
            .putExtra(EXTRA_CALLER_NAME, invite.callerName)
            .putExtra(EXTRA_RING_UNTIL, invite.ringUntil)

        fun inviteFrom(intent: Intent?): CallInvite? {
            val callId = intent?.getStringExtra(EXTRA_CALL_ID)?.ifBlank { null } ?: return null
            val coupleId = intent.getStringExtra(EXTRA_COUPLE_ID)?.ifBlank { null } ?: return null
            return CallInvite(
                callId = callId,
                coupleId = coupleId,
                callerId = intent.getStringExtra(EXTRA_CALLER_ID).orEmpty(),
                callerName = intent.getStringExtra(EXTRA_CALLER_NAME).orEmpty(),
                ringUntil = intent.getLongExtra(EXTRA_RING_UNTIL, 0L)
            )
        }
    }
}
