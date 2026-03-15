package krill.zone.shared.node

import krill.zone.shared.*
import kotlin.uuid.*

class NodeBuilder {

    var id: String? = null
    var parent: String? = null
    var type: KrillApp? = null
    private var meta: NodeMetaData? = null
    private var host: String? = null

    private var state: NodeState = NodeState.NONE


    fun id(id: String) = apply { this.id = id }
    fun parent(parent: String) = apply { this.parent = parent }
    fun type(type: KrillApp) = apply { this.type = type }
    fun meta(meta: NodeMetaData) = apply { this.meta = meta }
    fun host(host: String) = apply { this.host = host }
    fun state(state: NodeState) = apply { this.state = state }
    fun node(node: Node) = apply {
        this.id = node.id
        this.type = node.type
        this.parent = node.parent
        this.host = node.host
        this.meta = node.meta
        this.state = node.state

    }


     @ExperimentalUuidApi
     fun create(): Node {

        val checkedId = this.id ?: Uuid.random().toString()
        if (type is KrillApp.Server && parent == null) {
            parent = checkedId
        }



        return Node(
            id = checkedId,
            parent = parent ?: throw NullPointerException("Parent must not be null"),
            host = host ?: throw NullPointerException("Host must not be null"),
            type = type ?: throw NullPointerException("Node Type must not be null"),
            state = state,
            meta = meta ?: throw NullPointerException("Node Meta Data must not be null"),


            )



    }


}





