package com.aicallguard.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/** Hindi awaaz pehchan ka model (~42 MB) download karke phone mein rakhta hai. */
object ModelStore {
    fun dir(ctx: Context) = File(ctx.filesDir, Config.MODEL_DIR)

    fun ready(ctx: Context): Boolean =
        File(dir(ctx), "conf").isDirectory && File(dir(ctx), "am").isDirectory

    fun download(ctx: Context, onProgress: (Int) -> Unit, onDone: (String?) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        Thread {
            try {
                val zip = File(ctx.cacheDir, "model.zip")
                val conn = URL(Config.MODEL_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 20000
                conn.readTimeout = 30000
                val total = conn.contentLength.toLong()
                conn.inputStream.use { inp ->
                    zip.outputStream().use { out ->
                        val buf = ByteArray(32768)
                        var read = 0L
                        var n: Int
                        var last = -1
                        while (true) {
                            n = inp.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            read += n
                            if (total > 0) {
                                val p = (read * 90 / total).toInt()
                                if (p != last) {
                                    last = p
                                    main.post { onProgress(p) }
                                }
                            }
                        }
                    }
                }
                val root = ctx.filesDir.canonicalPath
                ZipInputStream(zip.inputStream().buffered()).use { zin ->
                    var e = zin.nextEntry
                    while (e != null) {
                        val out = File(ctx.filesDir, e.name)
                        if (!out.canonicalPath.startsWith(root)) throw SecurityException("bad zip")
                        if (e.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { zin.copyTo(it) }
                        }
                        e = zin.nextEntry
                    }
                }
                zip.delete()
                main.post {
                    onProgress(100)
                    onDone(null)
                }
            } catch (e: Exception) {
                main.post { onDone(e.message ?: "download fail") }
            }
        }.start()
    }
}
