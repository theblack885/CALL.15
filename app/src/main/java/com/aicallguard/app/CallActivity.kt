package com.aicallguard.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class CallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GuardTheme { CallScreen(onClose = { finish() }) } }
    }
}

@Composable
fun CallScreen(onClose: () -> Unit) {
    val phase = CallState.phase
    val ringingOwner = CallState.aiHandling && CallState.ownerRinging
    var showPad by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    LaunchedEffect(phase) { if (phase == Phase.IDLE) onClose() }

    val secs = if (CallState.startedAt > 0) ((now - CallState.startedAt) / 1000).toInt() else 0
    val timer = "%02d:%02d".format(secs / 60, secs % 60)
    val status = when {
        phase == Phase.OUTGOING -> "कॉल लगाई जा रही है…"
        phase == Phase.INCOMING -> "आपको कॉल आ रही है"
        phase == Phase.CONNECTING -> "कनेक्ट हो रहा है…"
        ringingOwner -> "कोड सही — लाइन पर है"
        phase == Phase.ACTIVE && CallState.aiHandling -> "AI असिस्टेंट बात कर रहा है • $timer"
        phase == Phase.ACTIVE -> timer
        else -> ""
    }
    val label = if (CallState.peerName.isNotBlank()) CallState.peerName else CallState.peerId

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF111A33), Bg)))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))
            Box(
                Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Accent, Violet))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label.take(1).ifEmpty { "?" }, fontSize = 48.sp,
                    fontWeight = FontWeight.Bold, color = Bg
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(label, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            if (CallState.peerName.isNotBlank()) {
                Text(CallState.peerId, color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(status, color = Accent, fontSize = 16.sp, textAlign = TextAlign.Center)
            if (CallState.info.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    CallState.info, color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.weight(1f))

            if (showPad && phase == Phase.ACTIVE && !CallState.aiHandling) {
                listOf(
                    listOf('1', '2', '3'), listOf('4', '5', '6'),
                    listOf('7', '8', '9'), listOf('*', '0', '#')
                ).forEach { row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { k ->
                            Box(
                                Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .clickable { CallManager.sendDigit(k) },
                                contentAlignment = Alignment.Center
                            ) { Text(k.toString(), fontSize = 22.sp) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            when {
                phase == Phase.INCOMING -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    RoundIcon(Icons.Rounded.CallEnd, Danger, 76.dp) { CallManager.decline() }
                    RoundIcon(Icons.Rounded.Call, Green, 76.dp) { CallManager.acceptCall(false) }
                }

                ringingOwner -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    RoundIcon(Icons.Rounded.CallEnd, Danger, 76.dp) { CallManager.hangup() }
                    RoundIcon(Icons.Rounded.Call, Green, 76.dp) { CallManager.takeOver() }
                }

                phase == Phase.ACTIVE && !CallState.aiHandling -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        RoundIcon(
                            if (CallState.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                            if (CallState.muted) Accent else Color.White.copy(alpha = 0.12f),
                            tint = if (CallState.muted) Bg else Color.White
                        ) { CallManager.toggleMute() }
                        RoundIcon(
                            Icons.Rounded.VolumeUp,
                            if (CallState.speaker) Accent else Color.White.copy(alpha = 0.12f),
                            tint = if (CallState.speaker) Bg else Color.White
                        ) { CallManager.toggleSpeaker() }
                        RoundIcon(
                            Icons.Rounded.Dialpad,
                            if (showPad) Accent else Color.White.copy(alpha = 0.12f),
                            tint = if (showPad) Bg else Color.White
                        ) { showPad = !showPad }
                    }
                    Spacer(Modifier.height(24.dp))
                    RoundIcon(Icons.Rounded.CallEnd, Danger, 76.dp) { CallManager.hangup() }
                }

                else -> RoundIcon(Icons.Rounded.CallEnd, Danger, 76.dp) { CallManager.hangup() }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
