/**
 * Cross-platform PIN-derivation primitives used by Krill's authentication
 * layer. The user's setup PIN is fed through PBKDF2-HMAC-SHA256 to produce two
 * distinct credentials:
 *
 *  * a stable [PinDerivation.deriveBearerToken] that is sent on every HTTP/SSE
 *    request as a `Bearer` token, and
 *  * a rolling [PinDerivation.deriveBeaconToken] that proves cluster
 *    membership in multicast beacons without ever transmitting the PIN itself.
 *
 * The interface is `expect`/`actual` because the underlying HMAC-SHA256
 * implementation is platform-specific (`javax.crypto.Mac` on JVM/Android,
 * Apple's CoreCrypto on iOS, a pure-Kotlin fallback on wasmJs).
 */
package krill.zone.shared.security

/**
 * Platform-specific PIN-derivation functions.
 *
 * All derivations key off the user's raw PIN and produce hex-string outputs so
 * the caller can put the result in HTTP headers / multicast payloads without
 * extra encoding. Implementations MUST agree byte-for-byte on the output of
 * both methods so a token derived on one platform validates on every other —
 * see the values of [BEARER_HMAC_KEY] and [TOTP_WINDOW_SECONDS] for the shared
 * constants.
 */
expect object PinDerivation {
    /**
     * Returns a stable, deterministic Bearer token for a Krill server.
     *
     * The token is `HMAC-SHA256([BEARER_HMAC_KEY], pin)` rendered as a
     * 64-character lowercase hex string. Stable across restarts and clients,
     * which is why it is safe to persist in [ClientPinStore] and present on
     * every authenticated HTTP/SSE call.
     */
    fun deriveBearerToken(pin: String): String

    /**
     * Returns a rolling 8-character hex token that proves the caller knows the
     * swarm PIN without revealing it on the wire — used in multicast beacons
     * to filter out devices that belong to a different swarm.
     *
     * Construction:
     *  1. `nodeKey = HMAC-SHA256(nodeUuid, pin)` — binds the token to a
     *     specific server's UUID so a leaked beacon can't be replayed against
     *     another server in the same LAN.
     *  2. `window = epochSeconds / TOTP_WINDOW_SECONDS` — quantises time into
     *     30-second buckets so the token rotates and old captures expire.
     *  3. token = first 4 bytes of `HMAC-SHA256(nodeKey, window)` as hex.
     */
    fun deriveBeaconToken(pin: String, nodeUuid: String, epochSeconds: Long): String
}

/**
 * Salt-like constant prefixed into the bearer-token HMAC.
 *
 * The literal `"krill-api-pbkdf2-v1"` is deliberately versioned: a future
 * scheme rotation can ship `"krill-api-pbkdf2-v2"` and the server can accept
 * both during a migration window. **Do not change this value** — every
 * Krill-deployed bearer token currently in use was derived from it.
 */
const val BEARER_HMAC_KEY = "krill-api-pbkdf2-v1"

/**
 * Width of one beacon-token rotation window, in seconds.
 *
 * Compatible with RFC 6238 TOTP step semantics. 30 seconds is the same window
 * used by Google Authenticator et al. — short enough that captured beacons
 * expire quickly, long enough that ordinary clock skew across a LAN doesn't
 * desynchronise senders and receivers.
 */
const val TOTP_WINDOW_SECONDS = 30L
