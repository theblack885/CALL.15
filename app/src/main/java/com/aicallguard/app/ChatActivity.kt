package com.aicallguard.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatActivity : ComponentActivity() {
    private var peer = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Sounds.init(this)
        ChatRepo.start(this)
        peer = intent.getStringExtra("peer") ?: ""
        if (peer.isBlank()) {
            finish()
            return
        }
        setContent { GuardTheme { ChatScreen(peer) { finish() } } }
    }

    override fun onResume() {
        super.onResume()
        ChatRepo.activePeer = peer
        ChatRepo.markRead(peer)
        Notifier.cancelMessage(this, peer)
    }

    override fun onPause() {
        super.onPause()
        ChatRepo.activePeer = ""
    }
}

private fun decodeThumb(b64: String): ImageBitmap? = try {
    if (b64.isBlank()) null else {
        val b = Base64.decode(b64, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap()
    }
} catch (e: Exception) {
    null
}

private fun fmtSize(n: Long): String = when {
    n >= 1024 * 1024 -> "%.1f MB".format(n / 1024.0 / 1024.0)
    n >= 1024 -> "${n / 1024} KB"
    else -> "$n B"
}

private fun openFile(ctx: Context, m: Msg) {
    ChatRepo.fetchFile(m) { f ->
        if (f == null) return@fetchFile
        try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, m.mime.ifBlank { "*/*" })
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Toast.makeText(ctx, "इस फ़ाइल को खोलने वाला ऐप नहीं मिला", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun ChatScreen(peer: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val me = remember { Prefs.myId(ctx) }
    val list = ChatRepo.conversation(peer)
    var text by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<Msg?>(null) }
    val listState = rememberLazyListState()
    val title = ChatRepo.peerName(peer).ifBlank { peer }

    val pickImg = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) ChatRepo.sendUri(peer, u, "image")
    }
    val pickVid = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) ChatRepo.sendUri(peer, u, "video")
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) ChatRepo.sendUri(peer, u, "file")
    }

    LaunchedEffect(list.size) {
        if (list.isNotEmpty()) listState.animateScrollToItem(list.lastIndex)
        ChatRepo.markRead(peer)
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
                .imePadding()
        ) {
            // ---- top bar ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "back")
                }
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Accent, Violet))),
                    contentAlignment = Alignment.Center
                ) { Text(title.take(1), color = Bg, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("ID: $peer", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                }
                IconButton(onClick = {
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        Toast.makeText(ctx, "माइक की permission दीजिए (मुख्य स्क्रीन पर)", Toast.LENGTH_LONG).show()
                    } else {
                        val err = CallManager.startCall(ctx, peer)
                        if (err != null) Toast.makeText(ctx, err, Toast.LENGTH_LONG).show()
                        else ctx.startActivity(Intent(ctx, CallActivity::class.java))
                    }
                }) { Icon(Icons.Rounded.Call, contentDescription = "call", tint = Green) }
            }

            // ---- messages ----
            if (list.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "अभी कोई मैसेज नहीं।\nपहला मैसेज भेजिए 👋",
                        color = Color.White.copy(alpha = 0.45f), textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(list, key = { it.id }) { m ->
                        Bubble(m, mine = m.from == me) { msg ->
                            when (msg.type) {
                                "image", "video" -> viewer = msg
                                "file" -> openFile(ctx, msg)
                            }
                        }
                    }
                }
            }

            // ---- uploads ----
            ChatRepo.uploads.values.filter { it.peer == peer }.forEach { u ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text("⬆ ${u.name} • ${u.percent}%", fontSize = 12.sp, color = Accent)
                    LinearProgressIndicator(
                        progress = { u.percent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ---- input bar ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Rounded.AttachFile, contentDescription = "attach", tint = Accent)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("🖼  फ़ोटो") }, onClick = {
                            menu = false
                            pickImg.launch("image/*")
                        })
                        DropdownMenuItem(text = { Text("🎥  वीडियो (8 MB तक)") }, onClick = {
                            menu = false
                            pickVid.launch("video/*")
                        })
                        DropdownMenuItem(text = { Text("📎  फ़ाइल (8 MB तक)") }, onClick = {
                            menu = false
                            pickFile.launch("*/*")
                        })
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("मैसेज लिखिए") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    shape = RoundedCornerShape(26.dp),
                    colors = fieldColors()
                )
                IconButton(
                    onClick = {
                        ChatRepo.sendText(peer, text)
                        text = ""
                    },
                    enabled = text.isNotBlank()
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Send, contentDescription = "send",
                        tint = if (text.isNotBlank()) Accent else Color.White.copy(alpha = 0.3f)
                    )
                }
            }
        }

        viewer?.let { MediaViewer(it) { viewer = null } }
    }
}

