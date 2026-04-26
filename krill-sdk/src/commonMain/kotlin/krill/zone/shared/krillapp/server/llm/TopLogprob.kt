/**
 * One alternative-token entry inside a [Logprob.topLogprobs] list. Carries
 * the log-probability the model assigned to a token *other* than the one it
 * actually emitted at that position — used by introspection tooling that
 * surfaces a model's alternative completions.
 */
package krill.zone.shared.krillapp.server.llm

import kotlinx.serialization.*

/** A single alternative-token candidate with its log-probability. */
@Serializable
data class TopLogprob(
    /** Raw byte sequence of the candidate token. */
    @SerialName("bytes")
    val bytes: List<Int>,
    /** Natural log of the probability the model assigned to this token. */
    @SerialName("logprob")
    val logprob: Int,
    /** Decoded string form of the candidate token. */
    @SerialName("token")
    val token: String,
)
