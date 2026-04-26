/**
 * One tool-call entry inside an LLM message. The Ollama / OpenAI schema
 * wraps the actual tool name + arguments in a single-field object whose only
 * key is `function`; this type matches that shape.
 */
package krill.zone.shared.krillapp.server.llm

import kotlinx.serialization.*

/** Wrapper carrying a single [Function] invocation; matches the upstream protocol shape. */
@Serializable
data class ToolCall(
    @SerialName("function")
    val function: Function,
)
