package krill.zone.server.db

import krill.zone.shared.node.persistence.*

/**
 * Repository interface for Node persistence operations
 * Extends NodePersistence for database-specific implementations
 */
interface NodeRepository : NodePersistence {
    // Inherits all methods from NodePersistence
}
