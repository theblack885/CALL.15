package com.aicallguard.app

import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class CallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Sounds.init(this)
        handleAction(intent)
        setContent { GuardTheme { CallScreen(onClose = { finish() }) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAction(intent)
    }

    /** Banner ka hara button dabane par call yahin se uthti hai. */
    private fun handleAction(i: Intent?) {
        if (i?.getStringExtra(Notifier.EXTRA_ACT) == "answer") {
            i.removeExtra(Notifier.EXTRA_ACT)
            when {
                CallState.phase == Phase.INCOMING -> CallManager.acceptCall(false)
                CallState.ownerRinging -> CallManager.takeOver()
            }
            Notifier.cancelIncoming(this)
        }
    }
}

@Composable
private fun ActionBtn(
    icon: ImageVector,
    label: String,
    active: Boolean,
    activeColor: Color = Accent,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RoundIcon(
            icon = icon,
            bg = if (active) activeColor else Color.White.copy(alpha = 0.12f),
            boxSize = 58.dp,
            tint = if (active) Bg else Color.White,
            onClick = {
                Sounds.play(Sounds.S.TAP)
                onClick()
            }
        )
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
fun CallScreen(onClose: () -> Unit) {
    val phase = CallState.phase
    val ringingOwner = CallState.aiHandling && CallState.ownerRinging
    var showPad by remember { mutableStateOf(false) }
    var showNote by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var prev by remember { mutableStateOf(phase) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    LaunchedEffect(phase) {
        if (phase == Phase.IDLE) onClose()
        // Call ke sounds: jud gayi / khatam
        if (phase == Phase.ACTIVE && prev != Phase.ACTIVE) Sounds.play(Sounds.S.CALL_CONNECT)
        if (phase == Phase.IDLE && prev != Phase.IDLE) Sounds.play(Sounds.S.CALL_END)
        prev = phase
    }
    // Call lagte waqt "tu... tu..." (ringback)
    LaunchedEffect(phase == Phase.OUTGOING) {
        while (CallState.phase == Phase.OUTGOING) {
            Sounds.play(Sounds.S.RINGBACK)
            delay(4000)
        }
    }

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
    val humanActive = phase == Phase.ACTIVE && !CallState.aiHandling

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
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))
                Box(
                    Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Accent, Violet))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label.take(1).ifEmpty { "?" }, fontSize = 44.sp,
                        fontWeight = FontWeight.Bold, color = Bg
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(label, fontSize = 26.sp, fontWeight = FontWeight.Bold)
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
                if (CallState.remoteHold) {
                    Spacer(Modifier.height(6.dp))
                    Text("⏸ सामने वाले ने आपको होल्ड पर रखा है", color = Color(0xFFFFC857), fontSize = 14.sp)
                }
                if (CallState.onHold) {
                    Spacer(Modifier.height(6.dp))
                    Text("⏸ आपने कॉल होल्ड पर रखी है", color = Color(0xFFFFC857), fontSize = 14.sp)
                }
                if (CallState.recording) {
                    Spacer(Modifier.height(6.dp))
                    Text("🔴 रिकॉर्डिंग चालू", color = Danger, fontSize = 14.sp)
                }
                if (CallState.remoteRecording) {
                    Spacer(Modifier.height(6.dp))
                    Text("🔴 सामने वाला कॉल रिकॉर्ड कर रहा है", color = Danger, fontSize = 14.sp)
                }

                if (humanActive && showNote) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = CallState.note,
                        onValueChange = { CallState.note = it },
                        label = { Text("कॉल का नोट (कॉल खत्म होने पर सेव होगा)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        colors = fieldColors()
                    )
                }

                if (humanActive && showPad) {
                    Spacer(Modifier.height(16.dp))
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
                                        .size(58.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .clickable {
                                            Sounds.dtmf(k)
                                            CallManager.sendDigit(k)
                                        },
                                    contentAlignment = Alignment.Center
                                ) { Text(k.toString(), fontSize = 22.sp) }
                            }
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

                humanActive -> Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ActionBtn(
                            if (CallState.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                            "म्यूट", CallState.muted
                        ) { CallManager.toggleMute() }
                        ActionBtn(
                            Icons.Rounded.VolumeUp, "स्पीकर", CallState.speaker
                        ) { CallManager.toggleSpeaker() }
                        ActionBtn(
                            if (CallState.onHold) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            "होल्ड", CallState.onHold, Color(0xFFFFC857)
                        ) { CallManager.toggleHold() }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ActionBtn(
                            Icons.Rounded.FiberManualRecord, "रिकॉर्ड",
                            CallState.recording, Danger
                        ) { CallManager.toggleRecord() }
                        ActionBtn(
                            Icons.Rounded.Dialpad, "कीपैड", showPad
                        ) { showPad = !showPad }
                        ActionBtn(
                            Icons.Rounded.Edit, "नोट", showNote
                        ) { showNote = !showNote }
                    }
                    Spacer(Modifier.height(18.dp))
                    RoundIcon(Icons.Rounded.CallEnd, Danger, 72.dp) { CallManager.hangup() }
                }

                else -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    RoundIcon(Icons.Rounded.CallEnd, Danger, 76.dp) { CallManager.hangup() }
                }
            }
        }
    }
}
