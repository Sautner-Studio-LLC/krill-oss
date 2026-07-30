package krill.zone.mcp.krill

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Regression coverage for the create->invoke race Ghost caught on krill-oss#224:
 * `invokeNode` is called immediately after `postNode` for a brand-new node id,
 * and the krill server can 404 "Node not found" for a short window before that
 * id is indexed. `invokeNode` must retry on 404 (and only 404) instead of
 * surfacing the race as an error to the swarm tools. Uses MockEngine since
 * this package has no other harness for [KrillClient] — `runTest` gives the
 * production `delay()` backoff virtual time so the test runs instantly.
 */
class KrillClientTest {

    private fun client(engine: MockEngine) = KrillClient(
        serverId = "server-1",
        baseUrl = "https://server-1.local:8442",
        publicBaseUrl = "https://server-1.local:8442",
        bearerToken = { "test-token" },
        httpClient = HttpClient(engine) { expectSuccess = false },
    )

    private val by = buildJsonObject { put("nodeId", "self-id"); put("hostId", "server-1") }

    @Test
    fun `invokeNode retries a 404 and succeeds once the node is indexed`() = runTest {
        var calls = 0
        val engine = MockEngine { _ ->
            calls++
            if (calls < 3) respond("Node not found: abc", HttpStatusCode.NotFound) else respond("", HttpStatusCode.Accepted)
        }

        client(engine).invokeNode("abc", by)

        assertEquals(3, calls)
    }

    @Test
    fun `invokeNode gives up after exhausting retries on a persistent 404`() = runTest {
        var calls = 0
        val engine = MockEngine { _ ->
            calls++
            respond("Node not found: abc", HttpStatusCode.NotFound)
        }

        val ex = assertFailsWith<IllegalStateException> {
            client(engine).invokeNode("abc", by)
        }

        assertEquals(5, calls)
        assertTrue("404" in (ex.message ?: ""), "message: ${ex.message}")
    }

    @Test
    fun `invokeNode does not retry a non-404 failure`() = runTest {
        var calls = 0
        val engine = MockEngine { _ ->
            calls++
            respond("boom", HttpStatusCode.InternalServerError)
        }

        assertFailsWith<IllegalStateException> {
            client(engine).invokeNode("abc", by)
        }

        assertEquals(1, calls)
    }

    @Test
    fun `postNode does not retry a 404 — only invokeNode opts in`() = runTest {
        var calls = 0
        val engine = MockEngine { _ ->
            calls++
            respond("Node not found", HttpStatusCode.NotFound)
        }

        assertFailsWith<IllegalStateException> {
            client(engine).postNode(buildJsonObject { put("id", "abc") })
        }

        assertEquals(1, calls)
    }
}
