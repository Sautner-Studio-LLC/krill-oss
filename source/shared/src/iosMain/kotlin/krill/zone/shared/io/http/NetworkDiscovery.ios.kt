@file:OptIn(ExperimentalForeignApi::class)

package krill.zone.shared.io.http

// iOS multicast beacon discovery using Darwin POSIX socket APIs.
//
// Deployment requirements:
//   - Info.plist: NSLocalNetworkUsageDescription key (iOS 14+ local network permission)
//   - Entitlements: com.apple.developer.networking.multicast
//     (required for sending/receiving UDP multicast on iOS 14+)

import co.touchlab.kermit.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import krill.zone.shared.node.*
import org.koin.ext.*
import platform.darwin.*
import platform.posix.*

// htons is a C macro on Darwin — not available in Kotlin/Native.
// iOS (ARM) is little-endian; network byte order is big-endian, so swap the bytes.
private fun Int.htons(): UShort = ((this ushr 8 and 0xFF) or (this and 0xFF shl 8)).toUShort()

actual class Multicast actual constructor(private val scope: CoroutineScope) {
    private val logger = Logger.withTag(this::class.getFullName())

    actual suspend fun sendBeacon(wire: NodeWire) {
        logger.d { "sendBeacon: ${wire.host()}:${wire.port}" }
        scope.launch(Dispatchers.IO) {
            val sock = socket(AF_INET, SOCK_DGRAM, 0)
            if (sock < 0) {
                logger.e { "sendBeacon: socket() failed errno=$errno" }
                return@launch
            }
            try {
                memScoped {
                    // Set multicast TTL so beacon stays within the local subnet
                    val ttl = alloc<UByteVar>()
                    ttl.value = DiscoveryConfig.TTL.toUByte()
                    setsockopt(sock, IPPROTO_IP, IP_MULTICAST_TTL, ttl.ptr, sizeOf<UByteVar>().convert())

                    val dest = alloc<sockaddr_in>()
                    dest.sin_family = AF_INET.convert()
                    dest.sin_port = DiscoveryConfig.PORT.htons()
                    dest.sin_addr.s_addr = inet_addr(DiscoveryConfig.GROUP_V4)

                    val payload = encodeBeacon(wire)
                    payload.usePinned { pinned ->
                        val result = sendto(
                            sock,
                            pinned.addressOf(0),
                            payload.size.convert(),
                            0,
                            dest.ptr.reinterpret(),
                            sizeOf<sockaddr_in>().convert()
                        )
                        if (result < 0) {
                            logger.w { "sendBeacon: sendto failed errno=$errno" }
                        }
                    }
                }
            } finally {
                close(sock)
            }
        }
    }

    actual suspend fun receiveBeacons(onPeer: (NodeWire) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val sock = socket(AF_INET, SOCK_DGRAM, 0)
            if (sock < 0) {
                logger.e { "receiveBeacons: socket() failed errno=$errno" }
                return@launch
            }

            if (!setupReceiveSocket(sock)) {
                close(sock)
                return@launch
            }

            try {
                logger.i { "receiveBeacons: listening on ${DiscoveryConfig.GROUP_V4}:${DiscoveryConfig.PORT}" }
                val buf = ByteArray(DiscoveryConfig.MAX_DATAGRAM)
                while (currentCoroutineContext().isActive) {
                    buf.usePinned { pinned ->
                        val n = recvfrom(
                            sock,
                            pinned.addressOf(0),
                            DiscoveryConfig.MAX_DATAGRAM.convert(),
                            0,
                            null,
                            null
                        )
                        when {
                            n > 0 -> decodeBeacon(buf.copyOfRange(0, n.toInt()))?.let(onPeer)
                            n < 0 && errno != EAGAIN -> logger.w { "receiveBeacons: recvfrom error errno=$errno" }
                            // n < 0 with EAGAIN is a normal 1-second SO_RCVTIMEO timeout — continue
                        }
                    }
                }
            } finally {
                memScoped {
                    val mreq = alloc<ip_mreq>()
                    mreq.imr_multiaddr.s_addr = inet_addr(DiscoveryConfig.GROUP_V4)
                    mreq.imr_interface.s_addr = INADDR_ANY
                    setsockopt(sock, IPPROTO_IP, IP_DROP_MEMBERSHIP, mreq.ptr, sizeOf<ip_mreq>().convert())
                }
                close(sock)
            }
        }
    }

    /**
     * Configures [sock] for multicast reception:
     *  - SO_REUSEADDR + SO_REUSEPORT so multiple sockets can bind the same port
     *  - bind to INADDR_ANY:PORT
     *  - join the multicast group
     *  - SO_RCVTIMEO = 1s so the receive loop can check coroutine cancellation
     *
     * Returns false and logs an error if binding fails.
     */
    private fun setupReceiveSocket(sock: Int): Boolean = memScoped {
        val one = alloc<IntVar>()
        one.value = 1
        setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, one.ptr, sizeOf<IntVar>().convert())
        setsockopt(sock, SOL_SOCKET, SO_REUSEPORT, one.ptr, sizeOf<IntVar>().convert())

        val bindAddr = alloc<sockaddr_in>()
        bindAddr.sin_family = AF_INET.convert()
        bindAddr.sin_port = DiscoveryConfig.PORT.htons()
        bindAddr.sin_addr.s_addr = INADDR_ANY
        if (bind(sock, bindAddr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) < 0) {
            logger.e { "receiveBeacons: bind() failed errno=$errno" }
            return false
        }

        val mreq = alloc<ip_mreq>()
        mreq.imr_multiaddr.s_addr = inet_addr(DiscoveryConfig.GROUP_V4)
        mreq.imr_interface.s_addr = INADDR_ANY
        if (setsockopt(sock, IPPROTO_IP, IP_ADD_MEMBERSHIP, mreq.ptr, sizeOf<ip_mreq>().convert()) < 0) {
            logger.w { "receiveBeacons: IP_ADD_MEMBERSHIP failed errno=$errno — multicast may not work on this interface" }
        }

        // 1-second receive timeout so the while(isActive) loop can respond to cancellation
        val timeout = alloc<timeval>()
        timeout.tv_sec = 1
        timeout.tv_usec = 0
        setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, timeout.ptr, sizeOf<timeval>().convert())

        true
    }


}
