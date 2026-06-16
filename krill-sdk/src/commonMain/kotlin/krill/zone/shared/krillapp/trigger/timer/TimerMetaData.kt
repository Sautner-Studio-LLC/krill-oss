/**
 * Metadata for the `Timer` trigger — a node that fires once
 * when invoked, can be REST or EXCUTED by an observed node; the
 * [CronMetaData.timestamp] field is updated by the server each time the
 * trigger fires so clients can render "last fired" without a separate query.
 */
package krill.zone.shared.krillapp.trigger.timer

import kotlinx.serialization.*
import krill.zone.shared.krillapp.datapoint.*
import krill.zone.shared.node.*

/**
 * Payload for a `Timer` trigger node.
 */
@Serializable
data class TimerMetaData(
    /** Display name shown in the editor and on the node chip. */
    val name: String,
    /** Epoch millis of the most recent fire — `0` if the trigger has not fired yet. */
    val timestamp: Long = 0L,
    /** time to wait  */
    val delay: Long = 1000L,
    override val nodeAction: NodeAction = NodeAction.EXECUTE,
    override val error: String = "",
    override val sources: List<NodeIdentity> = emptyList(),
    override val snapshot: Snapshot = Snapshot(),
    override val invocationTriggers: List<InvocationTrigger> = emptyList(),
    override val inputs: List<NodeIdentity> = emptyList(),
) : SourceMetaData {
    override fun withError(error: String) = copy(error = error)
}
