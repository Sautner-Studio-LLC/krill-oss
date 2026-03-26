package krill.zone.shared.io


import android.annotation.*
import co.touchlab.kermit.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import krill.zone.shared.*
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
        logger.i("Fetching Peer Certs Android $url")
        val context = ContextContainer.context
        val trustDir = File(context.filesDir, "trusted")
        if (!trustDir.exists()) {
            trustDir.mkdirs()
        }
        val file = File(trustDir, "${url.host}.crt")

        val insecureClient = createInsecureClient()

        val trustUrl = URLBuilder(url).apply {
            path("trust")
        }.build()
        try {
            logger.w("checking url: ${trustUrl}")
            val byteArray: ByteArray = insecureClient.prepareGet(trustUrl).execute { response ->
                if (response.status.isSuccess()) {
                    // Read the response body directly as bytes to avoid using blocking file I/O helpers.
                    response.body<ByteArray>()
                } else {
                    logger.w { "got an error while fetching Peer Certs: ${response.status.description}" }
                    byteArrayOf()
                }
            }
            logger.i("Received cert response: ${byteArray.size}")

            if (byteArray.isEmpty()) return false

            // Delegate blocking file ops to helper which runs on the injected IO dispatcher
             writeCertFile(file, byteArray)
            return true

        } catch (e: Exception) {
            logger.w("Android Exception while attempting to fetch peer certificate from $url")
            return false
        } finally {
            insecureClient.close()
        }
    }

    override suspend fun deleteCert(node: Node) {
        logger.i("${node.details()}: Deleting Invalid Peer Cert")
        val context = ContextContainer.context
        val trustDir = File(context.filesDir, "trusted")
        val meta = node.meta as ServerMetaData
        val url = URLBuilder(
            host = meta.resolvedHost(),
            port = meta.port,
            protocol = URLProtocol.HTTPS,
            pathSegments = listOf("trust")
        ).build()
        val file = File(trustDir, "${url.host}.crt")
        if (file.exists()) {
            file.delete()
        } else {
            logger.e { "trust file not found when deletin ${file.absolutePath}" }

        }

    }

    @SuppressLint("TrustAllX509TrustManager")
    private fun createInsecureClient(): HttpClient {
        // Create a permissive trust manager to allow fetching certificates from self-signed hosts.
        // This is only used in this short-lived insecure client for the sole purpose of downloading
        // the certificate itself.
        return HttpClient(CIO) {
            engine {
                https {
                    trustManager = object : X509TrustManager {
                        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                        override fun checkClientTrusted(certs: Array<out X509Certificate>?, authType: String) {}
                        override fun checkServerTrusted(certs: Array<out X509Certificate>?, authType: String) {}
                    }
                }
            }
        }
    }

    private fun writeCertFile(file: File, bytes: ByteArray)  {
        var certChanged = false
        if (file.exists()) {
            val existing = file.readBytes()
            if (!existing.contentEquals(bytes)) {
                // replace
                file.delete()
                file.writeBytes(bytes)
                logger.i("Replaced cert for ${file.nameWithoutExtension} to ${file.absolutePath}")
                certChanged = true
            } else {
                logger.i("Cert for ${file.nameWithoutExtension} unchanged")
            }
        } else {
            // write the file directly (writeBytes will create it)
            file.writeBytes(bytes)
            logger.i("Wrote cert for ${file.nameWithoutExtension} to ${file.absolutePath}")
            certChanged = true
        }

        // Rebuild the httpClient so it picks up the new certificate
        if (certChanged) {
            rebuildHttpClient()
        }


    }

}

