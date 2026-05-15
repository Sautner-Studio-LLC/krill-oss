/**
 * Metadata for the `IncomingWebHook` trigger — exposes a server-side HTTP
 * endpoint that, when called, fires the trigger and (optionally) writes a
 * value to a target node. Used to bridge external events back into the swarm.
 */
package krill.zone.shared.krillapp.trigger.webhook

import kotlinx.serialization.*
import krill.zone.shared.io.HttpMethod
import krill.zone.shared.node.ExecutionSource
import krill.zone.shared.node.NodeAction
import krill.zone.shared.node.NodeIdentity
import krill.zone.shared.node.TargetingNodeMetaData

/**
 * Payload for an `IncomingWebHook` trigger node.
 *
 * Older payloads carried a single `target: String` in `"<host>:<id>"` format;
 * the field is preserved in deprecated form so legacy persisted records still
 * deserialise. Newly-created records use [targets] directly. The default
 * value of [targets] is computed from a non-empty deprecated [target] so an
 * existing record migrates on first read.
 */
@Serializable
data class IncomingWebHookMetaData(
    val name: String = "",
    /** URL path the server exposes for this hook (e.g. `"/webhook/garage-open"`). */
    val path: String = "",
    @Deprecated(
        "Use targets instead",
        replaceWith = ReplaceWith("targets.firstOrNull()?.nodeId ?: \"\""),
    )
    val target: String = "",
    /** HTTP verb the trigger accepts — defaults to `GET`. */
    val method: HttpMethod = HttpMethod.GET,
    override val sources: List<NodeIdentity> = emptyList(),
    @Suppress("DEPRECATION")
    override val targets: List<NodeIdentity> = if (target.isNotEmpty()) {
        if (target.contains(":")) {
            val parts = target.split(":")
            listOf(NodeIdentity(parts.last(), parts.first()))
        } else {
            listOf(NodeIdentity(target, ""))
        }
    } else {
        emptyList()
    },
    override val executionSource: List<ExecutionSource> = emptyList(),
    override val nodeAction: NodeAction = NodeAction.EXECUTE,
    override val error: String = "",
) : TargetingNodeMetaData
