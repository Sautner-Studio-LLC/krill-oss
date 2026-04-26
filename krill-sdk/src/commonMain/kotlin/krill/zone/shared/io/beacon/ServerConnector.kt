/**
 * Receive-side hook that turns an incoming server-discovery datum into a
 * connection attempt. Three overloads cover the three forms a server can
 * arrive in: a freshly received [krill.zone.shared.node.NodeWire] beacon,
 * a [ServerMetaData] handed in from a configuration file, or a fully
 * materialised [Node] from another peer's swarm graph.
 *
 * Implementations live outside the SDK — on the client it speaks to the
 * UI / storage layer; on the server it speaks to the peer-management
 * subsystem.
 */
package krill.zone.shared.io.beacon

import krill.zone.shared.krillapp.server.ServerMetaData
import krill.zone.shared.node.Node
import krill.zone.shared.node.NodeWire

/**
 * Per-environment "given a server, connect to it" contract.
 */
interface ServerConnector {
    /** Connect from a multicast beacon record. */
    suspend fun connectWire(wire: NodeWire)

    /** Connect from configured server metadata (e.g. a saved peer entry). */
    suspend fun connectMeta(meta: ServerMetaData)

    /** Connect from a fully-materialised [Node] (typically forwarded by another peer). */
    suspend fun connectNode(node: Node)
}
