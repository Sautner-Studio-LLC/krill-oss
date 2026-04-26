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
import krill.zone.shared.krillapp.datapoint.graph.GraphMetaData
import krill.zone.shared.krillapp.executor.lambda.LambdaSourceMetaData
import krill.zone.shared.krillapp.project.ProjectMetaData
import krill.zone.shared.krillapp.project.camera.CameraMetaData
import krill.zone.shared.krillapp.project.diagram.DiagramMetaData
import krill.zone.shared.krillapp.project.journal.JournalMetaData
import krill.zone.shared.krillapp.project.tasklist.TaskListMetaData
import krill.zone.shared.krillapp.server.ServerMetaData
import krill.zone.shared.krillapp.server.backup.BackupMetaData
import krill.zone.shared.krillapp.server.pin.PinMetaData
import krill.zone.shared.krillapp.server.serialdevice.SerialDeviceMetaData

/**
 * Returns the canonical `https://<resolvedHost>:<port>` [Url] for a server
 * node. Caller must guarantee `node.meta` is a [ServerMetaData] — typically
 * by checking `node.type is KrillApp.Server`.
 */
fun Node.https(): Url {
    val meta = this.meta as ServerMetaData
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
 * [NodeIdentity] suitable for use inside [TargetingNodeMetaData] source /
 * target lists.
 */
fun Node.id(): NodeIdentity = NodeIdentity(
    nodeId = this.id,
    hostId = this.host,
)

/**
 * Returns the human-readable display name for the node. Each [KrillApp]
 * subtype reads its name out of the appropriate concrete `MetaData` field.
 *
 * The `else` arm stringifies the [KrillApp] type itself, which is the
 * desired fallback for `MenuCommand` entries and any subtype whose
 * MetaData has no dedicated name field.
 */
fun Node.name(): String {
    return when (this.type) {
        KrillApp.DataPoint -> (this.meta as DataPointMetaData).name
        KrillApp.Server.SerialDevice -> (this.meta as SerialDeviceMetaData).hardwareId
        KrillApp.Server, KrillApp.Server.Peer -> (this.meta as ServerMetaData).name
        KrillApp.Client -> (this.meta as krill.zone.shared.krillapp.client.ClientMetaData).name
        KrillApp.Server.Pin -> (this.meta as PinMetaData).name
        KrillApp.DataPoint.Graph -> (this.meta as GraphMetaData).name.ifEmpty { "Graph" }
        KrillApp.Project.TaskList -> (this.meta as TaskListMetaData).name
        KrillApp.Project.Journal -> (this.meta as JournalMetaData).name
        KrillApp.Project.Camera -> (this.meta as CameraMetaData).name.ifEmpty { "Camera" }
        KrillApp.Server.Backup -> (this.meta as BackupMetaData).name.ifEmpty { "Backup" }
        KrillApp.Project -> (this.meta as ProjectMetaData).name
        KrillApp.Project.Diagram -> (this.meta as DiagramMetaData).name
        KrillApp.Executor.Lambda -> {
            val meta = this.meta as LambdaSourceMetaData
            if (meta.filename.isNotEmpty()) meta.filename.removeSuffix(".py") else ""
        }
        else -> this.type.toString()
    }
}

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
