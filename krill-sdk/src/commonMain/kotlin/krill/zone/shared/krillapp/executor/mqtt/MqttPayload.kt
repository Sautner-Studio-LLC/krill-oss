/**
 * On-the-wire payload exchanged with an MQTT broker by the `MQTT` executor.
 *
 * Kept as a separate type from [MqttMetaData] so the broker-facing serializer
 * stays minimal — most MQTT message topics in Krill carry only a single
 * state string.
 */
package krill.zone.shared.krillapp.executor.mqtt

import kotlinx.serialization.*

/** A single MQTT message body — a stringified state value. */
@Serializable
data class MqttPayload(val state: String)
