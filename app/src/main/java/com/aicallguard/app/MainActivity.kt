package com.aicallguard.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
        setContent { GuardTheme { HomeScreen() } }
    }
}

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    var owner by remember { mutableStateOf(Prefs.owner(ctx)) }
    var code by remember { mutableStateOf(Prefs.code(ctx)) }
    var showCode by remember { mutableStateOf(false) }
    val active = GuardState.active

    val perms = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.POST_NOTIFICATIONS
    )

    fun startGuard() {
        ContextCompat.startForegroundService(ctx, Intent(ctx, GuardService::class.java))
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { r ->
        if (r[Manifest.permission.RECORD_AUDIO] == true &&
            r[Manifest.permission.READ_PHONE_STATE] == true &&
            r[Manifest.permission.ANSWER_PHONE_CALLS] == true
        ) {
            startGuard()
        }
    }

    fun toggle() {
        if (active) {
            ctx.stopService(Intent(ctx, GuardService::class.java))
        } else {
            val missing = perms.filter {
                ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isEmpty()) startGuard() else launcher.launch(missing.toTypedArray())
        }
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
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column {
                Text("AI Call Guard", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("आपका पर्सनल AI रिसेप्शनिस्ट", color = Color.White.copy(alpha = 0.6f))
            }

            PowerOrb(active) { toggle() }

            Text(
                if (active) "सुरक्षा चालू • हर कॉल AI उठाएगा" else "सुरक्षा बंद • चालू करने के लिए टैप करें",
                color = if (active) Accent else Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            GlassCard {
                Text("सेटिंग्स", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = owner,
                    onValueChange = { v ->
                        owner = v
                        Prefs.setOwner(ctx, v)
                    },
                    label = { Text("आपका नाम (AI यही बोलेगा)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
                Spacer(Modifier.height(12.dp))
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
                        IconButton(onClick = { showCode = !showCode }) {
                            Icon(
                                if (showCode) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
            }

            GlassCard {
                Text("लाइव ट्रांसक्रिप्ट", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                if (CallLog.lines.isEmpty()) {
                    Text("अभी तक कोई कॉल नहीं आई।", color = Color.White.copy(alpha = 0.5f))
                } else {
                    CallLog.lines.take(20).forEach { line ->
                        Text(
                            line,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                }
            }

            Text(
                "टिप: बैटरी सेटिंग्स में इस ऐप को \"Unrestricted\" रखें और पहले दूसरे फ़ोन से टेस्ट करें। " +
                    "फ़ोन को शांत जगह रखें और volume ज़्यादा रखें। कुछ फ़ोन कॉल के दौरान माइक बंद कर देते हैं।",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent,
    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
    focusedLabelColor = Accent
)

@Composable
fun PowerOrb(active: Boolean, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "pulse")
    val pulse by t.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(210.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(200.dp)
                .scale(if (active) pulse else 1f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        if (active) listOf(Accent.copy(alpha = 0.35f), Color.Transparent)
                        else listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)
                    )
                )
        )
        Box(
            Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        if (active) listOf(Accent, Violet)
                        else listOf(Color(0xFF1B2133), Color(0xFF141A2A))
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.PowerSettingsNew,
                contentDescription = "Toggle",
                tint = if (active) Bg else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(56.dp)
            )
        }
    }
}

@Composable
fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(28.dp))
            .padding(18.dp),
        content = content
    )
}
