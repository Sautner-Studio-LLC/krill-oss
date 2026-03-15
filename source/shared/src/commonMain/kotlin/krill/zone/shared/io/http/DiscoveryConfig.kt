package krill.zone.shared.io.http

/**
 * Configuration for multicast beacon discovery.
 * Defines the network parameters for peer-to-peer discovery.
 */
object DiscoveryConfig {
    const val GROUP_V4 = PeerConstants.MULTICAST_GROUP_V4
    const val PORT = PeerConstants.MULTICAST_PORT
    const val TTL = PeerConstants.MULTICAST_TTL
    const val MAX_DATAGRAM = PeerConstants.MAX_DATAGRAM_SIZE
}
