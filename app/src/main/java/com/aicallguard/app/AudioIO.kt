package com.aicallguard.app

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Mic se 16 kHz mono, 20 ms (320 samples) ke frames deta hai. */
class MicCapture(private val onFrame: (ShortArray) -> Unit) {
    @Volatile private var running = false
    private var rec: AudioRecord? = null
    private var th: Thread? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        return try {
            val min = AudioRecord.getMinBufferSize(
                16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val r = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 6400)
            )
            if (r.state != AudioRecord.STATE_INITIALIZED) {
                r.release()
                return false
            }
            rec = r
            try {
                if (AcousticEchoCanceler.isAvailable()) {
                    aec = AcousticEchoCanceler.create(r.audioSessionId)
                    aec?.enabled = true
                }
                if (NoiseSuppressor.isAvailable()) {
                    ns = NoiseSuppressor.create(r.audioSessionId)
                    ns?.enabled = true
                }
            } catch (_: Exception) {
            }
            r.startRecording()
            running = true
            th = Thread {
                val buf = ShortArray(320)
                while (running) {
                    var off = 0
                    while (running && off < 320) {
                        val n = r.read(buf, off, 320 - off)
                        if (n < 0) {
                            running = false
                            break
                        }
                        off += n
                    }
                    if (running && off == 320) onFrame(buf.copyOf())
                }
            }.also { it.start() }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun stop() {
        running = false
        try { th?.join(300) } catch (_: Exception) {}
        try { rec?.stop() } catch (_: Exception) {}
        try { aec?.release() } catch (_: Exception) {}
        try { ns?.release() } catch (_: Exception) {}
        try { rec?.release() } catch (_: Exception) {}
        rec = null
    }
}

/** 16 kHz mono awaaz bajata hai. */
class Playback {
    private val queue = LinkedBlockingQueue<ShortArray>(60)
    @Volatile private var running = false
    private var track: AudioTrack? = null
    private var th: Thread? = null

    fun start() {
        val min = AudioTrack.getMinBufferSize(
            16000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(16000)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(min, 6400))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        t.play()
        running = true
        th = Thread {
            while (running) {
                val f = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                try { t.write(f, 0, f.size) } catch (_: Exception) {}
            }
        }.also { it.start() }
    }

    fun push(f: ShortArray) {
        if (!queue.offer(f)) {
            queue.poll()
            queue.offer(f)
        }
    }

    fun stop() {
        running = false
        try { th?.join(300) } catch (_: Exception) {}
        try { track?.stop() } catch (_: Exception) {}
        try { track?.release() } catch (_: Exception) {}
        track = null
        queue.clear()
    }
}
