/**
 * Metadata for the `Compute` executor — runs a configured [ComputeOperation]
 * (average, sum, stddev, ...) over a [ComputeTimeRange]-bounded slice of its
 * source DataPoint's snapshot history, and writes the result to its targets.
 */
package krill.zone.shared.krillapp.executor.compute

import kotlinx.serialization.*
import krill.zone.shared.node.ExecutionSource
import krill.zone.shared.node.NodeIdentity
import krill.zone.shared.node.TargetingNodeMetaData

/**
 * Payload for a `Compute` executor node.
 */
@Serializable
data class ComputeMetaData(
    override val sources: List<NodeIdentity> = emptyList(),
    override val targets: List<NodeIdentity> = emptyList(),
    /** Lookback window the operation applies over. `NONE` means "use the whole series". */
    val range: ComputeTimeRange = ComputeTimeRange.NONE,
    /** Aggregation reduction to apply to the windowed snapshots. */
    val operation: ComputeOperation = ComputeOperation.AVERAGE,
    override val executionSource: List<ExecutionSource> = emptyList(),
    override val error: String = "",
) : TargetingNodeMetaData
