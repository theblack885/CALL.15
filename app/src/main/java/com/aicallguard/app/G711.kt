package com.aicallguard.app

/** Awaaz ko chhota karne ke liye mu-law (G.711) aur resampling. */
object G711 {
    private const val BIAS = 0x84
    private const val CLIP = 32635

    fun encode(sample: Short): Byte {
        var s = sample.toInt()
        val sign = if (s < 0) {
            s = -s
            0x80
        } else 0
        if (s > CLIP) s = CLIP
        s += BIAS
        var exp = 7
        var mask = 0x4000
        while (exp > 0 && (s and mask) == 0) {
            exp--
            mask = mask shr 1
        }
        val mant = (s shr (exp + 3)) and 0x0F
        return ((sign or (exp shl 4) or mant).inv() and 0xFF).toByte()
    }

    fun decode(b: Byte): Short {
        val u = b.toInt().inv() and 0xFF
        val sign = u and 0x80
        val exp = (u shr 4) and 0x07
        val mant = u and 0x0F
        var s = ((mant shl 3) + BIAS) shl exp
        s -= BIAS
        return (if (sign != 0) -s else s).toShort()
    }

    fun resample(src: ShortArray, from: Int, to: Int): ShortArray {
        if (from == to || src.isEmpty()) return src
        val n = (src.size.toLong() * to / from).toInt()
        val out = ShortArray(n)
        val ratio = from.toDouble() / to
        for (i in 0 until n) {
            val pos = i * ratio
            val i0 = pos.toInt().coerceAtMost(src.size - 1)
            val i1 = minOf(i0 + 1, src.size - 1)
            val f = pos - i0
            out[i] = (src[i0] * (1 - f) + src[i1] * f).toInt().toShort()
        }
        return out
    }
}
