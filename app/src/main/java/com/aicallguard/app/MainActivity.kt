package com.aicallguard.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.foundation.horizontalScroll
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.ensureChannels(this)
        Sounds.init(this)
        ChatRepo.start(this)
        setContent { GuardTheme { HomeScreen() } }
    }
}

/** SIM wale number par phone ke apne dialer se normal call. */
private fun placeSimCall(ctx: Context, number: String, direct: Boolean) {
    try {
        val uri = Uri.parse("tel:" + Uri.encode(number))
        val action = if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL
        ctx.startActivity(Intent(action, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val time = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date())
        Prefs.addHistory(ctx, "↗ 📱 SIM • $number • $time")
    } catch (e: Exception) {
        Toast.makeText(ctx, "कॉल नहीं लग पाई: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val myId = remember { Prefs.myId(ctx) }
    var tab by remember { mutableIntStateOf(0) }
    var pendingSim by remember { mutableStateOf("") }
    val listening = ServiceState.listening

    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    val simPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok ->
        val n = pendingSim
        pendingSim = ""
        if (n.isNotEmpty()) placeSimCall(ctx, n, ok)
    }

    fun simCall(number: String) {
        if (number.length < 5) {
            toast("सही नंबर डालिए")
            return
        }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            placeSimCall(ctx, number, true)
        } else {
            pendingSim = number
            simPerm.launch(Manifest.permission.CALL_PHONE)
        }
    }

    LaunchedEffect(Unit) {
        val p = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= 31) p.add(Manifest.permission.BLUETOOTH_CONNECT)
        permLauncher.launch(p.toTypedArray())

        if (Prefs.listenOn(ctx) && !ServiceState.listening) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, ListenService::class.java))
            } catch (_: Exception) {
            }
        }
        if (Prefs.btListenOn(ctx) && !BtState.listening) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, BtListenService::class.java))
            } catch (_: Exception) {
            }
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
                    "आपका अपना कॉलिंग सिस्टम + AI रिसेप्शनिस्ट + चैट",
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
                        cm?.setPrimaryClip(ClipData.newPlainText("id", myId))
                        toast("ID कॉपी हो गया")
                    }) { Icon(Icons.Rounded.ContentCopy, contentDescription = "copy") }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("इंटरनेट से कॉल रिसीव करना", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (listening) "चालू • कॉल और चैट नोटिफ़िकेशन आएँगे" else "बंद • इंटरनेट कॉल नहीं आएगी",
                            color = if (listening) Accent else Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                    Switch(checked = listening, onCheckedChange = { setListening(it) })
                }
            }

            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("डायल", "चैट", "AI", "हिस्ट्री", "नोट्स", "ब्लूटूथ").forEachIndexed { i, t ->
                    val sel = tab == i
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (sel) Accent else Color.White.copy(alpha = 0.07f))
                            .clickable {
                                Sounds.play(Sounds.S.TAP)
                                tab = i
                            }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            t, color = if (sel) Bg else Color.White,
                            fontWeight = FontWeight.Medium, fontSize = 13.sp
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
                        },
                        onSimCall = { number -> simCall(number) }
                    )
                    1 -> ChatTab()
                    2 -> AiTab()
                    3 -> {
                        HistoryTab()
                        RecordingsCard()
                    }
                    4 -> NotesTab()
                    else -> BtTab()
                }
            }
        }
    }
}

@Composable
fun DialTab(onCall: (String) -> Unit, onSimCall: (String) -> Unit) {
    var number by remember { mutableStateOf("") }

    @Composable
    fun Key(label: String) {
        Box(
            Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .clickable {
                    Sounds.dtmf(label[0])
                    if (number.length < 15) number += label
                },
            contentAlignment = Alignment.Center
        ) { Text(label, fontSize = 24.sp, fontWeight = FontWeight.Medium) }
    }

    GlassCard {
        Text(
            if (number.isEmpty()) "ID या मोबाइल नंबर डालिए" else number,
            fontSize = if (number.isEmpty()) 16.sp else if (number.length > 10) 26.sp else 34.sp,
            fontWeight = FontWeight.Bold,
            color = if (number.isEmpty()) Color.White.copy(alpha = 0.35f) else Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            textAlign = TextAlign.Center
        )
        Text(
            "8 अंक का ID = ऐप-से-ऐप कॉल (इंटरनेट) • मोबाइल नंबर = SIM से normal कॉल",
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.45f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) { row.forEach { Key(it) } }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable {
                        Sounds.play(Sounds.S.TAP)
                        number = number.dropLast(1)
                    },
                contentAlignment = Alignment.Center
            ) { Text("⌫", fontSize = 24.sp) }
            Key("0")
            RoundIcon(Icons.Rounded.Call, Green) {
                Sounds.play(Sounds.S.TAP)
                onCall(number)
            }
        }
        Spacer(Modifier.height(6.dp))
        Button(
            onClick = { onSimCall(number) },
            colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) { Text("📱 SIM नंबर पर कॉल (normal कॉल)") }
    }
}

