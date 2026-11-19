package com.aicallguard.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/** Saari awaazein code se banti hain (kisi sound file ki zaroorat nahi). */
object Sounds {
    enum class S {
        SEND, RECEIVE, ATTACH_SENT, ATTACH_RECEIVED, DOWNLOAD_DONE,
        ERROR, CALL_CONNECT, CALL_END, RINGBACK, TAP
    }

    private const val RATE = 22050
    private var app: Context? = null
    private val cache = HashMap<S, ShortArray>()
    private val dtmfCache = HashMap<Char, ShortArray>()
    @Volatile var enabled = true

    fun init(ctx: Context) {
        app = ctx.applicationContext
        enabled = Prefs.soundsOn(ctx)
    }

    private fun tone(freqs: List<Double>, ms: Int, vol: Double = 0.4, sweepTo: Double? = null): ShortArray {
        val n = RATE * ms / 1000
        val out = ShortArray(n)
        val phases = DoubleArray(freqs.size)
        val attack = (RATE * 0.004).toInt().coerceAtLeast(1)
        val fade = (RATE * 0.010).coerceAtLeast(1.0)
        for (i in 0 until n) {
            var s = 0.0
            for (k in freqs.indices) {
                val f = if (sweepTo != null) freqs[k] + (sweepTo - freqs[k]) * i / n else freqs[k]
                phases[k] += 2 * PI * f / RATE
                s += sin(phases[k])
            }
            s /= freqs.size
            val a = if (i < attack) i.toDouble() / attack else 1.0
            val decay = 0.35 + 0.65 * (1.0 - i.toDouble() / n)
            val end = minOf(1.0, (n - i).toDouble() / fade)
            out[i] = (s * vol * a * decay * end * 32767).toInt().toShort()
        }
        return out
    }

    private fun seq(vararg p: ShortArray): ShortArray {
        val out = ShortArray(p.sumOf { it.size })
        var o = 0
        for (a in p) {
            System.arraycopy(a, 0, out, o, a.size)
            o += a.size
        }
        return out
    }

    private fun gap(ms: Int) = ShortArray(RATE * ms / 1000)

    private fun build(s: S): ShortArray = when (s) {
        S.SEND -> tone(listOf(600.0), 120, 0.35, 1100.0)
        S.RECEIVE -> seq(tone(listOf(988.0), 80), tone(listOf(1319.0), 160))
        S.ATTACH_SENT -> seq(tone(listOf(784.0), 70), tone(listOf(988.0), 70), tone(listOf(1319.0), 130))
        S.ATTACH_RECEIVED -> seq(tone(listOf(1319.0), 70), tone(listOf(988.0), 70), tone(listOf(784.0), 130))
        S.DOWNLOAD_DONE -> seq(tone(listOf(1047.0), 90), tone(listOf(1568.0), 140))
        S.ERROR -> seq(tone(listOf(330.0), 130), gap(30), tone(listOf(247.0), 220))
        S.CALL_CONNECT -> seq(tone(listOf(659.0), 90), tone(listOf(880.0), 90), tone(listOf(1175.0), 170))
        S.CALL_END -> seq(tone(listOf(880.0), 90), tone(listOf(659.0), 90), tone(listOf(494.0), 220))
        S.RINGBACK -> tone(listOf(425.0), 1000, 0.25)
        S.TAP -> tone(listOf(1000.0), 20, 0.25)
    }

    private val rows = doubleArrayOf(697.0, 770.0, 852.0, 941.0)
    private val cols = doubleArrayOf(1209.0, 1336.0, 1477.0)
    private const val KEYS = "123456789*0#"

    /** Dialpad ke har ank ki asli phone jaisi (DTMF) awaaz. */
    fun dtmf(ch: Char) {
        val i = KEYS.indexOf(ch)
        if (i < 0) return
        val pcm = synchronized(dtmfCache) {
            dtmfCache.getOrPut(ch) { tone(listOf(rows[i / 3], cols[i % 3]), 140, 0.35) }
        }
        playPcm(pcm)
    }

    fun play(s: S) {
        val pcm = synchronized(cache) { cache.getOrPut(s) { build(s) } }
        playPcm(pcm)
    }

    private fun allowed(): Boolean {
        if (!enabled) return false
        val c = app ?: return false
        return try {
            c.getSystemService(AudioManager::class.java).ringerMode == AudioManager.RINGER_MODE_NORMAL
        } catch (e: Exception) {
            true
        }
    }

    private fun playPcm(pcm: ShortArray) {
        if (!allowed()) return
        Thread {
            try {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(pcm.size * 1000L / RATE + 150)
                track.release()
            } catch (_: Exception) {
            }
        }.start()
    }
}
