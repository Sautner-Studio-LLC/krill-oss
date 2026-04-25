/**
 * Metadata for a `Project` node — the top-level workspace inside a Krill
 * server that owns Diagrams, TaskLists, Journals, Cameras and other
 * project-scoped child nodes.
 */
package krill.zone.shared.krillapp.project

import kotlinx.serialization.*
import krill.zone.shared.node.NodeMetaData

/**
 * Payload for a `Project` node — the user-facing display name plus an
 * optional free-form description.
 */
@Serializable
data class ProjectMetaData(
    val name: String = "Project",
    val description: String = "",
    override val error: String = "",
) : NodeMetaData
