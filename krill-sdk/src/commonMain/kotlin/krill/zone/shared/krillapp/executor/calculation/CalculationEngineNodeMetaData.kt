/**
 * Metadata for the `Calculation` executor — runs a user-supplied formula
 * against its source DataPoints and writes the result to its targets.
 *
 * The formula is evaluated server-side by an embedded expression engine; the
 * client editor just stores the source string verbatim.
 */
package krill.zone.shared.krillapp.executor.calculation

import kotlinx.serialization.*
import krill.zone.shared.krillapp.datapoint.*
import krill.zone.shared.node.*

/**
 * Payload for a `Calculation` executor node.
 */
@Serializable
data class CalculationEngineNodeMetaData(
    override val sources: List<NodeIdentity> = emptyList(),

    /**
     * Display name; defaults to the literal `"Calculation"` on creation.
     *
     * Was previously `this::class.simpleName!!`, but because this is a
     * `@Serializable` data class the default-value initializer is compiled into
     * a synthetic context on the generated `Companion` — so `this::class`
     * resolved to the `Companion` object and every brand-new Calculation node
     * was labelled `"Companion"` instead of its type. A literal keeps the label
     * stable and independent of the class name / serialization machinery.
     */
    val name: String = "Calculation",
    /** Free-form formula string evaluated by the server's calculation engine. */
    val formula: String = "",
    /**
     * DOUBLE DataPoints referenced by [formula] (bracket tokens
     * `[hostId:nodeId]`). Read at evaluation time only — NOT invocation
     * sources (invocation is wired via [sources] / the Sources tab).
     */
    override val inputs: List<NodeIdentity> = emptyList(),
    /**
     * Last computed value + timestamp. The readable output a DataPoint
     * subscriber pulls when this calc wakes it. `null` until first compute.
     */
    override val snapshot: Snapshot = Snapshot(),
    override val invocationTriggers: List<InvocationTrigger> = emptyList(),
    override val nodeAction: NodeAction = NodeAction.EXECUTE,
    override val error: String = "",

) : SourceMetaData {
    override fun withError(error: String) = copy(error = error)
    override fun displayName() = name
}
