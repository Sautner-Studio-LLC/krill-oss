/**
 * Android actual of [PinDerivation], backed by `javax.crypto.Mac` for
 * HMAC-SHA256. Identical in behaviour to the JVM actual — kept as a separate
 * source set because the Android build pipeline compiles to a distinct
 * artifact and may diverge in the future (e.g. switching to AndroidX security
 * libraries or hardware-backed keys).
 */
package krill.zone.shared.security

import javax.crypto.*
import javax.crypto.spec.*

actual object PinDerivation {

    actual fun deriveBearerToken(pin: String): String {
        return hmacSha256(BEARER_HMAC_KEY.toByteArray(), pin.toByteArray()).toHex()
    }

    actual fun deriveBeaconToken(pin: String, nodeUuid: String, epochSeconds: Long): String {
        val nodeKey = hmacSha256(nodeUuid.toByteArray(), pin.toByteArray())
        val window = epochSeconds / TOTP_WINDOW_SECONDS
        val hmac = hmacSha256(nodeKey, window.toBigEndianBytes())
        return hmac.take(4).toByteArray().toHex()
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private fun Long.toBigEndianBytes(): ByteArray {
    val bytes = ByteArray(8)
    for (i in 7 downTo 0) {
        bytes[7 - i] = (this shr (i * 8) and 0xFF).toByte()
    }
    return bytes
}