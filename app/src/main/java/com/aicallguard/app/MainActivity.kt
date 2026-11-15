package com.aicallguard.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.ensureChannels(this)
        setContent { GuardTheme { HomeScreen() } }
    }
}

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val myId = remember { Prefs.myId(ctx) }
    var tab by remember { mutableIntStateOf(0) }
    val listening = ServiceState.listening

    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        val p = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
        permLauncher.launch(p.toTypedArray())
        if (Prefs.listenOn(ctx) && !ServiceState.listening) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, ListenService::class.java))
        }
    }

    fun setListening(on: Boolean) {
        Prefs.setListenOn(ctx, on)
        val i = Intent(ctx, ListenService::class.java)
        if (on) ContextCompat.startForegroundService(ctx, i) else ctx.stopService(i)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1020), Bg)))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text("AI Call Guard", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(
                    "आपका अपना कॉलिंग सिस्टम + AI रिसेप्शनिस्ट",
                    color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp
                )
            }

            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("आपका ID", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                        Text(myId, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Accent)
                    }
                    IconButton(onClick = {
                        val cm = ctx.getSystemService(ClipboardManager::class.java)
                        cm.setPrimaryClip(ClipData.newPlainText("id", myId))
                        toast("ID कॉपी हो गया")
                    }) { Icon(Icons.Rounded.ContentCopy, contentDescription = "copy") }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("कॉल रिसीव करना", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (listening) "चालू • आपके ID पर कॉल आ सकती हैं" else "बंद • कॉल नहीं आएगी",
                            color = if (listening) Accent else Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                    Switch(checked = listening, onCheckedChange = { setListening(it) })
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("डायल", "AI सेटिंग", "हिस्ट्री").forEachIndexed { i, t ->
                    val sel = tab == i
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(if (sel) Accent else Color.White.copy(alpha = 0.07f))
                            .clickable { tab = i }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            t, color = if (sel) Bg else Color.White,
                            fontWeight = FontWeight.Medium, fontSize = 14.sp
                        )
                    }
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (tab) {
                    0 -> DialTab(
                        onCall = { number ->
                            if (number.length < 4) {
                                toast("सही ID डालिए")
                            } else if (ContextCompat.checkSelfPermission(
                                    ctx, Manifest.permission.RECORD_AUDIO
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                toast("माइक की permission दीजिए")
                                permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                            } else {
                                val err = CallManager.startCall(ctx, number)
                                if (err != null) toast(err)
                                else ctx.startActivity(Intent(ctx, CallActivity::class.java))
                            }
                        }
                    )
                    1 -> AiTab()
                    else -> HistoryTab()
                }
            }
        }
    }
}

@Composable
fun DialTab(onCall: (String) -> Unit) {
    var number by remember { mutableStateOf("") }

    @Composable
    fun Key(label: String) {
        Box(
            Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .clickable { if (number.length < 12) number += label },
            contentAlignment = Alignment.Center
        ) { Text(label, fontSize = 24.sp, fontWeight = FontWeight.Medium) }
    }

    GlassCard {
        Text(
            if (number.isEmpty()) "सामने वाले का ID डालिए" else number,
            fontSize = if (number.isEmpty()) 16.sp else 34.sp,
            fontWeight = FontWeight.Bold,
            color = if (number.isEmpty()) Color.White.copy(alpha = 0.35f) else
