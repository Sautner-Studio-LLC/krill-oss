/**
 * Metadata for a `Project.Journal` node — a chronological log of user-
 * authored notes attached to a project. Each note (with optional photos) is
 * a [JournalEntry] in [JournalMetaData.entries].
 */
package krill.zone.shared.krillapp.project.journal

import kotlinx.serialization.*
import krill.zone.shared.node.ExecutionSource
import krill.zone.shared.node.NodeAction
import krill.zone.shared.node.NodeIdentity
import krill.zone.shared.node.TargetingNodeMetaData

/**
 * Payload for a `Project.Journal` node.
 */
@Serializable
data class JournalMetaData(
    /** Display name shown on the journal tile. */
    val name: String = "Journal",
    /** Optional free-form description. */
    val description: String = "",
    /** All entries in the journal, in chronological order (oldest first). */
    val entries: List<JournalEntry> = emptyList(),
    /** Epoch millis the journal was created. */
    val createdAt: Long = 0L,
    /** Epoch millis of the most recent edit (entry add/remove or metadata change). */
    val updatedAt: Long = 0L,
    override val error: String = "",
    override val sources: List<NodeIdentity> = emptyList(),
    override val targets: List<NodeIdentity> = emptyList(),
    override val executionSource: List<ExecutionSource> = emptyList(),
    override val nodeAction: NodeAction = NodeAction.EXECUTE,
) : TargetingNodeMetaData
