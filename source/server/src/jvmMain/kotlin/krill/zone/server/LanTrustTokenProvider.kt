package krill.zone.server

import co.touchlab.kermit.*
import org.koin.ext.*
import java.io.*

/**
 * Reads and caches the LAN trust token from /etc/krill/credentials/lan_trust_token.
 * The token is a shared secret that authorizes automatic peer handshakes between
 * servers on the same LAN.
 *
 * Hot-reloads: the file is re-read on each access if more than 60 seconds have
 * passed since the last read, supporting token rotation without restart.
 *
 * When the file is absent or empty, auto-trust is disabled and [token] returns null.
 */
open class LanTrustTokenProvider {

    private val logger = Logger.withTag(this::class.getFullName())

    private var cachedToken: String? = null
    private var lastReadMs: Long = 0L

    /**
     * Returns the current LAN trust token, or null if auto-trust is disabled
     * (file missing, empty, or unreadable).
     */
    open fun token(): String? {
        val now = System.currentTimeMillis()
        if (now - lastReadMs > RELOAD_INTERVAL_MS) {
            cachedToken = readTokenFile()
            lastReadMs = now
        }
        return cachedToken
    }

    /**
     * Constant-time comparison of the provided token against the configured token.
     * Returns false if auto-trust is disabled (no token configured).
     */
    fun validate(candidateToken: String): Boolean {
        val expected = token() ?: return false
        return constantTimeEquals(expected, candidateToken)
    }

    private fun readTokenFile(): String? {
        return try {
            val file = File(TOKEN_FILE_PATH)
            if (file.exists()) {
                val content = file.readText().trim()
                if (content.isNotEmpty()) {
                    logger.d { "LAN trust token loaded" }
                    content
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            logger.w("Failed to read LAN trust token: ${e.message}")
            null
        }
    }

    companion object {
        const val TOKEN_FILE_PATH = "/etc/krill/credentials/lan_trust_token"
        private const val RELOAD_INTERVAL_MS = 60_000L

        /**
         * Constant-time string comparison to prevent timing attacks.
         */
        private fun constantTimeEquals(a: String, b: String): Boolean {
            if (a.length != b.length) return false
            var result = 0
            for (i in a.indices) {
                result = result or (a[i].code xor b[i].code)
            }
            return result == 0
        }
    }
}
