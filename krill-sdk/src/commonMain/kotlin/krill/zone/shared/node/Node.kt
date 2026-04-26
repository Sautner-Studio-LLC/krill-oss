/**
 * The atomic unit of swarm state in Krill: every observable thing — server,
 * pin, DataPoint, project, trigger — is a [Node]. The shape is uniform so
 * cross-cutting machinery (SSE stream, polymorphic JSON, UI rendering,
 * snapshot persistence) can treat any node identically without knowing the
 * concrete subtype; the [type] discriminator and the polymorphic [meta]
 * payload carry everything subtype-specific.
 *
 * Note: the Compose `@Immutable` stability hint that this type carries
 * inside the `/shared` module has been intentionally dropped from the SDK
 * to avoid pulling the Compose Multiplatform runtime into the SDK's
 * dependency surface. The JSON wire format and behaviour are unchanged.
 */
package krill.zone.shared.node

import kotlinx.serialization.*
import krill.zone.shared.KrillApp

/**
 * A single node in a Krill swarm.
 */
@Serializable
data class Node(
    /** Stable per-node UUID, unique within [host]. */
    val id: String,
    /** UUID of the parent node — for top-level nodes, equals their own [id]. */
    val parent: String,
    /** UUID of the server hosting this node. */
    val host: String,
    /** Type discriminator — selects the concrete shape of [meta]. */
    val type: KrillApp,
    /** Lifecycle / status — see [NodeState]. Defaults to `NONE`. */
    val state: NodeState = NodeState.NONE,
    /** Polymorphic metadata payload; the concrete subtype matches [type]. */
    val meta: NodeMetaData,
    /** Producer-supplied epoch millis used for ordering and snapshot keys. */
    val timestamp: Long = 0L,
)
