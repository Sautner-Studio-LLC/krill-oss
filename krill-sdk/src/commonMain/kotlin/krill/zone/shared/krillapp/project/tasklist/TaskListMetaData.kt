/**
 * Metadata for a `Project.TaskList` node — an ordered list of [Task] entries
 * with a single overall [Priority]. The list owns its tasks; clients edit
 * the whole metadata blob (rather than addressing tasks individually) when
 * applying changes.
 */
package krill.zone.shared.krillapp.project.tasklist

import kotlinx.serialization.*
import krill.zone.shared.node.ExecutionSource
import krill.zone.shared.node.NodeAction
import krill.zone.shared.node.NodeIdentity
import krill.zone.shared.node.TargetingNodeMetaData

/**
 * Payload for a `Project.TaskList` node.
 */
@Serializable
data class TaskListMetaData(
    /** Display name shown on the list tile. */
    val name: String = "Task List",
    /** Optional free-form description. */
    val description: String = "",
    /** Tasks in the order the user arranged them. */
    val tasks: List<Task> = emptyList(),
    /** Overall priority for the whole list (used for sorting between lists). */
    val priority: Priority = Priority.NONE,
    /** Epoch millis the list was created. */
    val createdAt: Long = 0L,
    /** Epoch millis of the most recent edit (any task add/remove/edit or metadata change). */
    val updatedAt: Long = 0L,
    override val error: String = "",
    override val sources: List<NodeIdentity> = emptyList(),
    override val targets: List<NodeIdentity> = emptyList(),
    override val executionSource: List<ExecutionSource> = emptyList(),
    override val nodeAction: NodeAction = NodeAction.EXECUTE,
) : TargetingNodeMetaData
