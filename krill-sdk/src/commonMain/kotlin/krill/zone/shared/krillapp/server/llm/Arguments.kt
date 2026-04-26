/**
 * Placeholder type for the `arguments` field of a tool / function call in the
 * Ollama-style chat protocol. Tool calls in Krill currently do not carry
 * structured arguments, but the JSON shape requires the field to exist.
 *
 * Declared as an empty `@Serializable` class so the generated serializer
 * emits / accepts an empty JSON object (`{}`) — the wire format expected by
 * Ollama-compatible servers.
 */
package krill.zone.shared.krillapp.server.llm

import kotlinx.serialization.*

/** Empty `@Serializable` carrier for the `arguments` field of a [Function] call. */
@Serializable
class Arguments
