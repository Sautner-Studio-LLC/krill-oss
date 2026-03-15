package krill.zone.server.io.beacon

import co.touchlab.kermit.*
import kotlinx.coroutines.sync.*
import krill.zone.server.*
import krill.zone.shared.*
import krill.zone.shared.io.beacon.*
import krill.zone.shared.io.http.*
import krill.zone.shared.krillapp.server.*
import krill.zone.shared.node.*
import org.koin.ext.*
import kotlin.concurrent.atomics.*
import kotlin.time.*

/**
 * Sends beacon signals to the network for peer discovery.
 * Rate-limits beacon sending to avoid network flooding.
 *
 * @param multicast Multicast service for sending beacons
 */
class ServerBeaconSender(private val nodeManager: ServerNodeManager, private val multicast: Multicast ) : BeaconSender {
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
        val identity = ServerIdentity.getSelfWithInfo()
        mutex.withLock {
            val im = identity.meta as ServerMetaData
            val node = nodeManager.readNodeState(installId()).value
            val meta = node.meta as ServerMetaData
            val wire = NodeWire(
                timestamp = Clock.System.now().toEpochMilliseconds(),
                installId = installId(),
                host = im.name,
                port = meta.port,
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