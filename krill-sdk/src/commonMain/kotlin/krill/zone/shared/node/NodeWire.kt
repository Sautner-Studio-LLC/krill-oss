/**
 * Wire-format payload exchanged in Krill's multicast peer-discovery beacons.
 *
 * Every Krill participant — server or client — periodically broadcasts a
 * [NodeWire] on the swarm's multicast group; receivers use it to learn about
 * peers, their network address, and the cluster they belong to.
 *
 * Note: the Compose `@Immutable` stability hint that this type carries inside
 * the `/shared` module has been intentionally dropped from the SDK to avoid
 * pulling in the Compose Multiplatform runtime as a dependency. The JSON
 * shape (and therefore wire compatibility) is unchanged.
 */
package krill.zone.shared.node

import kotlinx.serialization.*
import krill.zone.shared.Platform

/**
 * The minimal payload required to discover and connect to a Krill peer.
 *
 * Marshalled as JSON inside a UDP datagram, so the field set is deliberately
 * small (datagram size limits) and primitive (no nested collections / nodes)
 * — heavier metadata is fetched later via the HTTP `/health` and `/nodes`
 * endpoints once the receiver has decided to connect.
 */
@Serializable
data class NodeWire(
    /** Wall-clock time the beacon was generated, epoch millis. Used to discard stale captures. */
    val timestamp: Long,

    /** Stable per-installation UUID of the sender — survives restarts; lets receivers de-duplicate beacons across IP changes. */
    val installId: String,

    /** Sender's reachable host (IP address or hostname). Private — accessed via the [host] sanitiser to strip noise injected by some platform stacks. */
    private val host: String,

    /** TCP port the sender's HTTP/SSE server is listening on. */
    val port: Int,

    /** What kind of device the sender is — drives the icon shown in the swarm UI. */
    val platform: Platform,

    /**
     * PIN-derived rolling token (see [krill.zone.shared.security.PinDerivation.deriveBeaconToken])
     * proving cluster membership. Receivers MUST verify this against their own
     * derivation before treating the sender as a swarm peer; mismatches indicate
     * a beacon from a different swarm sharing the LAN.
     *
     * Empty string is used during pre-pairing FTUE traffic.
     */
    val clusterToken: String = "",
) {
    /**
     * Returns the [host] field with whitespace, newlines, and accidental
     * `http://` / `https://` prefixes stripped.
     *
     * Some platforms have been observed embedding a stray newline or scheme
     * prefix when reading the local address — sanitising here keeps callers
     * (and the [url] extension) free of that quirk.
     */
    fun host(): String {
        return host.replace("\n", "").trim().removeSuffix("http://").removeSuffix("https://")
    }
}

/**
 * Returns the canonical `https://<host>:<port>` URL for this peer's HTTP/SSE
 * endpoint. Krill requires TLS on every interconnect, so the scheme is always
 * `https`.
 */
fun NodeWire.url(): String {
    return "https://${host()}:$port"
}
