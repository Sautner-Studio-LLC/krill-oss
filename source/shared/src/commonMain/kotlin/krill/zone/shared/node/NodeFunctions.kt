package krill.zone.shared.node

import io.ktor.http.*
import kotlinx.coroutines.flow.*
import krill.zone.shared.*
import krill.zone.shared.krillapp.datapoint.*
import krill.zone.shared.krillapp.datapoint.graph.*
import krill.zone.shared.krillapp.executor.lambda.*
import krill.zone.shared.krillapp.project.*
import krill.zone.shared.krillapp.project.camera.*
import krill.zone.shared.krillapp.project.diagram.*
import krill.zone.shared.krillapp.project.journal.*
import krill.zone.shared.krillapp.project.tasklist.*
import krill.zone.shared.krillapp.server.*
import krill.zone.shared.krillapp.server.backup.*
import krill.zone.shared.krillapp.server.pin.*
import krill.zone.shared.krillapp.server.serialdevice.*
import kotlin.uuid.*


fun Node.https(): Url {
    val meta = this.meta as ServerMetaData
    return URLBuilder(
        protocol = URLProtocol.HTTPS,
        host = meta.resolvedHost(),
        port = meta.port
    ).build()
}

fun Node.key() : String {
    return "${this.timestamp}:${this.id}"
}

@OptIn(ExperimentalUuidApi::class)
fun toPeer(node: Node) : Node {


    return node.copy(
        type = KrillApp.Server.Peer, 
        id = "${installId()}:${node.id}", 
        parent = installId(),

    )
}

fun Node.id() : NodeIdentity {
    return NodeIdentity(
        nodeId = this.id,
        hostId = this.host
    )
}

fun Node.name() : String {

   return when (this.type) {
        KrillApp.DataPoint -> {
            (this.meta as DataPointMetaData).name
        }
        KrillApp.Server.SerialDevice -> {
            (this.meta as SerialDeviceMetaData).hardwareId
        }

        KrillApp.Server, KrillApp.Server.Peer -> {
            val meta = this.meta as ServerMetaData
            meta.name

        }

       KrillApp.Server.Peer -> {
           (this.meta as ServerMetaData).name
       }

       KrillApp.Client -> {
          hostName
       }

       KrillApp.Server.Pin -> {
           (this.meta as PinMetaData).name
       }

       KrillApp.DataPoint.Graph -> {
           (this.meta as GraphMetaData).name
       }

       KrillApp.Project.TaskList -> {
           (this.meta as TaskListMetaData).name
       }

       KrillApp.Project.Journal -> {
           (this.meta as JournalMetaData).name
       }

       KrillApp.Project.Camera -> {
           (this.meta as CameraMetaData).name.ifEmpty { "Camera" }
       }

       KrillApp.Server.Backup -> {
           (this.meta as BackupMetaData).name.ifEmpty { "Backup" }
       }

       KrillApp.Project -> {
           (this.meta as ProjectMetaData).name
       }

       KrillApp.Project.Diagram -> {
           (this.meta as DiagramMetaData).name
       }

       KrillApp.Executor.Lambda -> {

           val meta = this.meta as LambdaSourceMetaData
           if (meta.filename.isNotEmpty()) {
               meta.filename.removeSuffix(".py")
           } else {
               ""
           }


       }

       else -> {

            this.type.toString()
        }
    }

}
/**
 * Returns the snapshot color for a COLOR DataPoint as a packed ARGB Long (0xFFRRGGBB).
 * Returns 0xFF000000 (black) if the node is not a COLOR DataPoint or parsing fails.
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

fun Node.isMine(): Boolean {
    return  this.host == installId()
}

fun Node.details(): String {
    return "${this.timestamp} ${this.type} ${this.name()} ${this.state}"
}

fun StateFlow<Node>.details(): String {
    return "${this.value.timestamp} ${this.value.type} ${this.value.name()} ${this.value.state} "
}


fun KrillApp.isMenuOption(): Boolean {

    return (this is MenuCommand)
}


@OptIn(ExperimentalUuidApi::class)
fun KrillApp.node(): Node {

    return NodeBuilder().type(this).parent(installId()).id(Uuid.random().toString()).host(installId()).meta(this.meta()).create()

}