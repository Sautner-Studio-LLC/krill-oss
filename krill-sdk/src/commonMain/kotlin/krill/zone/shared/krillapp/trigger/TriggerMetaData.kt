/**
 * Default metadata for generic numeric-threshold triggers
 * (`HighThreshold`, `LowThreshold`). Each subtype shares the
 * same shape — a name and a configured threshold value — so a single
 * data class powers all of them.
 */
package krill.zone.shared.krillapp.trigger

import kotlinx.serialization.*
import krill.zone.shared.krillapp.datapoint.*
import krill.zone.shared.node.*

/**
 * Payload for the simple-threshold trigger family.
 *
 * The interpretation of [value] depends on the [krill.zone.shared.KrillApp]
 * subclass the node is wearing: for `HighThreshold` it is the upper bound,
 * for `LowThreshold` the lower bound
 *
 * Default [name] uses the class's `simpleName` so a brand-new node already
 * has a sensible label without the user editing anything.
 */
@Serializable
data class TriggerMetaData(
    val name: String = this::class.simpleName!!,
    val value: Double = 0.0,
    override val nodeAction: NodeAction = NodeAction.EXECUTE,
    override val error: String = "",
    override val sources: List<NodeIdentity> = emptyList(),
    override val snapshot: Snapshot = Snapshot(),
    override val invocationTriggers: List<InvocationTrigger> = emptyList(),
    override val inputs: List<NodeIdentity> = emptyList(),
) : SourceMetaData
