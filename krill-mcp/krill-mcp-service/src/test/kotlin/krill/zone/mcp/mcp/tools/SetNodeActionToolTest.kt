package krill.zone.mcp.mcp.tools

import krill.zone.mcp.auth.PinProvider
import krill.zone.mcp.config.KrillMcpConfig
import krill.zone.mcp.krill.KrillNodeTypes
import krill.zone.mcp.krill.KrillRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit tests for [SetNodeActionTool] — pins the schema contract and the
 * pre-HTTP validation paths added for krill-oss#82.
 *
 * The full execute() path requires a live registry + HTTP, so these tests
 * cover schema introspection and the two rejection paths that fire before
 * any network call (invalid action string; non-action-capable node type).
 */
class SetNodeActionToolTest {

    private val tool = SetNodeActionTool(
        registry = KrillRegistry(
            config = KrillMcpConfig(),
            pin = PinProvider(path = "/dev/null"),
        ),
    )

    // ── Schema ──────────────────────────────────────────────────────────────

    @Test
    fun `inputSchema declares id and action as required`() {
        val required = tool.inputSchema["required"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("id", "action"), required)
    }

    @Test
    fun `inputSchema exposes server id and action properties`() {
        val props = tool.inputSchema["properties"] as JsonObject
        assertTrue("server" in props, "Schema must declare optional server selector")
        assertTrue("id" in props, "Schema must declare required node id")
        assertTrue("action" in props, "Schema must declare required action field")
    }

    // ── Action validation ────────────────────────────────────────────────────

    @Test
    fun `EXECUTE is a valid action value`() {
        assertTrue("EXECUTE" in SetNodeActionTool.VALID_ACTIONS)
    }

    @Test
    fun `RESET is a valid action value`() {
        assertTrue("RESET" in SetNodeActionTool.VALID_ACTIONS)
    }

    @Test
    fun `invalid action is rejected before any HTTP call`() {
        val args = buildJsonObject {
            put("id", "some-node-id")
            put("action", "TOGGLE")
        }
        val ex = assertFailsWith<IllegalStateException> {
            runBlocking { tool.execute(args) }
        }
        val msg = ex.message ?: error("Expected message")
        assertTrue("TOGGLE" in msg, "Error should cite the invalid action: $msg")
        assertTrue("EXECUTE" in msg || "RESET" in msg, "Error should name valid options: $msg")
    }

    // ── KrillNodeTypes registry: nodeAction presence ─────────────────────────

    @Test
    fun `Button type carries nodeAction in defaultMeta`() {
        val spec = KrillNodeTypes.resolve("KrillApp.Trigger.Button")
            ?: error("Button type missing from KrillNodeTypes registry")
        assertTrue(
            "nodeAction" in spec.defaultMeta,
            "KrillApp.Trigger.Button defaultMeta must expose nodeAction so create_node and set_node_action work",
        )
        assertEquals("EXECUTE", spec.defaultMeta["nodeAction"]?.jsonPrimitive?.content)
    }

    @Test
    fun `all trigger types carry nodeAction in defaultMeta`() {
        val triggerShortNames = listOf(
            "KrillApp.Trigger",
            "KrillApp.Trigger.Button",
            "KrillApp.Trigger.HighThreshold",
            "KrillApp.Trigger.LowThreshold",
            "KrillApp.Trigger.SilentAlarmMs",
            "KrillApp.Trigger.CronTimer",
            "KrillApp.Trigger.Color",
            "KrillApp.Trigger.IncomingWebHook",
        )
        for (shortName in triggerShortNames) {
            val spec = KrillNodeTypes.resolve(shortName)
                ?: error("$shortName missing from KrillNodeTypes registry")
            assertTrue(
                "nodeAction" in spec.defaultMeta,
                "$shortName defaultMeta must expose nodeAction",
            )
        }
    }

    @Test
    fun `executor types carry nodeAction in defaultMeta`() {
        val executorShortNames = listOf(
            "KrillApp.Executor.LogicGate",
            "KrillApp.Executor.OutgoingWebHook",
            "KrillApp.Executor.Lambda",
            "KrillApp.Executor.Calculation",
            "KrillApp.Executor.Compute",
            "KrillApp.Executor.SMTP",
            "KrillApp.MQTT",
        )
        for (shortName in executorShortNames) {
            val spec = KrillNodeTypes.resolve(shortName)
                ?: error("$shortName missing from KrillNodeTypes registry")
            assertTrue(
                "nodeAction" in spec.defaultMeta,
                "$shortName defaultMeta must expose nodeAction",
            )
        }
    }

    @Test
    fun `TaskList carries nodeAction in defaultMeta`() {
        val spec = KrillNodeTypes.resolve("KrillApp.Project.TaskList")
            ?: error("TaskList type missing from KrillNodeTypes registry")
        assertTrue(
            "nodeAction" in spec.defaultMeta,
            "KrillApp.Project.TaskList defaultMeta must expose nodeAction",
        )
    }

    @Test
    fun `non-action types do not carry nodeAction in defaultMeta`() {
        val nonActionShortNames = listOf(
            "KrillApp.DataPoint",
            "KrillApp.DataPoint.Filter",
            "KrillApp.Project",
            "KrillApp.Server.Pin",
        )
        for (shortName in nonActionShortNames) {
            val spec = KrillNodeTypes.resolve(shortName)
                ?: error("$shortName missing from KrillNodeTypes registry")
            assertTrue(
                "nodeAction" !in spec.defaultMeta,
                "$shortName should not expose nodeAction (not an action-capable type)",
            )
        }
    }
}
