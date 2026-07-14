/**
 * Metadata for the `Compute` executor — runs a configured [ComputeOperation]
 * (average, sum, stddev, ...) over a [ComputeTimeRange]-bounded slice of its
 * source DataPoint's snapshot history, and writes the result to its targets.
 */
package krill.zone.shared.krillapp.executor.compute

import kotlinx.serialization.*
import krill.zone.shared.krillapp.datapoint.Snapshot
import krill.zone.shared.node.InvocationTrigger
import krill.zone.shared.node.NodeAction
import krill.zone.shared.node.NodeIdentity
import krill.zone.shared.node.SourceMetaData

/**
 * Payload for a `Compute` executor node.
 */
@Serializable
data class ComputeMetaData(
    override val sources: List<NodeIdentity> = emptyList(),
    override val snapshot: Snapshot = Snapshot(),
    /** Lookback window the operation applies over. `NONE` means "use the whole series". */
    val range: ComputeTimeRange = ComputeTimeRange.NONE,
    /** Aggregation reduction to apply to the windowed snapshots. */
    val operation: ComputeOperation = ComputeOperation.AVERAGE,
    override val invocationTriggers: List<InvocationTrigger> = emptyList(),
    override val nodeAction: NodeAction = NodeAction.EXECUTE,
    override val error: String = "",
    override val inputs: List<NodeIdentity> = emptyList(),
) : SourceMetaData {
    override fun withError(error: String) = copy(error = error)
    override fun displayName() = ""
}
