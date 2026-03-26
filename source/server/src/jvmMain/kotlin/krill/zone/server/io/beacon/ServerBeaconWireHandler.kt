package krill.zone.server.io.beacon

import co.touchlab.kermit.*
import kotlinx.coroutines.*
import krill.zone.server.*
import krill.zone.shared.*
import krill.zone.shared.io.beacon.*
import krill.zone.shared.node.*
import org.koin.ext.*

/**
 * Handles incoming beacon wires and delegates to appropriate processors.
 * Filters out own beacons and triggers server beacon responses.
 */
class ServerBeaconWireHandler(

    private val serverConnector: ServerConnector,
    private val beaconSender: BeaconSender,
    private val pinProvider: PinProvider,
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
                val identity = ServerIdentity.getSelfWithInfo()
                // Ignore our own beacons
                if (wire.host() == hostName || wire.host() == "localhost" || wire.host() == identity.host) {
                    return@launch
                }

                logger.d { "$hostName received beacon from ${wire.host()}:${wire.port}" }

                // Validate cluster membership via rolling token
                if (pinProvider.isConfigured()) {
                    if (wire.clusterToken.isEmpty() || !pinProvider.validateBeaconToken(wire.installId, wire.clusterToken)) {
                        // Silently discard non-cluster beacons
                        return@launch
                    }
                } else if (wire.clusterToken.isNotEmpty()) {
                    // We have no PIN but they do — silently ignore
                    return@launch
                }

                // When server gets ANY valid beacon, announce itself
                beaconSender.sendSignal()


                // Process the wire (download cert if from another server)
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
