package krill.zone.server.io.http

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import krill.zone.shared.*

class ServerHttpClient {
    lateinit var client: HttpClient

    init {
        if (!::client.isInitialized) {
            client = buildCioClient()
        }
    }


    private fun buildCioClient(): HttpClient {

        return HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000  // 2 minutes for large generations
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 120_000
            }
            engine {

                // Connection pool settings for better performance
                maxConnectionsCount = 100
                endpoint {
                    maxConnectionsPerRoute = 100
                    keepAliveTime = 60_000
                    connectTimeout = 60_000
                    connectAttempts = 3
                }
            }

            install(ContentNegotiation) {
                json(fastJson)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000

            }
        }
    }
}