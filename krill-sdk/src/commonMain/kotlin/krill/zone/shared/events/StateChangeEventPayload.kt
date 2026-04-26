/**
 * Payload for a `STATE_CHANGE` [Event] — carries the new
 * [krill.zone.shared.node.NodeState] of the affected node. Drives the
 * coloured chip on the swarm UI without forcing a re-fetch of the full node.
 */
package krill.zone.shared.events

import kotlinx.serialization.*
import krill.zone.shared.node.NodeState

/** New lifecycle state after a `STATE_CHANGE` event. */
@Serializable
data class StateChangeEventPayload(val state: NodeState) : EventPayload
