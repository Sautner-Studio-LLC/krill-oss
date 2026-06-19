package krill.zone.mcp.mcp.tools

import krill.zone.mcp.auth.PinProvider
import krill.zone.mcp.config.KrillMcpConfig
import krill.zone.mcp.krill.KrillNodeTypes
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
import kotlin.test.assertTrue

/**
 * Unit tests for [UpdateNodeTool] — pins the schema contract and the
 * pre-HTTP validation paths that fire before any network call.
 *
 * The full execute() path requires a live registry + HTTP, so tests here
 * cover schema introspection and the rejection paths (missing id, missing
 * meta, empty meta, type-key guard).
 */
class UpdateNodeToolTest {

    private val tool = UpdateNodeTool(
        registry = KrillRegistry(
            config = KrillMcpConfig(),
            pin = PinProvider(path = "/dev/null"),
        ),
    )

    // ── Schema ───────────────────────────────────────────────────────────────

    @Test
    fun `inputSchema declares id and meta as required`() {
        val required = tool.inputSchema["required"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("id", "meta"), required)
    }

    @Test
    fun `inputSchema exposes server id and meta properties`() {
        val props = tool.inputSchema["properties"] as JsonObject
        assertTrue("server" in props)
        assertTrue("id" in props)
        assertTrue("meta" in props)
    }

    // ── Missing-argument guards ───────────────────────────────────────────────

    @Test
    fun `missing id is rejected before any HTTP call`() {
        val args = buildJsonObject {
            put("meta", buildJsonObject { put("expression", "*/5 * * * * *") })
        }
        val ex = assertFailsWith<IllegalStateException> {
            runBlocking { tool.execute(args) }
        }
        assertTrue("id" in (ex.message ?: ""), "Error should mention the missing field: ${ex.message}")
    }

    @Test
    fun `missing meta is rejected before any HTTP call`() {
        val args = buildJsonObject { put("id", "some-uuid") }
        val ex = assertFailsWith<IllegalStateException> {
            runBlocking { tool.execute(args) }
        }
        assertTrue("meta" in (ex.message ?: ""), "Error should mention the missing field: ${ex.message}")
    }

    @Test
    fun `empty meta object is rejected before any HTTP call`() {
        val args = buildJsonObject {
            put("id", "some-uuid")
            put("meta", buildJsonObject { })
        }
        val ex = assertFailsWith<IllegalStateException> {
            runBlocking { tool.execute(args) }
        }
        val msg = ex.message ?: ""
        assertTrue("empty" in msg || "meta" in msg, "Error should mention empty meta: $msg")
    }

    // ── KrillNodeTypes: CronTimer and Calculation have relevant meta fields ──

    @Test
    fun `CronTimer type has expression in defaultMeta`() {
        val spec = KrillNodeTypes.resolve("KrillApp.Trigger.CronTimer")
            ?: error("KrillApp.Trigger.CronTimer missing from KrillNodeTypes registry")
        assertTrue(
            "expression" in spec.defaultMeta,
            "CronTimer defaultMeta must expose expression so update_node can set it",
        )
    }

    @Test
    fun `Calculation type has formula in defaultMeta`() {
        val spec = KrillNodeTypes.resolve("KrillApp.Executor.Calculation")
            ?: error("KrillApp.Executor.Calculation missing from KrillNodeTypes registry")
        assertTrue(
            "formula" in spec.defaultMeta,
            "Calculation defaultMeta must expose formula so update_node can set it",
        )
    }
}
