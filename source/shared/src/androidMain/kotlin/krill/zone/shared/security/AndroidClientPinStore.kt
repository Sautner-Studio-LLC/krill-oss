package krill.zone.shared.security

import android.content.*
import krill.zone.shared.*

/**
 * Android client PIN store — persists the derived Bearer token to SharedPreferences
 * so the user only enters the PIN once.
 */
class AndroidClientPinStore : ClientPinStore {
    private val prefs: SharedPreferences by lazy {
        ContextContainer.context.getSharedPreferences("krill_pin", Context.MODE_PRIVATE)
    }

    override fun bearerToken(): String? {
        return prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotEmpty() }
    }

    override fun storePin(pin: String) {
        val token = PinDerivation.deriveBearerToken(pin)
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    companion object {
        private const val KEY_TOKEN = "pin_derived_token"
    }
}
