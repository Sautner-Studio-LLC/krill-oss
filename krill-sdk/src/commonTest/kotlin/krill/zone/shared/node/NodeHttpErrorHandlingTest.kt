package krill.zone.shared.node

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import krill.zone.shared.KrillApp
import krill.zone.shared.io.TrustHost
import krill.zone.shared.krillapp.server.ServerMetaData
import krill.zone.shared.krillapp.server.pin.PinMetaData
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression tests for error handling consistency in [NodeHttp]:
 *
 * 1. `deleteNode` was missing the SSL-error → `deleteCert` path that
 *    `postNode` and `invokeNode` already had. Its catch block was a
 *    no-op re-throw that neither logged nor evicted the stale certificate.
 *
 * 2. `chart` had no try-catch at all — a network failure escaped to
 *    callers as an unhandled exception while every other method absorbed it.
 *
 * 3. `readNodes` returned `emptyList()` on HTTP error or exception, making
 *    it impossible for callers to distinguish a network failure from a
 *    genuinely empty server. Now returns `null` on failure.
 */
class NodeHttpErrorHandlingTest {

    // Minimal JSON config — registers the two NodeMetaData subtypes used in these tests.
    // NodeMetaData is an interface (not sealed), so subtypes need explicit module registration.
    private val testJson = Json {
        ignoreUnknownKeys = true
        serializersModule = SerializersModule {
            polymorphic(NodeMetaData::class) {
                subclass(PinMetaData::class)
                subclass(ServerMetaData::class)
            }
        }
    }

    private val serverMeta = ServerMetaData(name = "test.krill.invalid", port = 8443)
    private val hostNode = Node(
        id = "server-1",
        parent = "server-1",
        host = "server-1",
        type = KrillApp.Server,
        meta = serverMeta,
    )
    private val pinNode = Node(
        id = "pin-1",
        parent = "server-1",
        host = "server-1",
        type = KrillApp.Server.Pin,
        meta = PinMetaData(name = "relay"),
    )

    private fun fakeTrust(onDeleteCert: (Node) -> Unit = {}): TrustHost = object : TrustHost {
        override suspend fun fetchPeerCert(url: Url) = false
        override suspend fun deleteCert(node: Node) = onDeleteCert(node)
    }

