package krill.zone.shared.events

import kotlinx.serialization.*
import krill.zone.shared.krillapp.datapoint.*
import krill.zone.shared.node.*
import kotlin.time.*
import kotlin.uuid.*

interface EventPayload

enum class EventType {
    STATE_CHANGE, SNAPSHOT_UPDATE, PIN_CHANGED, DELETED, ACK
}

@Serializable
data class Event @OptIn(ExperimentalUuidApi::class) constructor(
    val id: String,
    val type: EventType,
    val payload: EventPayload = EmptyPayload(),
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val eventId: String = Uuid.random().toString())

@Serializable
data class PinEventPayload(val state: DigitalState) : EventPayload

@Serializable
data class StateChangeEventPayload(val state: NodeState) : EventPayload

@Serializable
data class SnapshotUpdatedEventPayload(val snapshot: Snapshot) : EventPayload

@Serializable
data class EmptyPayload(val noop : String = "") : EventPayload