@Composable
fun Bubble(m: Msg, mine: Boolean, onOpen: (Msg) -> Unit) {
    val shape = RoundedCornerShape(
        topStart = 18.dp, topEnd = 18.dp,
        bottomStart = if (mine) 18.dp else 4.dp, bottomEnd = if (mine) 4.dp else 18.dp
    )
    val fg = if (mine) Bg else Color.White
    val time = remember(m.ts) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(m.ts)) }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            Modifier
                .widthIn(max = 290.dp)
                .clip(shape)
                .background(if (mine) Accent else Color.White.copy(alpha = 0.09f))
                .padding(8.dp)
        ) {
            when (m.type) {
                "image", "video" -> {
                    val bmp = remember(m.id) { decodeThumb(m.thumb) }
                    Box(
                        Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.25f))
                            .clickable { onOpen(m) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (bmp != null) {
                            Image(bmp, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize())
                        } else {
                            Text(if (m.type == "image") "🖼 फ़ोटो" else "🎥 वीडियो", color = Color.White)
                        }
                        if (m.type == "video") {
                            Box(
                                Modifier.size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
                        }
                    }
                    Text(
                        "${m.name} • ${fmtSize(m.size)}", color = fg.copy(alpha = 0.7f),
                        fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)
                    )
                }
                "file" -> Row(
                    Modifier.clickable { onOpen(m) }.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Description, null, tint = fg, modifier = Modifier.size(34.dp))
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(m.name, color = fg, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 2)
                        Text(fmtSize(m.size) + " • खोलने के लिए टैप करें", color = fg.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                }
                else -> Text(m.text, color = fg, fontSize = 15.sp)
            }
            Text(
                time + if (mine) (if (m.pending) "  🕓" else "  ✓") else "",
                color = fg.copy(alpha = 0.6f), fontSize = 10.sp,
                modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun MediaViewer(m: Msg, onClose: () -> Unit) {
    var file by remember { mutableStateOf<File?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(m.id) {
        ChatRepo.fetchFile(m) { f -> if (f == null) failed = true else file = f }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f)),
            contentAlignment = Alignment.Center
        ) {
            val f = file
            when {
                failed -> Text("डाउनलोड नहीं हो पाया", color = Color.White)
                f == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Accent)
                    Spacer(Modifier.height(12.dp))
                    Text("डाउनलोड हो रहा है…", color = Color.White)
                }
                m.type == "image" -> {
                    val bmp = remember(f) { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }
                    if (bmp != null) {
                        Image(bmp, contentDescription = null, contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize())
                    } else Text("फ़ोटो खुली नहीं", color = Color.White)
                }
                else -> AndroidView(
                    factory = { c ->
                        VideoView(c).apply {
                            setVideoPath(f.path)
                            val mc = MediaController(c)
                            mc.setAnchorView(this)
                            setMediaController(mc)
                            setOnPreparedListener { start() }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(320.dp)
                )
            }
            Text(
                "✕", color = Color.White, fontSize = 26.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .clickable { onClose() }
            )
        }
    }
}
