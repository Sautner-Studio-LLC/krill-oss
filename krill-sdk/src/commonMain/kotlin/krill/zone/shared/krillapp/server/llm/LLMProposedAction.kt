/**
 * One step in the agent's plan, embedded in [LLMResponse.proposedActions].
 *
 * The [action] discriminator selects which of the optional payload fields
 * carries meaningful data: `CREATE_NODES` populates [newNodes], `CREATE_LINKS`
 * populates [newLinks], `UPDATE_NODE` populates [targetNodeId] + [metaUpdates],
 * and `EXPLAIN` populates [explanation]. The other fields are left at their
 * empty defaults so the wire format stays a flat object the agent can emit
 * without conditional structure.
 */
package krill.zone.shared.krillapp.server.llm

import kotlinx.serialization.*

/**
 * A single planned action the LLM agent wants Krill to execute or surface.
 */
@Serializable
data class LLMProposedAction(
    /** Discriminator — selects which payload fields below are meaningful. */
    val action: LLMProposedActionType,
    /** Human-readable summary shown verbatim in the action confirmation UI. */
    val description: String = "",
    /** Populated for [LLMProposedActionType.CREATE_NODES]. */
    val newNodes: List<LLMNewNodeProposal> = emptyList(),
    /** Populated for [LLMProposedActionType.CREATE_LINKS]. */
    val newLinks: List<LLMProposedLink> = emptyList(),
    /** Populated for [LLMProposedActionType.UPDATE_NODE] — the existing node to mutate. */
    val targetNodeId: String? = "",
    /** Populated for [LLMProposedActionType.UPDATE_NODE] — field overrides. */
    val metaUpdates: Map<String, String> = emptyMap(),
    /** Populated for [LLMProposedActionType.EXPLAIN] — the explanation text. */
    val explanation: String = "",
)
