package com.aicallguard.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat

object Notifier {
    const val CH_SERVICE = "service"
    const val CH_INCOMING = "incoming"

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CH_SERVICE, "Call सुनना", NotificationManager.IMPORTANCE_LOW)
            )
            val ch = NotificationChannel(CH_INCOMING, "Incoming call", NotificationManager.IMPORTANCE_HIGH)
            ch.setSound(null, null)
            ch.enableVibration(false)
            nm.createNotificationChannel(ch)
        }
    }

    fun incoming(ctx: Context, title: String, text: String) {
        ensureChannels(ctx)
        val i = Intent(ctx, CallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(
            ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CH_INCOMING)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setOngoing(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(2, n)
    }

    fun cancelIncoming(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).cancel(2)
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
