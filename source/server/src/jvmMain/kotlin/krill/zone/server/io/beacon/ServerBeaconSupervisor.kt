package krill.zone.server.io.beacon

import co.touchlab.kermit.*
import kotlinx.coroutines.*
import krill.zone.shared.io.beacon.*
import krill.zone.shared.io.http.*
import org.koin.ext.*

/**
 * Supervises the beacon listener process.
 * Sends a startup beacon, then only responds to incoming beacons from new peers.
 * No periodic repeater — beacons are event-driven.
 */
class ServerBeaconSupervisor(

    private val beaconWireHandler: BeaconWireHandler,
    private val beaconSender: BeaconSender,
    private val multicast: Multicast,
    private val scope: CoroutineScope,

    ) : BeaconSupervisor {
    private val logger = Logger.withTag(this::class.getFullName())
    private var beaconListenerJob: Job? = null

    override fun startBeaconProcess() {
        if (beaconListenerJob != null) return

        beaconListenerJob = scope.launch {
            logger.i { "Starting beacon listener" }
            try {
                // Send one startup beacon to announce ourselves
                beaconSender.sendSignal()

                // Then listen — responses are triggered by handleIncomingWire
                multicast.receiveBeacons { wire ->
                    beaconWireHandler.handleIncomingWire(wire)
                }
                awaitCancellation()
            } catch (e: CancellationException) {
                logger.i { "Beacon listener cancelled" }
                throw e
            } catch (e: Exception) {
                logger.e(e) { "Error in beacon listener" }
            }
        }

        beaconListenerJob?.invokeOnCompletion {
            beaconListenerJob = null
            logger.i("Beacon listener job exited")
        }
    }
}
