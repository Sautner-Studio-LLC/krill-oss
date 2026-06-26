package krill.zone.mcp.mcp.tools

import krill.zone.mcp.auth.PinProvider
import krill.zone.mcp.config.KrillMcpConfig
import krill.zone.mcp.krill.KrillNodeTypes
import krill.zone.mcp.krill.KrillRegistry
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Regression tests for:
 *   - krill-oss#13: `create_node` must inject a parent-derived display name when
 *     creating a `KrillApp.DataPoint.Graph` so sibling Graphs don't share a name.
 *   - krill-oss#163: `create_node` with the server id as parent (or no parent)
 *     must synthesize a server-typed stub rather than calling `GET /node/{id}`,
 *     which fails because the server entity is not addressable at that endpoint.
 *   - krill-oss#165: Lambda `meta.type` discriminator must be `LambdaMetaData`
 *     (the serial name the krill server registers), not `LambdaSourceMetaData`
 *     (the stale SDK class name from 0.0.48).
 */
class CreateNodeToolTest {

    private val tool = CreateNodeTool(
        registry = KrillRegistry(
            config = KrillMcpConfig(),
            pin = PinProvider(path = "/dev/null"),
        ),
    )
    private val graphSpec = KrillNodeTypes.resolve("KrillApp.DataPoint.Graph")
        ?: error("Graph spec missing from registry")
    private val dataPointSpec = KrillNodeTypes.resolve("KrillApp.DataPoint")
        ?: error("DataPoint spec missing from registry")

    @Test
    fun `derives parent-aware default for Graph from parent DataPoint name`() {
        val parent = parentNode(typeFqn = "krill.zone.shared.KrillApp.DataPoint", name = "pH")
        assertEquals("pH graph", tool.derivedDefaultName(graphSpec, parent))
    }

    @Test
    fun `returns null for Graph when parent has no usable name`() {
        val parent = parentNode(typeFqn = "krill.zone.shared.KrillApp.DataPoint", name = "")
        assertNull(tool.derivedDefaultName(graphSpec, parent))
    }

    @Test
    fun `returns null for non-Graph specs so other types keep their registry default`() {
        val parent = parentNode(typeFqn = "krill.zone.shared.KrillApp.Server", name = "pi-krill-05")
        assertNull(tool.derivedDefaultName(dataPointSpec, parent))
    }

    @Test
    fun `Graph registry default name is empty so create_node always sources it from the parent or caller`() {
        val defaultName = graphSpec.defaultMeta["name"]?.let { (it as JsonPrimitive).content }
        assertEquals("", defaultName)
    }

    // ── krill-oss#163 — server-parent guard ──────────────────────────────────

    @Test
    fun `serverParentNode carries the canonical KrillApp Server FQN`() {
        val node = tool.serverParentNode("test-server-uuid")
        val fqn = node["type"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
        assertEquals("krill.zone.shared.KrillApp.Server", fqn)
    }

    @Test
    fun `serverParentNode embeds the supplied server id`() {
        val serverId = "deadbeef-0000-0000-0000-000000000001"
        val node = tool.serverParentNode(serverId)
        assertEquals(serverId, node["id"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `serverParentNode has a meta object so derivedDefaultName does not throw`() {
        val node = tool.serverParentNode("any-id")
        // derivedDefaultName must not NPE on a synthesized server parent
        assertNull(tool.derivedDefaultName(graphSpec, node))
    }

    // ── krill-oss#165 — Lambda meta serial-name guard ────────────────────────

    @Test
    fun `Lambda metaFqn is LambdaMetaData not LambdaSourceMetaData`() {
        val lambda = KrillNodeTypes.resolve("KrillApp.Executor.Lambda")
            ?: error("Lambda spec missing from registry")
        assertEquals(
            "krill.zone.shared.krillapp.executor.lambda.LambdaMetaData",
            lambda.metaFqn,
        )
    }

    @Test
    fun `Lambda defaultMeta type discriminator is LambdaMetaData`() {
        val lambda = KrillNodeTypes.resolve("KrillApp.Executor.Lambda")
            ?: error("Lambda spec missing from registry")
        val typeInMeta = lambda.defaultMeta["type"]?.jsonPrimitive?.contentOrNull
        assertEquals(
            "krill.zone.shared.krillapp.executor.lambda.LambdaMetaData",
            typeInMeta,
            "create_node posts this value as meta.type — must match the serial name the krill server registers",
        )
    }

    private fun parentNode(typeFqn: String, name: String) = buildJsonObject {
        put(
            "type",
            buildJsonObject { put("type", JsonPrimitive(typeFqn)) },
        )
        put(
            "meta",
            buildJsonObject { put("name", JsonPrimitive(name)) },
        )
    }
}
