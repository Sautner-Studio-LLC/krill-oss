/**
 * Abstraction over the per-platform credential store that holds a Krill
 * client's PIN-derived bearer token.
 *
 * A Krill server authenticates HTTP and SSE callers with a Bearer token derived
 * from the user's setup PIN. This interface lets the shared HTTP layer fetch
 * that token without caring whether it is persisted in Android Keystore,
 * iOS Keychain, the desktop JVM secret store, or `localStorage` in the browser
 * — each platform supplies its own implementation via Koin.
 */
package krill.zone.shared.security

/**
 * Client-side PIN credential store.
 *
 * Stores the PIN-derived Bearer token locally after the first-time user
 * experience (FTUE) PIN entry and serves it to the HTTP/SSE clients on every
 * outbound call. Implementations are platform-specific (JVM secret store,
 * Android Keystore, iOS Keychain, browser `localStorage`).
 */
interface ClientPinStore {
    /**
     * Returns the cached Bearer token derived from the user's PIN, or `null`
     * if no PIN has been entered on this client yet (in which case callers
     * should route the user back through the PIN-entry FTUE).
     */
    fun bearerToken(): String?

    /**
     * Derives a Bearer token from [pin] and persists it to the platform's
     * secure storage. Callers should pass the raw PIN; the derivation
     * (PBKDF2-HMAC-SHA256) happens inside the implementation so the raw PIN
     * never has to leave the entry screen.
     */
    fun storePin(pin: String)

    /**
     * Erases any persisted credential. Used when the user signs out, when a
     * stale token has been rejected by the server, or during a factory reset.
     */
    fun clear()
}
