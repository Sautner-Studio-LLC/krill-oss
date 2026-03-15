@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package krill.zone.shared.io.http

import kotlinx.coroutines.*
import krill.zone.shared.*
import krill.zone.shared.node.*

/**
 * Platform-specific multicast implementation for beacon discovery.
 * Handles sending and receiving beacons over the network.
 */
expect class Multicast(scope: CoroutineScope) {
    /** Periodically sends beacons while the coroutine is active. */
    suspend fun sendBeacon(wire: NodeWire )

    /** Receives beacons and invokes [onPeer] for each valid packet. */
    suspend fun receiveBeacons(onPeer: (NodeWire) -> Unit)
}

/**
 * Encodes a NodeWire to bytes for network transmission.
 */
internal fun encodeBeacon(p: NodeWire): ByteArray =
    fastJson.encodeToString(p).encodeToByteArray()

/**
 * Decodes beacon bytes to a NodeWire, returning null if invalid.
 */
internal fun decodeBeacon(bytes: ByteArray): NodeWire? = runCatching {
    fastJson.decodeFromString<NodeWire>(bytes.decodeToString())
}.getOrNull()

