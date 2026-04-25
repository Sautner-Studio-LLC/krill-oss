/**
 * Trigger contract for whatever component is responsible for emitting
 * Krill peer-discovery beacons. Implementations are platform / context
 * specific (the Compose client and the server each ship their own) and are
 * wired in through Koin.
 */
package krill.zone.shared.io.beacon

/**
 * SAM type for "fire one beacon now".
 *
 * Implementations build a [krill.zone.shared.node.NodeWire], serialise it to
 * JSON and put it on the multicast group defined by
 * [krill.zone.shared.io.http.PeerConstants]. Cadence is determined by the
 * caller (typically the [BeaconSupervisor]); this interface only encapsulates
 * the act of sending a single beacon.
 */
fun interface BeaconSender {
    /**
     * Emits one beacon onto the multicast group. Suspends until the platform
     * socket reports the datagram has been queued; does not wait for any
     * acknowledgement (multicast is best-effort).
     */
    suspend fun sendSignal()
}
