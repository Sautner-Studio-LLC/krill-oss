/**
 * Metadata for the `Lambda` executor — runs a user-supplied Python script
 * (uploaded as a `.py` file to the server) against its source DataPoints and
 * writes the result to its targets.
 *
 * The script's bytes live on the server's filesystem; this metadata holds the
 * filename and an optional tag map the script can read at runtime.
 */
package krill.zone.shared.krillapp.executor.lambda

import kotlinx.serialization.*
import krill.zone.shared.krillapp.datapoint.*
import krill.zone.shared.node.*

/**
 * Payload for a `Lambda` executor node.
 */
@Serializable
data class LambdaMetaData(
    override val sources: List<NodeIdentity> = emptyList(),
    override val snapshot: Snapshot = Snapshot(),

    /** Filename of the uploaded `.py` source on the server (e.g. `"average.py"`). */
    val filename: String = "",
    /** Epoch millis the source file was last uploaded — drives cache busting on clients. */
    val timestamp: Long = 0L,
    override val invocationTriggers: List<InvocationTrigger> = emptyList(),
    override val nodeAction: NodeAction = NodeAction.EXECUTE,
    override val error: String = "",
    override val inputs: List<NodeIdentity> = emptyList(),
) : SourceMetaData {
    override fun withError(error: String) = copy(error = error)
    override fun displayName() = if (filename.isNotEmpty()) filename.removeSuffix(".py") else ""
}
