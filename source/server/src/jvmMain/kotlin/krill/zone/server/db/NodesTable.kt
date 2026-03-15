package krill.zone.server.db

import org.jetbrains.exposed.v1.core.*


/**
 * Exposed table definition for Node storage
 */
object NodesTable : Table("nodes") {
    val id = varchar("id", 255).uniqueIndex()
    val parent = varchar("parent", 255).index()
    val host = varchar("host", 255)

    val state = varchar("state", 50)

    val timestamp = long("timestamp")
    val nodeType = varchar("node_type", 255).default("").index()
    val nodeData = text("node_data")  // Full JSON serialized Node
    
    override val primaryKey = PrimaryKey(id)
}
