/**
 * Metadata for the `LogicGate` executor — evaluates a boolean function (see
 * [LogicGate]) over its source DataPoints and writes the result to its
 * targets. Used to glue together pin states or DIGITAL DataPoints with
 * boolean wiring without writing a Calculation formula.
 */
package krill.zone.shared.krillapp.executor.logicgate

import kotlinx.serialization.*
import krill.zone.shared.node.ExecutionSource
import krill.zone.shared.node.NodeAction
import krill.zone.shared.node.NodeIdentity
import krill.zone.shared.node.TargetingNodeMetaData

/**
 * Payload for a `LogicGate` executor node.
 *
 * The default `sources` list is `[NodeIdentity("", "")]` (one empty slot)
 * rather than empty so the editor renders an empty source row that the user
 * can fill in immediately, matching the legacy behaviour of older clients.
 */
@Serializable
data class LogicGateMetaData(
    val name: String = "logic gate",
    /** Which boolean function to evaluate; see [LogicGate]. */
    val gateType: LogicGate = LogicGate.BUFFER,
    override val sources: List<NodeIdentity> = listOf(NodeIdentity("", "")),
    override val targets: List<NodeIdentity> = emptyList(),
    override val executionSource: List<ExecutionSource> = emptyList(),
    override val nodeAction: NodeAction = NodeAction.EXECUTE,
    override val error: String = "",
) : TargetingNodeMetaData
