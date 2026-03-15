package krill.zone.shared.node.persistence

import krill.zone.shared.*
import krill.zone.shared.node.*

/**
 * Interface for Node persistence operations
 * Platform-specific implementations handle storage (file-based, database, etc.)
 */
interface NodePersistence {
    /**
     * Load all nodes from storage
     */
    fun loadAll(): List<Node>
    
    /**
     * Read a single node by ID
     */
    fun read(id: String): Node?
    
    /**
     * Save or update a node
     */
    fun save(node: Node)
    
    /**
     * Delete a node by ID
     */
    fun delete(id: String)

    /**
     * Load all nodes of a specific type by their qualified class name.
     * Implementations may override this to use an indexed column for efficiency.
     */
    fun loadByType(type: KrillApp): List<Node>

    fun children(node: Node) : List<Node>
}
