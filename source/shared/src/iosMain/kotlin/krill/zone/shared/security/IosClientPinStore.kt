package krill.zone.shared.security

import platform.Foundation.*

/**
 * iOS client PIN store — persists the derived Bearer token to NSUserDefaults
 * so the user only enters the PIN once.
 */
class IosClientPinStore : ClientPinStore {
    private val defaults = NSUserDefaults(suiteName = "krill_pin")

    override fun bearerToken(): String? {
        return defaults.stringForKey(KEY_TOKEN)?.takeIf { it.isNotEmpty() }
    }

    override fun storePin(pin: String) {
        val token = PinDerivation.deriveBearerToken(pin)
        defaults.setObject(token, forKey = KEY_TOKEN)
        defaults.synchronize()
    }

    override fun clear() {
        defaults.removeObjectForKey(KEY_TOKEN)
        defaults.synchronize()
    }

    companion object {
        private const val KEY_TOKEN = "pin_derived_token"
    }
}
