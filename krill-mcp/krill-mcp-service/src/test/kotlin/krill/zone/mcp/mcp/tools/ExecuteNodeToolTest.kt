package krill.zone.mcp.mcp.tools

import krill.zone.mcp.auth.PinProvider
import krill.zone.mcp.config.KrillMcpConfig
import krill.zone.mcp.krill.KrillRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression tests for issue bsautner/krill-oss#24.
 *
 * `execute_node` fires a Trigger or Executor by POSTing the node back with
 * `state=EXECUTED` — the same wire pattern the Compose client uses when the
 * user taps the manual-execute button. The tool must:
 *
 * - Refuse pure-container types (`KrillApp.Server`, `KrillApp.Client`, peer
 *   links, backup tasks) where firing is meaningless and could confuse
 *   downstream processors.
 * - Accept the action types the issue calls out: Triggers, Executors,
 *   LogicGate, Lambda, Cron, Button, etc.
 * - Advertise itself as a manual fire-once primitive in the description,
 *   distinct from `record_snapshot` (which is a value write, not a fire).
 */
class ExecuteNodeToolTest {

    private val tool = ExecuteNodeTool(
        registry = KrillRegistry(
            config = KrillMcpConfig(),
            pin = PinProvider(path = "/dev/null"),
        ),
    )

    @Test
    fun `name is execute_node so the issue's proposed shape lands as-is`() {
        assertEquals("execute_node", tool.name)
    }

    @Test
    fun `description names the fire primitive distinctly from record_snapshot`() {
        val description = tool.description.lowercase()
        assertTrue(
            "manual" in description || "fire" in description || "execute" in description,
            "execute_node description should make the manual-fire intent explicit. Actual: '${tool.description}'",
        )
        assertFalse(
            "record_snapshot" in description && "use" in description,
            "Description should not redirect callers to record_snapshot — that's the wrong abstraction (see issue #24 description).",
        )
    }

    @Test
    fun `input schema requires the node id`() {
        val required = tool.inputSchema["required"]
        assertTrue(
            required.toString().contains("\"id\""),
            "execute_node must require the `id` argument. Schema: ${tool.inputSchema}",
        )
    }

    @Test
    fun `executor and trigger types are firable`() {
        val firable = listOf(
            "krill.zone.shared.KrillApp.Executor.LogicGate",
            "krill.zone.shared.KrillApp.Executor.Lambda",
            "krill.zone.shared.KrillApp.Executor.OutgoingWebHook",
            "krill.zone.shared.KrillApp.Executor.Calculation",
            "krill.zone.shared.KrillApp.Executor.Compute",
            "krill.zone.shared.KrillApp.Executor.SMTP",
            "krill.zone.shared.KrillApp.Trigger",
            "krill.zone.shared.KrillApp.Trigger.HighThreshold",
            "krill.zone.shared.KrillApp.Trigger.LowThreshold",
            "krill.zone.shared.KrillApp.Trigger.Color",
            "krill.zone.shared.KrillApp.Trigger.Button",
            "krill.zone.shared.KrillApp.Trigger.CronTimer",
            "krill.zone.shared.KrillApp.Trigger.IncomingWebHook",
            "krill.zone.shared.KrillApp.MQTT",
        )
        firable.forEach { fqn ->
            assertTrue(
                ExecuteNodeTool.isFirable(fqn),
                "$fqn should be firable — it's the user's primary use case for execute_node.",
            )
        }
    }

    @Test
    fun `pure container and infrastructure types are not firable`() {
        val notFirable = listOf(
            "krill.zone.shared.KrillApp.Server",
            "krill.zone.shared.KrillApp.Client",
            "krill.zone.shared.KrillApp.Client.About",
            "krill.zone.shared.KrillApp.Server.Peer",
            "krill.zone.shared.KrillApp.Server.Backup",
        )
        notFirable.forEach { fqn ->
            assertFalse(
                ExecuteNodeTool.isFirable(fqn),
                "$fqn must reject manual execution — it's a pure container/infra type. Issue #24 calls out 'bare Server' as the canonical reject case.",
            )
        }
    }
}
