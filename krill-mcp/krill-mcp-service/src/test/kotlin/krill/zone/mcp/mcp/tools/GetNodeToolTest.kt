package krill.zone.mcp.mcp.tools

import krill.zone.mcp.auth.PinProvider
import krill.zone.mcp.config.KrillMcpConfig
import krill.zone.mcp.krill.KrillRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Regression test for issue Sautner-Studio-LLC/krill-oss#49.
 *
 * Peer entries returned by `list_nodes type=Peer` carry composite ids of the
 * form `serverId:nodeId`. The pre-fix `get_node` passed those through to
 * `GET /node/{id}` on the seed Krill server, which 404s because no node is
 * keyed by the literal colon-prefixed string. The skill claimed the format
 * was supported; the tool 404'd. This test pins the fail-fast behavior:
 * `get_node` rejects peer-prefixed ids before any HTTP call, with a message
 * that names the bare-uuid retry path.
 */
class GetNodeToolTest {

    private val tool = GetNodeTool(
        registry = KrillRegistry(
            config = KrillMcpConfig(),
            pin = PinProvider(path = "/dev/null"),
        ),
    )

    @Test
    fun `peer-prefixed id is rejected before any HTTP call with an actionable message`() {
        val args = buildJsonObject {
            put(
                "id",
                JsonPrimitive("b87160aa-9cb1-4cf2-ae81-0864a3619874:2d625fe0-15f6-423c-9e30-9a598aecef06"),
            )
        }
        val ex = assertFailsWith<IllegalStateException> {
            runBlocking { tool.execute(args) }
        }
        val msg = ex.message ?: error("Expected message on rejection")
        assertTrue("Peer-prefixed" in msg, "Message should name the format: $msg")
        assertTrue("krill-oss#49" in msg, "Message should cite the tracking issue: $msg")
        assertTrue(
            "id='2d625fe0-15f6-423c-9e30-9a598aecef06'" in msg,
            "Message should suggest retrying with the bare uuid after the colon: $msg",
        )
    }

    @Test
    fun `bare uuid passes the format check`() {
        // Format check is the only thing this test exercises — it must not
        // throw before reaching the (empty) registry resolve, which then
        // fails with a different, registry-related error. Either way: no
        // IllegalArgumentException about peer-prefixed ids.
        val args = buildJsonObject {
            put("id", JsonPrimitive("2d625fe0-15f6-423c-9e30-9a598aecef06"))
        }
        val ex = runCatching { runBlocking { tool.execute(args) } }.exceptionOrNull()
        if (ex != null) {
            assertTrue(
                "Peer-prefixed" !in (ex.message ?: ""),
                "Bare uuid must not trip the peer-prefixed check: ${ex.message}",
            )
        }
    }
}
