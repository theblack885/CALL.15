package com.aicallguard.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager

class GuardService : Service() {

    private lateinit var tm: TelephonyManager
    private var registered = false
    private var aiAccepted = false
    private var newCallback: Any? = null
    private var oldListener: PhoneStateListener? = null

    private fun handleState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                if (!aiAccepted && !AssistantEngine.isRunning()) {
                    aiAccepted = true
                    Handler(Looper.getMainLooper()).postDelayed({ accept() }, 400)
                }
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (aiAccepted) AssistantEngine.start(applicationContext)
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                aiAccepted = false
                AssistantEngine.stop()
            }
        }
    }

    private fun accept() {
        try {
            getSystemService(TelecomManager::class.java).acceptRingingCall()
        } catch (e: SecurityException) {
            CallLog.add("⚠️ Phone permission missing")
        }
    }

    @Suppress("DEPRECATION")
    private fun register() {
        tm = getSystemService(TelephonyManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    handleState(state)
                }
            }
            newCallback = cb
            tm.registerTelephonyCallback(mainExecutor, cb)
        } else {
            val l = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    handleState(state)
                }
            }
            oldListener = l
            tm.listen(l, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    @Suppress("DEPRECATION")
    private fun unregister() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (newCallback as? TelephonyCallback)?.let { tm.unregisterTelephonyCallback(it) }
        } else {
            oldListener?.let { tm.listen(it, PhoneStateListener.LISTEN_NONE) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("guard", "AI Call Guard", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "guard")
            .setContentTitle("AI Call Guard चालू है")
            .setContentText("आने वाली कॉल AI असिस्टेंट उठाएगा")
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, n)
        }

        if (!registered) {
            try {
                register()
                registered = true
                GuardState.active = true
            } catch (e: SecurityException) {
                CallLog.add("⚠️ READ_PHONE_STATE permission needed")
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (registered) {
            try {
                unregister()
            } catch (_: Exception) {
            }
        }
        registered = false
        GuardState.active = false
        AssistantEngine.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
