package com.aicallguard.app

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotesTab() {
    val ctx = LocalContext.current
    var notes by remember { mutableStateOf(Prefs.notes(ctx)) }
    var text by remember { mutableStateOf("") }

    GlassCard {
        Text("नया नोट", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("यहाँ लिखिए") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            colors = fieldColors()
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                if (text.isNotBlank()) {
                    val t = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date())
                    Prefs.addNote(ctx, "$t\n${text.trim()}")
                    text = ""
                    notes = Prefs.notes(ctx)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg),
            modifier = Modifier.fillMaxWidth()
        ) { Text("नोट सेव करो") }
    }

    GlassCard {
        Text("सेव किए हुए नोट्स", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        if (notes.isEmpty()) {
            Text("अभी कोई नोट नहीं।", color = Color.White.copy(alpha = 0.5f))
        } else {
            notes.forEachIndexed { i, n ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(n, fontSize = 14.sp)
                    TextButton(onClick = {
                        Prefs.deleteNote(ctx, i)
                        notes = Prefs.notes(ctx)
                    }) { Text("हटाओ", color = Danger, fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
fun RecordingsCard() {
    val ctx = LocalContext.current
    var files by remember { mutableStateOf(listRecordings(ctx)) }
    var playing by remember { mutableStateOf("") }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try { player?.release() } catch (_: Exception) {}
        }
    }

    GlassCard {
        Text("कॉल रिकॉर्डिंग्स", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        if (files.isEmpty()) {
            Text(
                "अभी कोई रिकॉर्डिंग नहीं। कॉल के दौरान रिकॉर्ड बटन दबाइए।",
                color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp
            )
        } else {
            files.forEach { f ->
                val secs = ((f.length() - 44) / 32000).coerceAtLeast(0)
                val isPlaying = playing == f.name
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(f.name.removePrefix("call_").removeSuffix(".wav"), fontSize = 13.sp)
                    Text("${secs} सेकंड", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            try { player?.release() } catch (_: Exception) {}
                            player = null
                            if (isPlaying) {
                                playing = ""
                            } else {
                                try {
                                    val mp = MediaPlayer()
                                    mp.setDataSource(f.absolutePath)
                                    mp.prepare()
                                    mp.setOnCompletionListener { playing = "" }
                                    mp.start()
                                    player = mp
                                    playing = f.name
                                } catch (_: Exception) {
                                    playing = ""
                                }
                            }
                        }) { Text(if (isPlaying) "⏹ रोको" else "▶ सुनो", color = Accent) }
                        TextButton(onClick = {
                            if (isPlaying) {
                                try { player?.release() } catch (_: Exception) {}
                                player = null
                                playing = ""
                            }
                            f.delete()
                            files = listRecordings(ctx)
                        }) { Text("हटाओ", color = Danger) }
                    }
                }
            }
        }
    }
}
