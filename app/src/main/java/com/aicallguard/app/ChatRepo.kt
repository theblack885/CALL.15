package com.aicallguard.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

data class Msg(
    val id: String,
    val from: String,
    val to: String,
    val type: String,       // text, image, video, file
    val text: String,
    val name: String,
    val mime: String,
    val size: Long,
    val chunks: Int,
    val blobId: String,
    val thumb: String,
    val fromName: String,
    val ts: Long,
    val pending: Boolean
)

object ChatRepo {
    const val MAX_BYTES = 8 * 1024 * 1024
    private const val CHUNK = 700_000

    class Upload(val peer: String, val name: String, val percent: Int)

    private val main = Handler(Looper.getMainLooper())
    private lateinit var app: Context
    private var started = false

    val all = mutableStateMapOf<String, Msg>()
    val uploads = mutableStateMapOf<String, Upload>()
    val readTick = mutableIntStateOf(0)
    @Volatile var activePeer = ""

    private fun db() = FirebaseFirestore.getInstance()
    private fun me() = Prefs.myId(app)

    private fun toast(s: String) {
        main.post { Toast.makeText(app, s, Toast.LENGTH_LONG).show() }
    }

    // ---------------- listen ----------------
    fun start(ctx: Context) {
        app = ctx.applicationContext
        if (started) return
        started = true
        if (Prefs.lastNotified(app) == 0L) Prefs.setLastNotified(app, System.currentTimeMillis())
        val col = db().collection("messages")
        listen(col.whereEqualTo("to", me()), true)
        listen(col.whereEqualTo("from", me()), false)
    }

    private fun listen(q: Query, incoming: Boolean) {
        q.addSnapshotListener { snap, e ->
            if (e != null || snap == null) return@addSnapshotListener
            for (ch in snap.documentChanges) {
                val d = ch.document
                if (ch.type == DocumentChange.Type.REMOVED) {
                    all.remove(d.id)
                } else {
                    val m = parse(d)
                    all[d.id] = m
                    if (incoming && ch.type == DocumentChange.Type.ADDED) onIncoming(m)
                }
            }
        }
    }

    private fun parse(d: DocumentSnapshot): Msg {
        val ts = d.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?.toDate()?.time ?: System.currentTimeMillis()
        return Msg(
            id = d.id,
            from = d.getString("from") ?: "",
            to = d.getString("to") ?: "",
            type = d.getString("type") ?: "text",
            text = d.getString("text") ?: "",
            name = d.getString("name") ?: "",
            mime = d.getString("mime") ?: "",
            size = d.getLong("size") ?: 0L,
            chunks = (d.getLong("chunks") ?: 0L).toInt(),
            blobId = d.getString("blobId") ?: "",
            thumb = d.getString("thumb") ?: "",
            fromName = d.getString("fromName") ?: "",
            ts = ts,
            pending = d.metadata.hasPendingWrites()
        )
    }

    fun preview(m: Msg): String = when (m.type) {
        "image" -> "📷 फ़ोटो"
        "video" -> "🎥 वीडियो"
        "file" -> "📎 ${m.name}"
        else -> m.text
    }

    private fun onIncoming(m: Msg) {
        val last = Prefs.lastNotified(app)
        if (m.ts <= last) return
        Prefs.setLastNotified(app, m.ts)
        Sounds.play(if (m.type == "text") Sounds.S.RECEIVE else Sounds.S.ATTACH_RECEIVED)
        if (activePeer == m.from) {
            markRead(m.from)
        } else {
            val title = m.fromName.ifBlank { m.from }
            Notifier.message(app, m.from, title, preview(m))
        }
    }

    // ---------------- queries (UI) ----------------
    fun conversation(peer: String): List<Msg> {
        val m = me()
        return all.values
            .filter { (it.from == peer && it.to == m) || (it.from == m && it.to == peer) }
            .sortedBy { it.ts }
    }

    fun peers(): List<String> {
        val m = me()
        val last = HashMap<String, Long>()
        for (x in all.values) {
            val p = if (x.from == m) x.to else x.from
            if (p.isBlank()) continue
            last[p] = maxOf(last[p] ?: 0L, x.ts)
        }
        for (p in Prefs.extraPeers(app)) if (!last.containsKey(p)) last[p] = 0L
        return last.entries.sortedByDescending { it.value }.map { it.key }
    }

    fun peerName(peer: String): String =
        all.values.filter { it.from == peer && it.fromName.isNotBlank() }
            .maxByOrNull { it.ts }?.fromName ?: ""

    fun unread(peer: String): Int {
        @Suppress("UNUSED_VARIABLE") val tick = readTick.intValue
        val r = Prefs.lastRead(app, peer)
        val m = me()
        return all.values.count { it.from == peer && it.to == m && it.ts > r }
    }

    fun markRead(peer: String) {
        val m = me()
        val t = all.values.filter { it.from == peer && it.to == m }.maxOfOrNull { it.ts } ?: return
        if (t > Prefs.lastRead(app, peer)) {
            Prefs.setLastRead(app, peer, t)
            main.post { readTick.intValue = readTick.intValue + 1 }
        }
    }

    // ---------------- send ----------------
    private fun base(peer: String, type: String): Map<String, Any> = mapOf(
        "from" to me(),
        "to" to peer,
        "type" to type,
        "fromName" to Prefs.name(app),
        "createdAt" to FieldValue.serverTimestamp()
    )

    fun sendText(peer: String, text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        Sounds.play(Sounds.S.SEND)
        db().collection("messages").add(base(peer, "text") + mapOf("text" to t))
            .addOnFailureListener {
                Sounds.play(Sounds.S.ERROR)
                toast("मैसेज नहीं गया: ${it.message}")
            }
    }

