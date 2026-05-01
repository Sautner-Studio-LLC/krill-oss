package krill.zone.mcp.mcp.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression tests for the `type` / `typeExact` filter on `list_nodes`.
 *
 * Pre-fix (krill-oss#53), `type=DataPoint` substring-matched the FQN, so it
 * silently returned `KrillApp.DataPoint.Graph` (and any `.Filter`/`.Trigger`)
 * along with the bare `KrillApp.DataPoint` nodes. The new `typeExact: true`
 * flag suffix-matches the FQN so the filter actually means what a first-time
 * user expects.
 */
class ListNodesToolTest {

    @Test
    fun `null type filter returns the full list`() {
        val out = ListNodesTool.filterByType(SAMPLE, typeFilter = null, typeExact = false)
        assertEquals(SAMPLE.size, out.size)
    }

    @Test
    fun `substring (default) matches DataPoint and DataPoint Graph for back-compat`() {
        val out = ListNodesTool.filterByType(SAMPLE, typeFilter = "DataPoint", typeExact = false)
        val types = out.map { it.jsonObject["type"]!!.jsonObject["type"]!!.jsonPrimitive.content }
        assertEquals(
            listOf(
                "krill.zone.shared.KrillApp.DataPoint",
                "krill.zone.shared.KrillApp.DataPoint",
                "krill.zone.shared.KrillApp.DataPoint.Graph",
            ),
            types,
        )
    }

    @Test
    fun `typeExact bare leaf matches only KrillApp DataPoint exactly`() {
        val out = ListNodesTool.filterByType(SAMPLE, typeFilter = "DataPoint", typeExact = true)
        val types = out.map { it.jsonObject["type"]!!.jsonObject["type"]!!.jsonPrimitive.content }
        assertEquals(
            listOf(
                "krill.zone.shared.KrillApp.DataPoint",
                "krill.zone.shared.KrillApp.DataPoint",
            ),
            types,
            "DataPoint.Graph and Trigger.Button must be excluded under typeExact",
        )
    }

    @Test
    fun `typeExact short form matches the same set as the bare leaf`() {
        val bare = ListNodesTool.filterByType(SAMPLE, "DataPoint", typeExact = true)
        val short = ListNodesTool.filterByType(SAMPLE, "KrillApp.DataPoint", typeExact = true)
        assertEquals(bare, short)
    }

    @Test
    fun `typeExact full FQN matches the same set as the bare leaf`() {
        val bare = ListNodesTool.filterByType(SAMPLE, "DataPoint", typeExact = true)
        val full = ListNodesTool.filterByType(
            SAMPLE,
            "krill.zone.shared.KrillApp.DataPoint",
            typeExact = true,
        )
        assertEquals(bare, full)
    }

    @Test
    fun `typeExact does not partial-match suffixes`() {
        // "Point" must NOT match "DataPoint" — suffix-after-dot requires a
        // boundary, not a substring.
        val out = ListNodesTool.filterByType(SAMPLE, typeFilter = "Point", typeExact = true)
        assertEquals(0, out.size)
    }

    @Test
    fun `nodes missing the type field are skipped silently under filtering`() {
        val malformed = buildJsonArray {
            addJsonObject {
                put("id", JsonPrimitive("orphan"))
                // no `type` field at all
            }
            addJsonObject {
                put("id", JsonPrimitive("dp1"))
                put(
                    "type",
                    buildJsonObject { put("type", JsonPrimitive("krill.zone.shared.KrillApp.DataPoint")) },
                )
            }
        }
        val out = ListNodesTool.filterByType(malformed, typeFilter = "DataPoint", typeExact = true)
        assertEquals(1, out.size)
    }

    private companion object {
        val SAMPLE: JsonArray = buildJsonArray {
            // Two bare DataPoints
            node("dp1", "krill.zone.shared.KrillApp.DataPoint")
            node("dp2", "krill.zone.shared.KrillApp.DataPoint")
            // One Graph child of a DataPoint — substring "DataPoint" matches
            // this; typeExact "DataPoint" must NOT match it.
            node("g1", "krill.zone.shared.KrillApp.DataPoint.Graph")
            // Trigger subtype — substring "Trigger" matches; typeExact
            // "Trigger" must NOT match (only Button is present, the bare
            // Trigger isn't here).
            node("tb1", "krill.zone.shared.KrillApp.Trigger.Button")
            // An Executor.Lambda — substring "Lambda" matches mid-FQN;
            // typeExact "Lambda" should match (the FQN ends with .Lambda).
            node("lam1", "krill.zone.shared.KrillApp.Executor.Lambda")
        }

        private fun kotlinx.serialization.json.JsonArrayBuilder.node(id: String, typeFqn: String) {
            addJsonObject {
                put("id", JsonPrimitive(id))
                put(
                    "type",
                    buildJsonObject { put("type", JsonPrimitive(typeFqn)) },
                )
            }
        }
    }
}
