package com.aicallguard.app

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/** Peeche chalta rehta hai aur aapke ID par aane wali calls sunta hai. */
class ListenService : Service() {
    private var reg: ListenerRegistration? = null
    private var wl: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifier.ensureChannels(this)
        val n = NotificationCompat.Builder(this, Notifier.CH_SERVICE)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("AI Call चालू है")
            .setContentText("आने वाली कॉल सुन रहा हूँ • आपका ID: ${Prefs.myId(this)}")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, 1, n, type)

        if (wl == null) {
            val pm = getSystemService(PowerManager::class.java)
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "aicall:listen").also { it.acquire() }
        }

        if (reg == null) {
            val myId = Prefs.myId(this)
            reg = FirebaseFirestore.getInstance().collection("calls")
                .whereEqualTo("to", myId)
                .whereEqualTo("status", "ringing")
                .addSnapshotListener { snap, _ ->
                    snap?.documentChanges?.forEach { ch ->
                        if (ch.type == DocumentChange.Type.ADDED) {
                            CallManager.onIncoming(applicationContext, ch.document.id, ch.document.data)
                        }
                    }
                }
        }
        ServiceState.listening = true
        return START_STICKY
    }

    override fun onDestroy() {
        try { reg?.remove() } catch (_: Exception) {}
        reg = null
        try { if (wl?.isHeld == true) wl?.release() } catch (_: Exception) {}
        wl = null
        ServiceState.listening = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED && Prefs.listenOn(c)) {
            ContextCompat.startForegroundService(c, Intent(c, ListenService::class.java))
        }
    }
}
