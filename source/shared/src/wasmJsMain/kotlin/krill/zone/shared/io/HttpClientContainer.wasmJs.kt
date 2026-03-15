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
    WasmClientProvider().getInstance()
}



class WasmClientProvider {

    lateinit var c: HttpClient

    fun getInstance(): HttpClient {
        if (!::c.isInitialized) {
            c = HttpClient(Js) {
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
        return c
    }
}

