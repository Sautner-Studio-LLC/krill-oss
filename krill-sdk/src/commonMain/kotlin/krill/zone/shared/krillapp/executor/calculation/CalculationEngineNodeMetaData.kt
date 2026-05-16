/**
 * Metadata for the `Calculation` executor — runs a user-supplied formula
 * against its source DataPoints and writes the result to its targets.
 *
 * The formula is evaluated server-side by an embedded expression engine; the
 * client editor just stores the source string verbatim.
 */
package krill.zone.shared.krillapp.executor.calculation

import kotlinx.serialization.*
import krill.zone.shared.node.ExecutionSource
import krill.zone.shared.node.NodeAction
import krill.zone.shared.node.NodeIdentity
import krill.zone.shared.node.TargetingNodeMetaData

/**
 * Payload for a `Calculation` executor node.
 */
@Serializable
data class CalculationEngineNodeMetaData(
    override val sources: List<NodeIdentity> = emptyList(),
    override val targets: List<NodeIdentity> = emptyList(),
    /** Display name; defaults to the class's simple name on creation. */
    val name: String = this::class.simpleName!!,
    /** Free-form formula string evaluated by the server's calculation engine. */
    val formula: String = "",
    override val executionSource: List<ExecutionSource> = emptyList(),
    override val nodeAction: NodeAction = NodeAction.EXECUTE,
    override val error: String = "",
) : TargetingNodeMetaData
