/**
 * `EventPayload` carrying a fully materialised [Node]. Emitted on `CREATED`
 * events so listeners can render a freshly spawned node without an extra
 * `/node/<id>` round-trip.
 */
package krill.zone.shared.events

import kotlinx.serialization.*
import krill.zone.shared.node.Node

/** Payload of a `CREATED` event — the brand-new node, in full. */
@Serializable
data class NodeCreatedPayload(val node: Node) : EventPayload
