package com.example.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Decline (on the ringing notification) and Hang up (on the call notification). */
class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        when (intent.action) {
            ACTION_DECLINE -> {
                val invite = CallActivity.inviteFrom(intent)
                CallManager.declineFromNotification(context.applicationContext, invite) { pending.finish() }
            }
            ACTION_HANG_UP -> {
                CallManager.hangUp()
                pending.finish()
            }
            else -> pending.finish()
        }
    }

    companion object {
        private const val ACTION_DECLINE = "com.cherish.notes.call.DECLINE"
        private const val ACTION_HANG_UP = "com.cherish.notes.call.HANG_UP"

        fun declineIntent(context: Context, invite: CallInvite): Intent =
            CallActivity.putInvite(Intent(context, CallActionReceiver::class.java).setAction(ACTION_DECLINE), invite)

        fun hangUpIntent(context: Context): Intent =
            Intent(context, CallActionReceiver::class.java).setAction(ACTION_HANG_UP)
    }
}
