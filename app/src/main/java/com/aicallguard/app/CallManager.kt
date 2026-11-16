package com.aicallguard.app

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin

/**
 * Poora calling system: Firebase se signaling, WebRTC se seedha (peer-to-peer) connection,
 * aur DataChannel par apni awaaz (mu-law) bhejna.
 */
object CallManager {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var app: Context

    private var pcf: PeerConnectionFactory? = null
    private var pc: PeerConnection? = null
    private var audioDc: DataChannel? = null
    private var ctlDc: DataChannel? = null
    private var callDoc: DocumentReference? = null
    private val regs = mutableListOf<ListenerRegistration>()

    private var isCaller = false
    private var wasAi = false
    private var recordedThisCall = false
    @Volatile private var remoteSet = false
    @Volatile private var answerApplied = false
    private val pendingCands = mutableListOf<IceCandidate>()
    private var pendingOffer: Map<*, *>? = null
    @Volatile private var connectedOnce = false

    private var capture: MicCapture? = null
    private var playback: Playback? = null
    private var agent: AiAgent? = null
    @Volatile private var recorder: CallRecorder? = null
    @Volatile private var ringbackOn = false
    private var timeoutRunnable: Runnable? = null

    private fun db() = FirebaseFirestore.getInstance()

    private fun factory(): PeerConnectionFactory {
        pcf?.let { return it }
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(app).createInitializationOptions()
        )
        val f = PeerConnectionFactory.builder().createPeerConnectionFactory()
        pcf = f
        return f
    }

    private fun peerLabel(): String =
        if (CallState.peerName.isNotBlank()) "${CallState.peerName} (${CallState.peerId})"
        else CallState.peerId

    private fun resetFlags() {
        remoteSet = false
        answerApplied = false
        pendingCands.clear()
        pendingOffer = null
        connectedOnce = false
        wasAi = false
        recordedThisCall = false
        CallState.muted = false
        CallState.speaker = false
        CallState.aiHandling = false
        CallState.ownerRinging = false
        CallState.startedAt = 0L
        CallState.info = ""
        CallState.onHold = false
        CallState.remoteHold = false
        CallState.recording = false
        CallState.remoteRecording = false
        CallState.note = ""
    }

    // ---------------- Outgoing ----------------

    /** Error ho to message lautata hai, warna null. */
    fun startCall(ctx: Context, toId: String): String? {
        app = ctx.applicationContext
        if (CallState.phase != Phase.IDLE) return "पहले से एक कॉल चल रही है"
        val me = Prefs.myId(app)
        if (toId == me) return "आप अपने आप को कॉल नहीं कर सकते"
        resetFlags()
        isCaller = true
        CallState.phase = Phase.OUTGOING
        CallState.peerId = toId
        CallState.peerName = ""

        val doc = db().collection("calls").document()
        callDoc = doc
        createPeer()
        val p = pc ?: return "कॉल शुरू नहीं हो पाई"

        val a = p.createDataChannel("audio", DataChannel.Init().apply {
            ordered = false
            maxRetransmits = 0
        })
        val c = p.createDataChannel("ctl", DataChannel.Init())
        audioDc = a
        ctlDc = c
        a.registerObserver(DcObs(a))
        c.registerObserver(DcObs(c))

        p.createOffer(sdpObs(onCreate = { sdp ->
            pc?.setLocalDescription(sdpObs(onSet = {
                doc.set(
                    mapOf(
                        "from" to me,
                        "fromName" to Prefs.name(app),
                        "to" to toId,
                        "status" to "ringing",
                        "offer" to mapOf("type" to "offer", "sdp" to sdp.description),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).addOnFailureListener { fail("Server से जुड़ नहीं पाया: ${it.message}") }
                main.post {
                    watchDoc(doc)
                    watchCandidates("calleeCandidates")
                    scheduleTimeout(Config.RING_TIMEOUT_MS, "कोई जवाब नहीं")
                }
            }), sdp)
        }), MediaConstraints())
        return null
    }

    private fun scheduleTimeout(ms: Long, msg: String) {
        timeoutRunnable?.let { main.removeCallbacks(it) }
        val r = Runnable {
            if (CallState.phase != Phase.IDLE && CallState.phase != Phase.ACTIVE) {
                hangup(msg)
            }
        }
        timeoutRunnable = r
        main.postDelayed(r, ms)
    }

    // ---------------- Incoming ----------------

    fun onIncoming(ctx: Context, callId: String, data: Map<String, Any>) {
        app = ctx.applicationContext
        val ts = (data["createdAt"] as? Timestamp)?.toDate()?.time
        if (ts != null && System.currentTimeMillis() - ts > 60_000) return

        val doc = db().collection("calls").document(callId)
        if (CallState.phase != Phase.IDLE) {
            doc.update("status", "busy")
            return
        }
        resetFlags()
        isCaller = false
        callDoc = doc
        CallState.peerId = (data["from"] as? String) ?: "?"
        CallState.peerName = (data["fromName"] as? String) ?: ""
        pendingOffer = data["offer"] as? Map<*, *>
        CallState.phase = Phase.INCOMING
        watchDoc(doc)
        scheduleTimeout(Config.RING_TIMEOUT_MS, "कॉल छूट गई")

        val trusted = Prefs.isTrusted(app, CallState.peerId)
        if (!trusted && Prefs.aiMode(app)) {
            acceptCall(true)
        } else {
            Ringer.start(app)
            Notifier.incoming(app, "Incoming call", peerLabel())
        }
    }

    /** asAi = true: AI utha kar baat karega. false: aap khud. */
    fun acceptCall(asAi: Boolean) {
        if (CallState.phase != Phase.INCOMING) return
        val sdp = pendingOffer?.get("sdp") as? String ?: return
        Ringer.stop()
        Notifier.cancelIncoming(app)
        CallState.phase = Phase.CONNECTING
        CallState.aiHandling = asAi
        wasAi = asAi
        scheduleTimeout(Config.CONNECT_TIMEOUT_MS, "कनेक्शन नहीं बन पाया")
        createPeer()
        watchCandidates("callerCandidates")
        setRemote(SessionDescription(SessionDescription.Type.OFFER, sdp)) {
            pc?.createAnswer(sdpObs(onCreate = { ans ->
                pc?.setLocalDescription(sdpObs(onSet = {
                    callDoc?.update(
                        mapOf(
                            "answer" to mapOf("type" to "answer", "sdp" to ans.description),
                            "status" to "active"
                        )
                    )
                }), ans)
            }), MediaConstraints())
        }
    }

    fun decline() {
        if (CallState.phase != Phase.INCOMING) return
        try { callDoc?.update("status", "declined") } catch (_: Exception) {}
        finishCall("")
    }

    // ---------------- Peer / signaling ----------------

    private fun createPeer() {
        val ice = mutableListOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
        if (Config.TURN_URL.isNotEmpty()) {
            ice.add(
                PeerConnection.IceServer.builder(Config.TURN_URL)
                    .setUsername(Config.TURN_USER)
                    .setPassword(Config.TURN_PASS)
                    .createIceServer()
            )
        }
        val cfg = PeerConnection.RTCConfiguration(ice)
        cfg.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        pc = factory().createPeerConnection(cfg, pcObserver)
    }

    private fun sdpObs(
        onCreate: (SessionDescription) -> Unit = {},
        onSet: () -> Unit = {}
    ) = object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) {
            onCreate(sdp)
        }

        override fun onSetSuccess() {
            onSet()
        }

        override fun onCreateFailure(e: String?) {
            fail("create: $e")
        }

        override fun onSetFailure(e: String?) {
            fail("set: $e")
        }
    }

    private fun setRemote(desc: SessionDescription, then: () -> Unit) {
        pc?.setRemoteDescription(sdpObs(onSet = {
            remoteSet = true
            main.post { flushCands() }
            then()
        }), desc)
    }

    private fun flushCands() {
        pendingCands.forEach { pc?.addIceCandidate(it) }
        pendingCands.clear()
    }

    private fun addRemoteCandidate(c: IceCandidate) {
        if (remoteSet) pc?.addIceCandidate(c) else pendingCands.add(c)
    }

    private fun watchDoc(doc: DocumentReference) {
        regs.add(doc.addSnapshotListener { snap, _ ->
            val d = snap?.data ?: return@addSnapshotListener
            if (isCaller && !answerApplied) {
                val ans = d["answer"] as? Map<*, *>
                val sdp = ans?.get("sdp") as? String
                if (sdp != null) {
                    answerApplied = true
                    setRemote(SessionDescription(SessionDescription.Type.ANSWER, sdp)) {}
                }
            }
            when (d["status"] as? String) {
                "ended" -> remoteEnded("कॉल खत्म हुई")
                "busy" -> remoteEnded("नंबर व्यस्त है")
                "declined" -> remoteEnded("कॉल अस्वीकार हुई")
            }
        })
    }

    private fun watchCandidates(col: String) {
        val reg = callDoc?.collection(col)?.addSnapshotListener { snap, _ ->
            snap?.documentChanges?.forEach { ch ->
                if (ch.type == DocumentChange.Type.ADDED) {
                    val d = ch.document
                    addRemoteCandidate(
                        IceCandidate(
                            d.getString("sdpMid") ?: "",
                            (d.getLong("sdpMLineIndex") ?: 0L).toInt(),
                            d.getString("candidate") ?: ""
                        )
                    )
                }
            }
        }
        if (reg != null) regs.add(reg)
    }

    private val pcObserver = object : PeerConnection.Observer {
        override fun onSignalingChange(s: PeerConnection.SignalingState?) {}

        override fun onIceConnectionChange(s: PeerConnection.IceConnectionState?) {
            if (s == PeerConnection.IceConnectionState.FAILED ||
                s == PeerConnection.IceConnectionState.CLOSED
            ) {
                main.post {
                    if (CallState.phase != Phase.IDLE) finishCall("कनेक्शन टूट गया")
                }
            }
        }

        override fun onIceConnectionReceivingChange(b: Boolean) {}
        override fun onIceGatheringChange(s: PeerConnection.IceGatheringState?) {}

        override fun onIceCandidate(c: IceCandidate?) {
            if (c == null) return
            val col = if (isCaller) "callerCandidates" else "calleeCandidates"
            callDoc?.collection(col)?.add(
                mapOf(
                    "sdpMid" to (c.sdpMid ?: ""),
                    "sdpMLineIndex" to c.sdpMLineIndex,
                    "candidate" to c.sdp
                )
            )
        }

        override fun onIceCandidatesRemoved(a: Array<out IceCandidate>?) {}
        override fun onAddStream(m: MediaStream?) {}
        override fun onRemoveStream(m: MediaStream?) {}

        override fun onDataChannel(dc: DataChannel?) {
            if (dc == null) return
            if (dc.label() == "audio") audioDc = dc else ctlDc = dc
            dc.registerObserver(DcObs(dc))
            checkOpen()
        }

        override fun onRenegotiationNeeded() {}
    }

    private class DcObs(val dc: DataChannel) : DataChannel.Observer {
        override fun onBufferedAmountChange(p0: Long) {}

        override fun onStateChange() {
            if (dc.state() == DataChannel.State.OPEN) checkOpen()
        }

        override fun onMessage(b: DataChannel.Buffer) {
            val bytes = ByteArray(b.data.remaining())
            b.data.get(bytes)
            if (dc.label() == "audio") onAudio(bytes) else onCtl(String(bytes))
        }
    }

    @Synchronized
    private fun checkOpen() {
        if (!connectedOnce &&
            audioDc?.state() == DataChannel.State.OPEN &&
            ctlDc?.state() == DataChannel.State.OPEN
        ) {
            connectedOnce = true
            main.post { onConnected() }
        }
    }

    private fun onConnected() {
        if (CallState.phase == Phase.IDLE) return
        timeoutRunnable?.let { main.removeCallbacks(it) }
        CallState.phase = Phase.ACTIVE
        CallState.startedAt = System.currentTimeMillis()
        CallState.info = ""
        if (!isCaller && CallState.aiHandling) startAgent() else startAudio()
    }

    // ---------------- Audio ----------------

    private fun onAudio(bytes: ByteArray) {
        val pcm = ShortArray(bytes.size) { G711.decode(bytes[it]) }
        val a = agent
        if (a != null && CallState.aiHandling) {
            a.feed(pcm)
            return
        }
        if (CallState.onHold) return
        recorder?.addRemote(pcm)
        playback?.push(pcm)
    }

    private fun sendFrame(frame: ShortArray) {
        val dc = audioDc ?: return
        if (dc.state() != DataChannel.State.OPEN) return
        if (dc.bufferedAmount() > 64_000) return
        val b = ByteArray(frame.size) { G711.encode(frame[it]) }
        dc.send(DataChannel.Buffer(ByteBuffer.wrap(b), true))
    }

    private fun sendCtl(s: String) {
        val dc = ctlDc ?: return
        if (dc.state() != DataChannel.State.OPEN) return
        dc.send(DataChannel.Buffer(ByteBuffer.wrap(s.toByteArray()), false))
    }

    fun sendDigit(d: Char) = sendCtl("D:$d")

    private fun onCtl(msg: String) {
        when {
            msg.startsWith("D:") && msg.length > 2 -> agent?.onDigit(msg[2])
            msg == "BYE" -> main.post { remoteEnded("कॉल खत्म हुई") }
            msg == "STATE:CONNECTING" -> main.post { CallState.info = "आपको कनेक्ट किया जा रहा है…" }
            msg == "STATE:HUMAN" -> main.post { CallState.info = "" }
            msg == "REC:1" -> main.post { CallState.remoteRecording = true }
            msg == "REC:0" -> main.post { CallState.remoteRecording = false }
            msg == "HOLD:1" -> main.post { CallState.remoteHold = true }
            msg == "HOLD:0" -> main.post { CallState.remoteHold = false }
        }
    }

    @Suppress("DEPRECATION")
    private fun startAudio() {
        val am = app.getSystemService(AudioManager::class.java)
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        am.isSpeakerphoneOn = CallState.speaker
        val pb = Playback()
        pb.start()
        playback = pb
        val cap = MicCapture { f ->
            if (!CallState.muted && !CallState.onHold) {
                sendFrame(f)
                recorder?.addLocal(f)
            }
        }
        if (!cap.start()) CallState.info = "⚠️ माइक शुरू नहीं हुआ (permission देखें)"
        capture = cap
    }

    private fun startAgent() {
        CallState.addLog("📞 ${peerLabel()} की कॉल — AI ने उठाई")
        val name = Prefs.name(app).ifBlank { "मालिक" }
        val a = AiAgent(
            app, name, Prefs.code(app),
            sendFrame = { f -> sendFrame(f) },
            onUnlock = { main.post { ownerRing() } },
            onGiveUp = { main.post { hangup("AI ने कॉल खत्म की") } }
        )
        agent = a
        a.start()
    }

    private fun ownerRing() {
        if (CallState.phase != Phase.ACTIVE) return
        CallState.ownerRinging = true
        sendCtl("STATE:CONNECTING")
        startRingback()
        Ringer.start(app)
        Notifier.incoming(app, "कोड सही — ${peerLabel()} लाइन पर है", "बात करने के लिए टैप करें")
        CallState.addLog("🔔 आपको ring किया जा रहा है")
    }

    /** Owner ne uthaya: AI ruk jata hai, aap baat karte ho. */
    fun takeOver() {
        if (!CallState.ownerRinging) return
        stopRingback()
        Ringer.stop()
        Notifier.cancelIncoming(app)
        agent?.stop()
        agent = null
        CallState.aiHandling = false
        CallState.ownerRinging = false
        startAudio()
        sendCtl("STATE:HUMAN")
        CallState.addLog("🗣️ आपने कॉल उठाई")
    }

    private fun startRingback() {
        if (ringbackOn) return
        ringbackOn = true
        Thread {
            var ph = 0.0
            val frame = ShortArray(320)
            try {
                while (ringbackOn) {
                    for (n in 0 until 50) {
                        if (!ringbackOn) break
                        for (i in 0 until 320) {
                            frame[i] = (sin(ph) * 5000).toInt().toShort()
                            ph += 2 * Math.PI * 425 / 16000
                        }
                        sendFrame(frame)
                        Thread.sleep(20)
                    }
                    var w = 0
                    while (ringbackOn && w < 150) {
                        Thread.sleep(20)
                        w++
                    }
                }
            } catch (_: InterruptedException) {
            }
        }.start()
    }

    private fun stopRingback() {
        ringbackOn = false
    }

    // ---------------- Controls ----------------

    fun toggleMute() {
        CallState.muted = !CallState.muted
    }

    @Suppress("DEPRECATION")
    fun toggleSpeaker() {
        CallState.speaker = !CallState.speaker
        try {
            app.getSystemService(AudioManager::class.java).isSpeakerphoneOn = CallState.speaker
        } catch (_: Exception) {
        }
    }

    /** Hold: aapki awaaz band, saamne wale ko "होल्ड" dikhta hai. */
    fun toggleHold() {
        if (CallState.phase != Phase.ACTIVE || CallState.aiHandling) return
        CallState.onHold = !CallState.onHold
        sendCtl(if (CallState.onHold) "HOLD:1" else "HOLD:0")
    }

    /** Recording: saamne wale ko bhi "रिकॉर्डिंग चालू" ka notice jaata hai. */
    fun toggleRecord() {
        if (CallState.phase != Phase.ACTIVE || CallState.aiHandling) return
        if (recorder == null) {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val f = File(recordingsDir(app), "call_${stamp}_${CallState.peerId}.wav")
            val r = CallRecorder(f)
            if (r.start()) {
                recorder = r
                recordedThisCall = true
                CallState.recording = true
                sendCtl("REC:1")
            } else {
                CallState.info = "⚠️ रिकॉर्डिंग शुरू नहीं हो पाई"
            }
        } else {
            stopRecording()
        }
    }

    private fun stopRecording() {
        val r = recorder ?: return
        recorder = null
        try { r.stop() } catch (_: Exception) {}
        CallState.recording = false
        sendCtl("REC:0")
    }

    fun hangup(reason: String = "") {
        if (CallState.phase == Phase.IDLE) return
        sendCtl("BYE")
        finishCall(reason)
    }

    private fun remoteEnded(msg: String) {
        if (CallState.phase == Phase.IDLE) return
        finishCall(msg)
    }

    private fun fail(msg: String) {
        main.post {
            if (CallState.phase != Phase.IDLE) finishCall("त्रुटि: $msg")
        }
    }

    private fun finishCall(msg: String) {
        if (CallState.phase == Phase.IDLE) return
        val dur = if (CallState.startedAt > 0)
            (System.currentTimeMillis() - CallState.startedAt) / 1000 else 0L
        val dir = if (isCaller) "↗" else "↙"
        val tag = if (!isCaller && wasAi) " • AI" else ""
        val rec = if (recordedThisCall) " • 🔴" else ""
        val time = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date())
        Prefs.addHistory(app, "$dir ${peerLabel()} • $time • ${dur}s$tag$rec")

        val note = CallState.note.trim()
        if (note.isNotEmpty()) Prefs.addNote(app, "${peerLabel()} • $time\n$note")
        CallState.note = ""

        try { callDoc?.update("status", "ended") } catch (_: Exception) {}
        cleanup()
        CallState.info = msg
        CallState.aiHandling = false
        CallState.ownerRinging = false
        CallState.onHold = false
        CallState.remoteHold = false
        CallState.remoteRecording = false
        CallState.startedAt = 0L
        CallState.phase = Phase.IDLE
    }

    @Suppress("DEPRECATION")
    private fun cleanup() {
        stopRecording()
        stopRingback()
        Ringer.stop()
        Notifier.cancelIncoming(app)
        timeoutRunnable?.let { main.removeCallbacks(it) }
        timeoutRunnable = null
        try { agent?.stop() } catch (_: Exception) {}
        agent = null
        try { capture?.stop() } catch (_: Exception) {}
        try { playback?.stop() } catch (_: Exception) {}
        capture = null
        playback = null
        regs.forEach { try { it.remove() } catch (_: Exception) {} }
        regs.clear()
        try { audioDc?.close() } catch (_: Exception) {}
        try { ctlDc?.close() } catch (_: Exception) {}
        try {
            pc?.close()
            pc?.dispose()
        } catch (_: Exception) {
        }
        pc = null
        audioDc = null
        ctlDc = null
        try {
            val am = app.getSystemService(AudioManager::class.java)
            am.mode = AudioManager.MODE_NORMAL
            am.isSpeakerphoneOn = false
        } catch (_: Exception) {
        }
    }
}
