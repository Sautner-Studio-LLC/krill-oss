package krill.zone.shared.security

/**
 * WASM implementation of PIN derivation.
 * WASM clients served from the server get the bearer token injected via <meta> tag,
 * so deriveBearerToken is primarily used during FTUE PIN entry on non-kiosk WASM.
 *
 * Uses a pure-Kotlin HMAC-SHA256 / PBKDF2 implementation since javax.crypto is unavailable.
 */
actual object PinDerivation {

    actual fun deriveBearerToken(pin: String): String {
        return hmacSha256(BEARER_HMAC_KEY.encodeToByteArray(), pin.encodeToByteArray()).toHex()
    }

    actual fun deriveBeaconToken(pin: String, nodeUuid: String, epochSeconds: Long): String {
        val nodeKey = hmacSha256(nodeUuid.encodeToByteArray(), pin.encodeToByteArray())
        val window = epochSeconds / TOTP_WINDOW_SECONDS
        val hmac = hmacSha256(nodeKey, window.toBigEndianBytes())
        return hmac.take(4).toByteArray().toHex()
    }
}

// ---- Pure Kotlin HMAC-SHA256 and PBKDF2 ----

private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val blockSize = 64
    val k = if (key.size > blockSize) sha256(key) else key
    val paddedKey = k.copyOf(blockSize)
    val ipad = ByteArray(blockSize) { (paddedKey[it].toInt() xor 0x36).toByte() }
    val opad = ByteArray(blockSize) { (paddedKey[it].toInt() xor 0x5c).toByte() }
    return sha256(opad + sha256(ipad + data))
}

private fun pbkdf2HmacSha256(password: ByteArray, salt: ByteArray, iterations: Int, keyLength: Int): ByteArray {
    val hashLength = 32
    val blocks = (keyLength + hashLength - 1) / hashLength
    val result = ByteArray(blocks * hashLength)

    for (block in 1..blocks) {
        val blockBytes = byteArrayOf(
            (block shr 24 and 0xFF).toByte(),
            (block shr 16 and 0xFF).toByte(),
            (block shr 8 and 0xFF).toByte(),
            (block and 0xFF).toByte()
        )
        var u = hmacSha256(password, salt + blockBytes)
        var t = u.copyOf()
        for (i in 1 until iterations) {
            u = hmacSha256(password, u)
            for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
        }
        t.copyInto(result, (block - 1) * hashLength)
    }
    return result.copyOf(keyLength)
}

// SHA-256 pure Kotlin implementation
private fun sha256(input: ByteArray): ByteArray {
    val k = intArrayOf(
        0x428a2f98.toInt(), 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
        0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
        0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3,
        0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc,
        0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
        0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
        0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
        0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
        0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
        0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(),
        0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt()
    )

    var h0 = 0x6a09e667
    var h1 = 0xbb67ae85.toInt()
    var h2 = 0x3c6ef372
    var h3 = 0xa54ff53a.toInt()
    var h4 = 0x510e527f
    var h5 = 0x9b05688c.toInt()
    var h6 = 0x1f83d9ab
    var h7 = 0x5be0cd19

    val bitLen = input.size.toLong() * 8
    val padded = input + byteArrayOf(0x80.toByte()) +
            ByteArray((55 - input.size % 64 + 64) % 64) +
            ByteArray(8) { (bitLen shr (56 - it * 8) and 0xFF).toByte() }

    for (chunk in padded.indices step 64) {
        val w = IntArray(64)
        for (i in 0..15) {
            w[i] = (padded[chunk + i * 4].toInt() and 0xFF shl 24) or
                    (padded[chunk + i * 4 + 1].toInt() and 0xFF shl 16) or
                    (padded[chunk + i * 4 + 2].toInt() and 0xFF shl 8) or
                    (padded[chunk + i * 4 + 3].toInt() and 0xFF)
        }
        for (i in 16..63) {
            val s0 = w[i - 15].rotateRight(7) xor w[i - 15].rotateRight(18) xor (w[i - 15] ushr 3)
            val s1 = w[i - 2].rotateRight(17) xor w[i - 2].rotateRight(19) xor (w[i - 2] ushr 10)
            w[i] = w[i - 16] + s0 + w[i - 7] + s1
        }

        var a = h0; var b = h1; var c = h2; var d = h3
        var e = h4; var f = h5; var g = h6; var h = h7

        for (i in 0..63) {
            val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val ch = (e and f) xor (e.inv() and g)
            val temp1 = h + s1 + ch + k[i] + w[i]
            val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val temp2 = s0 + maj

            h = g; g = f; f = e; e = d + temp1
            d = c; c = b; b = a; a = temp1 + temp2
        }

        h0 += a; h1 += b; h2 += c; h3 += d
        h4 += e; h5 += f; h6 += g; h7 += h
    }

    return intArrayOf(h0, h1, h2, h3, h4, h5, h6, h7).flatMap { v ->
        listOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
    }.toByteArray()
}

private fun Int.rotateRight(n: Int): Int = (this ushr n) or (this shl (32 - n))

private fun ByteArray.toHex(): String = joinToString("") {
    val v = it.toInt() and 0xFF
    "${(v shr 4).digitToChar(16)}${(v and 0xF).digitToChar(16)}"
}

private fun Long.toBigEndianBytes(): ByteArray {
    val bytes = ByteArray(8)
    for (i in 7 downTo 0) {
        bytes[7 - i] = (this shr (i * 8) and 0xFF).toByte()
    }
    return bytes
}
