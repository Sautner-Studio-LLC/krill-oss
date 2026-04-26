/**
 * No-op payload used as the default value of [Event.payload] when an event
 * carries only its [EventType] tag and needs no additional data (typically
 * `ACK` or `DELETED` events).
 *
 * Carries an explicit `noop` field rather than being a `data object` because
 * kotlinx.serialization's polymorphic discriminator is friendlier with
 * non-empty data classes — and the `noop: ""` payload survives JSON
 * round-trips cleanly.
 */
package krill.zone.shared.events

import kotlinx.serialization.*

/** Empty `EventPayload` used as the default when no extra data is needed. */
@Serializable
data class EmptyPayload(val noop: String = "") : EventPayload
