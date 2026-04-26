/**
 * Payload for a `PIN_CHANGED` [Event] — carries the new digital level a
 * `Server.Pin` was just observed at. Listeners use this to update their
 * cached pin state without re-fetching the whole node.
 */
package krill.zone.shared.events

import kotlinx.serialization.*
import krill.zone.shared.node.DigitalState

/** New pin level after a `PIN_CHANGED` event. */
@Serializable
data class PinEventPayload(val state: DigitalState) : EventPayload
