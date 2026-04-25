/**
 * Connection-graph hints attached to a [KrillFeature]. Used by Krill's LLM
 * tools (and the manual swarm editor) to suggest plausible parent / child /
 * source / target wiring when the user is dropping a new node into a project.
 *
 * The hints are advisory, not enforced — a user is always free to wire nodes
 * outside what the feature suggests.
 */
package krill.zone.shared.feature

import kotlinx.serialization.*

/**
 * Per-node-type hints describing which other node types it is *typically*
 * wired to. Each list contains canonical [krill.zone.shared.KrillApp] short
 * names (e.g. `"Trigger"`, `"Pin"`, `"DataPoint.Graph"`).
 *
 * The lists are empty by default to keep newly added node types free of
 * implicit suggestions until their author opts in.
 */
@Serializable
data class LlmConnectionHints(
    /** Node types that typically appear as direct children of this one. */
    @SerialName("childTypes")
    val childTypes: List<String> = emptyList(),

    /** Node types that typically own this one as a child. */
    @SerialName("parentTypes")
    val parentTypes: List<String> = emptyList(),

    /** Node types that typically feed values into this one (`sources`). */
    @SerialName("sourceTypes")
    val sourceTypes: List<String> = emptyList(),

    /** Node types this one typically writes to (`targets`). */
    @SerialName("targetTypes")
    val targetTypes: List<String> = emptyList(),

    /**
     * Free-form summary of how this type plays in the swarm graph — e.g.
     * `"sensor"`, `"actuator"`, `"trigger source"`. Surfaced verbatim to the
     * LLM as part of the prompt context.
     */
    @SerialName("role")
    val role: String,
)
