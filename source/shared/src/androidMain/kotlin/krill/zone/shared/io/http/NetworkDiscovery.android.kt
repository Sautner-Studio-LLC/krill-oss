package krill.zone.shared.io.http

import android.content.*
import android.net.wifi.*
import co.touchlab.kermit.*
import kotlinx.coroutines.*
import krill.zone.shared.*
import krill.zone.shared.node.*
import org.koin.ext.*
import java.net.*

actual class Multicast actual constructor(private val scope: CoroutineScope) {
    private val logger = Logger.withTag(this::class.getFullName())
    private var multicastLock: WifiManager.MulticastLock? = null

    private fun v4Group(): InetAddress =
        InetAddress.getByName(DiscoveryConfig.GROUP_V4)

    private fun newSendSocket(): MulticastSocket =
        MulticastSocket().apply {
            timeToLive = DiscoveryConfig.TTL
            broadcast = false
        }

    private fun newReceiveSocket(): MulticastSocket =
        MulticastSocket(DiscoveryConfig.PORT).apply {
            reuseAddress = true
            timeToLive = DiscoveryConfig.TTL
            soTimeout = 1000
        }

    private fun acquireMulticastLock(context: Context) {
        if (multicastLock == null) {
            logger.i("Acquiring WiFi multicast lock for beacon reception")
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("krill_beacon").apply {
                setReferenceCounted(false)
                acquire()
            }
            logger.i("WiFi multicast lock acquired successfully")
        }
    }

    private fun releaseMulticastLock() {
        multicastLock?.release()
        multicastLock = null
        logger.i("WiFi multicast lock released")
    }

    actual suspend fun sendBeacon(wire: NodeWire) {


        logger.i("Starting beacon sender")
        scope.launch {
            newSendSocket().use { sock ->
                val group = v4Group()
                val addr = InetSocketAddress(group, DiscoveryConfig.PORT)

                val payload = encodeBeacon(wire)
                val pkt = DatagramPacket(payload, payload.size, addr)
                sock.send(pkt)

            }
        }

    }


    actual suspend fun receiveBeacons(onPeer: (NodeWire) -> Unit) {
        logger.i("Starting beacon receiver on Android")
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        // Get Android context
        val context = ContextContainer.context

        // Acquire multicast lock to enable multicast reception on Android
        acquireMulticastLock(context)

        try {
            scope.launch {
                newReceiveSocket().use { sock ->
                    val group = v4Group()
                    logger.i("Beacon receiver listening on ${DiscoveryConfig.GROUP_V4}:${DiscoveryConfig.PORT}")

                    val ifaces = NetworkInterface.getNetworkInterfaces().toList()
                        .filter { it.isUp && !it.isLoopback && it.supportsMulticastSafe() }

                    logger.i("Found ${ifaces.size} network interfaces for multicast")
                    ifaces.forEach { nif ->
                        logger.i("Joining multicast group on interface: ${nif.name}")
                        runCatching { sock.joinGroup(InetSocketAddress(group, DiscoveryConfig.PORT), nif) }
                            .onFailure { logger.w("Failed to join multicast group on ${nif.name}: ${it.message}") }
                    }

                    val buf = ByteArray(DiscoveryConfig.MAX_DATAGRAM)
                    while (currentCoroutineContext().isActive) {
                        try {
                            val dp = DatagramPacket(buf, buf.size)
                            sock.receive(dp)

                            val data = dp.data.copyOfRange(0, dp.length)
                            decodeBeacon(data)?.let {

                                onPeer(it)
                            }
                        } catch (_: SocketTimeoutException) {
                            // check cancellation
                        } catch (e: Throwable) {
                            logger.w("Error receiving beacon: ${e.message}")
                            // keep going
                        }
                    }

                    ifaces.forEach { nif ->
                        runCatching { sock.leaveGroup(InetSocketAddress(group, DiscoveryConfig.PORT), nif) }
                    }
                }
            }
        } finally {
            releaseMulticastLock()
            logger.w { "Finishing wiFi multicast lock" }
        }
    }

    private fun NetworkInterface.supportsMulticastSafe(): Boolean =
        runCatching { supportsMulticast() }.getOrElse { true }
}