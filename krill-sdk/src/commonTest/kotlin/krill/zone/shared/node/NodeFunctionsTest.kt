package krill.zone.shared.node

import io.ktor.http.URLProtocol
import krill.zone.shared.KrillApp
import krill.zone.shared.krillapp.datapoint.DataPointMetaData
import krill.zone.shared.krillapp.server.ServerMetaData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Regression tests for krill-oss#155: Node.https() used a hard cast to
 * ServerMetaData, producing a cryptic ClassCastException on mismatched metadata.
 */
class NodeFunctionsTest {

    private fun serverNode(name: String, port: Int, isLocal: Boolean = true) = Node(
        id = "s1", parent = "s1", host = "s1",
        type = KrillApp.Server,
        meta = ServerMetaData(name = name, port = port, isLocal = isLocal),
    )

    private fun nonServerNode() = Node(
        id = "dp1", parent = "s1", host = "s1",
        type = KrillApp.DataPoint,
        meta = DataPointMetaData(),
    )

    @Test
    fun `https returns correct URL for a local server node`() {
        val url = serverNode("pi", 8443, isLocal = true).https()
        assertEquals(URLProtocol.HTTPS, url.protocol)
        assertEquals("pi.local", url.host)
        assertEquals(8443, url.port)
    }

    @Test
    fun `https returns correct URL for an FQDN server node`() {
        val url = serverNode("server.example.com", 443, isLocal = false).https()
        assertEquals("server.example.com", url.host)
        assertEquals(443, url.port)
    }

    @Test
    fun `https throws IllegalArgumentException with descriptive message on non-server node`() {
        val node = nonServerNode()
        val ex = assertFailsWith<IllegalArgumentException> { node.https() }
        assertTrue(ex.message!!.contains("ServerMetaData"), "message should name the expected type")
        assertTrue(ex.message!!.contains("DataPointMetaData"), "message should name the actual type")
        assertTrue(ex.message!!.contains(node.id), "message should include the node id")
    }
}
