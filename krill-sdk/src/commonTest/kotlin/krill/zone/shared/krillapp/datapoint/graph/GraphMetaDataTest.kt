package krill.zone.shared.krillapp.datapoint.graph

import krill.zone.shared.KrillApp
import krill.zone.shared.node.Node
import krill.zone.shared.node.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Regression tests for issue bsautner/krill-oss#13.
 *
 * The previous `GraphMetaData(name = "Data Graph")` default produced
 * indistinguishable Graph siblings under different parent DataPoints; the
 * fix removes the literal default and supplies a fallback at display time.
 */
class GraphMetaDataTest {

    @Test
    fun `default name is empty so callers must supply a parent-derived value`() {
        val meta = GraphMetaData()
        assertEquals("", meta.name)
        assertNotEquals("Data Graph", meta.name)
    }

    @Test
    fun `Node-name falls back to Graph when the meta name is empty`() {
        val node = graphNode(name = "")
        assertEquals("Graph", node.name())
    }

    @Test
    fun `Node-name returns the supplied parent-derived name when present`() {
        val node = graphNode(name = "pH graph")
        assertEquals("pH graph", node.name())
    }

    private fun graphNode(name: String): Node = Node(
        id = "graph-1",
        parent = "datapoint-1",
        host = "server-1",
        type = KrillApp.DataPoint.Graph,
        meta = GraphMetaData(name = name),
    )
}
