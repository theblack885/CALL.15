package com.aicallguard.app

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Call utha kar caller se baat karne wala AI.
 * Caller ki awaaz -> Vosk (offline) -> text. Jawab -> Android TTS -> awaaz.
 */
class AiAgent(
    private val ctx: Context,
    private val ownerName: String,
    private val code: String,
    private val sendFrame: (ShortArray) -> Unit,
    private val onUnlock: () -> Unit,
    private val onGiveUp: () -> Unit
) {
    private val exec = Executors.newSingleThreadExecutor()
    private val speakExec = Executors.newSingleThreadExecutor()
    private val timer = Executors.newSingleThreadScheduledExecutor()

    @Volatile private var tts: TextToSpeech? = null
    private var model: Model? = null
    private var rec: Recognizer? = null

    @Volatile private var active = false
    @Volatile private var listening = false
    @Volatile private var speaking = true
    @Volatile private var unlockedFlag = false
    @Volatile private var ignoreUntil = 0L

    private var rounds = 0
    private var silent = 0
    private val digits = StringBuilder()
    private var silenceTask: ScheduledFuture<*>? = null
    private val pending = HashMap<String, Pair<File, () -> Unit>>()

    private val replies = listOf(
        "ठीक है, मैंने आपकी बात नोट कर ली है। सीधे बात करने के लिए कृपया सीक्रेट कोड बोलिए या कीपैड पर डालिए।",
        "मैं समझ गया। लेकिन सीधे बात तभी होगी जब आप सही कोड बताएँगे।",
        "क्षमा कीजिए, कोड सही नहीं है। कृपया कोड साफ़ आवाज़ में दोबारा बोलिए।",
        "मैं AI असिस्टेंट हूँ और बिना कोड के कॉल आगे नहीं भेज सकता। कृपया कोड बताइए।"
    )

    private val wordDigits = mapOf(
        "zero" to "0", "one" to "1", "two" to "2", "three" to "3", "four" to "4",
        "five" to "5", "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9",
        "thirty" to "30", "fifty" to "50",
        "शून्य" to "0", "जीरो" to "0", "ज़ीरो" to "0",
        "एक" to "1", "वन" to "1", "दो" to "2", "टू" to "2",
        "तीन" to "3", "थ्री" to "3", "चार" to "4", "फोर" to "4",
        "पांच" to "5", "पाँच" to "5", "फाइव" to "5",
        "छह" to "6", "छः" to "6", "सिक्स" to "6",
        "सात" to "7", "सेवन" to "7", "आठ" to "8", "एट" to "8",
        "नौ" to "9", "नाइन" to "9",
        "तीस" to "30", "पचास" to "50", "थर्टी" to "30", "फिफ्टी" to "50"
    )

    private fun runExec(b: () -> Unit) {
        try {
            exec.execute {
                try {
                    b()
                } catch (e: Throwable) {
                    CallState.addLog("⚠️ ${e.message}")
                }
            }
        } catch (_: RejectedExecutionException) {
        }
    }

    fun start() {
        active = true
        runExec {
            if (ModelStore.ready(ctx)) {
                try {
                    val m = Model(ModelStore.dir(ctx).absolutePath)
                    model = m
                    rec = Recognizer(m, 16000.0f)
                    CallState.addLog("🎙️ आवाज़ पहचान तैयार")
                } catch (e: Throwable) {
                    CallState.addLog("⚠️ voice model load नहीं हुआ: ${e.message}")
                }
            } else {
                CallState.addLog("ℹ️ Voice model डाउनलोड नहीं है — caller को कीपैड से कोड डालना होगा")
            }
            Handler(Looper.getMainLooper()).post { initTts() }
        }
    }

    private fun initTts() {
        if (!active) return
        tts = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS && active) {
                val lr = tts?.setLanguage(Locale("hi", "IN"))
                if (lr == TextToSpeech.LANG_MISSING_DATA || lr == TextToSpeech.LANG_NOT_SUPPORTED) {
                    CallState.addLog("⚠️ फ़ोन में हिंदी TTS आवाज़ नहीं मिली (Settings → Text-to-speech)")
                }
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId != null) onSynth(utteranceId)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId != null) onSynth(utteranceId)
                    }
                })
                runExec {
                    say(
                        "नमस्ते! मैं $ownerName का AI असिस्टेंट बोल रहा हूँ। वे अभी सीधे बात नहीं कर सकते। " +
                            "कृपया अपना नाम और काम बताइए, या अगर आपके पास सीक्रेट कोड है तो वह बोलिए।"
                    ) {}
                }
            } else {
                CallState.addLog("⚠️ TTS शुरू नहीं हुआ")
            }
        }
    }

    private fun say(text: String, after: () -> Unit) {
        if (!active) return
        speaking = true
        listening = false
        CallState.addLog("🤖 $text")
        val id = "u${System.nanoTime()}"
        val f = File(ctx.cacheDir, "tts_$id.wav")
        synchronized(pending) { pending[id] = f to after }
        val r = tts?.synthesizeToFile(text, Bundle(), f, id) ?: TextToSpeech.ERROR
        if (r != TextToSpeech.SUCCESS) {
            synchronized(pending) { pending.remove(id) }
            CallState.addLog("⚠️ आवाज़ बनाने में दिक्कत (TTS)")
            speaking = false
            after()
            if (!unlockedFlag) {
                listening = true
                scheduleSilence()
            }
        }
    }

    private fun onSynth(id: String) {
        try {
            speakExec.execute {
                val p = synchronized(pending) { pending.remove(id) } ?: return@execute
                val f = p.first
                try {
                    val pcm = readWav16k(f)
                    if (pcm != null) stream(pcm)
                } catch (_: Exception) {
                } finally {
                    f.delete()
                }
                ignoreUntil = System.currentTimeMillis() + 300
                speaking = false
                if (active) {
                    p.second.invoke()
                    if (!unlockedFlag) {
                        runExec {
                            try { rec?.reset() } catch (_: Throwable) {}
                            listening = true
                            scheduleSilence()
                        }
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
        }
    }

    private fun stream(pcm: ShortArray) {
        var i = 0
        var k = 0
        val t0 = System.nanoTime()
        try {
            while (i < pcm.size && active) {
                val frame = ShortArray(320)
                val end = minOf(i + 320, pcm.size)
                System.arraycopy(pcm, i, frame, 0, end - i)
                sendFrame(frame)
                k++
                val wait = (t0 + k * 20_000_000L - System.nanoTime()) / 1_000_000
                if (wait > 0) Thread.sleep(wait)
                i += 320
            }
        } catch (_: InterruptedException) {
        }
    }

    private fun readWav16k(f: File): ShortArray? {
        if (!f.exists()) return null
        val b = f.readBytes()
        if (b.size < 44) return null
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        val channels = bb.getShort(22).toInt().coerceAtLeast(1)
        val rate = bb.getInt(24)
        var p = 12
        var dataStart = -1
        var dataLen = 0
        while (p + 8 <= b.size) {
            val id = String(b, p, 4)
            val len = bb.getInt(p + 4)
            if (id == "data") {
                dataStart = p + 8
                dataLen = if (len <= 0 || dataStart + len > b.size) b.size - dataStart else len
                break
            }
            p += 8 + len
        }
        if (dataStart < 0) return null
        val frames = dataLen / 2 / channels
        val s = ShortArray(frames)
        for (i in 0 until frames) s[i] = bb.getShort(dataStart + i * 2 * channels)
        return G711.resample(s, rate, 16000)
    }

    /** Caller ki awaaz (16 kHz). */
    fun feed(pcm: ShortArray) {
        if (!listening || speaking || System.currentTimeMillis() < ignoreUntil) return
        runExec { process(pcm) }
    }

    private fun process(s: ShortArray) {
        val r = rec ?: return
        if (!listening) return
        val b = ByteArray(s.size * 2)
        ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(s)
        if (r.acceptWaveForm(b, b.size)) {
            val t = JSONObject(r.result).optString("text").trim()
            if (t.isNotEmpty()) handleHeard(t)
        }
    }

    /** Caller ne keypad se ankh dabaya. */
    fun onDigit(d: Char) {
        runExec {
            if (!active || unlockedFlag) return@runExec
            digits.append(d)
            if (digits.length > 16) digits.delete(0, digits.length - 16)
            CallState.addLog("⌨️ कीपैड: ${"*".repeat(digits.length)}")
            if (digits.toString().endsWith(code)) unlock()
        }
    }

    private fun handleHeard(text: String) {
        silenceTask?.cancel(false)
        silent = 0
        CallState.addLog("👤 $text")
        rounds++
        when {
            codeMatches(text) -> unlock()
            rounds >= Config.MAX_AI_ROUNDS ->
                say("आपका कोड नहीं मिला। कृपया बाद में दोबारा कॉल कीजिए। धन्यवाद।") { onGiveUp() }
            else -> say(replies[(rounds - 1) % replies.size]) {}
        }
    }

    private fun codeMatches(text: String): Boolean {
        val joined = text.lowercase()
            .split(Regex("[\\s,.\\-]+"))
            .joinToString("") { wordDigits[it] ?: it }
        return joined.filter { it.isDigit() }.contains(code)
    }

    private fun unlock() {
        if (unlockedFlag) return
        unlockedFlag = true
        listening = false
        silenceTask?.cancel(false)
        CallState.addLog("✅ सही कोड मिला")
        say("कोड सही है। कृपया लाइन पर रहिए, मैं आपको कनेक्ट कर रहा हूँ।") { onUnlock() }
    }

    private fun scheduleSilence() {
        silenceTask?.cancel(false)
        try {
            silenceTask = timer.schedule({ runExec { onSilence() } }, 20, TimeUnit.SECONDS)
        } catch (_: RejectedExecutionException) {
        }
    }

    private fun onSilence() {
        if (!active || unlockedFlag || speaking || !listening) return
        silent++
        if (silent >= 3) {
            say("कोई आवाज़ नहीं आई। कृपया बाद में कॉल कीजिए। धन्यवाद।") { onGiveUp() }
        } else {
            say("क्या आप मुझे सुन पा रहे हैं? अपना नाम और काम बताइए, या कोड बोलिए।") {}
        }
    }

    fun stop() {
        active = false
        listening = false
        try { silenceTask?.cancel(false) } catch (_: Exception) {}
        try { timer.shutdownNow() } catch (_: Exception) {}
        try {
            exec.execute {
                try { rec?.close() } catch (_: Throwable) {}
                try { model?.close() } catch (_: Throwable) {}
                rec = null
                model = null
            }
            exec.shutdown()
        } catch (_: Exception) {
        }
        try { speakExec.shutdownNow() } catch (_: Exception) {}
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
    }
}
