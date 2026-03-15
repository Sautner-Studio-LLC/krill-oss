package krill.zone.shared.io.http

import co.touchlab.kermit.*
import kotlinx.coroutines.sync.*
import krill.zone.shared.*
import krill.zone.shared.io.beacon.*
import krill.zone.shared.krillapp.client.*
import krill.zone.shared.node.*
import krill.zone.shared.node.manager.*
import org.koin.ext.*
import kotlin.concurrent.atomics.*
import kotlin.time.*

/**
 * Sends beacon signals to the network for peer discovery.
 * Rate-limits beacon sending to avoid network flooding.
 *
 * @param multicast Multicast service for sending beacons
 */
class ClientBeaconSender(private val nodeManager: ClientNodeManager, private val multicast: Multicast) : BeaconSender {
    private val logger = Logger.withTag(this::class.getFullName())

    @OptIn(ExperimentalAtomicApi::class)
    private val lastSentTimestamp = AtomicReference<Long?>(0L)

    private val mutex = Mutex()

    /**
     * Sends a one-time beacon signal to the network.
     * Rate-limited to prevent sending beacons too frequently.
     */
    @OptIn(ExperimentalAtomicApi::class, ExperimentalTime::class)
    override suspend fun sendSignal() {
        val timeSinceLastBeacon = Clock.System.now().toEpochMilliseconds() - (lastSentTimestamp.load() ?: 0L)

        mutex.withLock {

            val node = nodeManager.readNodeState(installId()).value
            val meta = node.meta as ClientMetaData
            val wire = NodeWire(
                timestamp = Clock.System.now().toEpochMilliseconds(),
                installId = installId(),
                host = meta.name,
                port = 0,
                platform = platform,
            )


            if (timeSinceLastBeacon > PeerConstants.BEACON_MIN_INTERVAL_MS) {
                multicast.sendBeacon(wire)
                logger.i("Sent beacon: $wire")
                lastSentTimestamp.update { Clock.System.now().toEpochMilliseconds() }
            }
        }
    }

}