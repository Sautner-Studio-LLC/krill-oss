package krill.zone.shared.io.http

import co.touchlab.kermit.*
import kotlinx.coroutines.*
import krill.zone.shared.node.*
import org.koin.ext.*
import java.net.*

actual class Multicast actual constructor(private val scope: CoroutineScope) {
    private val logger = Logger.withTag(this::class.getFullName())

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

    actual suspend fun sendBeacon(wire: NodeWire) {
        logger.d("sending beacon: ${wire.host()}:${wire.port}")

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
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        scope.launch {
            newReceiveSocket().use { sock ->
                val group = v4Group()

                val ifaces = NetworkInterface.getNetworkInterfaces().toList()
                    .filter { it.isUp && !it.isLoopback && it.supportsMulticastSafe() }

                ifaces.forEach { nif ->
                    runCatching { sock.joinGroup(InetSocketAddress(group, DiscoveryConfig.PORT), nif) }
                }

                val buf = ByteArray(DiscoveryConfig.MAX_DATAGRAM)
                while (currentCoroutineContext().isActive) {
                    try {
                        val dp = DatagramPacket(buf, buf.size)
                        sock.receive(dp)
                        val data = dp.data.copyOfRange(0, dp.length)
                        decodeBeacon(data)?.let(onPeer)

                    } catch (_: SocketTimeoutException) {
                        // check cancellation
                    } catch (_: Throwable) {
                        // keep going
                    }
                }

                ifaces.forEach { nif ->
                    runCatching { sock.leaveGroup(InetSocketAddress(group, DiscoveryConfig.PORT), nif) }
                }
            }
        }
    }

    private fun NetworkInterface.supportsMulticastSafe(): Boolean =
        runCatching { supportsMulticast() }.getOrElse { true }
}



