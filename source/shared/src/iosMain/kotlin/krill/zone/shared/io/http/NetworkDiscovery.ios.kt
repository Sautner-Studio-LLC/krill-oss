package krill.zone.shared.io.http

import kotlinx.coroutines.*
import krill.zone.shared.node.*

actual  class Multicast actual constructor(scope: CoroutineScope) {
    actual suspend fun sendBeacon(wire: NodeWire) {
        //NOOP - iOS does not send beacons
    }

    actual suspend fun receiveBeacons(onPeer: (NodeWire) -> Unit) {
        //NOOP - iOS does not recieve beacons
    }
}