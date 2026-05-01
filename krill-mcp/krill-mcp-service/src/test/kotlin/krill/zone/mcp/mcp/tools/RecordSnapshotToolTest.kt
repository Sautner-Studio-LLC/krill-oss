package krill.zone.mcp.mcp.tools

import krill.zone.mcp.auth.PinProvider
import krill.zone.mcp.config.KrillMcpConfig
import krill.zone.mcp.krill.KrillRegistry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the schema-side half of krill-oss#52 — the `record_snapshot` arg
 * targeting the DataPoint is named `id`, matching `get_node` / `read_series` /
 * `delete_node`. Pre-fix it was `dataPointId`, which surprised every first-time
 * caller carrying the variable straight from `record_snapshot` into the
 * read_series verification step the skill recommends.
 *
 * The full execute() path requires a live registry + HTTP, so this test sticks
 * to schema introspection. The "passing dataPointId fails" half of the contract
 * is enforced by McpServer's reject-unknown-args path (krill-oss#51) and is
 * covered by McpServerTest.
 */
class RecordSnapshotToolTest {

    private val tool = RecordSnapshotTool(
        registry = KrillRegistry(
            config = KrillMcpConfig(),
            pin = PinProvider(path = "/dev/null"),
        ),
    )

    @Test
    fun `inputSchema declares id, not dataPointId`() {
        val props = tool.inputSchema["properties"] as JsonObject
        assertTrue("id" in props.keys, "Schema must declare 'id' for the DataPoint UUID")
        assertTrue("dataPointId" !in props.keys, "Pre-rename name must not survive in the schema")
    }

    @Test
    fun `id is the only required argument`() {
        val required = tool.inputSchema["required"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("id"), required)
    }
}
