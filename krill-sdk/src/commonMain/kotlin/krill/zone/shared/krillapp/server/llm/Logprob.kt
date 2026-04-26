/**
 * Per-token log-probability data emitted by an LLM in introspection mode.
 * One [Logprob] per generated token; the [topLogprobs] list contains the
 * model's k-best alternatives for that position.
 */
package krill.zone.shared.krillapp.server.llm

import kotlinx.serialization.*

/** Log-probability information for a single emitted token plus its alternatives. */
@Serializable
data class Logprob(
    /** Raw byte sequence of the emitted token. */
    @SerialName("bytes")
    val bytes: List<Int>,
    /** Natural log of the probability assigned to the emitted token. */
    @SerialName("logprob")
    val logprob: Int,
    /** Decoded string form of the emitted token. */
    @SerialName("token")
    val token: String,
    /** Top-k alternative tokens the model considered at this position. */
    @SerialName("top_logprobs")
    val topLogprobs: List<TopLogprob>,
)
