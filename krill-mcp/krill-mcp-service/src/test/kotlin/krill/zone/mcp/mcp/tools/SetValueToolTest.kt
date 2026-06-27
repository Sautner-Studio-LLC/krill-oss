package krill.zone.mcp.mcp.tools

import krill.zone.mcp.auth.PinProvider
import krill.zone.mcp.config.KrillMcpConfig
import krill.zone.mcp.krill.KrillRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [SetValueTool] — pins the schema contract and the pre-HTTP
 * validation paths (missing target, missing value) that fire before any network
 * call. Regression for krill-oss#174: the demo orchestrator had no `set_value`
 * MCP tool and fell back to `set_node_wiring` (wrong tool, 404 result).
 */
class SetValueToolTest {

    private val tool = SetValueTool(
        registry = KrillRegistry(
            config = KrillMcpConfig(),
            pin = PinProvider(path = "/dev/null"),
        ),
    )

    // ── Schema ──────────────────────────────────────────────────────────────

    @Test
    fun `inputSchema declares target and value as required`() {
        val required = tool.inputSchema["required"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(setOf("target", "value"), required.toSet())
    }

    @Test
    fun `inputSchema exposes target, value, server, timestamp`() {
        val props = tool.inputSchema["properties"] as JsonObject
        assertTrue("target" in props)
        assertTrue("value" in props)
        assertTrue("server" in props)
        assertTrue("timestamp" in props)
        assertFalse("id" in props, "set_value uses 'target' not 'id' to support name-based resolution")
        assertFalse("dataPointId" in props, "legacy field must not appear")
    }

    // ── Pre-HTTP argument validation ─────────────────────────────────────────

    @Test
    fun `missing target is rejected before any HTTP call`() {
        val args = buildJsonObject { put("value", 10) }
        val ex = assertFailsWith<IllegalStateException> {
            runBlocking { tool.execute(args) }
        }
        assertTrue("target" in (ex.message ?: ""), "Error must cite the missing field")
    }

    @Test
    fun `missing value is rejected before any HTTP call`() {
        val args = buildJsonObject { put("target", "Series") }
        val ex = assertFailsWith<IllegalStateException> {
            runBlocking { tool.execute(args) }
        }
        assertTrue("value" in (ex.message ?: ""), "Error must cite the missing field")
    }
}
