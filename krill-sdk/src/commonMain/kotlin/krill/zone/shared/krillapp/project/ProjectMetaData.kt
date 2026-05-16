/**
 * Metadata for a `Project` node — the top-level workspace inside a Krill
 * server that owns Diagrams, TaskLists, Journals, Cameras and other
 * project-scoped child nodes.
 */
package krill.zone.shared.krillapp.project

import kotlinx.serialization.*
import krill.zone.shared.node.ExecutionSource
import krill.zone.shared.node.NodeAction
import krill.zone.shared.node.NodeIdentity
import krill.zone.shared.node.TargetingNodeMetaData

/**
 * Payload for a `Project` node — the user-facing display name plus an
 * optional free-form description.
 */
@Serializable
data class ProjectMetaData(
    val name: String = "Project",
    val description: String = "",
    override val error: String = "",
    override val sources: List<NodeIdentity> = emptyList(),
    override val targets: List<NodeIdentity> = emptyList(),
    override val executionSource: List<ExecutionSource> = emptyList(),
    override val nodeAction: NodeAction = NodeAction.EXECUTE,
) : TargetingNodeMetaData
