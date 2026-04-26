/**
 * Pointer the LLM agent attaches to its [LLMResponse] when it wants the
 * server (or user) to look at an existing node before any [LLMProposedAction]
 * is applied — typically because the action mutates that node or because
 * the agent isn't fully confident about the proposed change.
 */
package krill.zone.shared.krillapp.server.llm

import kotlinx.serialization.*

/**
 * One existing node the agent flags for review.
 *
 * `hostId` is optional so the agent can address a node by id alone when
 * there's no ambiguity in the swarm; populated when the same id appears on
 * more than one server.
 */
@Serializable
data class LLMNodeReview(
    val nodeId: String,
    val hostId: String = "",
    /** Why the agent surfaced this node — shown verbatim in the review prompt. */
    val reason: String,
)
