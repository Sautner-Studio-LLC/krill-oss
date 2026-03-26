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

private fun Application.configureCores() {
    install(CORS) {
        allowHeader("Authorization")
        allowCredentials = true
        anyHost()
    }

}

/**
 * PIN-based authentication.
 *
 * All cluster members (clients and peers) use the same Bearer token derived from
 * the shared cluster PIN. If no PIN is configured, the server starts in open
 * access mode (all requests accepted) until a PIN is set during install.
 */
private fun Application.configureAuthentication() {
    install(Authentication) {
        bearer("auth-api-key") {
            realm = "Krill API"
            authenticate { tokenCredential ->
                val pinProvider = PinProviderContainer.pinProvider
                println("tokenCredential: ${tokenCredential.token}")
                when {
                    // No PIN configured — open access until postinst sets one
                    pinProvider == null || !pinProvider.isConfigured() ->
                        UserIdPrincipal("krill-open")
                    // PIN-derived Bearer token matches

                    pinProvider.validateBearer(tokenCredential.token) ->
                        UserIdPrincipal("krill-member")
                    // Invalid token
                    else -> null
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
