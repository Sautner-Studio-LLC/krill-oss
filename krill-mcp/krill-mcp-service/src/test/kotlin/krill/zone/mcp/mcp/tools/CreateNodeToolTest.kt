package krill.zone.mcp.mcp.tools

import krill.zone.mcp.auth.PinProvider
import krill.zone.mcp.config.KrillMcpConfig
import krill.zone.mcp.krill.KrillNodeTypes
import krill.zone.mcp.krill.KrillRegistry
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Regression tests for issue Sautner-Studio-LLC/krill-oss#13.
 *
 * `create_node` must inject a parent-derived display name when creating a
 * `KrillApp.DataPoint.Graph` and the caller did not supply one — otherwise
 * sibling Graphs under different DataPoints all surface with the same name.
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
