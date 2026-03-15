package krill.zone.server.db

import co.touchlab.kermit.*
import krill.zone.shared.*
import krill.zone.shared.node.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.*
import org.koin.ext.*

/**
 * Exposed-based implementation of NodeRepository using H2 database
 */
class ExposedNodeRepository : NodeRepository {
    private val logger = Logger.withTag(this::class.getFullName())
    
    override fun loadAll(): List<Node> {
        return try {
            transaction {
                NodesTable.selectAll().mapNotNull { row ->
                    try {
                        val json = row[NodesTable.nodeData]
                        fastJson.decodeFromString<Node>(json)
                    } catch (e: Exception) {
                        logger.e("Error deserializing node ${row[NodesTable.id]}", e)
                        null
                    }
                }
            }
        } catch (e: Exception) {
            logger.e("Error loading nodes from database", e)
            emptyList()
        }
    }
    
    override fun loadByType(type: KrillApp): List<Node> {
        return try {
            val typeName = type::class.qualifiedName ?: throw Exception("Unknown type name")
            transaction {
                NodesTable.selectAll().where { NodesTable.nodeType eq typeName }
                    .mapNotNull { row ->
                        try {
                            val json = row[NodesTable.nodeData]
                            fastJson.decodeFromString<Node>(json)
                        } catch (e: Exception) {
                            logger.e("Error deserializing node ${row[NodesTable.id]}", e)
                            null
                        }
                    }
            }
        } catch (e: Exception) {
            logger.e("Error loading nodes by type $type from database", e)
            emptyList()
        }
    }

    override fun children(node: Node): List<Node> {
        return try {
            transaction {
                NodesTable.selectAll().where { NodesTable.parent eq node.id }
                    .mapNotNull { row ->
                        try {
                            val json = row[NodesTable.nodeData]
                            fastJson.decodeFromString<Node>(json)
                        } catch (e: Exception) {
                            logger.e("Error deserializing node ${row[NodesTable.id]}", e)
                            null
                        }
                    }
            }
        } catch (e: Exception) {
            logger.e("{${node.details()}: Error loading children from database", e)
            emptyList()
        }
    }
    
    override fun read(id: String): Node? {
        return try {
            transaction {
                NodesTable.selectAll().where { NodesTable.id eq id }
                    .singleOrNull()
                    ?.let { row ->
                        try {
                            val json = row[NodesTable.nodeData]
                            fastJson.decodeFromString<Node>(json)
                        } catch (e: Exception) {
                            logger.e("Error deserializing node $id", e)
                            null
                        }
                    }
            }
        } catch (e: Exception) {
            logger.e("Error reading node $id from database", e)
            null
        }
    }
    
    override fun save(node: Node) {
        try {
            transaction {
                val json = fastJson.encodeToString(node)
                val nodeTypeName = node.type::class.qualifiedName ?: node.type::class.java.name

                    NodesTable.upsert {
                        it[id] = node.id
                        it[parent] = node.parent
                        it[host] = node.host
                        it[state] = node.state.name
                        it[timestamp] = node.timestamp
                        it[nodeType] = nodeTypeName
                        it[nodeData] = json

                }
            }
        } catch (e: Exception) {
            logger.e("Error saving node ${node.id} to database", e)
            throw e
        }
    }
    
    override fun delete(id: String) {
        try {
            transaction {
                NodesTable.deleteWhere { NodesTable.id eq id }
            }
        } catch (e: Exception) {
            logger.e("Error deleting node $id from database", e)
            throw e
        }
    }
}
