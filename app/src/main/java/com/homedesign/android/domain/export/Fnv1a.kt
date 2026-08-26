package com.homedesign.android.domain.export

/**
 * FNV-1a 64-bit — stable HD-DOOR / HD-WIN block name hashes (web `fnv1a.ts`).
 */
object Fnv1a {
    // 0xcbf29ce484222325 as signed Long
    private const val FNV_OFFSET = -0x340d631b7bdddcdbL
    private const val FNV_PRIME = 0x100000001b3L

    fun hash64(data: String): Long {
        var h = FNV_OFFSET
        val bytes = data.toByteArray(Charsets.UTF_8)
        for (b in bytes) {
            h = h xor (b.toLong() and 0xffL)
            h *= FNV_PRIME
        }
        return h
    }

    fun hash64Base36(data: String): String {
        var v = hash64(data)
        if (v == 0L) return "0"
        val digits = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        // Treat as unsigned for base36 (match JS BigInt.toString(36).toUpperCase()).
        val buf = CharArray(13)
        var i = buf.size
        val unsigned = java.math.BigInteger(1, byteArrayOf(
            (v ushr 56).toByte(),
            (v ushr 48).toByte(),
            (v ushr 40).toByte(),
            (v ushr 32).toByte(),
            (v ushr 24).toByte(),
            (v ushr 16).toByte(),
            (v ushr 8).toByte(),
            v.toByte(),
        ))
        var n = unsigned
        val base = java.math.BigInteger.valueOf(36)
        while (n.signum() > 0) {
            val div = n.divideAndRemainder(base)
            n = div[0]
            i -= 1
            buf[i] = digits[div[1].toInt()]
        }
        return String(buf, i, buf.size - i)
    }
}