    /** kind: "image", "video" ya "file" */
    fun sendUri(peer: String, uri: Uri, kind: String) {
        Thread {
            try {
                var name = queryName(uri) ?: "file"
                val mime = app.contentResolver.getType(uri) ?: "application/octet-stream"
                val bytes: ByteArray
                var thumb = ""
                when (kind) {
                    "image" -> {
                        val bmp = decodeScaled(uri, 1280)
                        bytes = jpeg(bmp, 75)
                        thumb = thumbB64(bmp)
                        name = name.substringBeforeLast('.') + ".jpg"
                    }
                    "video" -> {
                        bytes = readBytes(uri)
                        thumb = videoThumb(uri)
                    }
                    else -> bytes = readBytes(uri)
                }
                upload(peer, kind, name, if (kind == "image") "image/jpeg" else mime, bytes, thumb)
            } catch (e: Exception) {
                Sounds.play(Sounds.S.ERROR)
                if (e.message == "TOO_BIG") toast("फ़ाइल 8 MB से बड़ी है, नहीं भेज सकते")
                else toast("भेजा नहीं जा सका: ${e.message}")
            }
        }.start()
    }

    private fun setUpload(id: String, u: Upload?) {
        main.post { if (u == null) uploads.remove(id) else uploads[id] = u }
    }

    private fun upload(peer: String, type: String, name: String, mime: String, bytes: ByteArray, thumb: String) {
        val blobId = UUID.randomUUID().toString().replace("-", "").take(20)
        val parts = Base64.encodeToString(bytes, Base64.NO_WRAP).chunked(CHUNK)
        setUpload(blobId, Upload(peer, name, 0))
        try {
            for ((i, p) in parts.withIndex()) {
                Tasks.await(
                    db().collection("blobs").document("${blobId}_$i").set(mapOf("d" to p)),
                    120, TimeUnit.SECONDS
                )
                setUpload(blobId, Upload(peer, name, (i + 1) * 100 / parts.size))
            }
            val fields = base(peer, type) + mapOf(
                "name" to name, "mime" to mime, "size" to bytes.size.toLong(),
                "chunks" to parts.size, "blobId" to blobId, "thumb" to thumb
            )
            Tasks.await(db().collection("messages").add(fields), 60, TimeUnit.SECONDS)
            Sounds.play(Sounds.S.ATTACH_SENT)
        } catch (e: Exception) {
            Sounds.play(Sounds.S.ERROR)
            toast("भेजा नहीं जा सका: ${e.message}")
        } finally {
            setUpload(blobId, null)
        }
    }

    // ---------------- download ----------------
    fun fetchFile(m: Msg, onDone: (File?) -> Unit) {
        val safe = m.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val f = File(app.cacheDir, "chat_${m.blobId}_$safe")
        if (f.exists() && f.length() > 0) {
            onDone(f)
            return
        }
        Thread {
            var ok = false
            try {
                f.outputStream().use { out ->
                    for (i in 0 until m.chunks) {
                        val s = Tasks.await(
                            db().document("blobs/${m.blobId}_$i").get(), 120, TimeUnit.SECONDS
                        ).getString("d") ?: throw IllegalStateException("हिस्सा $i नहीं मिला")
                        out.write(Base64.decode(s, Base64.NO_WRAP))
                    }
                }
                ok = true
            } catch (e: Exception) {
                toast("डाउनलोड नहीं हुआ: ${e.message}")
            }
            if (ok) Sounds.play(Sounds.S.DOWNLOAD_DONE) else {
                f.delete()
                Sounds.play(Sounds.S.ERROR)
            }
            main.post { onDone(if (ok) f else null) }
        }.start()
    }

    // ---------------- helpers ----------------
    private fun queryName(uri: Uri): String? =
        app.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        }

    private fun readBytes(uri: Uri): ByteArray {
        val out = ByteArrayOutputStream()
        app.contentResolver.openInputStream(uri)!!.use { inp ->
            val buf = ByteArray(65536)
            var total = 0L
            while (true) {
                val n = inp.read(buf)
                if (n <= 0) break
                total += n
                if (total > MAX_BYTES) throw IllegalStateException("TOO_BIG")
                out.write(buf, 0, n)
            }
        }
        return out.toByteArray()
    }

    private fun decodeScaled(uri: Uri, maxSide: Int): Bitmap {
        val cr = app.contentResolver
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / sample > maxSide * 2) sample *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = cr.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, o2) }
            ?: throw IllegalStateException("फ़ोटो खुली नहीं")
        val rot = try {
            cr.openInputStream(uri)!!.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
            }
        } catch (e: Exception) {
            1
        }
        val deg = when (rot) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (deg != 0f) {
            bmp = Bitmap.createBitmap(
                bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg) }, true
            )
        }
        val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
        if (scale < 1f) {
            bmp = Bitmap.createScaledBitmap(
                bmp, (bmp.width * scale).toInt().coerceAtLeast(1),
                (bmp.height * scale).toInt().coerceAtLeast(1), true
            )
        }
        return bmp
    }

    private fun jpeg(bmp: Bitmap, q: Int): ByteArray {
        val o = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, q, o)
        return o.toByteArray()
    }

    private fun thumbB64(bmp: Bitmap): String {
        val s = 220f / maxOf(bmp.width, bmp.height)
        val t = if (s < 1f) Bitmap.createScaledBitmap(
            bmp, (bmp.width * s).toInt().coerceAtLeast(1), (bmp.height * s).toInt().coerceAtLeast(1), true
        ) else bmp
        return Base64.encodeToString(jpeg(t, 60), Base64.NO_WRAP)
    }

    private fun videoThumb(uri: Uri): String {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(app, uri)
            val f = r.getFrameAtTime(0) ?: return ""
            thumbB64(f)
        } catch (e: Exception) {
            ""
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
    }
}
