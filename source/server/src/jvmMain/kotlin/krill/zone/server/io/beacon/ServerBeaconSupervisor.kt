package krill.zone.server.io.beacon

import co.touchlab.kermit.*
import kotlinx.coroutines.*
import krill.zone.shared.io.beacon.*
import krill.zone.shared.io.http.*
import org.koin.ext.*

/**
 * Supervises the beacon listener process.
 * Manages the lifecycle of beacon listening and sending initial startup beacon.
 *
 * @param beaconWireHandler Handles incoming beacon wires
 * @param beaconSender Sends beacons to network
 * @param multicast Multicast service for beacon communication
 * @param scope Coroutine scope for I/O operations

 */
class ServerBeaconSupervisor(

    private val beaconWireHandler: BeaconWireHandler,
    private val beaconSender: BeaconSender,
    private val multicast: Multicast,
    private val scope: CoroutineScope,

    ) : BeaconSupervisor {
    private val logger = Logger.withTag(this::class.getFullName())
    private var beaconListenerJob: Job? = null
    private var sentStartupBeacon = false

    private var repeaterJob : Job? = null
    /**
     * Starts the beacon listening process if not already started.
     */
    override fun startBeaconProcess() {
        if (beaconListenerJob != null) return

        beaconListenerJob = scope.launch {
            logger.i { "Starting/Restarting beacon listener" }
            try {
                startBeaconListener()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e(e) { "Error in beacon listener supervision" }
            } finally {
                logger.i("Exited in finally")
            }
        }
        repeaterJob = scope.launch {
            beaconRepeater()
        }


        beaconListenerJob?.invokeOnCompletion {
            beaconListenerJob = null
            if (repeaterJob?.isActive == true) repeaterJob?.cancel(CancellationException("Beacon Listener Completed"))
            logger.e("Beacon listener job exited")
        }
    }

    private suspend fun beaconRepeater() {
        logger.i { "Starting beacon repeater" }
        while (currentCoroutineContext().isActive) {
            logger.d { "sending repeater beacon" }
            beaconSender.sendSignal()
            delay(10000)
        }
    }

    private suspend fun startBeaconListener() {

        try {


            while (currentCoroutineContext().isActive) {
                multicast.receiveBeacons { wire ->
                    beaconWireHandler.handleIncomingWire(wire)
                }

                if (!sentStartupBeacon) {
                    sentStartupBeacon = true
                    beaconSender.sendSignal()
                }

                awaitCancellation()
            }

            logger.w { "Beacon listener receiveBeacons() returned unexpectedly" }
        } catch (e: CancellationException) {
            logger.i { "Beacon listener cancelled normally" }
            throw e
        } catch (e: Exception) {
            logger.e(e) { "Error in beacon service" }
            throw e // Re-throw to trigger supervision restart
        }
    }
}