/**
 * Metadata for the `DataPoint.Graph` node — a visualisation of a sibling
 * DataPoint's snapshot history over a configurable [ComputeTimeRange].
 *
 * The graph is rendered client-side; the server's only responsibility is to
 * serve the underlying snapshot series via `/node/<id>/data/series`.
 */
package krill.zone.shared.krillapp.datapoint.graph

import kotlinx.serialization.*
import krill.zone.shared.krillapp.datapoint.Snapshot
import krill.zone.shared.krillapp.executor.compute.ComputeTimeRange
import krill.zone.shared.node.ExecutionSource
import krill.zone.shared.node.NodeAction
import krill.zone.shared.node.NodeIdentity
import krill.zone.shared.node.SourceMetaData

/**
 * Payload for a `DataPoint.Graph` node.
 *
 * Implements [SourceMetaData] for consistency with the other
 * source-bound node types, but only [sources] is meaningful — graphs read
 * data, they don't write it. [targets] is kept empty.
 */
@Serializable
data class GraphMetaData(
    /**
     * Display name. Empty string falls back to `"Graph"` in [krill.zone.shared.node.name].
     * The previous default of `"Data Graph"` produced indistinguishable siblings under
     * different parent DataPoints; callers (UI, MCP) are expected to supply a
     * parent-derived name like `"<parent> graph"` at construction time.
     */
    val name: String = "",
    override val sources: List<NodeIdentity> = emptyList(),
    /** Not used for graphs but required by the [SourceMetaData] contract. */
    override val snapshot: Snapshot = Snapshot(),
    /** Lookback window the graph displays — drives the X-axis range. */
    val timeRange: ComputeTimeRange = ComputeTimeRange.HOUR,
    override val executionSource: List<ExecutionSource> = emptyList(),
    override val nodeAction: NodeAction = NodeAction.EXECUTE,
    override val error: String = "",
) : SourceMetaData
