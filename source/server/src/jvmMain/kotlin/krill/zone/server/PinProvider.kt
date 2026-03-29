package krill.zone.server

import co.touchlab.kermit.*
import krill.zone.shared.security.*
import org.koin.ext.*
import java.io.*

/**
 * Server-side PIN credential manager.
 *
 * Reads the pre-computed PIN-derived Bearer token from `/etc/krill/credentials/pin_derived_key`
 * and provides validation and beacon token computation.
 *
 * Hot-reloads: credentials are re-read every 60 seconds to support PIN rotation without restart.
 */
open class PinProvider {

    private val logger = Logger.withTag(this::class.getFullName())

    private var cachedBearerToken: String? = null
    private var lastReadMs: Long = 0L

    /**
     * Returns the PIN-derived Bearer token, or null if no PIN is configured.
     */
    open fun bearerToken(): String? {
        refreshIfStale()
        return cachedBearerToken
    }

    /**
     * Whether a PIN is configured on this server.
     */
    fun isConfigured(): Boolean = bearerToken() != null

    /**
     * Constant-time validation of a candidate Bearer token.
     */
    fun validateBearer(candidate: String): Boolean {
        val expected = bearerToken() ?: return false
        return constantTimeEquals(expected.lowercase(), candidate.lowercase())
    }

    /**
     * Compute the rolling beacon token for this server's node UUID at the current time.
     */
    fun computeBeaconToken(nodeUuid: String): String? {
        val pin = readRawDerivedKey() ?: return null
        // We use the derived key as the "pin" for beacon computation to avoid needing the raw PIN.
        // Since all nodes derive the same key from the same PIN, this produces the same beacon tokens.
        val epochSeconds = System.currentTimeMillis() / 1000
        return deriveBeaconTokenFromKey(pin, nodeUuid, epochSeconds)
    }

    /**
     * Validate a beacon rolling token with ±1 window clock skew tolerance.
     */
    fun validateBeaconToken(nodeUuid: String, token: String): Boolean {
        val pin = readRawDerivedKey() ?: return false
        val epochSeconds = System.currentTimeMillis() / 1000

        // Check current window and ±1 for clock skew
        for (offset in -1L..1L) {
            val windowEpoch = epochSeconds + (offset * TOTP_WINDOW_SECONDS)
            val expected = deriveBeaconTokenFromKey(pin, nodeUuid, windowEpoch)
            if (constantTimeEquals(expected, token)) return true
        }
        return false
    }

    private fun refreshIfStale() {
        val now = System.currentTimeMillis()
        if (now - lastReadMs > RELOAD_INTERVAL_MS) {
            cachedBearerToken = readTokenFile()
            lastReadMs = now
        }
    }

    private fun readTokenFile(): String? {
        return try {
            val file = File(DERIVED_KEY_PATH)
            if (file.exists()) {
                file.readText().trim().takeIf { it.isNotEmpty() }
            } else {
                null
            }
        } catch (e: Exception) {
            logger.w("Failed to read PIN derived key: ${e.message}")
            null
        }
    }

    private fun readRawDerivedKey(): String? = bearerToken()

    /**
     * Compute beacon token using the derived key as a seed.
     * Uses HMAC(nodeUuid, derivedKey) as the node key, then HMAC(nodeKey, window) for the token.
     * Both servers in the cluster derive the same key from the same PIN, so this produces matching tokens.
     */
    private fun deriveBeaconTokenFromKey(derivedKey: String, nodeUuid: String, epochSeconds: Long): String {
        val window = epochSeconds / TOTP_WINDOW_SECONDS
        // Node key: HMAC-SHA256(key=nodeUuid, data=derivedKey)
        val nodeKey = hmacSha256(nodeUuid.toByteArray(), derivedKey.toByteArray())
        // Token: HMAC-SHA256(key=nodeKey, data=window)
        val windowBytes = ByteArray(8)
        for (i in 7 downTo 0) {
            windowBytes[7 - i] = (window shr (i * 8) and 0xFF).toByte()
        }
        val hmac = hmacSha256(nodeKey, windowBytes)
        return hmac.take(4).joinToString("") { "%02x".format(it) }
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    companion object {
        const val DERIVED_KEY_PATH = "/etc/krill/credentials/pin_derived_key"

        private const val RELOAD_INTERVAL_MS = 60_000L

        fun constantTimeEquals(a: String, b: String): Boolean {
            if (a.length != b.length) return false
            var result = 0
            for (i in a.indices) {
                result = result or (a[i].code xor b[i].code)
            }
            return result == 0
        }
    }
}
