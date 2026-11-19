package com.aicallguard.app

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChatTab() {
    val ctx = LocalContext.current
    var newId by remember { mutableStateOf("") }
    var sounds by remember { mutableStateOf(Prefs.soundsOn(ctx)) }
    @Suppress("UNUSED_VARIABLE") val tick = ChatRepo.readTick.intValue
    val peers = ChatRepo.peers()

    fun open(p: String) {
        Prefs.addExtraPeer(ctx, p)
        ctx.startActivity(Intent(ctx, ChatActivity::class.java).putExtra("peer", p))
    }

    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("आवाज़ें (sounds)", fontWeight = FontWeight.SemiBold)
                Text(
                    "डायल, चैट और कॉल की आवाज़ें. फ़ोन silent हो तो नहीं बजेंगी.",
                    fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f)
                )
            }
            Switch(checked = sounds, onCheckedChange = {
                sounds = it
                Prefs.setSoundsOn(ctx, it)
                Sounds.enabled = it
                if (it) Sounds.play(Sounds.S.RECEIVE)
            })
        }
    }

    GlassCard {
        Text("नई चैट", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newId,
                onValueChange = { newId = it.filter { c -> c.isDigit() }.take(12) },
                label = { Text("सामने वाले का ID") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                colors = fieldColors()
            )
            Spacer(Modifier.size(8.dp))
            Button(
                onClick = {
                    if (newId.length >= 4) {
                        open(newId)
                        newId = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)
            ) { Text("चैट") }
        }
    }

    GlassCard {
        Text("आपकी चैट्स", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))
        if (peers.isEmpty()) {
            Text("अभी कोई चैट नहीं। ऊपर ID डालकर शुरू कीजिए।", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
        } else {
            peers.forEach { p ->
                val last = ChatRepo.conversation(p).lastOrNull()
                val unread = ChatRepo.unread(p)
                val name = ChatRepo.peerName(p).ifBlank { p }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { open(p) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Accent, Violet))),
                        contentAlignment = Alignment.Center
                    ) { Text(name.take(1), color = Bg, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                    Column(Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.Medium)
                        Text(
                            last?.let { (if (it.from == p) "" else "आप: ") + ChatRepo.preview(it) } ?: "ID: $p",
                            fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f), maxLines = 1
                        )
                    }
                    if (unread > 0) {
                        Box(
                            Modifier.size(24.dp).clip(CircleShape).background(Accent),
                            contentAlignment = Alignment.Center
                        ) { Text("$unread", color = Bg, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}
