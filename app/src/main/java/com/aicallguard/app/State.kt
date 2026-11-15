package com.aicallguard.app

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Phase { IDLE, OUTGOING, INCOMING, CONNECTING, ACTIVE }

object CallState {
    var phase by mutableStateOf(Phase.IDLE)
    var peerId by mutableStateOf("")
    var peerName by mutableStateOf("")
    var info by mutableStateOf("")
    var muted by mutableStateOf(false)
    var speaker by mutableStateOf(false)
    var aiHandling by mutableStateOf(false)
    var ownerRinging by mutableStateOf(false)
    var startedAt by mutableStateOf(0L)

    val log = mutableStateListOf<String>()

    fun addLog(s: String) {
        Handler(Looper.getMainLooper()).post {
            log.add(0, s)
            if (log.size > 60) log.removeAt(log.lastIndex)
        }
    }
}

object ServiceState {
    var listening by mutableStateOf(false)
}
