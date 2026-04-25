/**
 * Metadata for the `CronTimer` trigger — a node that fires on a quartz/cron
 * schedule. The expression is parsed and evaluated server-side; the
 * [CronMetaData.timestamp] field is updated by the server each time the
 * trigger fires so clients can render "last fired" without a separate query.
 */
package krill.zone.shared.krillapp.trigger.cron

import kotlinx.serialization.*
import krill.zone.shared.node.NodeMetaData

/**
 * Payload for a `CronTimer` trigger node.
 */
@Serializable
data class CronMetaData(
    /** Display name shown in the editor and on the node chip. */
    val name: String,
    /** Epoch millis of the most recent fire — `0` if the trigger has not fired yet. */
    val timestamp: Long = 0L,
    /** Quartz-style cron expression evaluated by the server scheduler. */
    val expression: String = "",
    override val error: String = "",
) : NodeMetaData
