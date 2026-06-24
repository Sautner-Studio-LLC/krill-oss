package krill.zone.shared.node

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import krill.zone.shared.KrillApp
import krill.zone.shared.io.TrustHost
import krill.zone.shared.krillapp.server.ServerMetaData
import krill.zone.shared.krillapp.server.pin.PinMetaData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Regression tests for the state-normalization guard in [NodeHttp].
 *
 * Both [NodeHttp.readNodes] and [NodeHttp.readHealth] coerce transient
 * server states (EXECUTED → NONE) on the client side. The guard introduced
 * in fix/154 avoids the unconditional [List.map] / [Node.copy] allocation in
 * the common case (no EXECUTED nodes / health already NONE) while preserving
 * the normalization semantics when EXECUTED nodes are present.
 */
class NodeHttpStateNormalizationTest {

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
        id = "server-1", parent = "server-1", host = "server-1",
        type = KrillApp.Server, meta = serverMeta,
    )

    private fun fakeTrust(): TrustHost = object : TrustHost {
        override suspend fun fetchPeerCert(url: Url) = false
        override suspend fun deleteCert(node: Node) = Unit
    }

    private fun clientWithJson(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) { json(testJson) }
    }

    private fun nodeOf(id: String, state: NodeState) = Node(
        id = id, parent = "server-1", host = "server-1",
        type = KrillApp.Server.Pin, meta = PinMetaData(name = id), state = state,
    )

    private fun respondJson(body: String) = MockEngine { _ ->
        respond(body, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
    }

    // ── readNodes: no EXECUTED nodes ─────────────────────────────────────────

    @Test
    fun `readNodes preserves state when no EXECUTED nodes present`() = runTest {
        val nodes = listOf(nodeOf("n1", NodeState.NONE), nodeOf("n2", NodeState.ERROR))
        val engine = respondJson(testJson.encodeToString(nodes))
        val nodeHttp = NodeHttp(clientWithJson(engine), fakeTrust()) { "token" }

        val result = nodeHttp.readNodes(hostNode)
        assertNotNull(result)
        assertEquals(NodeState.NONE, result[0].state)
        assertEquals(NodeState.ERROR, result[1].state)
    }

    // ── readNodes: EXECUTED nodes normalized ─────────────────────────────────

    @Test
    fun `readNodes normalizes EXECUTED to NONE`() = runTest {
        val nodes = listOf(nodeOf("n1", NodeState.EXECUTED), nodeOf("n2", NodeState.NONE))
        val engine = respondJson(testJson.encodeToString(nodes))
        val nodeHttp = NodeHttp(clientWithJson(engine), fakeTrust()) { "token" }

        val result = nodeHttp.readNodes(hostNode)
        assertNotNull(result)
        assertEquals(NodeState.NONE, result[0].state, "EXECUTED must be normalized to NONE")
        assertEquals(NodeState.NONE, result[1].state, "NONE must remain NONE")
    }

    @Test
    fun `readNodes normalizes all EXECUTED nodes in a mixed list`() = runTest {
        val nodes = listOf(
            nodeOf("n1", NodeState.EXECUTED),
            nodeOf("n2", NodeState.ERROR),
            nodeOf("n3", NodeState.EXECUTED),
        )
        val engine = respondJson(testJson.encodeToString(nodes))
        val nodeHttp = NodeHttp(clientWithJson(engine), fakeTrust()) { "token" }

        val result = nodeHttp.readNodes(hostNode)
        assertNotNull(result)
        assertEquals(NodeState.NONE, result[0].state)
        assertEquals(NodeState.ERROR, result[1].state)
        assertEquals(NodeState.NONE, result[2].state)
    }

    @Test
    fun `readNodes normalizes list where every node is EXECUTED`() = runTest {
        val nodes = listOf(nodeOf("n1", NodeState.EXECUTED), nodeOf("n2", NodeState.EXECUTED))
        val engine = respondJson(testJson.encodeToString(nodes))
        val nodeHttp = NodeHttp(clientWithJson(engine), fakeTrust()) { "token" }

        val result = nodeHttp.readNodes(hostNode)
        assertNotNull(result)
        assertEquals(2, result.size)
        result.forEach { assertEquals(NodeState.NONE, it.state) }
    }

    // ── readHealth: state always forced to NONE ───────────────────────────────

    @Test
    fun `readHealth returns NONE state when server sends NONE`() = runTest {
        val serverNode = Node(
            id = "server-1", parent = "server-1", host = "server-1",
            type = KrillApp.Server, meta = serverMeta, state = NodeState.NONE,
        )
        val engine = respondJson(testJson.encodeToString(serverNode))
        val nodeHttp = NodeHttp(clientWithJson(engine), fakeTrust()) { "token" }

        val result = nodeHttp.readHealth(hostNode)
        assertNotNull(result)
        assertEquals(NodeState.NONE, result.state)
    }

    @Test
    fun `readHealth forces NONE when server sends non-NONE state`() = runTest {
        val serverNode = Node(
            id = "server-1", parent = "server-1", host = "server-1",
            type = KrillApp.Server, meta = serverMeta, state = NodeState.EXECUTED,
        )
        val engine = respondJson(testJson.encodeToString(serverNode))
        val nodeHttp = NodeHttp(clientWithJson(engine), fakeTrust()) { "token" }

        val result = nodeHttp.readHealth(hostNode)
        assertNotNull(result)
        assertEquals(NodeState.NONE, result.state, "readHealth must force state to NONE regardless of server value")
    }

    @Test
    fun `readHealth forces NONE when server sends ERROR state`() = runTest {
        val serverNode = Node(
            id = "server-1", parent = "server-1", host = "server-1",
            type = KrillApp.Server, meta = serverMeta, state = NodeState.ERROR,
        )
        val engine = respondJson(testJson.encodeToString(serverNode))
        val nodeHttp = NodeHttp(clientWithJson(engine), fakeTrust()) { "token" }

        val result = nodeHttp.readHealth(hostNode)
        assertNotNull(result)
        assertEquals(NodeState.NONE, result.state, "readHealth must force state to NONE regardless of server value")
    }
}
