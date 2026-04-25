/**
 * Public contracts for the per-node payload (`meta`) that every Krill node
 * carries. The interface ([NodeMetaData]) and the cross-node addressing types
 * ([NodeIdentity], [TargetingNodeMetaData], [ExecutionSource]) live in the SDK
 * so external integrators can implement their own node types and link them
 * into a Krill swarm without depending on the proprietary metadata
 * implementations that ship with the reference server.
 *
 * The concrete `MetaData` data classes (e.g. `ServerMetaData`, `PinMetaData`,
 * `TriggerMetaData`, ...) and the `updateMetaWithError(...)` helper that
 * dispatches across them remain in the consuming module; they reference the
 * full sealed `KrillApp` hierarchy and are migrated subtype-by-subtype.
 */
package krill.zone.shared.node

import kotlinx.serialization.*

/**
 * Marker interface that every node's per-type metadata payload must implement.
 *
 * The single contract — surfacing an [error] string — is what lets generic
 * code (UI badges, the SSE event pump, the polymorphic JSON serializer) treat
 * any node uniformly without knowing the concrete subtype.
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
}

/**
 * Address pair that uniquely identifies a node in a multi-server Krill swarm.
 *
 * Used inside [TargetingNodeMetaData.sources] / [TargetingNodeMetaData.targets]
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
 * Stored as a `List<ExecutionSource>` on [TargetingNodeMetaData] so a single
 * node can opt into multiple firing modes simultaneously (e.g. fire when the
 * source DataPoint changes **and** when the user clicks the node chip).
 *
 * The [displayLabel] is the human-readable string the editor UI shows beside
 * the matching checkbox.
 */
enum class ExecutionSource(val displayLabel: String) {
    /** Fire when the parent node's most recent execution completed successfully. */
    PARENT_EXECUTE_SUCCESS("Parent Execute Success"),

    /** Fire when one of the configured `sources` changes its value. */
    SOURCE_VALUE_MODIFIED("Source Value Modified"),

    /** Fire when the user manually clicks / taps the node in the UI. */
    ON_CLICK("On Click"),
}

/**
 * Common contract for nodes that read from upstream `sources` and write to
 * downstream `targets` — the executor / filter / trigger family.
 *
 * Lifting these three properties out of the individual `MetaData` data classes
 * lets the editor UI render a single "wire your sources and targets" panel
 * regardless of the concrete node type, and lets cross-cutting machinery
 * (cycle detection, dependency graphing, propagation) walk the swarm without
 * caring which subtype it is on.
 */
interface TargetingNodeMetaData : NodeMetaData {
    /**
     * Upstream nodes whose values feed this one. For a filter or executor this
     * is the data being read; for a trigger it is the value being watched.
     */
    val sources: List<NodeIdentity>

    /**
     * Downstream nodes this one writes to or actuates. May be empty for nodes
     * that only side-effect outside the swarm (e.g. an SMTP executor).
     */
    val targets: List<NodeIdentity>

    /**
     * The set of [ExecutionSource]s configured to wake this node. The node
     * processor checks the incoming event against this list before doing any
     * work. An empty list means "never auto-fire" — only manual execution.
     */
    val executionSource: List<ExecutionSource>
}
