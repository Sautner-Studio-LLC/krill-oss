/**
 * Payload for a `SOURCE_TRIGGERED` [Event] — the dispatch contract that wakes
 * a receiver when one of its configured `sources` changes.
 *
 * Under the unified source-owned-verb model the verb applied is the
 * *originating* node's [krill.zone.shared.node.NodeAction], not the
 * receiver's. A woken processor cannot synchronously read a remote source
 * across servers, so the originator's identity **and** its verb travel in
 * this payload; the receiver's processor reads the source's current value
 * (locally, or from the value-bearing `PIN_CHANGED` / `SNAPSHOT_UPDATE`
 * event that accompanies the change) and decides what to do.
 */
package krill.zone.shared.events

import kotlinx.serialization.*
import krill.zone.shared.node.NodeAction
import krill.zone.shared.node.NodeIdentity

/**
 * Identifies the node that just changed and the verb it owns, delivered to
 * every node that lists it as a source with a firing `executionSource`.
 *
 * [nodeAction] defaults to [NodeAction.EXECUTE] so a payload that predates
 * the field (or omits it) yields the original forward behaviour.
 */
@Serializable
data class SourceTriggerPayload(
    /** Identity of the node whose change woke the receiver. */
    val triggeringSource: NodeIdentity,
    /** The originating node's durable verb — applied by the receiver, not its own. */
    val nodeAction: NodeAction = NodeAction.EXECUTE,
) : EventPayload
