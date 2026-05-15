/**
 * Metadata for the `OutgoingWebHook` executor — fires an outbound HTTP
 * request when triggered, with user-configured method, URL, params, and
 * headers. Used to bridge Krill swarm events to external services.
 */
package krill.zone.shared.krillapp.executor.webhook

import kotlinx.serialization.*
import krill.zone.shared.io.HttpMethod
import krill.zone.shared.node.ExecutionSource
import krill.zone.shared.node.NodeAction
import krill.zone.shared.node.NodeIdentity
import krill.zone.shared.node.TargetingNodeMetaData

/**
 * Payload for an `OutgoingWebHook` executor node.
 */
@Serializable
data class WebHookOutMetaData(
    val name: String = "",
    override val sources: List<NodeIdentity> = emptyList(),
    override val targets: List<NodeIdentity> = emptyList(),
    /** Full target URL, including scheme. */
    val url: String = "",
    /** HTTP verb to use — defaults to `GET`. */
    val method: HttpMethod = HttpMethod.GET,
    /**
     * Query / form params, as ordered key/value pairs. `List<Pair>` rather
     * than `Map` so authors can express the same key twice (e.g.
     * `?tag=a&tag=b`), which servers like Prometheus actually require.
     */
    val params: List<Pair<String, String>> = emptyList(),
    /**
     * HTTP headers, as ordered key/value pairs. Same multi-value rationale as
     * [params].
     */
    val headers: List<Pair<String, String>> = emptyList(),
    override val executionSource: List<ExecutionSource> = emptyList(),
    override val nodeAction: NodeAction = NodeAction.EXECUTE,
    override val error: String = "",
) : TargetingNodeMetaData
