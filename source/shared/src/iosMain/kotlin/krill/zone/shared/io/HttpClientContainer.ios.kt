package krill.zone.shared.io

import co.touchlab.kermit.*
import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.sse.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.cinterop.*
import krill.zone.shared.*
import platform.Foundation.*
import kotlin.time.Duration.Companion.seconds

private val logger = Logger.withTag("HttpClientContainer.ios")

private val clientProvider = HttpClientProvider()

actual val httpClient: HttpClient
    get() = clientProvider.getInstance()


internal fun rebuildHttpClient() {
    clientProvider.rebuild()
}

internal class HttpClientProvider {
    private var client: HttpClient? = null

    fun getInstance(): HttpClient {
        if (client == null) {
            client = buildDarwinClient()
        }
        return client!!
    }

    fun rebuild() {
        client?.close()
        client = buildDarwinClient()
        logger.i("Rebuilt HttpClient with updated certificates")
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun buildDarwinClient(): HttpClient {
        return HttpClient(Darwin) {
            engine {
                configureRequest {
                    setAllowsExpensiveNetworkAccess(true)
                    setAllowsConstrainedNetworkAccess(true)
                }

                // iOS certificate handling:
                // Krill uses a Trust-On-First-Use (TOFU) model with self-signed certificates.
                // NSURLSession rejects self-signed certs by default, even if the user installs
                // and trusts them in iOS Settings. We must explicitly accept server trust in
                // handleChallenge for connections to Krill servers.
                //
                // The user initiates trust by choosing to connect to a server (via manual entry
                // or future Bonjour discovery). Certificate pinning can be added later.

                @Suppress("ARGUMENT_TYPE_MISMATCH")
                handleChallenge { _, _, challenge, completionHandler ->
                    if (challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust) {
                        val serverTrust = challenge.protectionSpace.serverTrust
                        if (serverTrust != null) {
                            val host = challenge.protectionSpace.host
                            logger.i("Accepting server trust for Krill server: $host")
                            val credential = NSURLCredential.credentialForTrust(serverTrust)
                            completionHandler(NSURLSessionAuthChallengeUseCredential, credential)
                            return@handleChallenge
                        }
                    }

                    // Fall back to default handling for non-server-trust challenges
                    completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
                }
            }

            install(SSE) {
                maxReconnectionAttempts = 4
                reconnectionTime = 2.seconds
            }

            install(ContentNegotiation) {
                json(fastJson)
            }

            install(HttpTimeout) {
                // Do NOT set requestTimeoutMillis on Darwin.
                // Darwin maps it to NSURLRequest.timeoutInterval which is the total time
                // allowed for the entire request lifecycle including the body stream.
                // This kills SSE connections after the timeout. CIO (JVM/Android) only
                // applies requestTimeoutMillis to waiting for response headers, so it
                // doesn't affect streaming there.
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 300_000
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS

                // No socketTimeoutMillis - SSE/WebSocket connections should stay open indefinitely
            }
            engine {
                configureRequest { setAllowsCellularAccess(true) }
            }
        }
    }
}