@Composable
fun AiTab() {
    val ctx = LocalContext.current
    var ai by remember { mutableStateOf(Prefs.aiMode(ctx)) }
    var name by remember { mutableStateOf(Prefs.name(ctx)) }
    var code by remember { mutableStateOf(Prefs.code(ctx)) }
    var showCode by remember { mutableStateOf(false) }
    var trusted by remember { mutableStateOf(Prefs.trusted(ctx)) }
    var modelReady by remember { mutableStateOf(ModelStore.ready(ctx)) }
    var progress by remember { mutableIntStateOf(-1) }
    var err by remember { mutableStateOf<String?>(null) }

    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AI असिस्टेंट", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text(
                    "चालू हो तो आने वाली कॉल AI उठाएगा",
                    color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp
                )
            }
            Switch(checked = ai, onCheckedChange = { ai = it; Prefs.setAiMode(ctx, it) })
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; Prefs.setName(ctx, it) },
            label = { Text("आपका नाम (AI यही बोलेगा, चैट में भी दिखेगा)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { v ->
                val d = v.filter { it.isDigit() }.take(8)
                code = d
                if (d.length >= 4) Prefs.setCode(ctx, d)
            },
            label = { Text("सीक्रेट कोड (कम से कम 4 अंक)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = if (showCode) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                Text(
                    if (showCode) "छुपाओ" else "दिखाओ",
                    modifier = Modifier
                        .clickable { showCode = !showCode }
                        .padding(12.dp),
                    color = Accent, fontSize = 13.sp
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = trusted,
            onValueChange = { trusted = it; Prefs.setTrusted(ctx, it) },
            label = { Text("भरोसेमंद IDs / Bluetooth नाम (कॉमा से अलग)") },
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors()
        )
    }

    GlassCard {
        Text("आवाज़ पहचान मॉडल (हिंदी, ~42 MB)", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            if (modelReady) "✅ तैयार है — caller बोलकर भी कोड बता सकेगा"
            else "डाउनलोड नहीं है — बिना इसके caller को कीपैड से कोड डालना पड़ेगा",
            color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp
        )
        if (progress in 0..99) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }
        err?.let {
            Spacer(Modifier.height(6.dp))
            Text("⚠️ $it", color = Danger, fontSize = 12.sp)
        }
        if (!modelReady) {
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    err = null
                    progress = 0
                    ModelStore.download(ctx, { progress = it }) { e ->
                        err = e
                        progress = -1
                        modelReady = ModelStore.ready(ctx)
                    }
                },
                enabled = progress < 0,
                colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (progress in 0..99) "डाउनलोड हो रहा है… $progress%"
                    else "मॉडल डाउनलोड करो (WiFi पर)"
                )
            }
        }
    }

    GlassCard {
        Text("लाइव AI लॉग", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        if (CallState.log.isEmpty()) {
            Text("अभी तक कोई AI कॉल नहीं हुई।", color = Color.White.copy(alpha = 0.5f))
        } else {
            CallState.log.take(25).forEach {
                Text(
                    it, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
fun HistoryTab() {
    val ctx = LocalContext.current
    val hist = remember { Prefs.history(ctx) }
    GlassCard {
        Text("कॉल हिस्ट्री", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        if (hist.isEmpty()) {
            Text("अभी कोई कॉल नहीं।", color = Color.White.copy(alpha = 0.5f))
        } else {
            hist.forEach {
                Text(it, modifier = Modifier.padding(vertical = 5.dp), fontSize = 14.sp)
            }
        }
    }
}
