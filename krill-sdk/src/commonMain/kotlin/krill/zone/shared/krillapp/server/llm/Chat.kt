/**
 * Top-level wire envelope of an Ollama-style chat-completion response. One
 * [Chat] object holds the assistant's [Message], plus model and timing
 * metadata. Krill servers proxy this shape verbatim to clients so the
 * underlying inference backend stays an implementation detail.
 *
 * All numeric fields default to `0` and the message defaults to a sentinel
 * "default response, probably an error" so a deserialisation that hits an
 * empty body still yields something the UI can display rather than throwing.
 */
package krill.zone.shared.krillapp.server.llm

import kotlinx.serialization.*

/**
 * Full chat-completion response from an LLM backend.
 */
@Serializable
data class Chat(
    /** ISO-8601 timestamp of when the response was generated upstream. */
    @SerialName("created_at")
    val createdAt: String = "",
    /** `true` when this is the final chunk of a streamed response. */
    @SerialName("done")
    val done: Boolean = false,
    /** Reason the upstream stopped generating (`"stop"`, `"length"`, etc.). */
    @SerialName("done_reason")
    val doneReason: String = "",
    /** Number of evaluated tokens for the response itself. */
    @SerialName("eval_count")
    val evalCount: Long = 0,
    /** Wall-clock time in nanoseconds spent evaluating the response. */
    @SerialName("eval_duration")
    val evalDuration: Long = 0,
    /** Wall-clock time in nanoseconds the model spent loading. */
    @SerialName("load_duration")
    val loadDuration: Long = 0,
    /** Optional per-token log-probabilities; empty unless introspection is enabled. */
    @SerialName("logprobs")
    val logprobs: List<Logprob> = emptyList(),
    /** The assistant's reply. */
    @SerialName("message")
    val message: Message = Message(content = "default response, probably an error", role = ""),
    /** Identifier of the model that produced the response. */
    @SerialName("model")
    val model: String = "",
    /** Number of evaluated tokens in the prompt. */
    @SerialName("prompt_eval_count")
    val promptEvalCount: Long = 0,
    /** Wall-clock time in nanoseconds spent evaluating the prompt. */
    @SerialName("prompt_eval_duration")
    val promptEvalDuration: Long = 0,
    /** Total wall-clock time in nanoseconds for the whole request. */
    @SerialName("total_duration")
    val totalDuration: Long = 0,
)
