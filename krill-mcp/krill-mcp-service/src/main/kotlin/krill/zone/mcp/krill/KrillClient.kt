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
    /**
     * Client-resolvable base URL — what external apps (phones, browsers) should
     * use to reach this server. Derived from `/health`'s ServerMetaData.name +
     * port at probe time, falling back to [baseUrl] when we have nothing
     * better. Important for building Diagram `source` URLs that other Krill
     * clients will load — a `localhost:8442` seed is reachable from krill-mcp
     * itself but not from a phone on the same network.
     */
    val publicBaseUrl: String,
    private val bearerToken: () -> String?,
) {
    private val log = LoggerFactory.getLogger(KrillClient::class.java)

    suspend fun health(): JsonElement = get("/health")

    suspend fun nodes(): JsonArray = get("/nodes").jsonArray

    suspend fun node(id: String): JsonElement = get("/node/$id")

    suspend fun series(id: String, startMs: Long, endMs: Long): JsonArray =
        get("/node/$id/data/series?st=$startMs&et=$endMs").jsonArray

    /**
     * Post a node JSON body to /node/{id}. The server treats this as an upsert:
     * a new id with `state="CREATED"` creates the node, an existing id updates it.
     * Server responds 202 Accepted with empty body.
     */
    suspend fun postNode(node: JsonObject) {
        val id = node["id"]?.jsonPrimitive?.contentOrNull
            ?: error("Node JSON missing required 'id' field")
        postJson("/node/$id", node)
    }

    /** PUT raw SVG bytes to /project/{id}/diagram/{file}. */
    suspend fun uploadDiagramFile(projectId: String, fileName: String, svgContent: String) {
        val token = bearerToken() ?: error("krill-mcp has no PIN-derived bearer token configured")
        val bytes = svgContent.toByteArray(Charsets.UTF_8)
        val response: HttpResponse = http.put("$baseUrl/project/$projectId/diagram/$fileName") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Image.SVG)
            setBody(bytes)
        }
        if (!response.status.isSuccess()) {
            val body = runCatching { response.bodyAsText() }.getOrDefault("")
            error("PUT $baseUrl/project/$projectId/diagram/$fileName returned ${response.status}: $body")
        }
    }

    /** GET /project/{id}/diagram/{file} — returns the SVG as a UTF-8 string. */
    suspend fun downloadDiagramFile(projectId: String, fileName: String): String =
        getText("/project/$projectId/diagram/$fileName")

    private suspend fun get(path: String): JsonElement {
        val text = getText(path)
        return if (text.isBlank()) JsonNull else Json.parseToJsonElement(text)
    }

    private suspend fun getText(path: String): String {
        val token = bearerToken() ?: error("krill-mcp has no PIN-derived bearer token configured")
        val response: HttpResponse = http.get("$baseUrl$path") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        if (!response.status.isSuccess()) {
            val body = runCatching { response.bodyAsText() }.getOrDefault("")
            error("GET $baseUrl$path returned ${response.status}: $body")
        }
        return response.bodyAsText()
    }

    private suspend fun postJson(path: String, body: JsonElement) {
        val token = bearerToken() ?: error("krill-mcp has no PIN-derived bearer token configured")
        val response: HttpResponse = http.post("$baseUrl$path") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        if (!response.status.isSuccess()) {
            val errBody = runCatching { response.bodyAsText() }.getOrDefault("")
            error("POST $baseUrl$path returned ${response.status}: $errBody")
        }
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
                    requestTimeoutMillis = 30_000
                    connectTimeoutMillis = 5_000
                    socketTimeoutMillis = 30_000
                }
                expectSuccess = false
            }
        }
    }
}
