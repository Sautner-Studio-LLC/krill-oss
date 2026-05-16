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
 * Unit tests for [SetNodeWiringTool] — pins the schema contract and the
 * pre-HTTP validation paths (invalid nodeAction, invalid executionSource,
 * no-field-provided).
 *
 * The full execute() path requires a live registry + HTTP, so these tests
 * cover schema introspection and the rejection paths that fire before any
 * network call.
 */
class SetNodeWiringToolTest {

    private val tool = SetNodeWiringTool(
        registry = KrillRegistry(
            config = KrillMcpConfig(),
            pin = PinProvider(path = "/dev/null"),
        ),
    )

    // ── Schema ──────────────────────────────────────────────────────────────

    @Test
    fun `inputSchema declares only id as required`() {
        val required = tool.inputSchema["required"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("id"), required)
    }

    @Test
    fun `inputSchema exposes all four wiring fields`() {
        val props = tool.inputSchema["properties"] as JsonObject
        assertTrue("server" in props)
        assertTrue("id" in props)
        assertTrue("sources" in props)
        assertTrue("targets" in props)
        assertTrue("executionSource" in props)
        assertTrue("nodeAction" in props)
    }

    // ── nodeAction validation ────────────────────────────────────────────────

    @Test
    fun `EXECUTE and RESET are valid nodeAction values`() {
        assertTrue("EXECUTE" in SetNodeWiringTool.VALID_ACTIONS)
        assertTrue("RESET" in SetNodeWiringTool.VALID_ACTIONS)
    }

    @Test
    fun `invalid nodeAction is rejected before any HTTP call`() {
        val args = buildJsonObject {
            put("id", "some-uuid")
            put("nodeAction", "TOGGLE")
        }
        val ex = assertFailsWith<IllegalStateException> {
            runBlocking { tool.execute(args) }
        }
        assertTrue("TOGGLE" in (ex.message ?: ""), "Error should cite the bad value")
        assertTrue("EXECUTE" in (ex.message ?: "") || "RESET" in (ex.message ?: ""), "Error should name valid options")
    }

    // ── executionSource validation ───────────────────────────────────────────

    @Test
    fun `all three ExecutionSource values are accepted`() {
        assertTrue("SOURCE_VALUE_MODIFIED" in SetNodeWiringTool.VALID_EXECUTION_SOURCES)
        assertTrue("PARENT_EXECUTE_SUCCESS" in SetNodeWiringTool.VALID_EXECUTION_SOURCES)
        assertTrue("ON_CLICK" in SetNodeWiringTool.VALID_EXECUTION_SOURCES)
    }

    @Test
    fun `invalid executionSource is rejected before any HTTP call`() {
        val args = buildJsonObject {
            put("id", "some-uuid")
            put("executionSource", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("ON_HOVER")) })
        }
        val ex = assertFailsWith<IllegalStateException> {
            runBlocking { tool.execute(args) }
        }
        assertTrue("ON_HOVER" in (ex.message ?: ""), "Error should cite the bad value")
    }

    // ── No-fields guard ──────────────────────────────────────────────────────

    @Test
    fun `call with no wiring fields is rejected before any HTTP call`() {
        val args = buildJsonObject { put("id", "some-uuid") }
        val ex = assertFailsWith<IllegalStateException> {
            runBlocking { tool.execute(args) }
        }
        val msg = ex.message ?: ""
        assertTrue(
            "sources" in msg || "targets" in msg || "executionSource" in msg || "nodeAction" in msg,
            "Error should mention at least one accepted field: $msg",
        )
    }

    // ── KrillNodeTypes: sources/targets/executionSource/nodeAction now universal ──

    @Test
    fun `DataPoint carries sources targets executionSource and nodeAction after unify-source-verb-wiring`() {
        val spec = KrillNodeTypes.resolve("KrillApp.DataPoint")
            ?: error("KrillApp.DataPoint missing from KrillNodeTypes registry")
        assertTrue("sources" in spec.defaultMeta, "DataPoint.defaultMeta must expose sources")
        assertTrue("targets" in spec.defaultMeta, "DataPoint.defaultMeta must expose targets")
        assertTrue("executionSource" in spec.defaultMeta, "DataPoint.defaultMeta must expose executionSource")
        assertTrue("nodeAction" in spec.defaultMeta, "DataPoint.defaultMeta must expose nodeAction")
        assertEquals("EXECUTE", spec.defaultMeta["nodeAction"]?.jsonPrimitive?.content)
    }

    @Test
    fun `Server_Pin carries sources targets executionSource and nodeAction after unify-source-verb-wiring`() {
        val spec = KrillNodeTypes.resolve("KrillApp.Server.Pin")
            ?: error("KrillApp.Server.Pin missing from KrillNodeTypes registry")
        assertTrue("sources" in spec.defaultMeta, "Server.Pin.defaultMeta must expose sources")
        assertTrue("targets" in spec.defaultMeta, "Server.Pin.defaultMeta must expose targets")
        assertTrue("executionSource" in spec.defaultMeta, "Server.Pin.defaultMeta must expose executionSource")
        assertTrue("nodeAction" in spec.defaultMeta, "Server.Pin.defaultMeta must expose nodeAction")
        assertEquals("EXECUTE", spec.defaultMeta["nodeAction"]?.jsonPrimitive?.content)
    }
}
