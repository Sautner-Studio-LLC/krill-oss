package krill.zone.mcp.krill

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * Thin HTTP client to a single Krill server.
 *
 * Krill deployments use self-signed TLS certs derived on first install, so the
 * client trusts any certificate presented by the target host. Security is
 * provided by the PIN-derived Bearer token — a caller without the cluster PIN
 * cannot forge valid requests even on an intercepted TLS stream.
 *
 * Responses are returned as generic JsonElement trees. We intentionally do NOT
 * mirror Krill's internal node data model here: the MCP layer is a passthrough
 * proxy, and binding tightly to internal types would create a coupling we do
 * not need. Clients of this class pull the fields they want.
 */
class KrillClient(
    val serverId: String,
    val baseUrl: String,
    private val bearerToken: () -> String?,
) {
    private val log = LoggerFactory.getLogger(KrillClient::class.java)

    suspend fun health(): JsonElement = get("/health")

    suspend fun nodes(): JsonArray = get("/nodes").jsonArray

    suspend fun node(id: String): JsonElement = get("/node/$id")

    suspend fun series(id: String, startMs: Long, endMs: Long): JsonArray =
        get("/node/$id/data/series?st=$startMs&et=$endMs").jsonArray

    private suspend fun get(path: String): JsonElement {
        val token = bearerToken() ?: error("krill-mcp has no PIN-derived bearer token configured")
        val response: HttpResponse = http.get("$baseUrl$path") {
            header(HttpHeaders.Authorization, "Bearer $token")
            accept(ContentType.Application.Json)
        }
        if (!response.status.isSuccess()) {
            val body = runCatching { response.bodyAsText() }.getOrDefault("")
            error("Krill $baseUrl$path returned ${response.status}: $body")
        }
        val text = response.bodyAsText()
        return if (text.isBlank()) JsonNull else Json.parseToJsonElement(text)
    }

    companion object {
        /** Shared client — trust-all TLS, Krill's self-signed certs. */
        val http: HttpClient by lazy {
            HttpClient(CIO) {
                engine {
                    https {
                        trustManager = object : X509TrustManager {
                            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                        }
                    }
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = 15_000
                    connectTimeoutMillis = 5_000
                    socketTimeoutMillis = 15_000
                }
                expectSuccess = false
            }
        }
    }
}
