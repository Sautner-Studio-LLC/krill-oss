/**
 * A single chat message in the Krill ↔ LLM dialogue. Mirrors the
 * Ollama / OpenAI chat-completion schema so the server can speak to either
 * backend without translation.
 *
 * Lives both inside [krill.zone.shared.krillapp.server.llm.Chat] (server →
 * client) and inside `LLMMetaData.chat` (the persisted conversation history
 * on the LLM node).
 */
package krill.zone.shared.krillapp.server.llm

import kotlinx.serialization.*

/**
 * One chat message — the role-tagged unit of conversation between the user,
 * the assistant, and (in the case of tool calls) the tools.
 */
@Serializable
data class Message(
    /** Body of the message; markdown-flavoured plain text. */
    @SerialName("content")
    val content: String,
    /** Speaker role — typically `"user"`, `"assistant"`, `"system"`, or `"tool"`. */
    @SerialName("role")
    val role: String,
    /** Base64-encoded image payloads for multimodal models. */
    @SerialName("images")
    val images: List<String> = emptyList(),
    /** Raw chain-of-thought emitted by the model; surfaced for debugging only. */
    @SerialName("thinking")
    val thinking: String = "",
    /** Tool invocations the model wants the server to perform. Empty for normal replies. */
    @SerialName("tool_calls")
    val toolCalls: List<ToolCall> = emptyList(),
)
