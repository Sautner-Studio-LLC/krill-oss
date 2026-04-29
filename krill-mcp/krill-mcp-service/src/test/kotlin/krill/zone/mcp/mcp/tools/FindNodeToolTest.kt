package krill.zone.mcp.mcp.tools

import krill.zone.mcp.auth.PinProvider
import krill.zone.mcp.config.KrillMcpConfig
import krill.zone.mcp.krill.KrillRegistry
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression tests for issue bsautner/krill-oss#26 — `find_node`.
 *
 * The tool ranks nodes across one or more Krill servers against a free-text
 * query so a voice flow can resolve "the Vivarium mister" to a (serverId, nodeId)
 * pair without N MCP round-trips. The scoring + ranking logic lives in pure
 * helper functions so these tests can exercise it without standing up a real
 * Krill server.
 */
class FindNodeToolTest {

    private val tool = FindNodeTool(
        registry = KrillRegistry(
            config = KrillMcpConfig(),
            pin = PinProvider(path = "/dev/null"),
        ),
    )

    @Test
    fun `exact full-query name match scores higher than a partial path match`() {
        val nodes = buildJsonArray {
            add(node(id = "pin-1", parent = "proj-1", typeFqn = TYPE_PIN, name = "Vivarium Mister"))
            add(node(id = "proj-1", parent = "srv-1", typeFqn = TYPE_PROJECT, name = "Vivarium"))
            add(node(id = "srv-1", parent = "srv-1", typeFqn = TYPE_SERVER, name = "pi-krill"))
        }

        val results = tool.rank(
            query = "vivarium mister",
            typeFilter = null,
            byServer = mapOf("pi-krill" to nodes),
            limit = 5,
        )

        assertTrue(results.isNotEmpty(), "expected at least one match")
        val top = results.first()
        assertEquals("pin-1", top.nodeId)
        assertEquals("pi-krill", top.serverId)
        assertEquals("Vivarium Mister", top.displayName)
        assertEquals("Vivarium > Vivarium Mister", top.path)
        assertTrue(top.score >= 0.9, "expected near-perfect score for full match, got ${top.score}")
    }

    @Test
    fun `type filter excludes non-matching types from the result set`() {
        val nodes = buildJsonArray {
            add(node(id = "pin-1", parent = "proj-1", typeFqn = TYPE_PIN, name = "Vivarium Mister"))
            add(node(id = "gate-1", parent = "proj-1", typeFqn = TYPE_LOGIC_GATE, name = "Vivarium Mister Gate"))
            add(node(id = "proj-1", parent = "srv-1", typeFqn = TYPE_PROJECT, name = "Vivarium"))
            add(node(id = "srv-1", parent = "srv-1", typeFqn = TYPE_SERVER, name = "pi-krill"))
        }

        val results = tool.rank(
            query = "mister",
            typeFilter = "Pin",
            byServer = mapOf("pi-krill" to nodes),
            limit = 5,
        )

        assertEquals(1, results.size)
        assertEquals("pin-1", results.single().nodeId)
    }

    @Test
    fun `swarm scope merges and ranks candidates from every server`() {
        val server1Nodes = buildJsonArray {
            add(node(id = "pin-A", parent = "srv-1", typeFqn = TYPE_PIN, name = "Living Room Lamp"))
            add(node(id = "srv-1", parent = "srv-1", typeFqn = TYPE_SERVER, name = "pi-krill-05"))
        }
        val server2Nodes = buildJsonArray {
            add(node(id = "pin-B", parent = "proj-2", typeFqn = TYPE_PIN, name = "Vivarium Mister"))
            add(node(id = "proj-2", parent = "srv-2", typeFqn = TYPE_PROJECT, name = "Vivarium"))
            add(node(id = "srv-2", parent = "srv-2", typeFqn = TYPE_SERVER, name = "pi-krill"))
        }

        val results = tool.rank(
            query = "vivarium mister",
            typeFilter = null,
            byServer = mapOf("pi-krill-05" to server1Nodes, "pi-krill" to server2Nodes),
            limit = 5,
        )

        // The Vivarium Mister on pi-krill must outrank the Living Room Lamp on
        // pi-krill-05 — that's exactly the failure mode the issue calls out
        // (workaround picked the wrong server because it only saw one).
        assertTrue(results.isNotEmpty())
        val top = results.first()
        assertEquals("pin-B", top.nodeId)
        assertEquals("pi-krill", top.serverId)
    }

