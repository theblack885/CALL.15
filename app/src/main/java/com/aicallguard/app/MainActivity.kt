package com.aicallguard.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GuardTheme { HomeScreen() } }
    }
}

private fun dial(ctx: Context, code: String) {
    ctx.startActivity(
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(code)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent,
    unfocusedBorderColor = Color.White.copy(0.15f),
    focusedLabelColor = Accent
)

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    var twilio by remember { mutableStateOf(Prefs.get(ctx, Prefs.TWILIO_NUMBER)) }
    var server by remember { mutableStateOf(Prefs.get(ctx, Prefs.SERVER_URL)) }
    var token by remember { mutableStateOf(Prefs.get(ctx, Prefs.API_TOKEN)) }
    var calls by remember { mutableStateOf<List<CallItem>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    val validNumber = twilio.startsWith("+") && twilio.length >= 8

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1020), Bg)))
    ) {
        Column(
            Modifier.fillMaxSize()
                .statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column {
                Text("AI Call Guard", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("आपका पर्सनल AI रिसेप्शनिस्ट", color = Color.White.copy(0.6f))
            }

            GlassCard {
                Text("1. Call Forwarding", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "अपना Twilio नंबर देश-कोड के साथ डालो (जैसे +14155550123)। नीचे के बटन dialer में सही कोड भर देते हैं, " +
                        "आपको बस call दबानी है।",
                    color = Color.White.copy(0.5f), fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = twilio,
                    onValueChange = { twilio = it.trim(); Prefs.set(ctx, Prefs.TWILIO_NUMBER, twilio) },
                    label = { Text("Twilio नंबर") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { dial(ctx, "**61*$twilio**30#") },
                    enabled = validNumber,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)
                ) { Text("जब मैं न उठाऊँ तब AI (सुझाव)") }
                OutlinedButton(
                    onClick = { dial(ctx, "**67*$twilio#") },
                    enabled = validNumber, modifier = Modifier.fillMaxWidth()
                ) { Text("जब फ़ोन busy हो तब AI") }
                OutlinedButton(
                    onClick = { dial(ctx, "**62*$twilio#") },
                    enabled = validNumber, modifier = Modifier.fillMaxWidth()
                ) { Text("जब फ़ोन बंद/नेटवर्क के बाहर हो तब AI") }
                OutlinedButton(
                    onClick = { dial(ctx, "**21*$twilio#") },
                    enabled = validNumber, modifier = Modifier.fillMaxWidth()
                ) { Text("हर कॉल सीधे AI को") }
                TextButton(
                    onClick = { dial(ctx, "##002#") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("सारी forwarding बंद करो", color = Color(0xFFFF5C7A)) }
                Text(
                    "ये GSM कोड ज़्यादातर Airtel/Vi पर चलते हैं। Jio पर न चले तो Phone ऐप → Settings → Call forwarding से करो।",
                    color = Color.White.copy(0.4f), fontSize = 12.sp
                )
            }

            GlassCard {
                Text("2. Server", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it.trim(); Prefs.set(ctx, Prefs.SERVER_URL, server) },
                    label = { Text("Server URL (https://...)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it.trim(); Prefs.set(ctx, Prefs.API_TOKEN, token) },
                    label = { Text("API Token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        loading = true; status = ""
                        ApiClient.fetchCalls(server, token) { r ->
                            loading = false
                            r.onSuccess { calls = it; status = "${it.size} कॉल मिलीं" }
                            r.onFailure { status = "⚠️ Server से जुड़ नहीं पाया: ${it.message}" }
                        }
                    },
                    enabled = server.startsWith("http") && token.isNotEmpty() && !loading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Color.White)
                ) {
                    Icon(Icons.Rounded.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (loading) "लोड हो रहा है..." else "कॉल ट्रांसक्रिप्ट लाओ")
                }
                if (status.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(status, color = Color.White.copy(0.7f), fontSize = 13.sp)
                }
            }

            calls.forEach { c ->
                GlassCard {
                    Text(c.from.ifEmpty { "Unknown" }, fontWeight = FontWeight.SemiBold)
                    Text(c.startedAt, color = Color.White.copy(0.45f), fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    c.lines.forEach {
                        Text(it, color = Color.White.copy(0.85f), fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(0.06f))
            .border(1.dp, Color.White.copy(0.10f), RoundedCornerShape(28.dp))
            .padding(18.dp),
        content = content
    )
}
