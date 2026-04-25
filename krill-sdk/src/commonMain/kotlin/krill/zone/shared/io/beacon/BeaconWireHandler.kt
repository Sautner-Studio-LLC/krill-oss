/**
 * Receive-side hook for Krill beacons. Implementations decide what to do with
 * each incoming [krill.zone.shared.node.NodeWire]: clients pass it to a
 * [krill.zone.shared.io.beacon.ServerConnector] to grow the swarm view; the
 * server uses it to learn about peer servers in the same cluster.
 */
package krill.zone.shared.io.beacon

import krill.zone.shared.node.NodeWire

/**
 * SAM type for "do something with this incoming beacon".
 *
 * Called once per validated beacon datagram by the multicast receive loop.
 * Implementations should return quickly and dispatch heavier work onto a
 * coroutine — the receive loop is shared by every peer in the LAN and must
 * not block.
 */
fun interface BeaconWireHandler {
    /** Process a single incoming peer beacon. Should not block. */
    fun handleIncomingWire(wire: NodeWire)
}
