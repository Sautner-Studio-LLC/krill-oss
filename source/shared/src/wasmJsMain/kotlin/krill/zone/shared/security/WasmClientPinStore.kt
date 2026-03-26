package krill.zone.shared.security

import kotlinx.browser.*

/**
 * WASM client PIN store — persists the derived Bearer token to localStorage
 * so it survives page reloads and browser restarts.
 *
 * For WASM kiosk mode, the token can also be fetched from the server's
 * /krill-token endpoint via setServerToken().
 */
class WasmClientPinStore : ClientPinStore {

    override fun bearerToken(): String? {
        return window.localStorage.getItem(KEY_TOKEN)?.takeIf { it.isNotEmpty() }
    }

    override fun storePin(pin: String) {
        val token = PinDerivation.deriveBearerToken(pin)
        window.localStorage.setItem(KEY_TOKEN, token)
    }

    /**
     * Called after fetching /krill-token from the host server.
     * This is the primary path for WASM kiosk mode.
     */
    fun setServerToken(token: String?) {
        if (token != null && token.isNotBlank()) {
            window.localStorage.setItem(KEY_TOKEN, token)
        }
    }

    override fun clear() {
        window.localStorage.removeItem(KEY_TOKEN)
    }

    companion object {
        private const val KEY_TOKEN = "krill_pin_token"
    }
}
