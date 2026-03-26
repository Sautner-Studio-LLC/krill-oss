package krill.zone.shared.security

/**
 * Client-side PIN credential store.
 * Stores the PIN-derived Bearer token locally after FTUE PIN entry.
 */
interface ClientPinStore {
    /** Returns the stored Bearer token, or null if no PIN has been entered. */
    fun bearerToken(): String?

    /** Derives and stores the Bearer token from the raw PIN. */
    fun storePin(pin: String)

    /** Clears the stored PIN credentials. */
    fun clear()
}
