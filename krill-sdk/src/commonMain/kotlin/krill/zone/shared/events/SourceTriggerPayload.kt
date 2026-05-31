/**
 * Payload for a `SOURCE_TRIGGERED` [Event] — the cross-server SSE transport
 * contract that carries a source-change notification to remote receivers.
 *
 * Under the unified source-owned-verb model the verb applied is the
 * *originating* node's [krill.zone.shared.node.NodeAction], not the
 * receiver's. A woken processor cannot synchronously read a remote source
 * across servers, so the originator's identity **and** its verb travel in
 * this payload; the receiver's processor reads the source's current value
 * (locally, or from the value-bearing `PIN_CHANGED` / `SNAPSHOT_UPDATE`
 * event that accompanies the change) and decides what to do.
 *
 * ## Broadcast, not targeted push (observer model)
 *
 * The originating host emits this payload onto its general `/events` stream
 * for **every** subscriber — it does not address, or even know, which remote
 * nodes observe the source. A remote host receives it only because it
 * *subscribed* (its `PeerObservationClient` opened the stream because a local
 * node lists a source on the originating host). This keeps cross-server
 * delivery a pull/observe relationship: the observer's host listens, the
 * source's host just announces.
 *
 * ## Propagation metadata (cycle bounding)
 *
 * [epoch] and [hopTtl] carry the cross-server propagation context:
 *  - [epoch] — the originating host's monotonic propagation token, so a
 *    receiver can dedupe a payload re-delivered by SSE retransmit/reconnect
 *    within one logical propagation. Defaults to `0` for payloads that
 *    predate the field.
 *  - [hopTtl] — decremented at each host boundary; at `0` the receiver
 *    updates local state but SHALL NOT start a further outbound propagation,
 *    bounding cross-host cycles (`A → B → A → …`). Defaults to
 *    [DEFAULT_HOP_TTL]; legitimate cross-server chains are short.
 *
 * ## Local dispatch rule
 *
 * [SourceTriggerPayload] is **reserved for cross-server SSE transport only**.
 * Local (same-server) invocations SHALL use
 * `ServerNodeManager.invoke(target, by, verb)` instead, which routes through
 * [krill.zone.shared.node.ServerNodeProcessor.onInvoke].
 *
 * The [triggeringSource] field carries a full [krill.zone.shared.node.NodeIdentity]
 * (`nodeId` + `hostId`) so the originator's host identity survives the
 * cross-server hop intact.
 */
package krill.zone.shared.events

import kotlinx.serialization.*
import krill.zone.shared.node.NodeAction
import krill.zone.shared.node.NodeIdentity

/**
 * Identifies the node that just changed and the verb it owns, delivered to
 * every node that lists it as a source with a firing `invocationTrigger`.
 *
 * [nodeAction] defaults to [NodeAction.EXECUTE] so a payload that predates
 * the field (or omits it) yields the original forward behaviour. [epoch] and
 * [hopTtl] default so that a pre-propagation-metadata payload deserialises to
 * a safe "fresh propagation, full TTL budget" value.
 */
@Serializable
data class SourceTriggerPayload(
    /** Identity of the node whose change woke the receiver. */
    val triggeringSource: NodeIdentity,
    /** The originating node's durable verb — applied by the receiver, not its own. */
    val nodeAction: NodeAction = NodeAction.EXECUTE,
    /**
     * Originating host's monotonic propagation token. Lets a receiver dedupe
     * an SSE-redelivered payload within one logical propagation. `0` for
     * payloads predating this field.
     */
    val epoch: Long = 0L,
    /**
     * Remaining cross-host hops. Decremented at each host boundary; at `0`
     * the receiver updates local state but does not re-propagate outward,
     * bounding cross-server cycles. Defaults to [DEFAULT_HOP_TTL].
     */
    val hopTtl: Int = DEFAULT_HOP_TTL,
) : EventPayload {
    companion object {
        /**
         * Default cross-host hop budget for a fresh propagation. Generous —
         * legitimate cross-server source chains are short; the bound exists to
         * stop a misconfigured cycle from storming the swarm.
         */
        const val DEFAULT_HOP_TTL: Int = 16
    }
}
