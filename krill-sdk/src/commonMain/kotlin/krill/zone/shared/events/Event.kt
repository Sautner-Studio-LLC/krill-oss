/**
 * Core event-stream types: the [EventPayload] discriminator interface, the
 * [EventType] tag, and the [Event] wrapper that the SSE stream actually
 * delivers. The concrete [EventPayload] implementations
 * (`PinEventPayload`, `StateChangeEventPayload`, ...) live in sibling
 * files so each can be migrated to the SDK independently of the others.
 *
 * Cross-module polymorphism is supported here: `EventPayload` lives in the
 * SDK; some implementations (e.g. `NodeCreatedPayload`, `LLMEventPayload`)
 * remain in `/shared` and register against this base interface in the
 * shared-module `Serializer.kt`. kotlinx.serialization is fine with that as
 * long as the FQNs are stable.
 */
package krill.zone.shared.events

import kotlinx.serialization.*
import kotlin.time.*
import kotlin.uuid.*

/**
 * Marker interface implemented by every payload that travels inside an
 * [Event]. Polymorphic — concrete subclasses are registered in the
 * project-wide serializer module under [EventPayload]'s class.
 */
interface EventPayload

/**
 * The kind of swarm event being broadcast.
 *
 * Names are stable wire identifiers — reordering is fine, but renaming
 * breaks any client that pinned to the string form.
 */
enum class EventType {
    /** A node's [krill.zone.shared.node.NodeState] changed. */
    STATE_CHANGE,

    /** A DataPoint captured a fresh `Snapshot`. */
    SNAPSHOT_UPDATE,

    /** A `Server.Pin` flipped between ON and OFF. */
    PIN_CHANGED,

    /** An LLM-related event (request/response/streaming chunk). */
    LLM,

    /** A node was deleted. */
    DELETED,

    /** A node was created. */
    CREATED,

    /** Server acknowledgement of a client message. */
    ACK,
}

/**
 * The wire envelope every SSE / event-bus message rides in.
 *
 * `id` is assigned by the producer and used by [krill.zone.shared.events.EventTracker]
 * for deduplication — distinct from [eventId], which is a fresh UUID per
 * envelope so retransmits can be correlated. [timestamp] defaults to
 * `Clock.System.now()` so producers that don't care about wall-clock can
 * just call `Event(id, type, payload)`.
 */
@Serializable
data class Event @OptIn(ExperimentalUuidApi::class) constructor(
    val id: String,
    val type: EventType,
    val payload: EventPayload = EmptyPayload(),
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val eventId: String = Uuid.random().toString(),
)
