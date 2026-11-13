package com.aicallguard.app

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class CallItem(
    val id: String,
    val from: String,
    val startedAt: String,
    val lines: List<String>
)

object ApiClient {
    fun fetchCalls(baseUrl: String, token: String, onDone: (Result<List<CallItem>>) -> Unit) {
        Thread {
            val result = runCatching {
                val conn = URL("${baseUrl.trimEnd('/')}/calls").openConnection() as HttpURLConnection
                conn.setRequestProperty("x-api-token", token)
                conn.connectTimeout = 20000
                conn.readTimeout = 30000
                val body = conn.inputStream.bufferedReader().readText()
                val arr = JSONObject(body).getJSONArray("calls")
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    val l = o.getJSONArray("log")
                    CallItem(
                        id = o.optString("id"),
                        from = o.optString("from"),
                        startedAt = o.optString("startedAt"),
                        lines = List(l.length()) { j -> l.getString(j) }
                    )
                }
            }
            Handler(Looper.getMainLooper()).post { onDone(result) }
        }.start()
    }
}
