package krill.zone.shared.security

/**
 * Platform-specific PIN derivation functions.
 * All derivations use PBKDF2-HMAC-SHA256.
 */
expect object PinDerivation {
    /**
     * Derives a stable Bearer token from a PIN.
     * Result: PBKDF2(pin, "krill-api", 10000) as 64-char hex string.
     */
    fun deriveBearerToken(pin: String): String

    /**
     * Derives a rolling beacon token for cluster membership verification.
     * Result: HMAC-SHA256(PBKDF2(pin, nodeUuid, 10000), floor(epochSeconds/30)) → first 4 bytes as 8-char hex.
     */
    fun deriveBeaconToken(pin: String, nodeUuid: String, epochSeconds: Long): String
}

const val BEARER_HMAC_KEY = "krill-api-pbkdf2-v1"
const val TOTP_WINDOW_SECONDS = 30L
