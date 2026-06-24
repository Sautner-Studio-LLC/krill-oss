/**
 * Pure extension functions that operate on a [Node]. Limited to the helpers
 * whose dependencies live entirely in the SDK; the few helpers that need
 * `installId()` (`Node.isMine()`, `toPeer(...)`, `KrillApp.node()`) remain
 * in `/shared`, where the platform actuals for the install id live.
 */
package krill.zone.shared.node

import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.coroutines.flow.StateFlow
import krill.zone.shared.KrillApp
import krill.zone.shared.MenuCommand
import krill.zone.shared.krillapp.datapoint.DataPointMetaData
import krill.zone.shared.krillapp.datapoint.DataType
import krill.zone.shared.krillapp.server.ServerMetaData

/**
 * Returns the canonical `https://<resolvedHost>:<port>` [Url] for a server
 * node. Throws [IllegalArgumentException] with a diagnostic message when
 * [meta] is not a [ServerMetaData] — prefer calling this only on nodes whose
 * [type] is [KrillApp.Server].
 */
fun Node.https(): Url {
    val meta = this.meta as? ServerMetaData
        ?: throw IllegalArgumentException(
            "Node.https() requires ServerMetaData; got ${this.meta::class.simpleName} " +
                "(id=${this.id}, type=${this.type})"
        )
    return URLBuilder(
        protocol = URLProtocol.HTTPS,
        host = meta.resolvedHost(),
        port = meta.port,
    ).build()
}

/**
 * Renders the node as a `"<timestamp>:<id>"` key. Used as a stable cache /
 * comparison key in places where (timestamp, id) tuples need to be compared
 * by value rather than by structural equality of the full node.
 */
fun Node.key(): String = "${this.timestamp}:${this.id}"

/**
 * Returns this node's address pair — `(nodeId, hostId)` — as a
 * [NodeIdentity] suitable for use inside [SourceMetaData] source /
 * target lists.
 */
fun Node.id(): NodeIdentity = NodeIdentity(
    nodeId = this.id,
    hostId = this.host,
)

/**
 * Returns the human-readable display name for the node. Delegates to
 * [NodeMetaData.displayName]; when that returns empty, falls back to the
 * node's [KrillApp] type string (the desired behaviour for MenuCommand
 * entries and any subtype with no dedicated name field).
 */
fun Node.name(): String = this.meta.displayName().ifEmpty { this.type.toString() }

/**
 * For a `DataPoint` whose [DataType] is `COLOR`, returns the snapshot value
 * parsed as a packed ARGB `Long` (`0xFFRRGGBB`). For any other node type or
 * an unparseable value, returns opaque black (`0xFF000000`) so callers can
 * unconditionally render the result without `null`-checking.
 */
fun Node.snapshotColorArgb(): Long {
    val meta = this.meta as? DataPointMetaData ?: return 0xFF000000L
    if (meta.dataType != DataType.COLOR) return 0xFF000000L
    return try {
        val colorInt = meta.snapshot.value.toLong() and 0xFFFFFFL
        0xFF000000L or colorInt
    } catch (_: Exception) {
        0xFF000000L
    }
}

/**
 * Renders a debug-friendly one-liner of the node — used in log lines to
 * give the timestamp / type / display name / state in a compact form.
 */
fun Node.details(): String =
    "${this.timestamp} ${this.type} ${this.name()} ${this.state}"

/** Same as [Node.details], lifted to operate on a `StateFlow<Node>` directly. */
fun StateFlow<Node>.details(): String =
    "${this.value.timestamp} ${this.value.type} ${this.value.name()} ${this.value.state} "

/**
 * `true` when this [KrillApp] is a [MenuCommand] subtype rather than a real
 * node type. Used to skip menu-command discriminators in iteration code
 * that only cares about real swarm nodes.
 */
fun KrillApp.isMenuOption(): Boolean = this is MenuCommand

/** `true` when this node's value is inherently boolean / digital. Delegates to [NodeMetaData.isDigital]. */
fun Node.isDigital(): Boolean = this.meta.isDigital()