/**
 * Metadata for a downstream target of a `Server.SerialDevice` write operation.
 * Pairs a literal value with a target identifier so the SerialDevice node can
 * route distinct writes to distinct named targets without conflating them.
 */
package krill.zone.shared.krillapp.server.serialdevice

import kotlinx.serialization.*
import krill.zone.shared.node.ExecutionSource
import krill.zone.shared.node.NodeAction
import krill.zone.shared.node.NodeIdentity
import krill.zone.shared.node.TargetingNodeMetaData

/**
 * Payload for the auxiliary "serial-device-target" node type that wraps a
 * single named outbound write configuration.
 */
@Serializable
data class SerialDeviceTargetMetaData(
    /** Display name of the target slot. */
    val name: String,
    /** Free-form target identifier the SerialDevice processor matches against. */
    val target: String = "",
    /** Literal value to write when this target is fired. */
    val value: String = "",
    override val error: String = "",
    override val sources: List<NodeIdentity> = emptyList(),
    override val targets: List<NodeIdentity> = emptyList(),
    override val executionSource: List<ExecutionSource> = emptyList(),
    override val nodeAction: NodeAction = NodeAction.EXECUTE,
) : TargetingNodeMetaData
