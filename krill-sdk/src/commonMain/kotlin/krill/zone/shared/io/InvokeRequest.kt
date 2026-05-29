package krill.zone.shared.io

import krill.zone.shared.node.NodeAction
import krill.zone.shared.node.NodeIdentity
import kotlinx.serialization.Serializable

/**
 * Body of `POST /node/{id}/invoke`. The single explicit invocation entry
 * point on the HTTP surface — the replacement for the legacy click pattern
 * (clients posting a node with `state = NodeState.EXECUTED` to `/node/{id}`).
 *
 * Phase 4 (`separate-crud-from-invocation`): the server-side `update()` no
 * longer wakes a processor; deliberate invocation goes through this endpoint
 * which calls `ServerNodeManager.invoke(target, by, verb)`.
 *
 * @property by   Identity of the node (or actor) that triggered this call.
 *                For a UI click on a button-style node, the conventional
 *                value is the target's own identity (`by = target.id()`),
 *                modelling the click as a self-EXECUTE. Cross-server or
 *                automation-driven invocations carry the originator's
 *                identity so the receiver knows who fired it.
 * @property verb The action being applied — `EXECUTE` (default) or `RESET`.
 */
@Serializable
data class InvokeRequest(
    val by: NodeIdentity,
    val verb: NodeAction = NodeAction.EXECUTE,
)
