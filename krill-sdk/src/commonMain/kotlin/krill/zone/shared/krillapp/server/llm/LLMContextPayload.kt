/**
 * The full prompt-context bundle sent to the LLM agent: nodes the user
 * selected, the feature contracts for every node type those nodes are, and
 * the static node-type hierarchy. The agent uses this to reason about the
 * swarm without needing per-call schema discovery.
 */
package krill.zone.shared.krillapp.server.llm

import kotlinx.serialization.*

/**
 * Composite agent-context payload.
 *
 * Injected as a system message before the user's prompt. Field set is
 * deliberately small so the prompt window stays focused on what the agent
 * needs to plan; richer queries should use the agent's tool-call surface
 * instead of bloating this type.
 */
@Serializable
data class LLMContextPayload(
    /** Nodes the user explicitly selected as in-scope for this prompt. */
    val contextNodes: List<LLMNodeContext>,
    /** Feature contracts for every unique [LLMNodeContext.type] in [contextNodes]. */
    val featureContracts: List<LLMFeatureSummary>,
    /**
     * The static node-type hierarchy — keyed by parent type's canonical
     * short name (e.g. `"DataPoint"`), values are the legal child type
     * names. Lets the agent verify proposed parent → child wiring without
     * a round-trip.
     */
    val nodeTypeHierarchy: Map<String, List<String>>,
)
