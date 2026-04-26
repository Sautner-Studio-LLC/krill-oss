/**
 * Slim, agent-context form of a [Node] sent to an LLM as part of the
 * prompt. Carries the same fields as a `Node` (id / parent / host / type /
 * state / meta / timestamp) but lives here so the agent's prompt schema is
 * stable even if `Node` evolves new internal-only fields in the future.
 */
package krill.zone.shared.krillapp.server.llm

import kotlinx.serialization.*
import krill.zone.shared.KrillApp
import krill.zone.shared.node.Node
import krill.zone.shared.node.NodeMetaData
import krill.zone.shared.node.NodeState

/**
 * Compact agent-facing projection of a [Node].
 *
 * Use [from] to build an instance from an existing `Node`; otherwise the
 * primary constructor is fine for hand-built test fixtures.
 */
@Serializable
data class LLMNodeContext(
    val id: String,
    val parent: String,
    val host: String,
    val type: KrillApp,
    val state: NodeState,
    val meta: NodeMetaData,
    val timestamp: Long,
) {
    companion object {
        /** Project a real [Node] into its agent-context form. */
        fun from(node: Node): LLMNodeContext = LLMNodeContext(
            id = node.id,
            parent = node.parent,
            host = node.host,
            type = node.type,
            state = node.state,
            meta = node.meta,
            timestamp = node.timestamp,
        )
    }
}
