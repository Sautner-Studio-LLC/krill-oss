package krill.zone.shared.io.beacon

import krill.zone.shared.node.*

fun interface BeaconWireHandler {

    fun handleIncomingWire(wire: NodeWire)
}