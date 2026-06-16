/**
 * Public contracts for the per-node payload (`meta`) that every Krill node
 * carries. The interface ([NodeMetaData]) and the cross-node addressing types
 * ([NodeIdentity], [SourceMetaData], [InvocationTrigger], [NodeAction],
 * [ActionNodeMetaData]) live in the SDK so external integrators can implement
 * their own node types and link them into a Krill swarm without depending on
 * the proprietary metadata implementations that ship with the reference server.
 *
 * The concrete `MetaData` data classes (e.g. `ServerMetaData`, `PinMetaData`,
 * `TriggerMetaData`, ...) all live in `krill-sdk` under the
 * `krill.zone.shared.krillapp.*` packages and implement this interface.
 */
package krill.zone.shared.node

import kotlinx.serialization.*
import krill.zone.shared.krillapp.datapoint.*

/**
 * Marker interface that every node's per-type metadata payload must implement.
 *
 * Beyond surfacing an [error] string, the interface declares three extension
 * points that let cross-cutting helpers (error propagation, display naming,
 * digital-signal detection) work uniformly over any concrete subtype without
 * an exhaustive `when` dispatch:
 *
 * - [withError] — **abstract**: every implementing `data class` must supply
 *   a `copy(error = error)` override.  Making it abstract ensures a compile
 *   error rather than a silent no-op when a new subtype is introduced.
 * - [displayName] — default `""`: concrete types that carry a human-readable
 *   name field override this; callers fall back to the node's [KrillApp] type
 *   string when the result is empty.
 * - [isDigital] — default `false`: overridden to `true` for node types whose
 *   values are inherently boolean (Pin, LogicGate, TaskList) and to
 *   `dataType == DIGITAL` for DataPoint.
 *
 * The implementation classes are `@Serializable` data classes registered in
 * the consuming module's `Serializer.kt` polymorphic module; missing such a
 * registration is a runtime crash, not a compile error, so when adding a new
 * subtype always update the serializer module alongside it.
 */
interface NodeMetaData {
    /**
     * Last known error message for this node, or empty string when healthy.
     *
     * The UI surfaces a non-empty value as a red badge on the node chip; the
     * server clears it (writes `""`) on the next successful processing pass.
     */
    val error: String

    /**
     * Returns a copy of this metadata with [error] replaced by the given string.
     *
     * **Must be implemented** by every concrete `data class` as
     * `override fun withError(error: String) = copy(error = error)`.
     * Declared abstract (no default) so that adding a new subtype without this
     * override is a compile error rather than a runtime silent-no-op.
     */
    fun withError(error: String): NodeMetaData

    /**
     * Human-readable display name for this node, or empty string when the
     * concrete type has no dedicated name field.
     *
     * Callers that need a non-empty label (e.g. [krill.zone.shared.node.name])
     * fall back to the node's [krill.zone.shared.KrillApp] type string when
     * this returns `""`.
     */
    fun displayName(): String = ""

    /**
     * `true` when this node's value is inherently boolean / digital.
     *
     * Overridden to `true` in Pin, LogicGate, and TaskList metadata;
     * overridden to `dataType == DIGITAL` in DataPointMetaData.
     * All other types keep the default `false`.
     */
    fun isDigital(): Boolean = false
}

/**
 * Address pair that uniquely identifies a node in a multi-server Krill swarm.
 *
 * Used inside [SourceMetaData.sources] / [SourceMetaData.targets]
 * so callers never have to parse a `"host:id"` string back into its components.
 *
 * `@Serializable` because the type rides inside polymorphic node payloads and
 * is reflected back to clients verbatim.
 */
@Serializable
data class NodeIdentity(
    /** The target node's own UUID, unique within its [hostId] server. */
    val nodeId: String,
    /** The UUID of the Krill server (host) that owns the node. */
    val hostId: String,
) {
    /**
     * Renders the identity as `"<hostId>:<nodeId>"` — the format used in log
     * lines, error messages, and the `details()` extension on [Node].
     */
    override fun toString(): String {
        return "${hostId}:${nodeId}"
    }
}

/**
 * The set of events that can cause an executor / filter / trigger node to
 * fire its work.
 *
 * Stored as a `List<ExecutionSource>` on [SourceMetaData] so a single
 * node can opt into multiple firing modes simultaneously (e.g. fire when the
 * source DataPoint changes **and** when the user clicks the node chip).
 *
 * The [displayLabel] is the human-readable string the editor UI shows beside
 * the matching checkbox.
 */
enum class InvocationTrigger(val displayLabel: String) {

    /** Fire when one of the configured `sources` changes its value. */
    SOURCE_INVOKED("Source Value Modified"),

    /** Fire when the user manually clicks / taps the node in the UI. */
    ON_CLICK("On Click"),
}

/**
 * The verb a trigger or executor node performs when it fires.
 *
 * Stored on [ActionNodeMetaData] so the swarm processor knows whether to run
 * the normal forward execution path ([EXECUTE]) or to undo / clear the target
 * node back to its initial state ([RESET]).
 *
 * The [displayLabel] is the human-readable string shown in the editor's action
 * picker. Declared `@Serializable` so it travels in the node payload; enum
 * names are the wire form — do not rename without a coordinated migration.
 */
@Serializable
enum class NodeAction(val displayLabel: String) {
    /** Run the node's primary execution path — the default behaviour. */
    EXECUTE("Execute"),

    /** Revert the target node(s) to their initial / cleared state. */
    RESET("Reset"),
}

/**
 * Extended contract for trigger and executor metadata that carries an explicit
 * [nodeAction] discriminator.
 *
 * Applying this interface to the trigger-family and [SourceMetaData]
 * hierarchy lets the server processor and the editor UI branch on action
 * without per-type `when` arms. The default on every concrete implementation
 * is [NodeAction.EXECUTE], preserving wire-compatibility with pre-0.0.23
 * payloads that lack the field.
 */
interface ActionNodeMetaData : NodeMetaData {
    /**
     * The action this node performs when it fires. Defaults to [NodeAction.EXECUTE]
     * on every concrete implementation so that deserialising a payload that
     * predates this field yields the original behaviour unchanged.
     */
    val nodeAction: NodeAction
}

/**
 * Common contract for nodes that read from upstream `sources` and write to
 * downstream `targets` — the executor / filter / trigger family.
 *
 * Extends [ActionNodeMetaData] so that every targeting node also carries an
 * explicit [nodeAction] discriminator, letting the processor branch on
 * execute-vs-reset without per-type `when` arms.
 *
 * Lifting these properties out of the individual `MetaData` data classes lets
 * the editor UI render a single "wire your sources and targets" panel
 * regardless of the concrete node type, and lets cross-cutting machinery
 * (cycle detection, dependency graphing, propagation) walk the swarm without
 * caring which subtype it is on.
 */
interface SourceMetaData : ActionNodeMetaData {
    /**
     * Upstream nodes whose values feed this one. For a filter or executor this
     * is the data being read; for a trigger it is the value being watched.
     */
    val sources: List<NodeIdentity>

    /**
     * The set of [InvocationTrigger]s configured to wake this node. The node
     * processor checks the incoming event against this list before doing any
     * work. An empty list means "never auto-fire" — only manual execution.
     */

    val invocationTriggers: List<InvocationTrigger>

    /**
     * Stores the last result of when this node was invoked and is a source of data
     */
    val snapshot: Snapshot

    /**
     * sets the nodes this node reads from to complete its work when invoked
     */
    val inputs: List<NodeIdentity>

}
