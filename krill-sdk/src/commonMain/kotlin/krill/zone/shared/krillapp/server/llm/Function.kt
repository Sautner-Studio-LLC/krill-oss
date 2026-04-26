/**
 * The `function` half of an LLM tool-call request — names the tool the model
 * wants invoked and (in future) carries structured arguments. Mirrors the
 * Ollama / OpenAI tool-calling schema so a Krill server can sit in front of
 * either backend.
 */
package krill.zone.shared.krillapp.server.llm

import kotlinx.serialization.*

/**
 * Description of a single tool invocation requested by an LLM.
 */
@Serializable
data class Function(
    /** Structured arguments to the tool. Currently empty; see [Arguments]. */
    @SerialName("arguments")
    val arguments: Arguments,
    /** Human-readable description echoed back from the model's tool catalogue. */
    @SerialName("description")
    val description: String,
    /** Tool name — must match a registered tool on the Krill server. */
    @SerialName("name")
    val name: String,
)
