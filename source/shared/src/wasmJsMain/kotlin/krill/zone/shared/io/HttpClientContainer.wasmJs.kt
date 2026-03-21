package krill.zone.shared.io

import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.sse.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.browser.*
import krill.zone.shared.*
import kotlin.time.Duration.Companion.seconds

actual val httpClient: HttpClient by lazy {
    HttpClient(Js) {
        install(SSE) {
            maxReconnectionAttempts = 4
            reconnectionTime = 2.seconds
        }
        install(ContentNegotiation) {
            json(fastJson)
        }
        defaultRequest {
            port = window.location.port.toInt()
            url {
                protocol = URLProtocol.HTTP
                host = "/"
            }
        }
    }
}

/**
 * Separate HttpClient dedicated to SSE connections. The Ktor JS engine's internal
 * connection management can cause a long-lived SSE stream to block regular HTTP
 * requests when they share the same client instance. Using a dedicated client for
 * SSE avoids this contention.
 */
actual val sseHttpClient: HttpClient by lazy {
    HttpClient(Js) {
        install(SSE) {
            maxReconnectionAttempts = 4
            reconnectionTime = 2.seconds
        }
        install(ContentNegotiation) {
            json(fastJson)
        }
        defaultRequest {
            port = window.location.port.toInt()
            url {
                protocol = URLProtocol.HTTP
                host = "/"
            }
        }
    }
}

