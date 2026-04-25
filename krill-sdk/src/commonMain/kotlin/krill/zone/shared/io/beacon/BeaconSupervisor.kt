/**
 * Lifecycle owner for Krill's beacon machinery. Wraps the send loop, the
 * receive loop, and the discovery-debouncer that flips
 * [BeaconSupervisor.discoveryComplete] when the LAN appears to have
 * settled.
 *
 * One implementation per platform / role: the Compose client has a foreground
 * / background-aware variant; the server has a long-running variant tied to
 * the Ktor application lifecycle.
 */
package krill.zone.shared.io.beacon

import kotlinx.coroutines.flow.StateFlow

/**
 * Coordinator for the beacon send and receive loops.
 *
 * A single implementation typically owns:
 *  * a [BeaconSender] that fires periodically,
 *  * a [BeaconWireHandler] that processes incoming beacons, and
 *  * the discovery debouncer that closes the initial LAN-wide sweep.
 */
interface BeaconSupervisor {
    /**
     * `true` once the initial discovery sweep has settled — defined as
     * "no new beacons for 3 s, capped at 5 s after start". Consumers (the
     * onboarding screen, integration tests) use this to wait for the swarm
     * graph to stabilise before reading it.
     */
    val discoveryComplete: StateFlow<Boolean>

    /**
     * Spins up the send and receive coroutines. Idempotent — calling twice on
     * the same instance is a no-op. The supervisor manages its own coroutine
     * scope; cancel that scope (or rely on the platform lifecycle) to stop.
     */
    fun startBeaconProcess()
}
