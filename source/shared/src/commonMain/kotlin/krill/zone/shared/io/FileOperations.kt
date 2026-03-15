package krill.zone.shared.io

import krill.zone.shared.node.*


interface FileOperations {

    fun read(id: String): Node?
    fun update(node: Node)
    fun delete(id: String)
    fun load(): List<Node>
}