package krill.zone.shared.node

import io.ktor.http.*
import kotlinx.coroutines.flow.*
import krill.zone.shared.*
import krill.zone.shared.krillapp.datapoint.*
import krill.zone.shared.krillapp.datapoint.graph.*
import krill.zone.shared.krillapp.project.*
import krill.zone.shared.krillapp.project.journal.*
import krill.zone.shared.krillapp.project.tasklist.*
import krill.zone.shared.krillapp.server.*
import krill.zone.shared.krillapp.server.pin.*
import krill.zone.shared.krillapp.server.serialdevice.*
import kotlin.uuid.*


fun Node.https(): Url {
    val meta = this.meta as ServerMetaData
    return URLBuilder(
        protocol = URLProtocol.HTTPS,
        host = meta.name,
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

       KrillApp.Project -> {
           (this.meta as ProjectMetaData).name
       }

       else -> {

            this.type.toString()
        }
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