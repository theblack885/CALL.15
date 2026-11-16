package com.aicallguard.app

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue

fun recordingsDir(ctx: Context): File =
    File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "recordings")

fun listRecordings(ctx: Context): List<File> =
    recordingsDir(ctx).listFiles()
        ?.filter { it.extension == "wav" }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()

/** Dono taraf ki awaaz (aapki + saamne wale ki) mix karke ek WAV file mein likhta hai. */
class CallRecorder(private val file: File) {
    private val local = ConcurrentLinkedQueue<ShortArray>()
    private val remote = ConcurrentLinkedQueue<ShortArray>()
    @Volatile private var running = false
    private var th: Thread? = null
    private var out: RandomAccessFile? = null
    private var dataBytes = 0L

    fun start(): Boolean {
        return try {
            file.parentFile?.mkdirs()
            val o = RandomAccessFile(file, "rw")
            o.setLength(0)
            o.write(ByteArray(44))
            out = o
            running = true
            th = Thread { loop() }.also { it.start() }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun addLocal(f: ShortArray) {
        if (!running) return
        local.add(f)
        while (local.size > 100) local.poll()
    }

    fun addRemote(f: ShortArray) {
        if (!running) return
        remote.add(f)
        while (remote.size > 100) remote.poll()
    }

    private fun loop() {
        val buf = ByteArray(640)
        var next = System.nanoTime()
        try {
            while (running) {
                val a = local.poll()
                val b = remote.poll()
                val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until 320) {
                    val x = (a?.getOrNull(i)?.toInt() ?: 0) + (b?.getOrNull(i)?.toInt() ?: 0)
                    bb.putShort(x.coerceIn(-32768, 32767).toShort())
                }
                out?.write(buf)
                dataBytes += 640
                next += 20_000_000L
                val wait = (next - System.nanoTime()) / 1_000_000
                if (wait > 0) Thread.sleep(wait) else next = System.nanoTime()
            }
        } catch (_: Exception) {
        }
    }

    fun stop() {
        running = false
        try { th?.join(500) } catch (_: Exception) {}
        try {
            val o = out ?: return
            val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray())
            h.putInt((36 + dataBytes).toInt())
            h.put("WAVE".toByteArray())
            h.put("fmt ".toByteArray())
            h.putInt(16)
            h.putShort(1)
            h.putShort(1)
            h.putInt(16000)
            h.putInt(32000)
            h.putShort(2)
            h.putShort(16)
            h.put("data".toByteArray())
            h.putInt(dataBytes.toInt())
            o.seek(0)
            o.write(h.array())
            o.close()
        } catch (_: Exception) {
        }
        out = null
    }
}
