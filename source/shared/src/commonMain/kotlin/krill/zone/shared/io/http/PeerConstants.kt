package krill.zone.shared.io.http

/**
 * Constants used throughout the peer state machine for beacon discovery,
 * connection management, and session tracking.
 */
object PeerConstants {
    
    // Multicast discovery configuration
    const val MULTICAST_GROUP_V4 = "239.255.0.69"
    const val MULTICAST_PORT = 45317
    const val MULTICAST_TTL = 1 // stay within the local subnet
    const val MAX_DATAGRAM_SIZE = 2048

    // Beacon rate limiting
    const val BEACON_MIN_INTERVAL_MS = 1000L // Minimum 1 second between beacons

}
