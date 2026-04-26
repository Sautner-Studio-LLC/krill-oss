/**
 * One node the LLM agent wants Krill to create as part of a
 * [LLMProposedActionType.CREATE_NODES] action. Each proposal carries a
 * temporary [proposedId] so other proposals (and [LLMProposedLink] entries)
 * in the same response can reference it as a parent or link endpoint
 * before the server has assigned final UUIDs.
 */
package krill.zone.shared.krillapp.server.llm

import kotlinx.serialization.*

/**
 * Proposed-node payload — the agent's request "please create one of these".
 *
 * [type] is sent as a string (e.g. `"KrillApp.Executor.LogicGate"`) rather
 * than a typed `KrillApp` reference so this class can live in the SDK
 * without depending on the still-shared `KrillApp` sealed class.
 */
@Serializable
data class LLMNewNodeProposal(
    /** Temporary UUID assigned by the agent — used for cross-references inside the same response. */
    val proposedId: String,
    /**
     * ID of the parent node — either an existing real node id from context,
     * or another proposal's [proposedId] from the same response.
     */
    val parent: String,
    /** Canonical KrillApp short name (e.g. `"KrillApp.Executor.LogicGate"`). */
    val type: String,
    /** Human-readable rationale the agent attaches to the proposal. */
    val reason: String = "",
    /** Initial metadata field overrides; the server validates against the type's schema. */
    val initialMeta: Map<String, String> = emptyMap(),
)
