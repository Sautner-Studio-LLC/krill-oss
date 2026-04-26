/**
 * One source → target link the LLM agent wants Krill to create as part of
 * a [LLMProposedActionType.CREATE_LINKS] action. Endpoints can refer to
 * already-existing nodes (real ids) or to nodes proposed earlier in the
 * same response (via their temporary `proposedId`s on
 * [LLMNewNodeProposal]).
 */
package krill.zone.shared.krillapp.server.llm

import kotlinx.serialization.*

/**
 * Proposed source → target wiring between two nodes.
 *
 * Host ids are optional so the agent can omit them when the source / target
 * pair is unambiguous within the swarm context.
 */
@Serializable
data class LLMProposedLink(
    val sourceNodeId: String = "",
    val sourceHostId: String = "",
    val targetNodeId: String = "",
    val targetHostId: String = "",
    /** Human-readable rationale shown when the user is asked to confirm. */
    val reason: String = "",
)
