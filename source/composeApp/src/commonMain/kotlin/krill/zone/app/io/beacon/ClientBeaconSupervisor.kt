package krill.zone.app.io.beacon

import co.touchlab.kermit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import krill.zone.shared.io.beacon.*
import krill.zone.shared.io.http.*
import org.koin.ext.*
import kotlin.time.Clock

/**
 * Supervises the beacon listener process on client apps.
 * Sends a startup beacon, then only listens — responses are handled by the wire handler.
 * No periodic repeater — beacons are event-driven.
 *
 * Exposes [discoveryComplete] which emits true after the initial beacon sweep finishes
 * (3 seconds of no new beacons, capped at 5 seconds total).
 */
class ClientBeaconSupervisor(

    private val beaconWireHandler: BeaconWireHandler,
    private val beaconSender: BeaconSender,
    private val multicast: Multicast,
    private val scope: CoroutineScope
) : BeaconSupervisor {
    private val logger = Logger.withTag(this::class.getFullName())
    private var beaconListenerJob: Job? = null

    private val _discoveryComplete = MutableStateFlow(false)
    override val discoveryComplete: StateFlow<Boolean> = _discoveryComplete.asStateFlow()

    private var discoveryTimerJob: Job? = null
    private var discoveryStartTime = 0L

    override fun startBeaconProcess() {
        if (beaconListenerJob != null) return

        _discoveryComplete.value = false
        discoveryStartTime = Clock.System.now().toEpochMilliseconds()

        beaconListenerJob = scope.launch {
            logger.i { "Starting beacon listener" }
            try {
                // Send one startup beacon to announce ourselves
                beaconSender.sendSignal()

                // Start the discovery completion timer
                startDiscoveryTimer()

                // Then listen — wire handler processes incoming beacons
                multicast.receiveBeacons { wire ->
                    onBeaconReceived()
                    beaconWireHandler.handleIncomingWire(wire)
                }
                awaitCancellation()
            } catch (e: CancellationException) {
                logger.i { "Beacon listener cancelled" }
                throw e
            } catch (e: Exception) {
                logger.e(e) { "Error in beacon listener" }
                // Mark discovery complete on error so UI doesn't wait forever
                _discoveryComplete.value = true
            }
        }

        beaconListenerJob?.invokeOnCompletion {
            beaconListenerJob = null
            logger.i("Beacon listener job exited")
        }
    }

    /** Start or restart the 3-second idle timer. Capped at 5 seconds from discovery start. */
    private fun startDiscoveryTimer() {
        discoveryTimerJob?.cancel()
        discoveryTimerJob = scope.launch {
            val elapsed = Clock.System.now().toEpochMilliseconds() - discoveryStartTime
            val remaining = (5000L - elapsed).coerceAtLeast(0)
            val waitTime = minOf(3000L, remaining)
            delay(waitTime)
            _discoveryComplete.value = true
            logger.i { "Beacon discovery complete" }
        }
    }

    /** Called on each beacon received — resets the idle timer unless we've hit the 5s cap. */
    private fun onBeaconReceived() {
        if (_discoveryComplete.value) return
        val elapsed = Clock.System.now().toEpochMilliseconds() - discoveryStartTime
        if (elapsed >= 5000L) {
            _discoveryComplete.value = true
        } else {
            startDiscoveryTimer()
        }
    }
}
