package krill.zone.shared.io.http

import kotlinx.coroutines.*
import krill.zone.shared.node.*

actual class Multicast actual constructor(scope: CoroutineScope) {
    actual suspend fun sendBeacon(wire: NodeWire) {
        //wasm doesn't send beacons
    }

    actual suspend fun receiveBeacons(onPeer: (NodeWire) -> Unit) {
        //wasm doesn't receive beacons
    }
}