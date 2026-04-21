package krill.zone.server.events

import krill.zone.shared.node.NodeState

/**
 * Single source of truth for which [NodeState] values the `/sse` route
 * forwards to connected clients. Replaces an implicit `state != NONE`
 * check so that future [NodeState] additions must explicitly classify
 * themselves (enforced by [SseStateForwardingTest]).
 */
object SseStateForwarding {

    /** States forwarded over SSE. */
    val forwarded: Set<NodeState> = setOf(
        NodeState.PAUSED,
        NodeState.INFO,
        NodeState.WARN,
        NodeState.SEVERE,
        NodeState.ERROR,
        NodeState.PAIRING,
        NodeState.PROCESSING,
        NodeState.EXECUTED,
        NodeState.DELETING,
        NodeState.CREATED,
        NodeState.USER_EDIT,
        NodeState.USER_SUBMIT,
        NodeState.SNAPSHOT_UPDATE,
        NodeState.UNAUTHORISED,
        NodeState.EDITING,
    )

    /** States suppressed — idle/reset transitions aren't broadcast. */
    val filtered: Set<NodeState> = setOf(
        NodeState.NONE,
    )

    fun shouldForward(state: NodeState): Boolean = state in forwarded
}
