package krill.zone.shared.security

import co.touchlab.kermit.*
import java.io.*

/**
 * JVM/Desktop client PIN store — persists the derived Bearer token to disk
 * so the user only enters the PIN once.
 */
class JvmClientPinStore : ClientPinStore {
    private val logger = Logger.withTag("JvmClientPinStore")
    private var cachedToken: String? = null

    init {
        cachedToken = loadFromDisk()
    }

    override fun bearerToken(): String? = cachedToken

    override fun storePin(pin: String) {
        val token = PinDerivation.deriveBearerToken(pin)
        cachedToken = token
        saveToDisk(token)
    }

    override fun clear() {
        cachedToken = null
        val file = tokenFile()
        if (file.exists()) file.delete()
    }

    private fun loadFromDisk(): String? {
        return try {
            val file = tokenFile()
            if (file.exists()) {
                file.readText().trim().takeIf { it.isNotEmpty() }
            } else null
        } catch (e: Exception) {
            logger.w("Failed to load PIN token from disk: ${e.message}")
            null
        }
    }

    private fun saveToDisk(token: String) {
        try {
            val file = tokenFile()
            file.parentFile?.mkdirs()
            file.writeText(token)
        } catch (e: Exception) {
            logger.e("Failed to save PIN token to disk: ${e.message}")
        }
    }

    private fun tokenFile(): File {
        val home = System.getProperty("user.home") ?: "."
        return File(home, ".krill/pin_token")
    }
}
