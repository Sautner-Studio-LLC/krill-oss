package krill.zone.mcp.auth

import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Reads the PIN-derived Bearer token from disk and provides validation.
 *
 * Mirrors the server-side PinProvider in the main Krill codebase: the token
 * file is expected to contain a lowercase hex HMAC-SHA256 on a single line.
 * Re-reads every 60 seconds so PIN rotation picks up without a restart.
 *
 * Default path: /etc/krill-mcp/credentials/pin_derived_key.
 * If that file is a symlink to /etc/krill/credentials/pin_derived_key (co-located
 * with a Krill server install), we transparently read through it.
 */
class PinProvider(
    private val path: String = DEFAULT_PATH,
    private val reloadIntervalMs: Long = 60_000L,
) {
    private val log = LoggerFactory.getLogger(PinProvider::class.java)

    private data class Cached(val token: String?, val readAtMs: Long)

    private val cache = AtomicReference(Cached(null, 0L))

    fun bearerToken(): String? {
        val now = System.currentTimeMillis()
        val current = cache.get()
        if (now - current.readAtMs <= reloadIntervalMs && current.readAtMs > 0) {
            return current.token
        }
        val fresh = readFromDisk()
        cache.set(Cached(fresh, now))
        return fresh
    }

    fun isConfigured(): Boolean = bearerToken() != null

    /** Constant-time compare; lowercases both sides to tolerate client case differences. */
    fun validate(candidate: String): Boolean {
        val expected = bearerToken() ?: return false
        return constantTimeEquals(expected.lowercase(), candidate.lowercase())
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }

    private fun readFromDisk(): String? {
        val file = File(path)
        if (!file.exists()) {
            log.warn("PIN-derived key not found at {}", path)
            return null
        }
        return try {
            file.readText().trim().takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            log.warn("Failed to read {}: {}", path, e.message)
            null
        }
    }

    companion object {
        const val DEFAULT_PATH = "/etc/krill-mcp/credentials/pin_derived_key"
    }
}
