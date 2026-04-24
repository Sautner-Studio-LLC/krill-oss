/**
 * Low-level tunables for Krill's multicast peer discovery and beaconing.
 *
 * These are deliberately constants rather than configuration because every node
 * in a swarm must agree on them — a server on a different multicast group or
 * port will never see its peers. They are read by both the JVM beacon emitter
 * and the per-platform discovery clients (JVM/Android/iOS).
 */
package krill.zone.shared.io.http

/**
 * Shared compile-time constants used across the peer state machine:
 * multicast transport parameters, datagram framing limits, and beacon rate
 * limiting.
 *
 * Values are frozen: changing any of them without coordinated updates to every
 * running Krill node in a swarm will partition the swarm (new nodes will not
 * see old nodes and vice versa).
 */
object PeerConstants {

    /**
     * IPv4 multicast group used by Krill beacons.
     *
     * `239.255.0.69` sits in the administratively-scoped IPv4 multicast range
     * (RFC 2365) and is intended to stay within a single administrative domain,
     * which for Krill means a single LAN / broadcast domain.
     */
    const val MULTICAST_GROUP_V4 = "239.255.0.69"

    /** UDP port on which beacons are emitted and listened for. */
    const val MULTICAST_PORT = 45317

    /**
     * IP multicast TTL for beacon packets. Set to `1` so beacons never cross
     * a router — Krill discovery is LAN-scoped by design.
     */
    const val MULTICAST_TTL = 1 // stay within the local subnet

    /**
     * Maximum datagram size (in bytes) the beacon receivers are willing to
     * buffer. Sized well above a typical beacon payload; packets larger than
     * this are silently truncated by the socket.
     */
    const val MAX_DATAGRAM_SIZE = 2048

    /**
     * Minimum time between successive beacons from the same source, in
     * milliseconds. Enforced at the emit side to prevent a misbehaving node
     * from flooding the multicast group.
     */
    const val BEACON_MIN_INTERVAL_MS = 1000L // Minimum 1 second between beacons

}
