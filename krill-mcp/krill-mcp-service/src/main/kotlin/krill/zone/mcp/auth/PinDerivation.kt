package krill.zone.mcp.auth

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Produces the PIN-derived Bearer token used across the Krill cluster.
 *
 * Must stay byte-for-byte compatible with the Krill server's postinst, which does:
 *
 *     openssl dgst -sha256 -hmac "krill-api-pbkdf2-v1" <<< "<pin>"
 *
 * Any drift here breaks authentication with every Krill server the MCP talks to.
 */
object PinDerivation {
    private const val HMAC_KEY = "krill-api-pbkdf2-v1"

    fun deriveBearerToken(pin: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(HMAC_KEY.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val bytes = mac.doFinal(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}
