package krill.zone.shared.io.http

import co.touchlab.kermit.*
import kotlinx.coroutines.sync.*
import krill.zone.shared.*
import krill.zone.shared.io.beacon.*
import krill.zone.shared.krillapp.client.*
import krill.zone.shared.node.*
import krill.zone.shared.node.manager.*
import krill.zone.shared.security.*
import org.koin.ext.*
import kotlin.concurrent.atomics.*
import kotlin.time.*

/**
 * Sends beacon signals to the network for peer discovery.
 * Rate-limits beacon sending to avoid network flooding.
 *
 * @param multicast Multicast service for sending beacons
 * @param pinStore  Stored PIN-derived bearer token, used to sign beacons so
 *                  PIN-protected servers accept them (servers drop beacons
 *                  whose [NodeWire.clusterToken] is missing or stale).
 */
class ClientBeaconSender(
    private val nodeManager: ClientNodeManager,
    private val multicast: Multicast,
    private val pinStore: ClientPinStore?,
) : BeaconSender {
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
                clusterToken = computeClusterToken(),
            )


            if (timeSinceLastBeacon > PeerConstants.BEACON_MIN_INTERVAL_MS) {
                multicast.sendBeacon(wire)
                logger.i("Sent beacon: $wire")
                lastSentTimestamp.update { Clock.System.now().toEpochMilliseconds() }
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun computeClusterToken(): String {
        val bearer = pinStore?.bearerToken() ?: return ""
        val epochSeconds = Clock.System.now().toEpochMilliseconds() / 1000
        return PinDerivation.deriveBeaconToken(bearer, installId(), epochSeconds)
    }

}
