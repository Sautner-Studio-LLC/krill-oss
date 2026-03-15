package krill.zone.shared.io.beacon

import krill.zone.shared.krillapp.server.*
import krill.zone.shared.node.*

interface ServerConnector {

    suspend fun connectWire(wire: NodeWire)
    suspend fun connectMeta(meta: ServerMetaData)
    suspend fun connectNode(node: Node)
}