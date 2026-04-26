/**
 * Process-wide flags that describe the runtime environment of the current
 * Krill participant. All fields are mutable and set once at startup —
 * callers that need to gate behaviour on "are we the server?" or "what
 * port did the WASM bundle get served from?" read from here.
 *
 * This singleton intentionally has no setters with validation logic; it's
 * a plain bag of bits that startup code populates and the rest of the
 * codebase reads.
 */
package krill.zone.shared

/**
 * Runtime-environment flags shared across the Krill stack.
 */
object SystemInfo {
    private var isServer = false

    /**
     * The TCP port the WASM bundle was served from, or `0` outside of a
     * browser context. [krill.zone.shared.node.NodeHttp] reads this to
     * override the dialled host on WASM (where the browser already knows
     * the right server).
     */
    var wasmPort = 0

    /** The hostname from the browser's `window.location` — set at WASM startup. */
    var wasmHost: String = ""

    private var isReady = false

    /** `true` when the current process is hosting a Krill server (vs. a client). */
    fun isServer(): Boolean = isServer

    /** Marks the current process as a Krill server. Called once at server startup. */
    fun setServer(value: Boolean) {
        isServer = value
    }

    /** Toggles the "process is fully initialised" flag — read by health probes. */
    fun setReady(value: Boolean) {
        isReady = value
    }
}
