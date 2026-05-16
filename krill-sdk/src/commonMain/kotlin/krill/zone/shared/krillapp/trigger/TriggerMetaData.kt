/**
 * Default metadata for generic numeric-threshold triggers
 * (`SilentAlarmMs`, `HighThreshold`, `LowThreshold`). Each subtype shares the
 * same shape — a name and a configured threshold value — so a single
 * data class powers all of them.
 */
package krill.zone.shared.krillapp.trigger

import kotlinx.serialization.*
import krill.zone.shared.node.ExecutionSource
import krill.zone.shared.node.NodeAction
import krill.zone.shared.node.NodeIdentity
import krill.zone.shared.node.TargetingNodeMetaData

/**
 * Payload for the simple-threshold trigger family.
 *
 * The interpretation of [value] depends on the [krill.zone.shared.KrillApp]
 * subclass the node is wearing: for `HighThreshold` it is the upper bound,
 * for `LowThreshold` the lower bound, for `SilentAlarmMs` the silence
 * duration in milliseconds.
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
    override val targets: List<NodeIdentity> = emptyList(),
    override val executionSource: List<ExecutionSource> = emptyList(),
) : TargetingNodeMetaData
