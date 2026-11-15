package com.aicallguard.app

object Config {
    // Kuch networks par call jodne ke liye TURN server chahiye hota hai (optional).
    // Khali chhodo to sirf Google STUN chalega.
    const val TURN_URL = ""      // jaise "turn:global.relay.metered.ca:80"
    const val TURN_USER = ""
    const val TURN_PASS = ""

    const val RING_TIMEOUT_MS = 45_000L
    const val CONNECT_TIMEOUT_MS = 25_000L
    const val MAX_AI_ROUNDS = 12

    const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip"
    const val MODEL_DIR = "vosk-model-small-hi-0.22"
}
