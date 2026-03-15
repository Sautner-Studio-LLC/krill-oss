package krill.zone.server

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.sse.*
import krill.zone.server.di.*
import krill.zone.shared.*
import krill.zone.shared.di.*
import org.koin.ktor.plugin.*
import java.io.*

private const val API_KEY_FILE_PATH = "/etc/krill/credentials/api_key"

/**
 * Configures all Ktor plugins for the server
 */
internal fun Application.configurePlugins() {
    configureSSE()
    configureContentNegotiation()
    configureAuthentication()
    configureCores()
    configureKoin()
}


private fun Application.configureSSE() {
    install(SSE) {  }
}

/**
 * Reads the API key from the credentials file.
 * Returns null if the file doesn't exist or is empty.
 */
fun readApiKey(): String? {
    return try {
        val file = File(API_KEY_FILE_PATH)
        if (file.exists()) {
            file.readText().trim().takeIf { it.isNotEmpty() }
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}

private fun Application.configureCores() {
    install(CORS) {
        allowHeader("Authorization")
        allowCredentials = true
        anyHost()
    }

}

private fun Application.configureAuthentication() {
    install(Authentication) {
        bearer("auth-api-key") {
            realm = "Krill API"
            authenticate { tokenCredential ->
                if (File("/etc/krill/kiosk").exists()) {
                    UserIdPrincipal("krill-kiosk")
                }
                else {
                    val expectedApiKey = readApiKey()
                    if (expectedApiKey != null && tokenCredential.token == expectedApiKey) {
                        UserIdPrincipal("krill-client")
                    } else {
                        null
                    }
                }
            }
        }
    }
}

/**
 * Configure Dependency Injection
 */
private fun Application.configureKoin() {

    install(Koin) {
        modules(
            sharedModule,
            serverModule,
            serverProcessModule,
        )

    }
}


/**
 * Configure JSON content negotiation
 */
private fun Application.configureContentNegotiation() {
    install(ContentNegotiation) {
        json(fastJson)
    }
}



