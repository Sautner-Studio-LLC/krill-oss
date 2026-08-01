/**
 * Metadata for a `Swarm.Batch` node — a group of related LLM work items
 * dispatched together as child `Swarm.Work` nodes, sharing one set of
 * eligibility requirements and tracked as a single unit of progress.
 *
 * SDK-only contract: fan-out into child `Swarm.Work` nodes, arbitration, and
 * progress accounting are krill-side processor behavior. This class only
 * defines the wire shape.
 */
package krill.zone.shared.krillapp.swarm

import kotlinx.serialization.*
import krill.zone.shared.krillapp.datapoint.FileRef
import krill.zone.shared.krillapp.datapoint.Snapshot
import krill.zone.shared.node.InvocationTrigger
import krill.zone.shared.node.NodeAction
import krill.zone.shared.node.NodeIdentity
import krill.zone.shared.node.SourceMetaData

/**
 * One item's payload within a [SwarmBatchMetaData] — the per-item counterpart
 * of [WorkMetaData.prompt] / [WorkMetaData.images] / [WorkMetaData.fileRefs],
 * minus the fields the batch already carries as shared requirements.
 */
@Serializable
data class SwarmBatchItem(
    val prompt: String,
    val images: List<String> = emptyList(),
    val fileRefs: List<FileRef> = emptyList(),
)

/**
 * Payload for a `Swarm.Batch` node.
 */
@Serializable
data class SwarmBatchMetaData(
    /** The batch's item payloads — one child `Swarm.Work` node is dispatched per item. */
    val items: List<SwarmBatchItem> = emptyList(),
    /** Required advertised model shared by every item in this batch; empty means any. */
    val requiredModel: String = "",
    /** Minimum advertised VRAM class shared by every item in this batch. */
    val minVramClass: String = "",
    /** Maximum advertised cost score shared by every item in this batch. */
    val maxCostScore: Double = Double.MAX_VALUE,
    /** Epoch millis after which unclaimed items are abandoned; `0` means wait forever. */
    val deadlineAt: Long = 0,
    /** Maximum number of child `Swarm.Work` nodes this batch may spawn at once. */
    val maxChildren: Int = DEFAULT_MAX_CHILDREN,
    /** Count of items whose child work reached [SwarmWorkPhase.DONE]. */
    val completedCount: Int = 0,
    /** Count of items whose child work reached [SwarmWorkPhase.FAILED]. */
    val failedCount: Int = 0,

    override val error: String = "",
    override val sources: List<NodeIdentity> = emptyList(),
    override val snapshot: Snapshot = Snapshot(),
    override val invocationTriggers: List<InvocationTrigger> = emptyList(),
    override val nodeAction: NodeAction = NodeAction.EXECUTE,
    override val inputs: List<NodeIdentity> = emptyList(),
) : SourceMetaData {
    override fun withError(error: String) = copy(error = error)
    override fun displayName() = ""

    companion object {
        /** Default cap on concurrently in-flight child `Swarm.Work` nodes. */
        const val DEFAULT_MAX_CHILDREN: Int = 16
    }
}
