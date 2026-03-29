package krill.zone.app.io.beacon

import co.touchlab.kermit.*
import kotlinx.coroutines.*
import krill.zone.shared.io.beacon.*
import krill.zone.shared.io.http.*
import org.koin.ext.*

/**
 * Supervises the beacon listener process on client apps.
 * Sends a startup beacon, then only listens — responses are handled by the wire handler.
 * No periodic repeater — beacons are event-driven.
 */
class ClientBeaconSupervisor(

    private val beaconWireHandler: BeaconWireHandler,
    private val beaconSender: BeaconSender,
    private val multicast: Multicast,
    private val scope: CoroutineScope
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

                // Then listen — wire handler processes incoming beacons
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
