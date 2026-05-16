/**
 * Metadata for a `Client` node — the synthetic node that represents the
 * Krill UI process itself in the swarm graph. One of these is created on
 * every running Compose / browser / Android client so peers can list who is
 * currently watching the swarm.
 */
package krill.zone.shared.krillapp.client

import kotlinx.serialization.*
import krill.zone.shared.node.ExecutionSource
import krill.zone.shared.node.NodeAction
import krill.zone.shared.node.NodeIdentity
import krill.zone.shared.node.TargetingNodeMetaData

/**
 * Payload for a `Client` node.
 */
@Serializable
data class ClientMetaData(
    /** Hostname of the device hosting this client — shown to peers. */
    val name: String,
    /** `true` while the client is still walking the user through first-time setup. */
    val ftue: Boolean = true,
    /** `true` while the client is offering to enable verbose logging on this device. */
    val logginPrompt: Boolean = true,
    /** `true` when verbose logging has been opted into. */
    val logging: Boolean = false,
    override val error: String = "",
    override val sources: List<NodeIdentity> = emptyList(),
    override val targets: List<NodeIdentity> = emptyList(),
    override val executionSource: List<ExecutionSource> = emptyList(),
    override val nodeAction: NodeAction = NodeAction.EXECUTE,
) : TargetingNodeMetaData
