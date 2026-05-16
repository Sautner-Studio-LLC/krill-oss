/**
 * Metadata for the `Executor` parent node — Krill's generic "trigger an
 * action" container. Subtypes (`OutgoingWebHook`, `Lambda`, `SMTP`,
 * `Compute`, ...) each carry their own typed metadata; this base form
 * holds only the source-discriminator pair (`sourceType` + `source`) used
 * by the editor to filter which kinds of upstream nodes are wireable.
 *
 * File name (`ExecutorMetaData.kt`) and class name (`ExecuteMetaData`)
 * differ — historical naming preserved verbatim from the `/shared` source
 * to keep the FQN and `@SerialName` stable.
 */
package krill.zone.shared.krillapp.executor

import kotlinx.serialization.*
import krill.zone.shared.KrillApp
import krill.zone.shared.node.ExecutionSource
import krill.zone.shared.node.NodeAction
import krill.zone.shared.node.NodeIdentity
import krill.zone.shared.node.TargetingNodeMetaData

/**
 * Payload for the generic `Executor` node.
 */
@Serializable
data class ExecuteMetaData(
    val name: String,
    /** Optional [KrillApp] discriminator used by the editor to filter wireable source types. */
    val sourceType: KrillApp? = null,
    /** Free-form source identifier — typically a node id, but may be a path or scheme-specific string. */
    val source: String = "",
    override val error: String = "",
    override val sources: List<NodeIdentity> = emptyList(),
    override val targets: List<NodeIdentity> = emptyList(),
    override val executionSource: List<ExecutionSource> = emptyList(),
    override val nodeAction: NodeAction = NodeAction.EXECUTE,
) : TargetingNodeMetaData
