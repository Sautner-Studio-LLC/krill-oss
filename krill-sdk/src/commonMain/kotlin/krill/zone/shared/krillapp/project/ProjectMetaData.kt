/**
 * Metadata for a `Project` node — the top-level workspace inside a Krill
 * server that owns Diagrams, TaskLists, Journals, Cameras and other
 * project-scoped child nodes.
 */
package krill.zone.shared.krillapp.project

import kotlinx.serialization.*
import krill.zone.shared.krillapp.datapoint.Snapshot
import krill.zone.shared.node.InvocationTrigger
import krill.zone.shared.node.NodeAction
import krill.zone.shared.node.NodeIdentity
import krill.zone.shared.node.SourceMetaData

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
    override val snapshot: Snapshot = Snapshot(),
    override val invocationTriggers: List<InvocationTrigger> = emptyList(),
    override val nodeAction: NodeAction = NodeAction.EXECUTE,
override val inputs: List<NodeIdentity> = emptyList(),
) : SourceMetaData {
    override fun withError(error: String) = copy(error = error)
    override fun displayName() = name
}
