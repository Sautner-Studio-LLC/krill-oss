package krill.zone.app.io.beacon

import co.touchlab.kermit.*
import kotlinx.coroutines.*
import krill.zone.shared.*
import krill.zone.shared.io.beacon.*
import krill.zone.shared.node.*
import krill.zone.shared.node.manager.*
import org.koin.ext.*

/**
 * Handles incoming beacon wires and delegates to appropriate processors.
 * Filters out own beacons and triggers server beacon responses.
 */
class ClientBeaconWireHandler(
    private val nodeManager: ClientNodeManager,
    private val serverConnector: ServerConnector,
    private val scope: CoroutineScope
) : BeaconWireHandler {
    private val logger = Logger.withTag(this::class.getFullName())

    /**
     * Handles an incoming beacon wire from a peer.
     * Ignores own beacons and processes valid peer beacons.
     */
    override fun handleIncomingWire(wire: NodeWire) {
        scope.launch {
            try {
                // Ignore our own beacons
                if (wire.host() == hostName) {
                    return@launch
                }
                if (nodeManager.nodeAvailable(wire.installId)) {
                    nodeManager.readNodeStateOrNull(wire.installId).value?.let { node ->
                        if (node.state == NodeState.EDITING || node.state == NodeState.USER_EDIT) {

                            return@launch
                        }
                    }
                }

                logger.d { "received beacon from ${wire.host()}:${wire.port}" }


                if (wire.port > 0 && wire.installId != installId()) {
                     serverConnector.connectWire(wire)
                }
            } catch (e: Exception) {
                logger.e(e) { "Error handling beacon from $wire" }
            }
        }
    }
}
