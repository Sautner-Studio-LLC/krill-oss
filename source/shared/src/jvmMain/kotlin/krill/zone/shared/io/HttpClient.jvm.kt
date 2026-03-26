package krill.zone.shared.io


import co.touchlab.kermit.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.io.*
import krill.zone.shared.krillapp.server.*
import krill.zone.shared.node.*
import org.koin.ext.*
import java.io.*
import java.security.cert.*
import javax.net.ssl.*

actual val trustHttpClient: TrustHost by lazy {
    DefaultTrustHttpClient()
}

class DefaultTrustHttpClient : TrustHost {
    private val logger = Logger.withTag(this::class.getFullName())

    override suspend fun fetchPeerCert(url: Url): Boolean {
        // Trust‐all client just to grab the cert
        logger.i { "JVM FeatureProcessor: Fetching certificate from $url" }
        val file = File("${trustStore}/${url.host}.crt")
        logger.i("checking trust store: ${file.exists()} ${file.absolutePath}")
        val store = File(trustStore)
        if (!store.exists()) {
            store.mkdirs()
        }
        val insecureClient = HttpClient(CIO) {
            engine {
                https {
                    trustManager = object : X509TrustManager {
                        override fun getAcceptedIssuers() = arrayOf<X509Certificate>()
                        override fun checkClientTrusted(certs: Array<out X509Certificate>?, authType: String) {}
                        override fun checkServerTrusted(certs: Array<out X509Certificate>?, authType: String) {}
                    }
                }
                endpoint {
                    connectTimeout = 10_000
                    connectAttempts = 1
                }
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
            }
        }

        val trustUrl = URLBuilder(url).apply { path("trust") }.build()

        try {
            val byteArray: ByteArray = insecureClient.prepareGet(trustUrl).execute { response ->
                if (response.status.isSuccess()) {
                    val channel: ByteReadChannel = response.body()
                    channel.readRemaining().readByteArray()
                } else {
                    logger.e { "cannot connect to trusted url: ${response.status.description}" }
                    byteArrayOf()
                }


            }
            logger.i("Received cert response: ${byteArray.size}")
            return if (byteArray.isNotEmpty()) {
                var certChanged = false
                if (file.exists()) {
                    val existing = file.readBytes()
                    if (!existing.contentEquals(byteArray)) {
                        file.delete()
                        file.writeBytes(byteArray)
                        logger.i("Replaced cert for ${url.host} to ${file.absolutePath}")
                        certChanged = true
                    } else {
                        logger.i("Cert for ${url.host} unchanged")
                    }
                } else {
                    file.writeBytes(byteArray)
                    logger.i("Wrote cert for ${url.host} to ${file.absolutePath}")
                    certChanged = true
                }

                // Rebuild the httpClient so it picks up the new certificate
                if (certChanged) {
                    rebuildHttpClient()
                }

                true
            } else {
                false
            }


        } catch (e: Exception) {
            logger.e("jvm Exception while attempting to fetch peer certificate $trustUrl", e)
            return false
        } finally {
            insecureClient.close()
        }
    }

    override suspend fun deleteCert(node: Node) {
        val meta = node.meta as ServerMetaData
        val url = URLBuilder(
            host = meta.resolvedHost(),
            port = meta.port,
            protocol = URLProtocol.HTTPS,
            pathSegments = listOf("trust")
        ).build()
        val file = File("${trustStore}/${url.host}.crt")
        logger.i("${node.details()}: deleting invalid trust store: ${file.exists()} ${file.absolutePath}")
        file.delete()
    }

}