    // deleteNode sends a Node body — ContentNegotiation with testJson is required.
    private fun clientWithJson(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) { json(testJson) }
    }

    // ── deleteNode ────────────────────────────────────────────────────────────

    @Test
    fun `deleteNode calls deleteCert on SSL exception and rethrows`() = runTest {
        var deleteCertCalled = false
        val engine = MockEngine { _ -> throw Exception("certification validation failed") }
        val nodeHttp = NodeHttp(clientWithJson(engine), fakeTrust { deleteCertCalled = true }) { "token" }

        assertFailsWith<Exception> { nodeHttp.deleteNode(hostNode, pinNode) }
        assertTrue(deleteCertCalled, "deleteCert must be called when an SSL exception is thrown")
    }

    @Test
    fun `deleteNode does not call deleteCert on non-SSL exception`() = runTest {
        var deleteCertCalled = false
        val engine = MockEngine { _ -> throw Exception("connection refused") }
        val nodeHttp = NodeHttp(clientWithJson(engine), fakeTrust { deleteCertCalled = true }) { "token" }

        assertFailsWith<Exception> { nodeHttp.deleteNode(hostNode, pinNode) }
        assertFalse(deleteCertCalled, "deleteCert must not be called for non-SSL exceptions")
    }

    @Test
    fun `deleteNode rethrows on non-SSL exception`() = runTest {
        val engine = MockEngine { _ -> throw Exception("connection refused") }
        val nodeHttp = NodeHttp(clientWithJson(engine), fakeTrust()) { "token" }

        assertFailsWith<Exception>("non-SSL exception must propagate to the caller") {
            nodeHttp.deleteNode(hostNode, pinNode)
        }
    }

    // ── chart ─────────────────────────────────────────────────────────────────

    @Test
    fun `chart returns empty ByteArray on exception instead of propagating`() = runTest {
        val engine = MockEngine { _ -> throw Exception("connection refused") }
        val nodeHttp = NodeHttp(HttpClient(engine), fakeTrust()) { "token" }

        val result = nodeHttp.chart(hostNode, "dp-1", 0L, 1_000L)
        assertEquals(0, result.size, "chart must absorb exceptions and return empty array")
    }

    // ── readNodes ─────────────────────────────────────────────────────────────

    @Test
    fun `readNodes returns null on HTTP error so callers can distinguish failure from empty server`() = runTest {
        val engine = MockEngine { _ ->
            respond("Server Error", HttpStatusCode.InternalServerError)
        }
        val nodeHttp = NodeHttp(clientWithJson(engine), fakeTrust()) { "token" }

        val result = nodeHttp.readNodes(hostNode)
        assertNull(result, "readNodes must return null on HTTP error, not emptyList()")
    }

    @Test
    fun `readNodes returns null on network exception`() = runTest {
        val engine = MockEngine { _ -> throw Exception("connection refused") }
        val nodeHttp = NodeHttp(clientWithJson(engine), fakeTrust()) { "token" }

        val result = nodeHttp.readNodes(hostNode)
        assertNull(result, "readNodes must return null on exception, not emptyList()")
    }

    @Test
    fun `readNodes returns empty list when server responds 200 with no nodes`() = runTest {
        val emptyJson = testJson.encodeToString<List<Node>>(emptyList())
        val engine = MockEngine { _ ->
            respond(emptyJson, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }
        val nodeHttp = NodeHttp(clientWithJson(engine), fakeTrust()) { "token" }

        val result = nodeHttp.readNodes(hostNode)
        assertNotNull(result, "readNodes must return non-null on 200 OK")
        assertEquals(0, result.size, "readNodes must return empty list when server has no nodes")
    }

    // ── clientProvider ────────────────────────────────────────────────────────

    @Test
    fun `clientProvider constructor is invoked on each request so a swapped client is picked up`() = runTest {
        val emptyJson = testJson.encodeToString<List<Node>>(emptyList())
        var callCount = 0
        val engine = MockEngine { _ ->
            respond(emptyJson, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }
        val nodeHttp = NodeHttp(
            clientProvider = { callCount++; clientWithJson(engine) },
            trustHost = fakeTrust(),
            bearerTokenProvider = { "token" },
        )

        nodeHttp.readNodes(hostNode)
        nodeHttp.readNodes(hostNode)

        assertEquals(2, callCount, "clientProvider must be invoked once per request, not cached")
    }

    @Test
    fun `convenience HttpClient constructor wraps instance without breaking behaviour`() = runTest {
        val emptyJson = testJson.encodeToString<List<Node>>(emptyList())
        val engine = MockEngine { _ ->
            respond(emptyJson, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }
        val nodeHttp = NodeHttp(clientWithJson(engine), fakeTrust()) { "token" }

        val result = nodeHttp.readNodes(hostNode)
        assertNotNull(result, "convenience constructor must work identically to provider constructor")
    }

    // ── invokeNode state guard (krill-oss#158) ────────────────────────────────

    @Test
    fun `invokeNode does not make a network call when target is PAUSED`() = runTest {
        var requestCount = 0
        val engine = MockEngine { _ -> requestCount++; respond("", HttpStatusCode.OK) }
        val nodeHttp = NodeHttp(clientWithJson(engine), fakeTrust()) { "token" }

        val pausedNode = pinNode.copy(state = NodeState.PAUSED)
        nodeHttp.invokeNode(hostNode, pausedNode, pausedNode.id())

        assertEquals(0, requestCount, "invokeNode must not send a request when the target is PAUSED")
    }

    @Test
    fun `invokeNode does not make a network call when target is DELETING`() = runTest {
        var requestCount = 0
        val engine = MockEngine { _ -> requestCount++; respond("", HttpStatusCode.OK) }
        val nodeHttp = NodeHttp(clientWithJson(engine), fakeTrust()) { "token" }

        val deletingNode = pinNode.copy(state = NodeState.DELETING)
        nodeHttp.invokeNode(hostNode, deletingNode, deletingNode.id())

        assertEquals(0, requestCount, "invokeNode must not send a request when the target is DELETING")
    }

    @Test
    fun `invokeNode sends a network call when target is NONE`() = runTest {
        var requestCount = 0
        val engine = MockEngine { _ -> requestCount++; respond("", HttpStatusCode.OK) }
        val nodeHttp = NodeHttp(clientWithJson(engine), fakeTrust()) { "token" }

        val activeNode = pinNode.copy(state = NodeState.NONE)
        nodeHttp.invokeNode(hostNode, activeNode, activeNode.id())

        assertEquals(1, requestCount, "invokeNode must send a request when the target is in an invokable state")
    }
}
