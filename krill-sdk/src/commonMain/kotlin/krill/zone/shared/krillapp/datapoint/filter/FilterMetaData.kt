/**
 * Default metadata for the `DataPoint.Filter` family
 * (`DiscardAbove`, `DiscardBelow`, `Deadband`, `Debounce`). Like the
 * threshold-trigger family, every filter subtype shares the same on-wire
 * shape so one data class covers all of them — the [krill.zone.shared.KrillApp]
 * subtype the node is wearing tells the runtime which numeric semantics to
 * apply.
 */
package krill.zone.shared.krillapp.datapoint.filter

import kotlinx.serialization.*
import krill.zone.shared.krillapp.datapoint.Snapshot
import krill.zone.shared.node.InvocationTrigger
import krill.zone.shared.node.NodeAction
import krill.zone.shared.node.NodeIdentity
import krill.zone.shared.node.SourceMetaData

/**
 * Payload for any `DataPoint.Filter` node — a name and a single numeric
 * configuration value (cutoff, deadband width, debounce window, etc.).
 */
@Serializable
data class FilterMetaData(
    val name: String = this::class.simpleName!!,
    override val error: String = "",
    override val sources: List<NodeIdentity> = emptyList(),
    override val snapshot: Snapshot = Snapshot(),
    override val invocationTriggers: List<InvocationTrigger> = emptyList(),
    override val nodeAction: NodeAction = NodeAction.EXECUTE,
override val inputs: List<NodeIdentity> = emptyList(),
) : SourceMetaData
