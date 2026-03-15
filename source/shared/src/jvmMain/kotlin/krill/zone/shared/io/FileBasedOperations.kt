package krill.zone.shared.io

import krill.zone.shared.node.*
import krill.zone.shared.node.persistence.*

/**
 * File-based implementation of FileOperations for desktop clients
 * Delegates to NodePersistence
 */
class FileBasedOperations(private val persistence: NodePersistence) : FileOperations {

    override fun read(id: String): Node? {
        return persistence.read(id)
    }
    
    override fun update(node: Node) {
        persistence.save(node)
    }
    
    override fun delete(id: String) {
        persistence.delete(id)
    }
    
    override fun load(): List<Node> {
        return persistence.loadAll()
    }
}
