package krill.zone.shared.io.http

import kotlinx.serialization.json.Json
import krill.zone.shared.node.NodeWire

/**
 * Encodes a [NodeWire] to UTF-8 JSON bytes suitable for a multicast datagram.
 */
fun encodeBeacon(json: Json, p: NodeWire): ByteArray =
    json.encodeToString(NodeWire.serializer(), p).encodeToByteArray()

/**
 * Decodes beacon-payload [bytes] back into a [NodeWire], or returns `null`
 * if the bytes don't parse — callers should treat `null` as "ignore this
 * datagram" rather than as an error condition, since stray non-Krill UDP
 * traffic on the multicast group is not unexpected.
 */
fun decodeBeacon(json: Json, bytes: ByteArray): NodeWire? = runCatching {
    json.decodeFromString(NodeWire.serializer(), bytes.decodeToString())
}.getOrNull()
