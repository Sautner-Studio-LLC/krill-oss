package krill.zone.shared.io


import co.touchlab.kermit.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.sse.*
import io.ktor.serialization.kotlinx.json.*
import krill.zone.shared.*
import java.io.*
import java.security.*
import java.security.cert.*
import javax.net.ssl.*
import kotlin.time.Duration.Companion.seconds

val trustStore = if (SystemInfo.isServer()) { "/var/lib/krill/trusted" } else { "${System.getProperty("user.home")}/.krill/trusted" }


private val logger = Logger.withTag("HttpClientContainer.jvm")

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
            client = buildCioClient()
        }
        return client!!
    }

    fun rebuild() {
        client?.close()
        client = buildCioClient()
        logger.i("Rebuilt HttpClient with updated certificates")
    }

    private fun buildCioClient(): HttpClient {
    logger.w { "BUILDING NEW HTTP CLIENT" }
        return HttpClient(CIO) {
            engine {
                https {
                    trustManager = buildTrustManagerFromTrustedCerts()
                }
                // Connection pool settings for better performance
                maxConnectionsCount = 100
                endpoint {
                    maxConnectionsPerRoute = 10
                    keepAliveTime = 10_000
                    connectTimeout = 10_000
                    connectAttempts = 3
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
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
                // No socketTimeoutMillis - WebSocket connections should stay open indefinitely
            }
        }
    }

    private fun buildTrustManagerFromTrustedCerts(): X509TrustManager {
        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        ks.load(null)

        val trustDir = File(trustStore)
        if (trustDir.exists()) {
            trustDir.listFiles()?.forEachIndexed { i, file ->
                try {
                    FileInputStream(file).use { fis ->
                        val certFactory = CertificateFactory.getInstance("X.509")
                        val cert = certFactory.generateCertificate(fis)
                        ks.setCertificateEntry("krill-peer-$i", cert)
                    }
                } catch (e: Exception) {
                    logger.e("Failed to load certificate from ${file.name}", e)
                }
            }
        }

        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(ks)
        return tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager
    }
}




