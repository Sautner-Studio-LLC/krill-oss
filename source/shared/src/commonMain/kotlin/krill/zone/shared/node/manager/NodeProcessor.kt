package krill.zone.shared.node.manager

import krill.zone.shared.node.*

interface NodeProcessor {
    fun post(node: Node)
    suspend fun process(node: Node): Boolean
}