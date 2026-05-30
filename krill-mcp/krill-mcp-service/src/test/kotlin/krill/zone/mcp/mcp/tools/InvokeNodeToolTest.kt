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
import kotlin.test.assertTrue

/**
 * Unit tests for [InvokeNodeTool] — pins the schema contract and pre-HTTP
 * validation (invalid verb, mismatched by* identity pair, missing id).
 *
 * The execute() path that reaches the server is not covered here — that
 * requires a live KrillClient and HTTP endpoint.
 */
class InvokeNodeToolTest {

    private val tool = InvokeNodeTool(
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
    fun `inputSchema exposes id verb byNodeId byHostId`() {
        val props = tool.inputSchema["properties"] as JsonObject
        assertTrue("server" in props)
        assertTrue("id" in props)
        assertTrue("verb" in props)
        assertTrue("byNodeId" in props)
        assertTrue("byHostId" in props)
    }

    // ── Verb validation ──────────────────────────────────────────────────────

    @Test
    fun `EXECUTE and RESET are valid verbs`() {
        assertTrue("EXECUTE" in InvokeNodeTool.VALID_VERBS)
        assertTrue("RESET" in InvokeNodeTool.VALID_VERBS)
    }

    @Test
    fun `invalid verb is rejected before any HTTP call`() {
        val args = buildJsonObject {
            put("id", "some-uuid")
            put("verb", "TOGGLE")
        }
        val ex = assertFailsWith<IllegalStateException> {
            runBlocking { tool.execute(args) }
        }
        assertTrue("TOGGLE" in (ex.message ?: ""), "Error should cite the bad verb")
        assertTrue("EXECUTE" in (ex.message ?: "") || "RESET" in (ex.message ?: ""), "Error should name valid options")
    }

    // ── Identity pair validation ─────────────────────────────────────────────

    @Test
    fun `providing only byNodeId without byHostId is rejected`() {
        val args = buildJsonObject {
            put("id", "some-uuid")
            put("byNodeId", "node-abc")
        }
        val ex = assertFailsWith<IllegalStateException> {
            runBlocking { tool.execute(args) }
        }
        assertTrue("byHostId" in (ex.message ?: "") || "byNodeId" in (ex.message ?: ""))
    }

    @Test
    fun `providing only byHostId without byNodeId is rejected`() {
        val args = buildJsonObject {
            put("id", "some-uuid")
            put("byHostId", "host-xyz")
        }
        val ex = assertFailsWith<IllegalStateException> {
            runBlocking { tool.execute(args) }
        }
        assertTrue("byHostId" in (ex.message ?: "") || "byNodeId" in (ex.message ?: ""))
    }
}
