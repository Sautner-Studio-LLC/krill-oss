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
import krill.zone.shared.node.ExecutionSource
import krill.zone.shared.node.NodeAction
import krill.zone.shared.node.NodeIdentity
import krill.zone.shared.node.TargetingNodeMetaData

/**
 * Payload for a `Lambda` executor node.
 */
@Serializable
data class LambdaSourceMetaData(
    override val sources: List<NodeIdentity> = emptyList(),
    override val targets: List<NodeIdentity> = emptyList(),
    /** User-supplied tag/value pairs; passed through to the script as environment-style data. */
    val tags: Map<String, String> = emptyMap(),
    /** Filename of the uploaded `.py` source on the server (e.g. `"average.py"`). */
    val filename: String = "",
    /** Epoch millis the source file was last uploaded — drives cache busting on clients. */
    val timestamp: Long = 0L,
    override val executionSource: List<ExecutionSource> = emptyList(),
    override val nodeAction: NodeAction = NodeAction.EXECUTE,
    override val error: String = "",
) : TargetingNodeMetaData
