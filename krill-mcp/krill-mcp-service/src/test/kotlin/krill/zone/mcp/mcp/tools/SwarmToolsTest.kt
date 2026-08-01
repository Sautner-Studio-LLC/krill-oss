package krill.zone.mcp.mcp.tools

import krill.zone.mcp.auth.PinProvider
import krill.zone.mcp.config.KrillMcpConfig
import krill.zone.mcp.krill.KrillRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit tests for the swarm-llm-workloads MCP tools (krill-oss#223): schema
 * contracts and the pre-HTTP validation paths that must fire before any
 * network call reaches a Krill server. These exercise the same "validate
 * before resolve()" seam every other tool in this package uses (see
 * [SetValueToolTest], [CreateNodeToolTest]) — the HTTP-boundary behavior of
 * [krill.zone.mcp.krill.KrillClient] itself (e.g. the invoke-after-create
 * retry) is covered separately in `KrillClientTest` via `MockEngine`.
 */
class SwarmToolsTest {

    private val emptyRegistry = KrillRegistry(
        config = KrillMcpConfig(),
        pin = PinProvider(path = "/dev/null"),
    )

    private val submitWork = SubmitSwarmWorkTool(emptyRegistry)
    private val submitBatch = SubmitSwarmBatchTool(emptyRegistry)
    private val getFleet = GetSwarmFleetTool(emptyRegistry)
    private val getStatus = GetSwarmWorkStatusTool(emptyRegistry)

    // ── submit_swarm_work — schema ────────────────────────────────────────

    @Test
    fun `submit_swarm_work declares only prompt as required`() {
        val required = submitWork.inputSchema["required"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("prompt"), required)
    }

    @Test
    fun `submit_swarm_work exposes every documented optional field`() {
        val props = submitWork.inputSchema["properties"] as JsonObject
        for (field in listOf(
            "server", "prompt", "images", "fileRefs", "requiredModel",
            "minVramClass", "maxCostScore", "deadlineAt", "maxPayloadBytes",
        )) {
            assertTrue(field in props, "missing property: $field")
        }
    }

    // ── submit_swarm_work — pre-HTTP validation ───────────────────────────

    @Test
    fun `missing prompt is rejected before any HTTP call`() {
        val ex = assertFailsWith<IllegalStateException> {
            runBlocking { submitWork.execute(buildJsonObject {}) }
        }
        assertTrue("prompt" in (ex.message ?: ""))
    }

    @Test
    fun `fileRefs entry missing hash is rejected before any HTTP call`() {
        val args = buildJsonObject {
            put("prompt", "hello")
            put(
                "fileRefs",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("sizeBytes", 10)
                            put("mime", "text/plain")
                            put("host", "server-1")
                        },
                    )
                },
            )
        }
        val ex = assertFailsWith<IllegalStateException> {
            runBlocking { submitWork.execute(args) }
        }
        assertTrue("hash" in (ex.message ?: ""))
    }

    @Test
    fun `oversized payload is rejected before any HTTP call`() {
        val args = buildJsonObject {
            put("prompt", "x".repeat(100))
            put("maxPayloadBytes", 10)
        }
        val ex = assertFailsWith<IllegalStateException> {
            runBlocking { submitWork.execute(args) }
        }
        assertTrue("maxPayloadBytes" in (ex.message ?: ""))
    }

    @Test
    fun `fileRefs sizeBytes count toward the payload estimate`() {
        val args = buildJsonObject {
            put("prompt", "hi")
            put("maxPayloadBytes", 100)
            put(
                "fileRefs",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("hash", "a".repeat(64))
                            put("sizeBytes", 1_000)
                            put("mime", "image/png")
                            put("host", "server-1")
                        },
                    )
                },
            )
        }
        val ex = assertFailsWith<IllegalStateException> {
            runBlocking { submitWork.execute(args) }
        }
        assertTrue("1002" in (ex.message ?: ""), "message: ${ex.message}")
    }

    // ── submit_swarm_batch — schema ───────────────────────────────────────

    @Test
    fun `submit_swarm_batch declares only items as required`() {
        val required = submitBatch.inputSchema["required"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("items"), required)
    }

    // ── submit_swarm_batch — pre-HTTP validation ──────────────────────────

    @Test
    fun `missing items is rejected before any HTTP call`() {
        val ex = assertFailsWith<IllegalStateException> {
            runBlocking { submitBatch.execute(buildJsonObject {}) }
        }
        assertTrue("items" in (ex.message ?: ""))
    }

    @Test
    fun `empty items array is rejected before any HTTP call`() {
        val args = buildJsonObject { put("items", buildJsonArray {}) }
        val ex = assertFailsWith<IllegalStateException> {
            runBlocking { submitBatch.execute(args) }
        }
        assertTrue("items" in (ex.message ?: ""))
    }

    @Test
    fun `item missing prompt is rejected before any HTTP call`() {
        val args = buildJsonObject {
            put(
                "items",
                buildJsonArray { add(buildJsonObject { put("images", buildJsonArray {}) }) },
            )
        }
        val ex = assertFailsWith<IllegalStateException> {
            runBlocking { submitBatch.execute(args) }
        }
        assertTrue("items[0].prompt" in (ex.message ?: ""), "message: ${ex.message}")
    }

    @Test
    fun `items exceeding maxChildren is rejected before any HTTP call, never truncated`() {
        val args = buildJsonObject {
            put(
                "items",
                buildJsonArray {
                    repeat(3) { add(buildJsonObject { put("prompt", "item") }) }
                },
            )
            put("maxChildren", 2)
        }
        val ex = assertFailsWith<IllegalStateException> {
            runBlocking { submitBatch.execute(args) }
        }
        assertTrue("maxChildren" in (ex.message ?: ""))
    }

    // ── get_swarm_fleet ────────────────────────────────────────────────────

    @Test
    fun `get_swarm_fleet takes no required arguments`() {
        assertEquals(null, getFleet.inputSchema["required"])
    }

    @Test
    fun `get_swarm_fleet against an empty registry returns an empty fleet, not an error`() {
        val result = runBlocking { getFleet.execute(buildJsonObject {}) } as JsonObject
        assertEquals(0, result["count"]?.jsonPrimitive?.content?.toInt())
        assertEquals(0, result["fleet"]!!.jsonArray.size)
    }

    // ── get_swarm_work_status — schema + pre-HTTP validation ──────────────

    @Test
    fun `get_swarm_work_status declares nodeId as required`() {
        val required = getStatus.inputSchema["required"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("nodeId"), required)
    }

    @Test
    fun `missing nodeId is rejected before any HTTP call`() {
        val ex = assertFailsWith<IllegalStateException> {
            runBlocking { getStatus.execute(buildJsonObject {}) }
        }
        assertTrue("nodeId" in (ex.message ?: ""))
    }
}
