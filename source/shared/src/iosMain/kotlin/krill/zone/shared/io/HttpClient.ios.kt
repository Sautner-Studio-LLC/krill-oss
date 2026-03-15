package krill.zone.shared.io

import co.touchlab.kermit.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.darwin.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.cinterop.*
import kotlinx.io.*
import krill.zone.shared.krillapp.server.*
import krill.zone.shared.node.*
import platform.Foundation.*

actual val trustHttpClient: TrustHost by lazy {
    DefaultTrustHttpClient()
}

private const val TRUST_STORE_KEY_PREFIX = "krill_trusted_cert_"

class DefaultTrustHttpClient : TrustHost {
    private val logger = Logger.withTag("HttpClient.ios")

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun fetchPeerCert(url: Url): Boolean {
        // For iOS, we fetch and store the certificate data, but the actual trust
        // must be configured by the user through iOS Settings or by installing
        // the certificate profile manually.

        logger.i("Fetching peer cert from $url")

        // Create an insecure client that accepts any server trust to fetch the certificate.
        // This is equivalent to the trust-all TrustManager used on JVM/Android.
        // Only used for the one-time cert download from /trust.
        val insecureClient = HttpClient(Darwin) {
            engine {
                configureRequest {
                    setAllowsExpensiveNetworkAccess(true)
                    setAllowsConstrainedNetworkAccess(true)
                }

                @Suppress("ARGUMENT_TYPE_MISMATCH")
                handleChallenge { _, _, challenge, completionHandler ->
                    if (challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust) {
                        val serverTrust = challenge.protectionSpace.serverTrust
                        if (serverTrust != null) {
                            val credential = NSURLCredential.credentialForTrust(serverTrust)
                            completionHandler(NSURLSessionAuthChallengeUseCredential, credential)
                            return@handleChallenge
                        }
                    }
                    completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
                }
            }
        }

        val trustUrl = URLBuilder(url).apply {
            path("trust")
        }.build()

        try {
            val byteArray: ByteArray = insecureClient.prepareGet(trustUrl).execute { response ->
                if (response.status.isSuccess()) {
                    val channel: ByteReadChannel = response.body()
                    channel.readRemaining().readByteArray()
                } else {
                    byteArrayOf()
                }
            }

            logger.i("Received cert response: ${byteArray.size} bytes")

            if (byteArray.isEmpty()) {
                logger.w("No certificate data received")
                return false
            }

            // Store the certificate in UserDefaults
            val certKey = "$TRUST_STORE_KEY_PREFIX${url.host}"
            val existing = NSUserDefaults.standardUserDefaults.dataForKey(certKey)
            val newData = byteArray.toNSData()

            var certChanged = false
            if (existing != null) {
                if (!existing.isEqualToData(newData)) {
                    NSUserDefaults.standardUserDefaults.setObject(newData, forKey = certKey)
                    logger.i("Updated certificate for ${url.host}")
                    certChanged = true
                } else {
                    logger.i("Certificate for ${url.host} unchanged")
                }
            } else {
                NSUserDefaults.standardUserDefaults.setObject(newData, forKey = certKey)
                logger.i("Stored new certificate for ${url.host}")
                certChanged = true
            }

            if (certChanged) {
                // Trigger client rebuild
                rebuildHttpClient()


            }

            return true

        } catch (e: Exception) {
            logger.e("Exception while fetching peer certificate: ${e.message}", e)
            return false
        } finally {
            insecureClient.close()
        }
    }

    override suspend fun deleteCert(node: Node) {
        val meta = node.meta as ServerMetaData
        val url = URLBuilder(
            host = meta.name,
            port = meta.port,
            protocol = URLProtocol.HTTPS,
            pathSegments = listOf("trust")
        ).build()

        val certKey = "$TRUST_STORE_KEY_PREFIX${url.host}"
        NSUserDefaults.standardUserDefaults.removeObjectForKey(certKey)
    }
}

// Extension to convert ByteArray to NSData
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData {
    return this.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = this.size.toULong())
    }
}

