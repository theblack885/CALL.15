package com.aicallguard.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.Person

object Notifier {
    const val CH_SERVICE = "service"
    const val CH_INCOMING = "incoming_v2"
    const val CH_CHAT = "chat"
    const val ACTION_DECLINE = "com.aicallguard.app.DECLINE"
    const val EXTRA_ACT = "act"

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CH_SERVICE, "Call सुनना", NotificationManager.IMPORTANCE_LOW)
            )
            val ch = NotificationChannel(CH_INCOMING, "Incoming call", NotificationManager.IMPORTANCE_HIGH)
            ch.setSound(null, null)
            ch.enableVibration(false)
            ch.lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(ch)

            val chat = NotificationChannel(CH_CHAT, "चैट मैसेज", NotificationManager.IMPORTANCE_HIGH)
            chat.setSound(null, null)   // आवाज़ ऐप खुद बजाता है
            chat.enableVibration(true)
            nm.createNotificationChannel(chat)
        }
    }

    /** Normal call jaisa banner: hara = uthao, laal = kaato. */
    fun incoming(ctx: Context, title: String, text: String) {
        ensureChannels(ctx)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val base = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP

        val openPi = PendingIntent.getActivity(
            ctx, 10, Intent(ctx, CallActivity::class.java).addFlags(base), flags
        )
        val answerPi = PendingIntent.getActivity(
            ctx, 11,
            Intent(ctx, CallActivity::class.java).addFlags(base).putExtra(EXTRA_ACT, "answer"),
            flags
        )
        val declinePi = PendingIntent.getBroadcast(
            ctx, 12,
            Intent(ctx, CallActionReceiver::class.java).setAction(ACTION_DECLINE),
            flags
        )

        val who = CallState.peerName.ifBlank { CallState.peerId }.ifBlank { title }
        val person = Person.Builder().setName(who).setImportant(true).build()

        val n = NotificationCompat.Builder(ctx, CH_INCOMING)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(who)
            .setContentText(text)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, declinePi, answerPi))
            .addPerson(person)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(Config.RING_TIMEOUT_MS)
            .setContentIntent(openPi)
            .setFullScreenIntent(openPi, true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(2, n)
    }

    fun cancelIncoming(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).cancel(2)
    }

    private fun msgId(peer: String) = 1000 + (peer.hashCode() and 0xFFFF)

    /** Naye chat message ki notification. */
    fun message(ctx: Context, peer: String, title: String, text: String) {
        ensureChannels(ctx)
        val i = Intent(ctx, ChatActivity::class.java)
            .putExtra("peer", peer)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            ctx, msgId(peer), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CH_CHAT)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(msgId(peer), n)
    }

    fun cancelMessage(ctx: Context, peer: String) {
        ctx.getSystemService(NotificationManager::class.java).cancel(msgId(peer))
    }
}

/** Banner ka laal (kaato) button yahan aata hai. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Notifier.ACTION_DECLINE) {
            if (CallState.ownerRinging) CallManager.hangup() else CallManager.decline()
            Notifier.cancelIncoming(c)
        }
    }
}

object Ringer {
    private var ringtone: Ringtone? = null
    private var vib: Vibrator? = null

    @Suppress("DEPRECATION")
    fun start(ctx: Context) {
        stop()
        try {
            val r = RingtoneManager.getRingtone(
                ctx, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            )
            if (Build.VERSION.SDK_INT >= 28) r.isLooping = true
            r.play()
            ringtone = r
            val v = ctx.getSystemService(Vibrator::class.java)
            v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 700), 0))
            vib = v
        } catch (_: Exception) {
        }
    }

    fun stop() {
        try { ringtone?.stop() } catch (_: Exception) {}
        try { vib?.cancel() } catch (_: Exception) {}
        ringtone = null
        vib = null
    }
}