    @Test
    fun `limit caps the number of returned candidates`() {
        val nodes = buildJsonArray {
            add(node(id = "pin-1", parent = "srv-1", typeFqn = TYPE_PIN, name = "Mister A"))
            add(node(id = "pin-2", parent = "srv-1", typeFqn = TYPE_PIN, name = "Mister B"))
            add(node(id = "pin-3", parent = "srv-1", typeFqn = TYPE_PIN, name = "Mister C"))
            add(node(id = "srv-1", parent = "srv-1", typeFqn = TYPE_SERVER, name = "pi-krill"))
        }

        val results = tool.rank(
            query = "mister",
            typeFilter = null,
            byServer = mapOf("pi-krill" to nodes),
            limit = 2,
        )

        assertEquals(2, results.size)
    }

    @Test
    fun `empty query returns no candidates`() {
        val nodes = buildJsonArray {
            add(node(id = "pin-1", parent = "srv-1", typeFqn = TYPE_PIN, name = "Vivarium Mister"))
            add(node(id = "srv-1", parent = "srv-1", typeFqn = TYPE_SERVER, name = "pi-krill"))
        }

        val results = tool.rank(
            query = "   ",
            typeFilter = null,
            byServer = mapOf("pi-krill" to nodes),
            limit = 5,
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun `non-matching query returns empty list (not an error)`() {
        val nodes = buildJsonArray {
            add(node(id = "pin-1", parent = "srv-1", typeFqn = TYPE_PIN, name = "Vivarium Mister"))
            add(node(id = "srv-1", parent = "srv-1", typeFqn = TYPE_SERVER, name = "pi-krill"))
        }

        val results = tool.rank(
            query = "xyzzy",
            typeFilter = null,
            byServer = mapOf("pi-krill" to nodes),
            limit = 5,
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun `path includes parent project but excludes the server node`() {
        val nodes = buildJsonArray {
            add(node(id = "pin-1", parent = "proj-1", typeFqn = TYPE_PIN, name = "Mister"))
            add(node(id = "proj-1", parent = "srv-1", typeFqn = TYPE_PROJECT, name = "Vivarium"))
            add(node(id = "srv-1", parent = "srv-1", typeFqn = TYPE_SERVER, name = "pi-krill"))
        }

        val results = tool.rank(
            query = "mister",
            typeFilter = null,
            byServer = mapOf("pi-krill" to nodes),
            limit = 5,
        )

        val top = results.first()
        // Parent path keeps the project but drops the server (the serverId is
        // already a separate field on the response).
        assertEquals("Vivarium > Mister", top.path)
    }

    @Test
    fun `path-only query still scores a match (recall bias)`() {
        val nodes = buildJsonArray {
            // Pin's own name has no overlap with "vivarium" — only the parent does.
            add(node(id = "pin-1", parent = "proj-1", typeFqn = TYPE_PIN, name = "Mister"))
            add(node(id = "proj-1", parent = "srv-1", typeFqn = TYPE_PROJECT, name = "Vivarium"))
            add(node(id = "srv-1", parent = "srv-1", typeFqn = TYPE_SERVER, name = "pi-krill"))
        }

        val results = tool.rank(
            query = "vivarium",
            typeFilter = null,
            byServer = mapOf("pi-krill" to nodes),
            limit = 5,
        )

        assertTrue(results.isNotEmpty(), "path-only token match should still surface the candidate")
        // The Project itself is also a match (its own name is "Vivarium") and
        // should be the higher-scored one — but the Pin must still appear.
        val pin = results.firstOrNull { it.nodeId == "pin-1" }
        assertNotNull(pin)
        val proj = results.first { it.nodeId == "proj-1" }
        assertTrue(proj.score >= pin.score)
    }

    @Test
    fun `type filter accepts short name without the KrillApp prefix`() {
        val nodes = buildJsonArray {
            add(node(id = "pin-1", parent = "srv-1", typeFqn = TYPE_PIN, name = "Mister"))
            add(node(id = "gate-1", parent = "srv-1", typeFqn = TYPE_LOGIC_GATE, name = "Mister Gate"))
            add(node(id = "srv-1", parent = "srv-1", typeFqn = TYPE_SERVER, name = "pi-krill"))
        }

        val results = tool.rank(
            query = "mister",
            typeFilter = "LogicGate",
            byServer = mapOf("pi-krill" to nodes),
            limit = 5,
        )

        assertEquals(1, results.size)
        assertEquals("gate-1", results.single().nodeId)
    }

    @Test
    fun `null type filter does not exclude any candidate by type`() {
        val nodes = buildJsonArray {
            add(node(id = "pin-1", parent = "srv-1", typeFqn = TYPE_PIN, name = "Mister"))
            add(node(id = "gate-1", parent = "srv-1", typeFqn = TYPE_LOGIC_GATE, name = "Mister Gate"))
            add(node(id = "srv-1", parent = "srv-1", typeFqn = TYPE_SERVER, name = "pi-krill"))
        }

        val results = tool.rank(
            query = "mister",
            typeFilter = null,
            byServer = mapOf("pi-krill" to nodes),
            limit = 5,
        )

        assertEquals(2, results.size)
    }

    @Test
    fun `score is normalized into the unit interval`() {
        val nodes = buildJsonArray {
            add(node(id = "pin-1", parent = "proj-1", typeFqn = TYPE_PIN, name = "Vivarium Mister"))
            add(node(id = "proj-1", parent = "srv-1", typeFqn = TYPE_PROJECT, name = "Vivarium"))
            add(node(id = "srv-1", parent = "srv-1", typeFqn = TYPE_SERVER, name = "pi-krill"))
        }

        val results = tool.rank(
            query = "vivarium mister",
            typeFilter = null,
            byServer = mapOf("pi-krill" to nodes),
            limit = 5,
        )

        results.forEach { c ->
            assertTrue(c.score in 0.0..1.0, "score ${c.score} must be in [0, 1]")
        }
    }

    @Test
    fun `nodes without a meta name are still scored against type and path`() {
        val nodes = buildJsonArray {
            // Pin has no meta.name (Krill app sometimes leaves it blank).
            // We want it ranked off path + type instead of being silently dropped
            // — the issue specifically calls out that meta.name lookup is the
            // current ceiling.
            add(node(id = "pin-1", parent = "proj-1", typeFqn = TYPE_PIN, name = ""))
            add(node(id = "proj-1", parent = "srv-1", typeFqn = TYPE_PROJECT, name = "Vivarium Mister"))
            add(node(id = "srv-1", parent = "srv-1", typeFqn = TYPE_SERVER, name = "pi-krill"))
        }

        val results = tool.rank(
            query = "vivarium mister",
            typeFilter = "Pin",
            byServer = mapOf("pi-krill" to nodes),
            limit = 5,
        )

        // pin-1's own name is empty, but its parent path is "Vivarium Mister"
        // — the find_node should still surface it. (Without the path scoring
        //  it would return zero candidates and the agent would have to keep
        //  hunting by hand.)
        assertEquals(1, results.size)
        assertEquals("pin-1", results.single().nodeId)
    }

    @Test
    fun `findFirstServer returns null when registry is empty`() {
        // sanity guard so we don't accidentally start treating empty registry
        // as a successful zero-result lookup
        val results = tool.rank(
            query = "vivarium",
            typeFilter = null,
            byServer = emptyMap(),
            limit = 5,
        )
        assertTrue(results.isEmpty())
    }

    private fun node(
        id: String,
        parent: String,
        typeFqn: String,
        name: String,
    ) = buildJsonObject {
        put("id", JsonPrimitive(id))
        put("parent", JsonPrimitive(parent))
        put(
            "type",
            buildJsonObject { put("type", JsonPrimitive(typeFqn)) },
        )
        put(
            "meta",
            buildJsonObject { put("name", JsonPrimitive(name)) },
        )
    }

    private companion object {
        const val TYPE_PIN = "krill.zone.shared.KrillApp.Server.Pin"
        const val TYPE_PROJECT = "krill.zone.shared.KrillApp.Project"
        const val TYPE_LOGIC_GATE = "krill.zone.shared.KrillApp.Executor.LogicGate"
        const val TYPE_SERVER = "krill.zone.shared.KrillApp.Server"
    }
}
