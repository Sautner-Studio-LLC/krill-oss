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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [SetNodeWiringTool] — pins the schema contract and the
 * pre-HTTP validation paths (invalid nodeAction, invalid invocationTriggers,
 * legacy-field rejection, no-field-provided).
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
    fun `inputSchema exposes the observer wiring fields and no legacy fields`() {
        val props = tool.inputSchema["properties"] as JsonObject
        assertTrue("server" in props)
        assertTrue("id" in props)
        assertTrue("sources" in props)
        assertTrue("inputs" in props)
        assertTrue("invocationTriggers" in props)
        assertTrue("nodeAction" in props)
        assertFalse("targets" in props, "targets was removed by unify-source-verb-wiring")
        assertFalse("executionSource" in props, "executionSource was renamed to invocationTriggers")
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

    // ── invocationTriggers validation ────────────────────────────────────────

    @Test
    fun `both InvocationTrigger values are accepted`() {
        assertTrue("SOURCE_INVOKED" in SetNodeWiringTool.VALID_INVOCATION_TRIGGERS)
        assertTrue("ON_CLICK" in SetNodeWiringTool.VALID_INVOCATION_TRIGGERS)
        assertEquals(2, SetNodeWiringTool.VALID_INVOCATION_TRIGGERS.size)
    }

    @Test
    fun `invalid invocationTriggers value is rejected before any HTTP call`() {
        val args = buildJsonObject {
            put("id", "some-uuid")
            put("invocationTriggers", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("ON_HOVER")) })
        }
        val ex = assertFailsWith<IllegalStateException> {
            runBlocking { tool.execute(args) }
        }
        assertTrue("ON_HOVER" in (ex.message ?: ""), "Error should cite the bad value")
    }

    // ── Legacy-field guards ──────────────────────────────────────────────────

    @Test
    fun `legacy targets argument is rejected with an observer-wiring hint`() {
        val args = buildJsonObject {
            put("id", "some-uuid")
            put("targets", kotlinx.serialization.json.buildJsonArray { })
        }
        val ex = assertFailsWith<IllegalStateException> {
            runBlocking { tool.execute(args) }
        }
        val msg = ex.message ?: ""
        assertTrue("sources" in msg, "Error should redirect callers to observer-side sources: $msg")
    }

    @Test
    fun `legacy executionSource argument is rejected with the rename hint`() {
        val args = buildJsonObject {
            put("id", "some-uuid")
            put("executionSource", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("ON_CLICK")) })
        }
        val ex = assertFailsWith<IllegalStateException> {
            runBlocking { tool.execute(args) }
        }
        val msg = ex.message ?: ""
        assertTrue("invocationTriggers" in msg, "Error should name the new field: $msg")
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
            "sources" in msg || "inputs" in msg || "invocationTriggers" in msg || "nodeAction" in msg,
            "Error should mention at least one accepted field: $msg",
        )
    }

    // ── KrillNodeTypes: observer wiring fields are universal ─────────────────

    @Test
    fun `DataPoint skeleton carries sources inputs invocationTriggers and nodeAction`() {
        val spec = KrillNodeTypes.resolve("KrillApp.DataPoint")
            ?: error("KrillApp.DataPoint missing from KrillNodeTypes registry")
        assertTrue("sources" in spec.defaultMeta, "DataPoint.defaultMeta must expose sources")
        assertTrue("inputs" in spec.defaultMeta, "DataPoint.defaultMeta must expose inputs")
        assertTrue("invocationTriggers" in spec.defaultMeta, "DataPoint.defaultMeta must expose invocationTriggers")
        assertTrue("nodeAction" in spec.defaultMeta, "DataPoint.defaultMeta must expose nodeAction")
        assertFalse("targets" in spec.defaultMeta, "targets must not survive in SDK-derived skeletons")
        assertFalse("executionSource" in spec.defaultMeta, "executionSource must not survive in SDK-derived skeletons")
        assertEquals("EXECUTE", spec.defaultMeta["nodeAction"]?.jsonPrimitive?.content)
    }

    @Test
    fun `Server_Pin skeleton carries sources inputs invocationTriggers and nodeAction`() {
        val spec = KrillNodeTypes.resolve("KrillApp.Server.Pin")
            ?: error("KrillApp.Server.Pin missing from KrillNodeTypes registry")
        assertTrue("sources" in spec.defaultMeta, "Server.Pin.defaultMeta must expose sources")
        assertTrue("inputs" in spec.defaultMeta, "Server.Pin.defaultMeta must expose inputs")
        assertTrue("invocationTriggers" in spec.defaultMeta, "Server.Pin.defaultMeta must expose invocationTriggers")
        assertTrue("nodeAction" in spec.defaultMeta, "Server.Pin.defaultMeta must expose nodeAction")
        assertEquals("EXECUTE", spec.defaultMeta["nodeAction"]?.jsonPrimitive?.content)
    }
}
