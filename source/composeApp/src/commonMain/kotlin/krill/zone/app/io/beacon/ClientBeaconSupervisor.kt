package krill.zone.app.io.beacon

import co.touchlab.kermit.*
import kotlinx.coroutines.*
import krill.zone.shared.io.beacon.*
import krill.zone.shared.io.http.*
import org.koin.ext.*

/**
 * Supervises the beacon listener process.
 * Manages the lifecycle of beacon listening and sending initial startup beacon.
 *
 * @param fileOperations File operations for platform-specific I/O
 * @param beaconWireHandler Handles incoming beacon wires
 * @param beaconSender Sends beacons to network
 * @param multicast Multicast service for beacon communication
 * @param ioScope Coroutine scope for I/O operations
 * @param scope Coroutine scope for general operations
 */
class ClientBeaconSupervisor(

    private val beaconWireHandler: BeaconWireHandler,
    private val beaconSender:  BeaconSender,
    private val multicast: Multicast,
    private val scope: CoroutineScope
) : BeaconSupervisor {
    private val logger = Logger.withTag(this::class.getFullName())
    private var beaconListenerJob: Job? = null
    private var sentStartupBeacon = false

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

        beaconListenerJob?.invokeOnCompletion {
            logger.e("Beacon listener job exited")
        }
    }

    private suspend fun startBeaconListener() {
        try {


            while (currentCoroutineContext().isActive) {
                if (!sentStartupBeacon) {
                    sentStartupBeacon = true
                    beaconSender.sendSignal()
                }
                multicast.receiveBeacons { wire ->
                    logger.d { "beacon recieved $wire" }
                    beaconWireHandler.handleIncomingWire(wire)
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