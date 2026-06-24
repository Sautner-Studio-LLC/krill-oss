/**
 * Two core state enums used by every Krill node: a binary pin-level state
 * ([DigitalState]) and the richer lifecycle state ([NodeState]) that drives
 * UI colour, processor gating, and SSE event emission across the swarm.
 *
 * Both enums are part of the public wire contract: their ordinals and names
 * are observed by clients across JVM, Android, iOS, and wasmJs and MUST NOT
 * be reordered or renamed without a coordinated migration.
 */
package krill.zone.shared.node

import kotlinx.serialization.*

/**
 * Binary state for nodes that represent a single on/off signal — typically
 * GPIO pins and pin-backed abstractions (solenoids, relays, LEDs, buttons).
 *
 * `@Serializable` because this value travels across the SSE stream and is
 * persisted inside snapshot payloads.
 */
@Serializable
enum class DigitalState {
    /** The signal is asserted — pin high, relay closed, LED lit. */
    ON,

    /** The signal is not asserted — pin low, relay open, LED off. */
    OFF,
}

@Serializable
enum class PinPullResistance {

    PULL_DOWN,

    PULL_UP,

    OFF,
}

/** Maps [DigitalState] to a numeric value for graphing: `ON → 1.0`, `OFF → 0.0`. */
fun DigitalState.toDouble(): Double = when (this) {
    DigitalState.ON -> 1.0
    DigitalState.OFF -> 0.0
}

/**
 * Returns `true` when a node in this state may receive a deliberate invocation.
 *
 * [NodeState.PAUSED] is documented as suppressing execution — the node exists
 * but its processor is deliberately suspended by user action. [NodeState.DELETING]
 * means the node is in the middle of removal; firing work into it is semantically
 * wrong and the server will reject it.
 *
 * All other states (including error and transient execution states such as
 * [NodeState.PROCESSING] and [NodeState.EXECUTED]) are considered invokable:
 * the server remains the authoritative gatekeeper, and the SDK should not
 * over-eagerly block invocations that the server might still accept.
 *
 * Used by [krill.zone.shared.node.NodeHttp.invokeNode] to short-circuit
 * network calls for clearly non-invokable states.
 */
fun NodeState.isInvokable(): Boolean = when (this) {
    NodeState.PAUSED -> false
    NodeState.DELETING -> false
    else -> true
}

/**
 * Lifecycle / status state for every Krill node.
 *
 * Consumed widely: the UI colours node chips by this value, processors gate
 * work on it (e.g., `PAUSED` suppresses execution), the SSE layer emits
 * changes as `STATE_CHANGE` events, and downstream logic (triggers, filters)
 * reacts to specific transitions.
 *
 * Both enum names and ordinals are part of the public wire contract and are
 * observed by clients across JVM, Android, iOS, and wasmJs — do not rename
 * or reorder without a coordinated migration.
 *
 * Use [isInvokable] to check whether a node in this state may receive a
 * deliberate invocation (via [krill.zone.shared.node.NodeHttp.invokeNode]).
 */
@Serializable
enum class NodeState {
    /** Processing is suspended by user action; the node exists but is not running. */
    PAUSED,

    /** Informational status — the node is healthy and has a non-alarm message to surface in the UI. */
    INFO,

    /** A warning-level condition the operator should see but no immediate intervention is required. */
    WARN,

    /** A severe condition — node may still be running but degraded; prompts attention. */
    SEVERE,

    /** A hard error condition — the node failed to execute, connect, or read. */
    ERROR,

    /** The node is in a pairing / discovery handshake (e.g., a Zigbee device being joined). */
    PAIRING,

    /** Default idle state — the node exists and has nothing noteworthy to report. */
    NONE,

    /** The node is actively executing its work (reading a sensor, firing an executor, etc.). */
    PROCESSING,

    /** The node just completed a successful execution; often a transient state visible briefly in the UI. */
    EXECUTED,

    /** The node is in the middle of being deleted; shown briefly so clients can animate the removal. */
    DELETING,

    /** The node was just created; shown briefly to allow the UI to fade it in, or it changed in a way where it needs to be completely refreshed */
    CREATE_OR_OVERWRITE,

    /** The user has opened the node in the editor but has not yet submitted changes. */
    USER_EDIT,

    /** The user has submitted edits; the node will transition away once the server acknowledges. */
    USER_SUBMIT,

    /**
     * A snapshot (historical time-series payload) arrived for this node; used to invalidate
     * cached views on the client side so the next read pulls fresh data.
     */
    SNAPSHOT_UPDATE,

    /** The caller lacks authorisation to view or mutate this node (missing/invalid API key). */
    UNAUTHORISED,

    /**
     * The node is currently being edited by some client; used as a soft lock so concurrent
     * editors can see a `Someone else is editing this` hint.
     */
    EDITING,

    /**
     * The node was reset to its initial / cleared state by a [krill.zone.shared.node.NodeAction.RESET]
     * action; parallel to [EXECUTED] for the reset execution path.
     */
    RESET,
}
