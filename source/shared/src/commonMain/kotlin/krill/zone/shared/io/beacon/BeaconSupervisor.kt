package krill.zone.shared.io.beacon

import kotlinx.coroutines.flow.*

interface BeaconSupervisor {
    /** True after the initial beacon discovery sweep has completed (no new beacons for 3s, capped at 5s). */
    val discoveryComplete: StateFlow<Boolean>
    fun startBeaconProcess()
}