package krill.zone.server.io.beacon

import co.touchlab.kermit.*
import kotlinx.coroutines.*
import krill.zone.server.*
import krill.zone.shared.*
import krill.zone.shared.io.beacon.*
import krill.zone.shared.node.*
import org.koin.ext.*

/**
 * Handles incoming beacon wires on the server.
 * Only responds with our own beacon when a new peer joins the swarm
 * (not already connected). Validates cluster token before responding.
 */
class ServerBeaconWireHandler(

    private val serverConnector: ServerConnector,
    private val beaconSender: BeaconSender,
    private val pinProvider: PinProvider,
    private val nodeManager: ServerNodeManager,
    private val scope: CoroutineScope
) : BeaconWireHandler {
    private val logger = Logger.withTag(this::class.getFullName())

    override fun handleIncomingWire(wire: NodeWire) {
        scope.launch {
            try {
                val identity = ServerIdentity.getSelfWithInfo()
                // Ignore our own beacons
                if (wire.host() == hostName || wire.host() == "localhost" || wire.host() == identity.host) {
                    return@launch
                }

                // Validate cluster membership via rolling token
                if (pinProvider.isConfigured()) {
                    if (wire.clusterToken.isEmpty() || !pinProvider.validateBeaconToken(wire.installId, wire.clusterToken)) {
                        return@launch
                    }
                } else if (wire.clusterToken.isNotEmpty()) {
                    return@launch
                }

                logger.d { "$hostName received valid beacon from ${wire.host()}:${wire.port}" }

                // Only reply with our beacon if this peer is NEW (not already connected).
                // This prevents constant beacon ping-pong between known peers, but still
                // responds to client apps (which send wire.port=0 since they don't host)
                // so they can discover us.
                val isNewPeer = !nodeManager.nodeAvailable(wire.installId)
                if (isNewPeer) {
                    beaconSender.sendSignal()
                }

                // Only peers that expose a port are server-to-server connections;
                // client apps (wire.port=0) don't need a serverConnector handshake.
                if (wire.port > 0 && wire.installId != installId()) {
                    serverConnector.connectWire(wire)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logger.e(e) { "Error handling beacon from $wire" }
            }
        }
    }
}
