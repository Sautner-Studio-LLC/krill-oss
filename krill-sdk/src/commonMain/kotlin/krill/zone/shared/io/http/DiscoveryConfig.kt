/**
 * Discovery-facing view onto the low-level [PeerConstants] values.
 *
 * This file exists so callers in the discovery code path can import a small,
 * intention-named symbol (`DiscoveryConfig.PORT`) instead of the more
 * implementation-flavoured `PeerConstants.MULTICAST_PORT`. The two surfaces
 * must always stay in sync; [DiscoveryConfig] never adds values, only renames.
 */
package krill.zone.shared.io.http

/**
 * Configuration aliases for multicast beacon discovery.
 *
 * Every member re-exports a value from [PeerConstants] under a
 * discovery-domain name. Do not hard-code literal values here — always point
 * back at [PeerConstants] so a single-point edit there remains sufficient.
 */
object DiscoveryConfig {
    /** Alias for [PeerConstants.MULTICAST_GROUP_V4] — the IPv4 multicast group used for discovery. */
    const val GROUP_V4 = PeerConstants.MULTICAST_GROUP_V4

    /** Alias for [PeerConstants.MULTICAST_PORT] — the UDP port used for discovery. */
    const val PORT = PeerConstants.MULTICAST_PORT

    /** Alias for [PeerConstants.MULTICAST_TTL] — the IP multicast TTL for beacon packets. */
    const val TTL = PeerConstants.MULTICAST_TTL

    /** Alias for [PeerConstants.MAX_DATAGRAM_SIZE] — the maximum inbound datagram size the discovery socket will buffer. */
    const val MAX_DATAGRAM = PeerConstants.MAX_DATAGRAM_SIZE
}
