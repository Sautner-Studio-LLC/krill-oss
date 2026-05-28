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
import krill.zone.shared.*
import krill.zone.shared.krillapp.datapoint.*
import krill.zone.shared.node.*

/**
 * Payload for the generic `Executor` node.
 */
@Serializable
data class ExecuteMetaData(
    val name: String,
    /** Optional [KrillApp] discriminator used by the editor to filter wireable source types. */
    val sourceType: KrillApp? = null,

    override val error: String = "",
    override val sources: List<NodeIdentity> = emptyList(),
    override val snapshot: Snapshot = Snapshot(),
    override val invocationTriggers: List<InvocationTrigger> = emptyList(),
    override val nodeAction: NodeAction = NodeAction.EXECUTE,
    override val inputs: List<NodeIdentity> = emptyList(),
) : SourceMetaData
