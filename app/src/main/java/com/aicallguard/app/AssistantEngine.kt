package com.aicallguard.app

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telecom.TelecomManager
import java.util.Locale

object AssistantEngine {

    private const val MAX_ROUNDS = 15   // itni koshish ke baad AI call kaat dega

    private val main = Handler(Looper.getMainLooper())
    private var ctx: Context? = null
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var unlocked = false
    private var rounds = 0
    private var errStreak = 0
    private var warnedMic = false
    private var afterSpeech: (() -> Unit)? = null
    private var ringtone: Ringtone? = null

    private val replies = listOf(
        "ठीक है, मैंने आपकी बात नोट कर ली है। सीधे बात करने के लिए कृपया सीक्रेट कोड बोलिए।",
        "मैं समझ गया। लेकिन सीधे बात तभी होगी जब आप सही कोड बोलेंगे।",
        "क्षमा कीजिए, कोड सही नहीं है। कृपया कोड साफ़ आवाज़ में दोबारा बोलिए।",
        "मैं AI असिस्टेंट हूँ और बिना कोड के कॉल आगे नहीं भेज सकता। कृपया कोड बोलिए।"
    )

    fun isRunning() = running

    fun start(context: Context) {
        if (running) return
        ctx = context.applicationContext
        running = true; unlocked = false; rounds = 0; errStreak = 0; warnedMic = false
        CallLog.add("📞 Incoming call — AI ने उठा लिया")

        tts = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS && running) {
                tts?.language = Locale("hi", "IN")
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) = fire()
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = fire()
                })
                main.postDelayed({
                    speaker(true)
                    val name = Prefs.owner(ctx!!)
                    say("नमस्ते! मैं $name का AI असिस्टेंट बोल रहा हूँ। वे अभी सीधे बात नहीं कर सकते। " +
                        "कृपया अपना नाम और काम बताइए, या अगर आपके पास सीक्रेट कोड है तो वह बोलिए।") { listen() }
                }, 1000)
            }
        }
    }

    private fun fire() {
        main.post {
            val a = afterSpeech
            afterSpeech = null
            a?.invoke()
        }
    }

    private fun say(text: String, done: (() -> Unit)? = null) {
        if (!running) return
        CallLog.add("🤖 $text")
        afterSpeech = done
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "u${System.nanoTime()}")
    }

    private fun speaker(on: Boolean) {
        try {
            @Suppress("DEPRECATION")
            ctx?.getSystemService(AudioManager::class.java)?.isSpeakerphoneOn = on
        } catch (_: Exception) {}
    }

    private fun listen() {
        val c = ctx ?: return
        if (!running || unlocked) return
        if (!SpeechRecognizer.isRecognitionAvailable(c)) {
            CallLog.add("⚠️ इस फ़ोन पर speech recognition उपलब्ध नहीं है")
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(c).apply {
            setRecognitionListener(listener)
        }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        recognizer?.startListening(i)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onError(error: Int) {
            if (!running || unlocked) return
            errStreak++
            // Mic call ke dauran band ho to bar-bar AUDIO / INSUFFICIENT_PERMISSIONS jaisi errors aati hain
            if (!warnedMic && errStreak >= 8) {
                warnedMic = true
                CallLog.add("⚠️ लगता है इस फ़ोन में कॉल के दौरान mic बंद हो जाता है। यह फ़ीचर इस फ़ोन पर शायद काम न करे।")
            }
            if (errStreak % 6 == 0) {
                say("आवाज़ साफ़ नहीं आ रही। कृपया कोड बोलिए।") { listen() }
            } else {
                main.postDelayed({ listen() }, 700)
            }
        }

        override fun onResults(results: Bundle?) {
            val heard = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            if (heard.isBlank()) { onError(SpeechRecognizer.ERROR_NO_MATCH); return }
            errStreak = 0
            handleHeard(heard)
        }
    }

    private fun handleHeard(text: String) {
        CallLog.add("👤 $text")
        rounds++
        when {
            codeMatches(text) -> unlock()
            rounds >= MAX_ROUNDS -> say("आपका कोड नहीं मिला। धन्यवाद, अब कॉल समाप्त की जा रही है।") { hangup() }
            else -> say(replies[(rounds - 1) % replies.size]) { listen() }
        }
    }

    private val wordDigits = mapOf(
        "zero" to "0", "one" to "1", "two" to "2", "three" to "3", "four" to "4",
        "five" to "5", "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9",
        "thirty" to "30", "fifty" to "50",
        "शून्य" to "0", "एक" to "1", "दो" to "2", "तीन" to "3", "चार" to "4",
        "पांच" to "5", "पाँच" to "5", "छह" to "6", "सात" to "7", "आठ" to "8", "नौ" to "9",
        "तीस" to "30", "पचास" to "50", "थर्टी" to "30", "फिफ्टी" to "50"
    )

    private fun codeMatches(text: String): Boolean {
        val code = Prefs.code(ctx!!)
        val joined = text.lowercase().split(Regex("[\\s,.\\-]+"))
            .joinToString("") { wordDigits[it] ?: it }
        return joined.filter { it.isDigit() }.contains(code)
    }

    private fun unlock() {
        unlocked = true
        recognizer?.destroy(); recognizer = null
        CallLog.add("✅ सही कोड — कॉल आपको connect की जा रही है")
        say("कोड सही है। कृपया लाइन पर रहिए, मैं आपको कनेक्ट कर रहा हूँ।") {
            speaker(false)
            ringOwner()
        }
    }

    private fun ringOwner() {
        val c = ctx ?: return
        try {
            ringtone = RingtoneManager.getRingtone(c, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            ringtone?.play()
            c.getSystemService(VibratorManager::class.java).defaultVibrator
                .vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400, 600, 400, 600), -1))
            main.postDelayed({ ringtone?.stop() }, 10000)
        } catch (_: Exception) {}
    }

    private fun hangup() {
        try {
            @Suppress("DEPRECATION")
            ctx?.getSystemService(TelecomManager::class.java)?.endCall()
        } catch (_: SecurityException) {}
    }

    fun stop() {
        if (!running) return
        running = false
        main.removeCallbacksAndMessages(null)
        recognizer?.destroy(); recognizer = null
        tts?.stop(); tts?.shutdown(); tts = null
        ringtone?.stop(); ringtone = null
        speaker(false)
        CallLog.add("📴 Call खत्म")
    }
}
